# ACCOUNT-RECOVERY-SECRET: rescate y runner focal

Estado: preparación solicitada por el usuario; no candidato, ejecución E2E ni GO.
Formato operativo elegido tras identificar el AAB publicado v32: `legacy-v32`, con
snapshot/restauración de `secret_question` y `secret_answer`. La propuesta hashed de
tres campos descrita originalmente queda retirada para esta activación: anular la
respuesta legacy rompe el consumidor publicado. Véase `deployment-compatible-v21.md`.
El runner debe pasar `storageFormat: "legacy-v32"` explícitamente al helper y registrar
ese formato dentro del journal.
Fuente examinada: `c86c324ca2a635fd3eb003c6c981fb842b6f0fb9`.
Base inicial reconciliada: `d16be356fdefb2e479cd36b4ae7ead8174935021` (`main`, #319).
Sincronizado con `main` `009af1b1790d73a8f9cc2ace9bf9e2de7bb85b64` (#320),
sin conflictos; los 17 contratos focales y de separación de ACCOUNT-DETAILS pasan.
Rama nueva: `codex/account-recovery-secret-salvage`, creada desde main; no cherry-pick
ni continuación del commit antiguo. Antes de desarrollar/publicar se sincronizará
con main y se resolverán las dependencias reales de las otras unidades.

El inventario §ACCOUNT-RECOVERY-SECRET exige el productor Cuenta → pregunta/respuesta,
lectura permitida sin revelar respuesta, recuperación autorizada y restauración.
ACCOUNT-DETAILS conserva su cierre focal. SCR-AUTH-RECOVERY conserva su GO; sólo falta
conectar su consumo al secreto realmente configurado desde Cuenta.

## Decisiones sobre los doce archivos del commit antiguo

| Pieza | Decisión |
| --- | --- |
| `ProfileScreenHost.kt` | Conservar exclusivamente las anclas de opciones de pregunta, basadas en `option.value`. No cambiar slots, eventos ni snapshot de ACCOUNT-DETAILS. |
| Contrato `quata-auth-bridge/contract.test.mjs` | Conservar: escritura autenticada antes del fallback de login, separación lectura/reset y ausencia de logs de respuesta. Es análisis de fuente, no prueba del endpoint desplegado. No despliega nada. |
| `account-recovery-secret-fixture.mjs` | Sustituir por helper mínimo en `scripts/e2e-fixtures/account-recovery-secret.mjs`: conexión inyectada, identidad exacta perfil/auth, sólo los campos del formato explícito (dos en legacy-v32), restore transaccional y readback. Sin búsqueda aproximada por teléfono, defaults privados, redacción por regex ni conexión oculta. |
| `ProfileDetailsRealInstrumentedTest.kt`, test UIKit de AccountDetails | Descartar todas las ampliaciones: no serán propietarios de recuperación. |
| Los tres `account-details-*-evidence.mjs` y su contrato | Descartar todos los cambios. No modificar nombre, barrio, prefijo ni teléfono para probar recuperación. |
| `WebProfileDetailsE2eBridge.kt` | Descartar la ampliación: no enviar secretos por el bridge de ACCOUNT-DETAILS. |
| `run-ios-account-details-ui-test.sh`, `package.json` | Descartar los cambios. No renombrar un recorrido ACCOUNT-DETAILS como evidencia de esta unidad. |

## Runner nuevo: ACCOUNT-RECOVERY-SECRET-REAL-001

Diseño de `scripts/account-recovery-secret-evidence.mjs`, con adaptadores de plataforma
propios y utilidades comunes de fixtures/sesión. El documento define el runner;
todavía no hay adaptadores ejecutados ni una implementación E2E acreditada.

El núcleo del runner ya implementa la secuencia con adaptadores inyectados;
faltan el ensamblado ejecutable y los adaptadores reales. Exige estado visual
terminal completo y registra sesiones y posibles mutaciones antes de iniciarlas.
La restauración compara atómicamente los valores temporales esperados mediante
`IS NOT DISTINCT FROM` junto a la identidad exacta. Las pruebas son simuladas,
salvo el ensayo DPAPI de Windows con datos sintéticos. Los adaptadores deben
confirmar la terminación de operaciones antes del cleanup: un timeout no cancela
una mutación remota ni demuestra que no pueda terminar después.

Entradas explícitas: plataforma, artefacto y SHA, cuenta de prueba autorizada,
identidad perfil/auth exacta, destino local privado de recuperación y evidencia.
Sólo una plataforma y una cuenta en mutación simultáneamente. No crear cuentas,
endpoints, políticas ni secretos de despliegue. Un bridge no implementado, selector
ausente, SKIPPED o respuesta inesperada falla cerrado, nunca produce PASS.

1. **Preflight sin mutaciones.** Verificar artefacto/base/head, login con contraseña
   original, identidad exacta y existencia de usuario auth activo. Auditar el contrato
   desplegado con solicitud sin bearer: `{action: "update_recovery_secret", version: 1}` debe responder
   401/authentication_required. No asumir que la fuente en Git está desplegada.
   Si no existe el contrato, registrar bloqueo focal; no desplegar automáticamente.
2. **Preparar restitución antes del primer Save.** Capturar el secreto mediante el
   helper privado y verificar que los dos campos legacy existen; no degradar silenciosamente
   el esquema esperado. Retener contraseña original sólo en memoria privada. Registrar
   cleanup de todas las sesiones antes de crearlas. Preparar además un journal privado
   cifrado, protegido para el usuario del host, que permita recuperar el snapshot tras
   caída del proceso. `scripts/e2e-fixtures/recovery-private-journal.mjs` ya implementa
   el journal DPAPI CurrentUser del coordinador Windows; su ensayo real con datos
   sintéticos pasa. `persistSnapshot` permite exigir persistencia antes de recibir
   el restorer y `resumeRecoverySecretSnapshot` retoma los dos campos legacy descifrados.
   El runner debe hacer obligatorio ese callback antes de cualquier mutación.
   El JSON público no contendrá respuestas, hashes, contraseñas, JWT ni cuerpos de API.
3. **Productor real.** Login → Cuenta → formulario que contiene pregunta/respuesta.
   Seleccionar `profile.details.secret-question.option.<valor>` (usar el valor real
   de la constante del producto), escribir respuesta temporal y guardar desde producto.
   Android/iOS utilizan las anclas Compose y el flujo real. Web ya dispone de un
   bridge localhost-only de ACCOUNT-RECOVERY-SECRET, con opt-in propio, que llama
   exclusivamente SecretQuestionChanged, SecretAnswerChanged y Save. No extender el
   bridge de ACCOUNT-DETAILS ni escribir SQL para simular el productor.
   `__quataRecoverySecretE2eProduct` ofrece open/configure/save/snapshot; requiere
   query `quata-recovery-secret-e2e=1` y opt-in localStorage propio. Su snapshot sólo
   contiene pregunta y booleanos visible/answerEmpty/saving/failed/saved. Esperar
   recomposición tras open/configure, consultar la referencia global vigente y hacer
   un único Save; corroborar persistencia real independientemente de saved. Las tres
   pruebas del bridge, los diez contratos ACCOUNT-DETAILS y la compilación
   `:web:compileKotlinWasmJs` pasan; no constituyen evidencia E2E.
4. **Lectura permitida.** Salir y volver al formulario/reponer sesión según producto;
   comprobar pregunta seleccionada y respuesta vacía/enmascarada. Capturar únicamente
   después de ocultar/vaciar el campo; no capturar respuesta escrita. Leer la pregunta
   mediante `recovery_question` y comprobar allowlist de campos públicos, sin respuesta
   ni hash. Una consulta privilegiada del snapshot no demuestra esta restricción.
5. **Consumidor real.** Logout → Login → Recuperar → identidad autorizada → pregunta
   producida → respuesta temporal → contraseña temporal. Android/iOS recorren Auth;
   Web usa el bridge de Auth existente sólo como límite de canvas documentado.
   Verificar login real con contraseña temporal en sesión nueva. Marcar la contraseña
   como potencialmente modificada *antes* de enviar reset: un timeout puede ocurrir
   después de que el servidor haya aceptado la operación. No deducir rollback de HTTP.
6. **Restitución en finally, también si falla el recorrido.** Conservar el secreto
   temporal mientras se restaura la contraseña original mediante recuperación autorizada.
   Verificar login original con sesión nueva; revocar también esa sesión. Sólo entonces
   restaurar los dos campos legacy originales mediante el helper y comprobar readback exacto.
   Si falla la contraseña, no borrar primero el secreto temporal que permite recuperarla:
   conservar journal privado, limpiar sesiones posibles y emitir fallo de cleanup.
   No copiar hashes de auth.users ni alterar roles, nombre, barrio o teléfono.
7. **Cierre.** Revocar todas las sesiones del run, demostrar login original y secreto
   restaurado, cerrar sólo procesos propios y retirar journal privado cuando cleanup
   esté verificado. Emitir booleanos/resultados permitidos y SHA de capturas redactadas.
   Un fallo de restitución mantiene el run FALLIDO aunque el flujo funcional haya pasado.

Restaurar la contraseña significa recuperar su funcionamiento original, no garantizar
que el hash salado o timestamps de auth sean idénticos. El secreto sí se restaura byte
por byte. Cualquier limitación adicional de sesiones revocadas por reset se registra.

## Pruebas y separación de evidencia

Los tests locales del helper cubren identidad, columnas limitadas, readback, rollback,
idempotencia y no serialización; no prueban permisos SQL reales. El contrato rescatado
prueba fuente del auth bridge. Se conserva intacto el contrato de ACCOUNT-DETAILS para
detectar contaminación de alcance. La integración real requiere todavía adaptadores,
journal privado seguro, compilación, ejecución en Android/Web/iOS, cleanup y revisión
independiente sobre el candidato exacto; este rescate no acredita esos pasos.

El journal usa pipes privados y archivos cifrados; no guarda secretos en argumentos,
variables de entorno ni archivos temporales de texto. Un archivo exclusivo por actor
evita sobrescribir un run pendiente. Checkpoint y borrado se excluyen por un lock de
archivo entre handles y procesos. Un lock abandonado bloquea hasta comprobar el proceso
propietario; nunca se elimina por timeout. La lectura aislada no adquiere ese lock.
El test reproduce reapertura, rechazo de identidad, persistencia fallida, restitución
del snapshot y la carrera checkpoint/cleanup. Esto no prueba resistencia a pérdida de
alimentación ni gestión real de sesiones; esas garantías no se deducen de rename.

La rama antigua `codex/account-recovery-secret` se retiró local y remotamente tras
verificar el rescate. Su worktree se conserva detached y su commit está preservado
en un bundle local verificado. El commit antiguo no es ancestro de esta rama.

## Dependencia de despliegue comprobada

Auditoría de sólo lectura del 2026-09-08: Supabase CLI informa que
`quata-auth-bridge` está ACTIVE en versión 21, actualizada el 2026-07-25T19:53:31Z.
La fuente descargada no contiene `update_recovery_secret` ni `recoverySecretPatch`;
sí contiene `recovery_question` y `reset_password`. SHA-256 del archivo descargado:
`d57969b79deca66926e6e78b575c556e2e2812d5d13dc4a85998f93428e0dc01`.

La solicitud sin bearer, identidad ni campos de mutación, con acción y versión 1,
devuelve 400/`password_required` en lugar de 401/`authentication_required`.
Por tanto, los contratos de fuente que pasan no acreditan el productor desplegado.
No se han cambiado contraseñas, secretos, políticas ni funciones durante esta auditoría.
Los recibos y la fuente descargada están en el directorio ignorado
`build-reports/account-recovery-secret-salvage`.

Antes de ejecutar el runner real se necesita preparar y revisar por separado el
cambio aditivo de despliegue y su restitución, conforme al operating model §3.
No desplegar toda la fuente de main suponiendo que sólo cambia esta acción:
comparar primero con la versión 21 y preservar el comportamiento existente.
Esta dependencia no reabre ACCOUNT-DETAILS ni invalida el GO histórico de Auth.
