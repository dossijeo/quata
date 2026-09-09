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
No equivalen al recorrido autenticado. `scripts/flow-deep-links-web.mjs` aporta
el wrapper privado y el preflight de producto, distribución, versiones Edge y
contratos DB. `scripts/e2e-fixtures/chat-deep-link-web.mjs` verifica frío/caliente,
destino exacto, foco, salida y recarga. Las pruebas con Chrome real y HTML sintético
pasan con POST 200 y 502: sólo el primero permite declarar transporte asentado.
El adaptador bloquea nuevos envíos al cerrar, espera los ya enviados y conserva
incertidumbre si fallan. Service workers desactivados para observar ese transporte.
El wrapper cuenta con revisión estática independiente; queda pendiente el ensayo
real y la verificación de restitución. No hay GO de unidad por estos tests.

## Primer intento real

Run `3deb87f1-4a00-4335-b50d-f768fe3263a9`, runner `bbc1cb3e`, producto
`524843237632be56a32237e008bed26dfdff1e18`: llegó a `web_ui` y falló sin aceptación.
Se retiraron el hilo, mensaje y perfil secundario. El retiro del actor rechazó
el email que el bridge había normalizado conforme a `profileEmail` del paquete
certificado. Se corrigió la guarda para admitir sólo ese alias exacto cuando
existe intención de login durable, manteniendo UUID y metadata de propiedad.

Reconciliación posterior verificada: cero usuarios Auth del run, ambos perfiles,
sesiones Web, directorio, hilo y mensaje; también cero perfil auxiliar, sesiones
Auth y Storage del actor pendiente. Journals y locks retirados después de esas
comprobaciones. El informe original fallido se conserva junto a la reconciliación;
no se transforma en PASS. Falta diagnosticar la aserción Web y repetir sólo después
de esa corrección y su revisión.

## Preparación de normas para perfiles nuevos

El ensayo `6174af02-8a38-4f2d-9508-8fe0406fcd15` (runner `7e89a45e`)
llegó al destino Chat y observó selección, pero falló en `message_text`. La captura
mostró el mensaje detrás del diálogo «Normas de la comunidad». No se declara PASS:
la limpieza automática terminó completa y el informe fallido se conserva.

El fixture prepara la aceptación `2026-07` junto al perfil nuevo en la misma
transacción SQL, con versión registrada en el journal antes de crear la identidad.
Es una precondición sintética de navegación, no evidencia del flujo de aceptación
UGC. No se falsifica estado del navegador ni se modifica producto. El preflight
comprueba la versión común y los contratos remotos UGC. La retirada sólo permite
esa versión journaled bajo la FK exacta con cascade y verifica ausencia posterior;
una versión ajena o una dependencia distinta detiene el borrado.

Inspección remota de sólo lectura: PK `(profile_id, terms_version)`, FK de perfil
con cascade, restricción de versión no vacía y ningún trigger de usuario en la
tabla. Los nueve tests locales del ciclo de perfiles pasan, incluidos rechazos de
versión ajena, ausencia de journal y FK distinta. Pendiente repetir el ensayo real
tras revisión independiente; esta preparación no promueve el inventario.

Repetición revisada con runner `39bb32e3`, run
`9e8767ee-dca6-43f1-86cd-71dd86735f43`: restitución automática completa,
sin journals/locks pendientes. La captura confirma ausencia del diálogo UGC y
mensaje correcto, pero `getByText(body, exact=true)` volvió a fallar. El marcador
de ruta y el episodio de selección sí se observaron; cero errores de página.
Resultado **FAIL**, no aceptación Web. La hipótesis de que el diálogo fuese la
única causa queda descartada. Siguiente diagnóstico: representación semántica del
texto en Compose/Wasm, antes de modificar las aserciones. La burbuja común usa
`Role.Button` y `contentDescription = message.accessibleActionLabel()`.

El runner `e6b40bdb` exige la intersección del ID exacto y el nombre accesible
completo de esa burbuja, sin aceptar texto en otro mensaje. Cinco tests del
adaptador pasan en Chrome, incluidos ID ausente y cuerpo incorrecto. Revisión
independiente aprobada. Ensayo remoto `873f7b89-3737-49fb-8f79-ce10a72a232d`:
**FAIL en `message_anchor`**, ruta y selección observadas, cero errores de página,
restitución completa. La semántica declarada en Kotlin todavía no demuestra que
ese nodo se exporte al DOM Web. No seguir repitiendo fixtures remotos para resolver
esa exposición: usar primero el fixture localhost `quata-chat-e2e=1` y el owner
hermético existente `scripts/web-chat-a11y-browser-e2e.mjs` como diagnóstico local.
El emparejamiento accesible sigue sin aceptación real; no se sustituye por un
marcador de ruta ni por coordenadas.

### Diagnóstico hermético del árbol accesible

Se derivó un diagnóstico local del owner de accesibilidad existente, manteniendo
su fixture de Auth/Chat y bloqueo de red externa. Se verificó el hash de distribución
`c613c373d36b8417f5bff10e6601ff060e8425dcefab65955730b25a12a256de`
(producto `524843237632be56a32237e008bed26dfdff1e18`), sin regenerar ni modificar
el bundle. Artefactos locales: `build-reports/flow-deep-links/local-compose-semantics/`.

El envío de «mensaje AX local» funciona y la captura muestra la burbuja común.
La inspección recorre también shadow roots: el input nativo «Mensaje» existe,
pero la burbuja no se exporta. La observación por etapas muestra algo más preciso:
`cmp_a11y_root` conserva IDs de Auth y `quata-splash-root` después de navegar a Chat,
enviar y esperar otros cinco segundos. No es sólo falta de un test tag. Los ensayos
locales registran cero orígenes externos; no crean perfiles, sesiones ni filas remotas.

Compose 1.10.0 configura accesibilidad activa por defecto y `Main.kt` no la desactiva.
Su fuente local `ComposeWebSemanticsListener` mantiene un único `semanticsOwner`;
la causa de que el árbol quede desactualizado sigue por demostrar. No se introduce
una actualización global de dependencias ni un bridge de aceptación para ocultarlo.
Estos informes son diagnósticos locales, no certificación de enlaces reales.
