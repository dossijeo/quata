# Activación compatible de ACCOUNT-RECOVERY-SECRET

Estado: **código desplegado inicialmente como v23; revisión activa v59 con escritura
desactivada**, tras el segundo ensayo iOS y su restitución del 9 de septiembre de 2026.
La propuesta hashed se descartó por incompatibilidad con el AAB v32 publicado;
su análisis queda en el historial Git, no en estas instrucciones operativas.

Destino: proyecto Supabase `yrrlankpwmhluexshxnw`, función `quata-auth-bridge`.
Base desplegada v21 SHA-256
`d57969b79deca66926e6e78b575c556e2e2812d5d13dc4a85998f93428e0dc01`.
Paquete local: `build-reports/account-recovery-secret-salvage/deployment-review-compatible`.
SHA-256 de la función propuesta:
`4da3929f0fe4a63b4773402926e9848d8c3fd9509e53f4672905da4b2de0aa6f`.
SHA-256 del config:
`d9327ada00ffcb375f48059f7150636f55d2f60f827145a07578d94c69233191`.

El diff añade sólo el productor autenticado `update_recovery_secret`, versión 1:
valida JWT y campos, resuelve exactamente un perfil por auth_user_id y actualiza
pregunta/respuesta sobre ese id y usuario, comprobando una fila. Respuesta pública
sin secreto. Mantiene consumidor, login, formato de contraseña y admisión de claves
de v21. La escritura está desactivada por defecto. Conserva la deuda de almacenamiento
legacy necesaria para el AAB v32 publicado; no acredita confidencialidad SQL.

No requiere columna, pepper, cambio de RLS, grants, registro ni otros endpoints.
Revisión independiente aprobada. Siete pruebas locales del handler con servicios
simulados pasan. Deno 2.9.6 empaqueta 44 módulos. No es evidencia de backend real.

## Resultado del despliegue

El resumen previo se comunicó al propietario. Se descargó nuevamente la fuente de
producción v21 y coincidió con el hash base; el paquete exacto pasó sus siete pruebas
y revisión independiente para despliegue desactivado. Se fijó el flag en `false` y
se desplegó sólo el workdir indicado. La fuente descargada de v23 coincide con el
SHA-256 propuesto y `verify_jwt=false` se conserva. El digest remoto del flag coincide
con `false`.

Las sondas reales de login/web_login sin contraseña, recuperación sin perfil y consulta
de pregunta del actor auditado conservan status y respuesta de v21. El productor
devuelve 401 sin bearer o con bearer inválido y 400 para versión no soportada.
Recibo: `build-reports/account-recovery-secret-salvage/deployment-execution.json`.
Una cuenta temporal aislada pasó después la guarda real y un login Web satisfactorio,
con recibos de sesión verificados. El productor devolvió 503 con ese JWT válido y sin
campos de escritura; se revocaron sus sesiones y se eliminó el journal de la sonda.
Recibo: `disabled-producer-live.json` en el mismo directorio. No acredita el E2E de Cuenta.
El primer recorrido Web real guardó el secreto, leyó sólo la pregunta, recuperó la
contraseña y verificó restitución de contraseña/secreto, baseline y sesiones; cerró
su journal y recursos. Se volvió a desactivar la escritura y se eliminó el fixture,
con ausencia verificada de perfiles, Auth, sesiones, directorio y aceptación UGC.
El código de la función no cambió durante la activación/desactivación.

La evidencia visual no se acepta: el diálogo UGC cubría Cuenta pese al marcador
`accepted`. `live-web-report.json` conserva el resultado funcional;
`live-web-review.json` registra este límite y la limpieza. No hay GO de la unidad.

El segundo recorrido Web también pasó funcionalmente. La preparación UGC mediante
RPC real dejó Cuenta descubierta, comprobada antes de activar la escritura y Guardar.
La revisión visual detectó etiquetas de pregunta secreta en inglés con locale `es-ES`:
`live-web-visible-report.json` y `live-web-visible-review.json` conservan resultado y
límite. Contraseña/secreto fueron restituidos, sesiones y journal cerrados, y el
fixture eliminado con siete contadores cero. El flag remoto `false` y v27 se verificaron
después. La corrección focal de idioma requiere un nuevo artefacto y evidencia Web.

El tercer recorrido, sobre Product/Runner `88be97f9f79aac13a07aa995b3bf27f4535eb6fc`,
pasó funcional y visualmente con revisión independiente: Cuenta sin overlay, pregunta
en español y respuesta vacía después de Guardar. La captura de Login se obtuvo mediante
apertura explícita del caller después de recuperar; no demuestra retorno automático.
Se verificaron las seis condiciones de cleanup, cero errores de página y eliminación
del fixture con siete contadores cero. La consulta remota final confirmó v29 y flag
`false`. No se desplegó código nuevo en este ensayo; sólo se activó/desactivó el flag.
Recibos: `live-web-localized-report.json` y `live-web-localized-review.json`.
GO local Web acotado; Android/iOS y certificación final siguen pendientes.

## Secuencia autorizada

El primer ensayo iOS sólo activó/desactivó el flag; la fuente remota coincidía con
el hash compatible. Login pasó, pero el instalador del siguiente paso falló por
espacio antes de Cuenta/Save/reset. Se verificaron las seis condiciones de restitución,
Keychain vacío en otro proceso, hosts terminados, baseline intacto y cero sesiones
Auth/Web activas. No acredita el flujo iOS ni exige volver a desplegar código.

El segundo ensayo pasó login e identidad; `open` falló esperando Feed mientras la
jerarquía mostraba Novedades y su cierre. No alcanzó Cuenta, Save ni reset. Tras
reconciliar ese fallo de lectura, `clear-owned` y `empty` pasaron en procesos nuevos,
se detuvieron ambos hosts y se retiraron intercambios y artefactos privados. El core
verificó las seis condiciones de restitución y retiró el journal. Auditoría final:
contraseña original, secreto null/null, baseline intacto, cero sesiones Auth/Web
activas y v59 con escritura desactivada. No se desplegó código nuevo ni se acredita
aceptación iOS. Recibos locales: `ios2-settlement.json`, `ios2-resumed-cleanup.json`
e `ios2-closeout.json`.

Pasos 1 y 2 completados; no repetir el despliegue. El paso 3 está parcialmente
comprobado como se indica arriba. Antes de activar, revalidar la fuente v23 contra
el hash propuesto, el esquema y el fixture; un cambio concurrente exige nueva revisión.

1. Revalidar v21, hashes, configuración `verify_jwt=false`, columnas legacy y vínculos
   de actor sin duplicados. Si cambió el despliegue o esquema, detener y revisar.
2. Verificar inmediatamente SHA-256 de index.ts y config.toml del directorio exacto
   indicado arriba; abortar ante cualquier diferencia. Fijar escritura desactivada y
   desplegar exclusivamente ese paquete mediante workdir explícito (nunca desde main):

   ```sh
   supabase secrets set QUATA_RECOVERY_SECRET_WRITE_ENABLED=false --project-ref yrrlankpwmhluexshxnw
   supabase functions deploy quata-auth-bridge --project-ref yrrlankpwmhluexshxnw --no-verify-jwt --use-api --workdir "C:/Users/PC/StudioProjects/quata-account-recovery-secret-salvage/build-reports/account-recovery-secret-salvage/deployment-review-compatible"
   ```

3. Comprobar contrato sin sesión (401), rechazo con escritura desactivada (503) y
   lectura pública permitida sin respuesta. Registrar sesiones de comprobación y cleanup.
4. Sólo cuando el runner tenga journal privado persistido del actor autorizado y registro
   previo de sesiones/mutaciones, activar para ejecutar productor real y recuperación:

   ```sh
   supabase secrets set QUATA_RECOVERY_SECRET_WRITE_ENABLED=true --project-ref yrrlankpwmhluexshxnw
   ```

5. Restaurar contraseña original mediante recuperación autorizada; demostrar login;
   después restaurar snapshot explícito `legacy-v32` (secret_question, secret_answer),
   revocar sesiones propias y verificar cleanup antes de retirar journal. Nunca sustituir
   el productor por SQL. El helper soporta ese formato explícito, sin autodetección ni
   fallback ante columna ausente; sus pruebas pasan.

## Reversión

```sh
supabase secrets set QUATA_RECOVERY_SECRET_WRITE_ENABLED=false --project-ref yrrlankpwmhluexshxnw
```

Se conserva el consumidor. Si se necesita restaurar el artefacto v21 verificado,
el formato almacenado sigue siendo compatible; antes se restituyen los cambios propios
del ensayo y se comprueba que no haya un despliegue concurrente. No se sobrescriben datos
de usuarios ajenos ni se elimina ningún campo. Sólo tras verificar nuevamente el SHA-256
v21 se podría restaurar con:

```sh
supabase functions deploy quata-auth-bridge --project-ref yrrlankpwmhluexshxnw --no-verify-jwt --use-api --workdir "C:/Users/PC/StudioProjects/quata-account-recovery-secret-salvage/build-reports/account-recovery-secret-salvage/deployed-v21"
```

El propietario autorizó el 8 de septiembre de 2026 este despliegue y los futuros
despliegues compatibles con producción, con resumen previo de cada cambio.
La autorización cubre función y activación; siguen siendo obligatorias las verificaciones
de compatibilidad, preparación del fixture y restitución descritas aquí.
