# FLOW-DEEP-LINKS: ensayo autenticado de Chat

Estado: preparación, sin sesiones ni fixtures creados. Este documento no es
evidencia de aceptación ni cambia el inventario operativo.

El runner focal debe abrir un enlace a una conversación temporal y a su mensaje
exacto, con arranque frío y con la aplicación ya abierta. Debe comprobar destino,
foco, salida y consumo único. No reutiliza favoritos, adjuntos ni SOS para probar
la recepción del enlace. El contrato de foco ya integrado sigue siendo propiedad
de `CHAT-FOCUSED-MESSAGE`.

## Preflight observado

Las tres definiciones remotas consultadas conservan estos MD5 de control:

| RPC | MD5 de `pg_get_functiondef` |
| --- | --- |
| `quata_chat_start_thread` | `50e9b0f50dc9f11f36dda313f9f3195a` |
| `quata_chat_send_message` | `8c5a1bfa4dd69d350f3438e5936cf93b` |
| `quata_chat_get_thread` | `f0516fd6c639b607623d3bd6d3dc8339` |

La consulta de claves foráneas identifica referencias `ON DELETE SET NULL` que
exigen una comprobación adicional: respuestas/reenvíos desde otros hilos, estado
de otra conversación y referencias SOS. El helper
`scripts/e2e-fixtures/chat-deep-link-cleanup.mjs` rechaza esas referencias y
resultados incompletos. Sólo consulta: no determina propiedad ni borra datos.
Su consulta se ha ejecutado contra el esquema real en una transacción de sólo
lectura, revertida al terminar. Esa comprobación prueba compatibilidad del SQL;
no demuestra limpieza real ni seguridad frente a escrituras concurrentes.

La lectura de triggers confirma que insertar texto en `chat_messages` encola una
llamada a `quata-push-dispatch`. No se desactivará ese trigger global para el
ensayo: los perfiles nuevos deben carecer de dispositivos y suscripciones. La
existencia de la llamada encolada no certifica entrega push ni amplía esta unidad.

## Requisitos del coordinador antes de ejecutar

1. Revisar las definiciones completas y efectos de triggers/notificaciones;
   volver a comprobar los contratos inmediatamente antes de crear el fixture.
2. Registrar de forma durable el run, actores autorizados, identificador de
   cliente y clave única del hilo antes de cada solicitud que pueda mutar datos.
3. Reutilizar los recibos de sesión verificados. Conservar tokens sólo en memoria
   o almacenamiento privado cifrado; nunca en argumentos, entorno o informes.
   Un login sin respuesta queda incierto: no repetirlo ni atribuir sesiones por
   una diferencia temporal del conjunto de sesiones de la cuenta.
4. Usar dos perfiles nuevos y exclusivos del run, sin dispositivos ni
   suscripciones push. Marcar la propiedad en `auth.users.raw_app_meta_data` bajo
   `quata_e2e: {unit: "FLOW-DEEP-LINKS", run_id: ...}`. El adaptador comprueba esa
   propiedad, vínculo único activo y ausencia de sesiones antes del login.
   También exige que el teléfono resuelva a un único perfil coincidente y envía
   `profile_id` explícito al bridge, antes de verificar de nuevo el recibo Auth.
   `web_login` puede reconciliar credenciales Auth internamente: no usar cuentas
   existentes para este ensayo. Crear un grupo y un mensaje de texto del run;
   no cambiar secretos ni conversaciones existentes.
5. Antes del borrado, verificar identidad y propiedad exactas, participantes y
   mensajes esperados. Mantener bloqueadas las filas del hilo y de sus mensajes
   durante el guard, borrado y verificación dentro de la misma transacción.
   La estrategia de bloqueo debe revisarse frente a inserciones concurrentes de
   referencias; una comprobación previa aislada no basta.
6. Verificar ausencia de residuos por IDs originales después del borrado,
   incluyendo dependencias de mensajes. Revocar únicamente sesiones propias con
   recibo comprobado. Mantener el journal si falta cualquier restitución.
7. Obtener revisión independiente del coordinador completo antes de usarlo.

La creación y retirada están implementadas en
`scripts/e2e-fixtures/chat-deep-link-profile.mjs`; falta ensamblarlas y ejecutarlas
con el coordinador completo. La creación solicita el UUID previamente journaled
mediante el parámetro `id` de Auth Admin, contemplado en el
[código oficial de Supabase Auth](https://github.com/supabase/auth/blob/master/internal/api/admin.go).
El trigger remoto de Auth crea además `public.profiles`: el retiro verifica esa
fila, el perfil común, directorio, sesiones, identidades y ausencia de Storage.
Las dependencias permitidas se identifican por tabla, columna, padre y acción de
borrado. Las demás dependencias pobladas impiden retirar el fixture.

Los tests simulados cubren colisiones, transporte incierto, journal fallido,
dependencias ajenas y reanudación tras commit con checkpoint fallido. Se han
planificado 88 sentencias con 78 FKs reales mediante `EXPLAIN` sin `ANALYZE` en una
transacción de sólo lectura: compatibilidad SQL, no creación/borrado demostrado.

La propiedad exclusiva permite reconciliar un login incierto sobre el fixture;
no autoriza a revocar sesiones ajenas ni a declarar limpieza por inferencia.
El adaptador de sesión requiere un lock exclusivo del run durante todo el ciclo.
Guarda intención antes del POST y respuesta privada antes del recibo, y rechaza
tickets nativos o mixtos antes de solicitar un login Web.

## Ensamblado local

`scripts/flow-deep-links-chat-trial.mjs` ensambla el lock exclusivo, los journals
DPAPI, perfiles, login, fixture de conversación y cierre de UI antes del retiro.
La respuesta HTTP de un login no resuelve incertidumbre por sí sola: hacen falta
los recibos verificados o la respuesta específica de credenciales inválidas.

`scripts/e2e-fixtures/chat-deep-link-thread.mjs` prepara grupo, participantes y
mensaje en una transacción SQL. Son datos sintéticos para aceptar navegación por
enlace; no evidencia de creación de grupos ni de envío desde la UI. La retirada
verifica propiedad, participantes y mensaje exactos, bloquea hilo/mensaje y
rechaza adjuntos por cualquiera de sus dos referencias antes del borrado.

Las pruebas locales del ensamblado usan DPAPI y lock reales en Windows, con
transporte simulado: fallo de preflight, Admin incierto y fallo de cierre de UI.
También simulan timeout de login seguido de journal ilegible: no se inicia
ningún retiro y se conservan ambos journals y el lock para reconciliar.
No equivalen al recorrido autenticado. Quedan pendientes el adaptador Web,
transporte privado concreto y comprobación exacta de contratos del wrapper,
su revisión independiente y la ejecución con restitución real.
