# RLS-003 — Guard de actor para `community_profiles`

## Resultado

Se preparó una migración **no desplegada** que elimina la actualización pública
incondicional de perfiles y protege los campos de identidad, roles, ciclo de
vida y contadores. La lectura pública requerida por los feeds y el alta anónima
que todavía utiliza Android se mantienen.

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
| Alta legacy segura | Sí | Sí | Sí | Sí |
| Editar datos propios | No | Sí | Sí | Sí |
| Editar datos de otro perfil | No | No | No | Sí |
| Cambiar roles | No | No | Sí | Sí |
| Cambiar identidad/ciclo/contadores | No | No | No | Sí |
| Borrar/truncar | No | No | No | Sí |

El alta legacy sigue admitiendo contraseña y recuperación porque Android aún
crea el perfil antes de recibir el JWT del Auth bridge. El servidor reemplaza
siempre cualquier `id` aportado por un cliente no privilegiado y rechaza
`auth_user_id`, roles, estado, desactivación y contadores no seguros.

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
2. alta anónima legacy y sustitución del UUID elegido por el cliente;
3. rechazo `42501` de escalada de admin en INSERT;
4. edición legítima del perfil propio;
5. bloqueo de suplantación y de UPDATE anónimo;
6. rechazo `42501` de cambios propios de `auth_user_id`, `is_admin` y estado;
7. asignación de rol por admin y bloqueo de edición de datos ajenos;
8. actualización de ciclo de vida por `service_role`;
9. limpieza de fixtures;
10. aplicación y comprobación del rollback.

Resultado local: `COMMUNITY_PROFILES_ACTOR_GUARD_TEST_OK`.

`scripts/run-community-profiles-postgrest-test.ps1` añade un PostgREST 12
efímero sobre esa base y repite por HTTP la lectura anónima, el alta legacy,
la edición propia, la suplantación, el UPDATE anónimo y las dos escaladas. El
resultado es `COMMUNITY_PROFILES_POSTGREST_TEST_OK`; al terminar elimina API,
base y red Docker, por lo que no conserva fixtures.

Los INSERT hostiles se prueban por separado para `auth_user_id`, `is_admin`,
`is_official`, estado/desactivación y contadores. Todos devuelven `42501`.

## Compatibilidad y orden de rollout

La lectura pública conserva su forma y no afecta a feeds Android/Web/iOS. El
INSERT anónimo mínimo conserva el registro Android actual. El UPDATE anónimo
de contraseña que usa el Android legacy deja de funcionar; por eso esta
migración debe publicarse en el mismo release coordinado que la migración de
registro/recuperación privilegiada `20260726171004`, nunca sola.

No debe retirarse la contención de cliente ni desplegarse este SQL hasta que el
release integrado ejecute las pruebas de registro, login, recuperación, perfil,
feed y lifecycle en staging.

### Rollout sin romper Android publicado

No existe una policy segura que pueda conservar el reset Android actual: ese
cliente envía un PATCH anónimo con sólo `pass_hash` y `pass_plain`, sin aportar
en la request ninguna prueba verificable por el servidor. Mantener ese PATCH
equivale a permitir que cualquiera que conozca un UUID cambie la contraseña.

Por tanto, `171003` no es candidata a producción por sí sola. El orden seguro es:

1. desplegar primero endpoints Edge/RPC de registro, pregunta y reset que
   validen la respuesta en servidor, sin cambiar grants;
2. publicar Android usando esos endpoints para alta/login/recuperación y dejar
   de leer o parchear credenciales en `community_profiles`;
3. medir adopción y, si el producto mantiene APK antiguas, aplicar una versión
   mínima antes del corte;
4. desplegar `171003` junto al contrato de auth ya adoptado;
5. ejecutar E2E cross-platform y sólo entonces retirar contenciones.

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
