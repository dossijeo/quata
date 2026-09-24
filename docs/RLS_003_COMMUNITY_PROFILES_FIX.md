# RLS-003 — Guard de actor para `community_profiles`

## Resultado

Se preparó una migración **no desplegada** que elimina la actualización pública
incondicional de perfiles y protege los campos de identidad, roles, ciclo de
vida y contadores. La lectura pública requerida por los feeds se mantiene. El
alta y el reset anónimos directos quedan limitados a la firma de request del AAB
Android v32 publicado y detrás de un interruptor de servidor.

La migración es
`supabase/migrations/20260726171003_community_profiles_actor_guard.sql`. Su
rollback revisado está fuera del directorio de migraciones automáticas, en
`supabase/rollbacks/20260726171003_community_profiles_actor_guard.rollback.sql`.
Ambos usan transacciones explícitas.

## Evidencia remota de solo lectura

La inspección del catálogo de producción se realizó dentro de una operación de
solo lectura y sin imprimir secretos. Confirmó:

- RLS activado, pero una policy `public update profiles` con `USING (true)` y
  `WITH CHECK (true)`.
- `anon` y `authenticated` disponen de `UPDATE`, `DELETE`, `TRUNCATE`,
  `REFERENCES` y `TRIGGER`, además de `SELECT` e `INSERT`.
- Esos grants alcanzan todas las columnas, incluidas `auth_user_id`,
  `is_admin`, `is_official`, `account_status` y los campos de desactivación.
- `quata_guard_profile_roles_trg` ejecuta
  `quata_guard_profile_roles()` como `SECURITY DEFINER`, propiedad de
  `postgres`.
- La función considera servicio a `current_user = postgres`; por ello, al
  ejecutarse como definidor, el guard de roles retorna antes de comprobar al
  actor. La defensa de `is_admin`/`is_official` no es efectiva.

No se ejecutó DDL ni DML contra el proyecto remoto.

## Contrato propuesto

| Operación | `anon` | dueño autenticado | admin autenticado | `service_role` |
|---|---:|---:|---:|---:|
| Leer perfiles | Sí | Sí | Sí | Sí |
| Alta legacy Android v32 | Sólo firma v32 | No | No | Sí |
| Editar datos propios | No | Sí | Sí | Sí |
| Editar datos de otro perfil | No | No | No | Sí |
| Cambiar roles | No | No | Sí | Sí |
| Cambiar identidad/ciclo/contadores | No | No | No | Sí |
| Borrar/truncar | No | No | No | Sí |

El alta legacy sigue admitiendo contraseña y recuperación exclusivamente para
Android v32, que crea el perfil antes de recibir el JWT del Auth bridge. El
grant de INSERT por columnas no permite aportar `id` y el servidor lo genera;
también rechaza
`auth_user_id`, roles, timestamps, estado, desactivación y contadores.

Las ediciones propias conservan nombre, avatar, ubicación, teléfono, contraseña
y pregunta/respuesta de recuperación. `id`, `auth_user_id`, timestamps de ciclo
de vida, roles y contadores quedan vinculados al servidor. Un administrador sólo
puede tocar `is_admin` e `is_official` en perfiles ajenos.

## Validación aislada

`scripts/run-community-profiles-actor-guard-test.ps1` levanta un PostgreSQL 16
efímero. Los roles `anon`, `authenticated` y `service_role`, junto con los GUC
`request.jwt.claim.*`, reproducen la semántica de actor de PostgREST. La prueba
aplica la migración real y verifica:

1. lectura anónima de feed;
2. alta anónima legacy con UUID generado por servidor y rechazo de UUID elegido
   por el cliente;
3. rechazo `42501` de escalada de admin en INSERT;
4. edición legítima del perfil propio;
5. bloqueo de suplantación y de UPDATE anónimo fuera de la firma v32;
6. rechazo `42501` de cambios propios de `auth_user_id`, `is_admin` y estado;
7. asignación de rol por admin y bloqueo de edición de datos ajenos;
8. actualización de ciclo de vida por `service_role`;
9. limpieza de fixtures;
10. firma v32 exacta para alta/reset, rechazo del flag moderno y de otros
    orígenes, contador de uso e interruptor de retirada;
11. aplicación y comprobación del rollback.

Resultado local: `COMMUNITY_PROFILES_ACTOR_GUARD_TEST_OK`.

`scripts/run-community-profiles-postgrest-test.ps1` añade un PostgREST 12
efímero sobre esa base y repite por HTTP la lectura anónima, el alta legacy,
la edición propia, la suplantación, el UPDATE anónimo y las dos escaladas. El
resultado es `COMMUNITY_PROFILES_POSTGREST_TEST_OK`; al terminar elimina API,
base y red Docker, por lo que no conserva fixtures.

Los INSERT hostiles se prueban por separado para `auth_user_id`, `is_admin`,
`is_official`, estado/desactivación, contadores e `id`. Todos devuelven
`42501`. El INSERT se concede por allowlist de columnas, de modo que una futura
columna queda denegada por defecto.

También se prueban dueño y administrador desactivados, tanto en SQL como en
PostgREST. Ambos reciben cero filas mutables: la policy y el trigger usan
`quata_chat_auth_profile_id()`, cuyo contrato exige `account_status = 'active'`.

## Compatibilidad y orden de rollout

La lectura pública conserva su forma y no afecta a feeds Android/Web/iOS. El
cliente actual añade `x-quata-client-generation: android-auth-boundary-v1` a
sus requests y usa `quata-register`/`quata-auth-bridge`. Ese flag impide que
entre en la excepción legacy aunque intentase un INSERT o PATCH directo.

No debe retirarse la contención de cliente ni desplegarse este SQL hasta que el
release integrado ejecute las pruebas de registro, login, recuperación, perfil,
feed y lifecycle en staging.

### Compatibilidad acotada con Android publicado

El AAB v32 no envía atestación ni un identificador criptográfico de la APK. No
es posible añadirlos retroactivamente. La excepción continúa siendo insegura
frente a un cliente no navegador capaz de falsificar cabeceras, y se limita a
la mínima superficie compatible:

- rol JWT `anon`, método y ruta exactos de PostgREST;
- `User-Agent: okhttp/4.12.0`, que coincide con la dependencia incorporada en
  el AAB v32, sin `Origin`/`Referer` ni flag de generación moderno;
- alta con allowlist de columnas, ID generado por servidor y rechazo de roles,
  identidad, estado, contadores y timestamps enviados por el cliente;
- reset que sólo puede cambiar `pass_hash` y `pass_plain`, exige que cambien
  juntos y verifica en PostgreSQL que el SHA-256 corresponde al plaintext;
- `request_count` y `last_used_at` para observar uso, sin guardar IP, teléfono,
  UUID, contraseña ni cabeceras;
- fila única `quata_legacy_android_v32_compatibility.enabled` como interruptor.

Cuando el contador permanezca estable durante la ventana de retirada acordada,
se desactiva la rama con una actualización administrada de esa fila. El rollback
versionado elimina policy, funciones y tabla de compatibilidad. Hasta entonces
la limitación residual queda descrita como deliberadamente insegura y exclusiva
del contrato v32; los clientes actuales no la usan.

### Preflight histórico obligatorio

`scripts/run-community-profiles-rollout-preflight.ps1` sólo ejecuta una
transacción `READ ONLY` y se niega a arrancar sin los fingerprints SHA-256
aprobados de los perfiles admin y official. No imprime IDs. Falla si detecta:

- una identidad Auth que el mapping `id OR auth_user_id` resuelva a más de un
  perfil;
- identidades telefónicas duplicadas tras canonicalizar todos los campos usados
  por el Auth bridge (`phone_e164`, `phone`, país+local, code+telefono y las
  variantes locales legacy);
- estados de desactivación incoherentes;
- contadores que no coincidan exactamente con las aristas reales de
  `community_profile_follows`, incluidos positivos manipulados;
- cualquier diferencia frente al inventario de roles privilegiados revisado.

Los fingerprints deben obtenerse y aprobarse durante la revisión operativa
privada; no se incluyen en Git. Un fallo bloquea el rollout y exige investigar
las filas afectadas, no corregirlas automáticamente.

La URL de base no se pasa como argumento de proceso: el runner monta el fichero
local como secreto de solo lectura y lo consume dentro del contenedor.

La reconciliación RLS-005 desplegada el 24 de septiembre de 2026 dejó los
contadores en cero diferencias. El preflight de esta candidata debe repetirse
contra ese estado y los fingerprints de roles aprobados; no modifica datos.

## Riesgo pendiente no incluido

La policy de lectura pública y los grants de tabla exponen actualmente también
`pass_hash`, `pass_plain`, `secret_answer` y otros datos de autenticación. No se
revocan aquí porque los clientes legacy todavía los consultan y el encargo
prohíbe romper la Web publicada. Se registra como RLS-004 y requiere migrar
todos los flujos de login/recuperación a RPC/Edge antes de otorgar SELECT sólo a
columnas públicas.

### Fase posterior para RLS-004

La exposición quedó confirmada también mediante un GET PostgREST anónimo real:
en una muestra de diez perfiles se recibieron valores no vacíos de
`pass_plain`, `pass_hash`, `secret_answer` y `auth_user_id`. No se registró
ningún valor, ID, teléfono ni secreto.

Cerrar RLS-004 requiere una migración posterior, también no desplegable hasta
completar los pasos 1–3 anteriores:

- revocar el SELECT de tabla a `anon` y `authenticated`;
- conceder SELECT por columna únicamente para el contrato público usado por
  Feed/Official/Communities (`id`, nombres visibles, barrio, avatar, contadores
  y flags públicos);
- mover pregunta de recuperación y perfil privado a RPC/Edge con contrato
  mínimo;
- eliminar `pass_plain` tras migrar/invalidar el legado y no devolver nunca
  `pass_hash` ni `secret_answer`;
- verificar que todos los `select=` Android/Web/iOS funcionan con las columnas
  concedidas y que pedir cualquier columna privada devuelve denegación.

Una vista pública dedicada es una alternativa válida, pero debe exponerse sólo
con la proyección anterior y probar explícitamente su comportamiento de
seguridad; no se debe crear una vista propietaria con `SELECT *`.

La unión exacta que consume hoy `PROFILE_PUBLIC_SELECT` y los repositorios Web
de Feed/Official/Communities es:

```text
id, display_name, phone, country_code, phone_local, barrio, neighborhood,
code, telefono, nombre, avatar_url, avatar, followers_count,
following_count, is_admin, is_official
```

Esa lista permite una primera revocación compatible de credenciales, aunque
`phone`, `phone_local`, `country_code`, `code` y `telefono` siguen siendo PII y
deben revisarse en una segunda reducción de producto. Los gateways de perfil
Web/iOS añaden hoy `secret_question`; antes del grant por columnas deberán dejar
de solicitarla y obtenerla exclusivamente desde el endpoint de recuperación.
Android debe reemplazar `PROFILE_AUTH_SELECT`, que actualmente añade
`pass_hash`, `pass_plain`, `secret_answer`, `created_at`, `last_login_at`,
`phone_e164` y `secret_question`.
