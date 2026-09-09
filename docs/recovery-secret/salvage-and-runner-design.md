# ACCOUNT-RECOVERY-SECRET: estado operativo

## Alcance y referencia

Unidad `ACCOUNT-RECOVERY-SECRET`: aceptación local Android/Web verificada;
iOS y certificación/integración final pendientes. Cuenta debe configurar pregunta/respuesta; recuperación debe consumir
ese secreto; el ensayo debe restaurar contraseña/secreto y limpiar sus sesiones.
ACCOUNT-DETAILS y SCR-AUTH-RECOVERY conservan sus cierres. No se amplía contraseña
legacy, avatar, SOS ni otros subflujos.

La referencia publicada es el AAB v32 confirmado por el propietario; véase
[identidad y contrato Android](../ANDROID_PUBLISHED_REFERENCE_V32.md). El cliente
publicado compara `secret_answer`: no se puede anular ese campo durante la migración.
La rama vieja `codex/account-recovery-secret` se examinó como salvage y fue retirada;
`c86c324` no se promovió. Se conservaron las anclas semánticas de pregunta y se
sustituyó el runner antiguo por un propietario focal independiente de ACCOUNT-DETAILS.

## Backend desplegado, escritura desactivada

[Plan compatible revisado](deployment-compatible-v21.md): v21 más productor
autenticado sobre `secret_question`/`secret_answer`, sin migración de esquema, RLS,
pepper ni sustitución de consumidores. El propietario autorizó el despliegue y su
activación tras verificar compatibilidad con producción. El paquete está desplegado
inicialmente como v23 y su fuente descargada coincide con el hash revisado. Tras
activar/desactivar para los ensayos, la última revisión comprobada es v57 con el mismo código.
La escritura sigue desactivada. Las sondas históricas sin bearer devolvieron `401/authentication_required`. Las sondas
de contratos anteriores conservaron sus respuestas. La sonda con una sesión Web nueva
del fixture también confirmó el 503 y limpió sus sesiones y journal. La escritura
permanece desactivada tras el último recorrido Android y su restitución.

La propuesta hashed retirada se conserva únicamente en el historial Git, por ejemplo
en el documento de dependencia del commit `8b2da0ff`. No forma parte del plan operativo.
El contrato hash/pepper de main tampoco acredita el paquete compatible propuesto.

## Implementación disponible

### Preparación iOS en curso

Worktree aislado en Mac: `/Users/gabriel/StudioProjects/quata-account-recovery-secret`,
base inicial `9b920f38c92f1d80c8710f5b0a9b46b583ad363e`. No modifica los worktrees
de unidades cerradas. El espacio libre inicial de 3,2 GiB no garantiza que alcance
para compilar; comprobarlo antes del build y conservar simuladores/evidencia.

El runner iOS será propietario focal de login → Cuenta/configurar/Save → lectura
sin respuesta → logout → recuperación → restitución. Reutiliza el repositorio real,
Keychain y las anclas existentes; no reutiliza ACCOUNT-DETAILS como propietario.
El core Windows mantiene snapshot DPAPI y tickets previos a las sesiones/mutaciones.
Las etapas iOS sólo devolverán recibos de identidad verificables y resultados públicos.
No se aceptan sesiones deducidas por diferencias ni reintentos de operaciones inciertas.

Condiciones revisadas para el transporte de pruebas: archivos privados en directorio
propio 0700, creados 0600 sin sobrescritura/enlaces y con rutas registradas antes de
escribir; retiro también tras interrupción. DPAPI no protege las copias temporales
del Mac. No colocar credenciales en argv/env ni usar typeText para secretos, porque
XCTest registra el texto. Si se utiliza pasteboard, exigir contenido local, caducidad
y limpieza sin fallback a typeText. Mantener xcresult privado y revisar sus adjuntos
antes de exportar evidencia. Timeout no equivale a cancelación remota.

Login real y restitución iOS comprobados; Cuenta/recuperación y aceptación iOS pendientes.

Preparados `RecoverySecretPrivateFiles` y `QuataIosRecoverySecretSessionTests` sólo
para targets de pruebas. Lectura acotada a 32 KiB, permisos/propietario comprobados,
openat sin enlaces y recibo exclusivo sincronizado. Una marca exclusiva `started`
se crea antes de la operación para impedir reutilizar un paso. El test de sesión
exige identidad exacta y emite el bearer sólo a un recibo privado; el coordinador
debe validarlo y registrar la sesión antes de retirar el recibo. `sessionEmpty`
no acredita revocación remota. Prueba Swift sintética en Mac: permisos, rechazo de
enlaces, exclusión de pasos repetidos y no sobrescritura de recibos correctos.
`QuataIosRecoverySecretUITests` implementa configurar/Save único, lectura tras
relanzar y recuperación por `auth-recovery-real` (no acredita entrada normal desde
Login). Comprueba nombre/teléfono visibles; la etapa de sesión `identity` comprueba
los IDs almacenados antes de la UI. `saveDispatched` no significa persistencia:
exige auditoría backend y lectura posterior. Entrada mediante Paste, con portapapeles
local, caducidad y restitución sólo si conserva su propiedad. Preflight sintético
opt-in sobre `auth-launch`, sin credenciales ni Submit. Revisión estática independiente
sin bloqueantes y comprobación Swift de tipos con SDK del simulador correctas.
Build nativo y build-for-testing del host correctos; último build del runner con
fuente `2a56147b0acc25c4d71e2195714d4803a07dfb18`, con firmas verificadas.
El resultado Kotlin de login llega como Any: se verifica la sesión concreta
persistida y ambos IDs, sin asumir éxito por un callback no nulo.
Preflight sintético `1f695e38-attempt6`: **PASS**, una prueba, cero fallos,
XCTest terminal y exit 0. Verifica teléfono/respuesta exactos y contraseña por
valor o máscara de longitud equivalente; la contraseña real todavía necesitará
login backend para acreditar su contenido. No envía Submit ni toca Supabase.
El ajuste permite hasta tres aperturas del menú, verificando propiedad y contenido
del portapapeles cada vez; Paste se pulsa una sola vez. No incorpora fallback a
typeText ni repite Save/reset. Los fallos sintéticos previos de apertura y sus
resultados se conservan; no demostraban un fallo exclusivo de contraseña.
Cuenta vacía exige valor AX ausente o cadena vacía y ausencia del botón de limpiar.

Preparación del enlace al core: `open` comprueba Cuenta sin Save; el adaptador
preparará configurar sólo en memoria y ejecutará `configure` desde `saveSecret`,
después del checkpoint `before_save_secret`. El recibo `read` añade Save habilitado
y ausencia de error. `saved=true` requerirá además Save único previo y persistencia
validada por el core. `clear-owned` sólo limpia Keychain vacío o con ambos IDs
propios; el cierre exige otro proceso vacío y auditoría/revocación backend separada.
Extensiones con build-for-testing correcto y revisión estática independiente sin
bloqueantes. La etapa `empty` pasó en un proceso nuevo (`2a56147b-attempt1`):
XCTest exit 0, recibo exacto de Keychain vacío e intercambio privado eliminado.
Sólo usó identificadores sintéticos. `recovery-ios-product.mjs` conecta el contrato
del core con esas etapas: recibos exactos, bearer validado y registrado antes de
retirar el intercambio, Save diferido, lectura comprobada y cierre propio en dos
procesos. Seis pruebas simuladas correctas; no acreditan ejecución real.
El logout iOS es asíncrono: el adaptador exige además `verifyLogout` del coordinador
para observar revocación de la sesión productora registrada antes de recuperar.
`runStep`, `releaseStep`, esa auditoría y `closeResources` deben tener plazos acotados;
un timeout conserva incertidumbre y no autoriza repetir una operación.
`recovery-ios-step.py` transporta un único paso por stdin/stdout privados. Mantiene
un bloqueo exclusivo por worktree hasta retirar el intercambio verificado; status
sólo observa, y un timeout retiene incertidumbre/bloqueo. Limita la espera de XCTest
a 300 segundos y sólo puede detener su propio grupo de procesos. Exige terminación
previa del host o confirmación explícita de que no estaba ejecutándose. El caller
debe comprobar propiedad/exclusividad del simulador antes de invocarlo.

Preflight real del transporte, sólo `empty` con IDs sintéticos: exit 0, recibo
exacto, segunda ejecución rechazada, retirada con otro Auth ID rechazada y recibo
original conservado; retirada correcta verificada después. Run
`fea9c850-b4f1-4908-82d9-41ef2dd403f4`, step
`cdd4358b-7783-4092-8f9f-8229e91cb0a4`; host firmado `2a56147b`.
Los logs/xcresult permanecen bajo directorio privado hasta revisión y limpieza del
caller; el límite de 16 MiB del log se comprueba al terminar. Un release interrumpido
requiere reconciliación, no borrar el bloqueo ni repetir automáticamente.
El caller Windows local `run-prepared-ios.mjs` conecta DPAPI/core/backend con plazos
de transporte, registro previo de rutas, auditoría exacta de logout y revisión de
las tres capturas focales. Conserva un lock por run hasta completar el cierre.
El transporte exporta sólo nombres de capturas permitidos; tras revisión y release,
purga los adjuntos automáticos privados y conserva metadatos/hash del log.
Purge del ensayo sintético archivado comprobado correctamente.

Antes de declarar recursos cerrados, exige `empty` final liberado y ausencia de
`com.quata.ios` y `com.quata.ios.uitests.xctrunner` mediante simctl; las constantes
se contrastaron con los productos firmados. Ese cierre pasó en el ensayo sintético.
Ensamblado y corrección de cierre revisados estáticamente sin bloqueantes.
El primer ensayo real pasó readiness y login, con bearer validado/registrado antes
de retirar su intercambio. Run `df176404-5642-4785-a462-447df225c68b`, host `2a56147b`,
runner `a6a31b3b`. El siguiente paso `identity` no inició el test: el instalador falló
por falta de espacio, antes de Cuenta/Save/reset. Se conservó el fallo y se comprobó
ausencia de proceso/marca started; no se reejecutó ese paso ni se convirtió en éxito.

Restitución independiente: `clear-owned` correcto, `empty` en otro proceso correcto,
ambos hosts ausentes, intercambios/artefactos privados retirados y journal eliminado.
`ios1-resumed-cleanup.json` acredita las seis condiciones; `ios1-closeout.json`
confirma baseline intacto, cero sesiones Auth/Web activas y escritura desactivada v57.
La cuenta temporal queda restituida para otro ensayo. No hay GO iOS ni de la unidad.

Simulador exclusivo: `Quata-ACCOUNT-RECOVERY-SECRET-iOS18`, UDID
`F2E1EA50-FBAD-443C-A98F-2A576C14C70B`. El disco del Mac limita la preparación.
Se retiraron sólo ModuleCache/Index propios y la salida nativa intermedia `bin`,
previamente archivada en Windows como `ios-native-bin-e6cb777d.tar` (SHA-256
`57d14980b4de2ec456e4125eb6e76dc88cf61e4c7126e5c526f97421e954898b`).
El XCFramework retenido tiene el mismo binario; productos y resultados se conservan.
La firma de `2a56147b` falló con unos 227 MiB libres; el build completo pasó tras
detener temporalmente el simulador propio. Su instalación sintética fue retirada
y el test de sesión volvió a instalar el host. No se tocaron otros simuladores.
Tras el fallo del instalador se retiraron únicamente un bundle desechado, cachés del
build focal y diagnósticos regenerables del simulador propio apagado. Keychain se
conservó hasta la limpieza explícita de identidad; no se tocaron otros dispositivos.
El próximo ensayo requiere verificar margen de disco antes de activar la escritura.

### Coordinador y adaptadores actuales

- `account-recovery-secret-evidence.mjs`: preparación persistente, flujo focal y
  restitución tras interrupción. La reanudación devuelve `restored`, nunca GO E2E.
- Snapshot por actor exacto; sólo acepta el formato explícito `legacy-v32`, sin
  autodetección ni soporte anticipado de hash. Restauración de los
  dos campos condicionada atómicamente a los valores temporales esperados.
- Journal privado DPAPI del coordinador Windows; tickets antes de sesiones,
  posibles mutaciones antes de Save/reset y eliminación sólo tras verificar cleanup.
  Si una preparación falla después de intentarse, la existencia del journal queda
  desconocida; no se elimina automáticamente.
- Adaptador Web mediante bridges Auth y Recovery propios, con opt-ins localhost.
  Espera recomposición, ejecuta un Save y sólo expone pregunta/booleanos. No captura
  respuestas escritas. Los tokens capturados de la sesión quedan en memoria; sus
  IDs se acreditan con Auth y consultas exactas antes de persistir recibos privados.
- Backend con preflight, auditoría de sesiones, lectura pública, login de verificación,
  reset de restitución y limpieza por recibos. Los tickets HTTP no se reutilizan.
  Una petición incierta conserva el journal; un timeout no prueba cancelación remota.
- `account-recovery-secret-web.mjs`: entrada invocable que une esos módulos.
  El caller aporta DB dedicada con timeout, acceso serializado, comprobación de
  candidata/despliegue y navegador/servidor propios. El caller local preservado con la
  evidencia conecta esa infraestructura al core; no es un runner de ACCOUNT-DETAILS.

Antes de login, reset o restitución se comprueba que no haya sesiones Auth ajenas;
los IDs excluidos deben estar acreditados, nunca deducidos por fechas o diferencias.
La auditoría readonly del 8 de septiembre observó A sin sesiones Auth y B con 13;
no es una garantía vigente ni una autorización para revocar sus sesiones históricas.
La comprobación puntual no proporciona exclusión atómica frente a otros clientes.

## Aislamiento del fixture

El Save de `KmpProfileRepository` también escribe campos generales y contactos de
emergencia. El preflight y la preparación exigen campos que no cambien al aplicar
esa proyección y los triggers desplegados: teléfono internacional en `telefono` y
`phone_e164`, normalizado local, directorio telefónico consistente, barrio vacío
sin membresías y cero contactos. El digest privado se comprueba antes y después
de Save y al cerrar; una discrepancia impide certificar y conserva el journal.
No se normalizan ni restauran campos ajenos al secreto. El caller debe aportar
un contexto de navegador nuevo y desechable, sin selecciones SOS almacenadas.
Estas lecturas no excluyen cambios concurrentes de terceros.

La guarda inicial sólo modelaba el patch Kotlin y rechazaba incorrectamente el
teléfono internacional. La auditoría de triggers del 8 de septiembre corrigió esa
premisa: A queda excluida por contactos de emergencia y B por estado de comunidad.
Sus datos no se modificaron. Se creó una cuenta temporal aislada, dentro de la
autorización existente, y pasó la guarda real. Su ledger de creación/cierre está en
`build-reports/account-recovery-secret-salvage/private-fixture`. El recorrido terminó
con restitución verificada; su journal se eliminó y después se borraron el fixture
y sus derivados, con siete contadores de ausencia comprobados en DB.

Una fila sintética en `pg_temp` verificó el trigger BEFORE real de normalización;
la transacción se revirtió. No ejecutó los triggers AFTER ni modificó perfiles
públicos. Definiciones y recibo: `profile-trigger-audit.json` en los build-reports
ignorados de la unidad. El ensamblado sigue simulando servicios; no acredita el
recorrido real ni los efectos completos de Save.

## Validación y trabajo restante

Se han probado módulos y ensamblado con servicios simulados y DPAPI real de Windows.
El ensamblado verifica orden, recibos, restitución sintética y cleanup; no acredita
SQL real, firmas JWT, Compose, cancelación remota ni exclusión concurrente.
La distribución Web de `8b2da0ff` compiló y pasó el smoke sin sesión. Sus artefactos
y recibos permanecen en `build-reports/account-recovery-secret-salvage`; son
preparación, no certificación final.

El primer recorrido Web real pasó los checks funcionales del core, con cero errores
de página y cleanup completo. Sin embargo, las capturas de Cuenta muestran el diálogo
UGC superpuesto aunque el marcador indicaba aceptación: no se acepta el GO visual.
Se conservan `live-web-report.json`, las tres capturas y `live-web-review.json` con
ese límite. El segundo recorrido preparó UGC mediante su RPC real y verificó Cuenta
descubierta antes de Guardar. Pasó el flujo funcional y la restitución completa; el
fixture fue eliminado y se comprobaron siete contadores cero. Se conservan
`live-web-visible-report.json`, capturas y `live-web-visible-review.json`.
No se atribuye a esta unidad una corrección del gate UGC.

La segunda revisión visual detectó preguntas secretas en inglés con navegador `es-ES`.
La corrección focal reutiliza `toQuataLanguage().tag` sólo en `secretQuestions()` de
WebProfileCatalog; mantiene prefijos, valores persistidos y Auth general intactos.
Revisión independiente aprobada. La nueva distribución compiló en 9m 9s con cleanup
del proceso acreditado y 38 archivos identificados por hash. El tercer recorrido real
sobre Product/Runner `88be97f9f79aac13a07aa995b3bf27f4535eb6fc` obtuvo GO local Web
acotado con revisión independiente de las tres capturas: Cuenta descubierta, pregunta
en español, respuesta vacía y confirmación visible. Core: tres pasos funcionales, seis
comprobaciones de cleanup, cero errores de página. Se eliminó el fixture y se verificaron
siete contadores cero. Supabase quedó en v29 con escritura desactivada.

La captura de Login usa navegación explícita del caller después del reset; no acredita
retorno automático. No se acredita confidencialidad SQL del secreto legacy, exclusión
concurrente ni GO de otras plataformas. Los intentos anteriores mantienen sus límites.
Evidencia preservada con índice y nueve hashes verificados en
`C:/Users/PC/Desktop/QÜATA/migration-v2/evidence/ACCOUNT-RECOVERY-SECRET/88be97f9f79aac13a07aa995b3bf27f4535eb6fc-web`.

Queda conectar Android/iOS a superficies reales y ejecutar
la aceptación focal sobre el candidato integrado exacto. El test Android de Auth
con repositorio simulado no sustituye esa aceptación; el runner iOS de Auth puede
aportar pasos reutilizables, pero no será el propietario del productor de Cuenta.
No extender el runner de ACCOUNT-DETAILS ni construir otro framework de XCTest.
El backend admite ahora `sessionKind="native"` explícito: tickets con `ticketId`,
login `action=login`, validación del bearer en Auth y cruce de `session_id` con el
perfil exacto. Sólo persiste el ID Auth; rechaza campos Web y reutilización del ID
en otro ticket. El cleanup nativo no escribe `web_client_sessions`. Para reanudar,
el caller debe seleccionar ese mismo tipo; el backend Web rechaza tickets nativos.
Web sigue siendo el valor predeterminado y conserva su recibo completo.

Revisión independiente estática aprobada; 19 pruebas de recibos, backend, reanudación
y ensamblado Web pasan, con DPAPI real y servicios simulados. Esto no acredita una
sesión nativa real. Queda conectar la lectura privada de la sesión del proceso de la
app, comprobar preferencias SOS vacías antes de Save y acreditar el flujo Android/iOS.
El token no debe pasar por argumentos, logs, capturas ni archivos de credenciales.
El core existente conserva la propiedad de contraseña/secreto, journal y limpieza.

Preparación Android de `973e8d0c`: test focal `RecoverySecretRealInstrumentedTest` y
adaptador `recovery-android-product.mjs`, sin modificar el runner de ACCOUNT-DETAILS.
Socket privado con comandos serializados; credenciales/bearer sólo en memoria,
capturas antes del secreto y tras vaciar la respuesta. EOF/error no acreditan PASS.
Compilación de instrumentación y ambos APK correctos; tres pruebas del canal pasan,
con revisión independiente estática. APK instalados en AVD separado API 28
`QuataRecoveryApi28` (`emulator-5556`), porque la vía Espresso disponible no soporta
el API 35 del emulador existente. Configuración real comprobada; ningún fixture
nuevo ni activación de escritura en esta preparación. El caller local revisado
`run-prepared-android.mjs` verifica hashes instalados y pausa para revisar Cuenta.
Recibo preparatorio: `android-preparation.json` en los build-reports de la unidad.
El primer intento real abrió Cuenta con sesión nativa verificada y respuesta vacía,
pero terminó antes de Guardar. No acredita productor ni recuperación. La cronología
apunta a la pausa de revisión de 60 segundos; el caller anterior no registraba su
causa explícita. Se amplía a 180 segundos, con idle nativo de 240 segundos y límite
global de 600 segundos, y se registra el resultado de esa pausa. No se elimina el
tratamiento de incertidumbre ni se presume cancelación remota.
Core verificó restitución, sesiones, recursos y retirada del journal. Se eliminó el
fixture con nueve contadores cero, incluidos push y estado de versiones, y su PNG
del dispositivo después de archivarlo por hash. Evidencia del intento no aceptado:
`C:/Users/PC/Desktop/QÜATA/migration-v2/evidence/ACCOUNT-RECOVERY-SECRET/973e8d0c-android-attempt1`.
Queda completar el recorrido real y acreditar Android; no hay GO ni certificación final.

El segundo intento (`438ab2f9`) falló durante login, antes de abrir Cuenta y sin
activar escritura. La instrumentación entregó resultado final y se retiró su forward.
El core conserva fallo de restitución y journal con ticket no resuelto: no se fabrica
un recibo `noSession`. El fixture se eliminó por identidad exacta y auditoría de sus
dependencias; nueve contadores cero acreditan esa eliminación, no éxito del E2E.
Recibos locales: `live-android-review-window-report.json` y
`android-attempt2-fixture-closeout.json` en los build-reports de la unidad.

Se verificó que quedaba una sesión local del primer fixture, pese al cierre reportado
por su core. El test ahora exige coincidencia de perfil y usuario Auth y persistencia
sincrónica de la limpieza; no cambia SessionPreferences de producto. Una identidad
distinta fue rechazada conservando el hash de las preferencias; la identidad exacta
se limpió y otro proceso de instrumentación comprobó sesión nula y preferencias vacías.
No se afirma como causa probada el uso de `apply()`. Compilación e inspección
independiente aprobadas; recibo `android-local-session-cleanup.json`. Esta restitución
no acredita el productor Android ni resuelve retrospectivamente el ticket del segundo
intento. Estado operativo Supabase comprobado: v31, escritura desactivada.

El tercer intento (`226bbf24`) abrió Cuenta con actor nativo verificado y respuesta
vacía, pero falló antes de `before_save_secret`: no se distingue aún configuración
del formulario de la guarda posterior de campos ajenos al secreto. No hay evidencia
de Save. La revisión de Material3 no confirmó la hipótesis de un menú sin ancestro
desplazable; antes de repetir se requieren diagnósticos acotados de esas etapas.
La coordinación también falló: un error local anterior a la activación no impidió
enviar la continuación al caller. La escritura nunca se activó; no explica por sí
sola un fallo anterior a Save. Las mutaciones y su continuación deben ejecutarse
separadamente, inspeccionando el resultado intermedio.

La instrumentación terminó realmente; socket y forward ausentes, y otro proceso
verificó sesión local vacía. La reanudación exclusiva de restitución obtuvo
`restored`, seis comprobaciones verdaderas y journal retirado. Después se eliminó
el fixture con nueve contadores cero. Se archivaron nueve archivos con hashes
verificados en
`C:/Users/PC/Desktop/QÜATA/migration-v2/evidence/ACCOUNT-RECOVERY-SECRET/226bbf24-android-attempt3`
y se retiró el PNG exacto del emulador. El intento funcional permanece fallido.
El fallo previo de preparación se conserva por separado: journal preparado, cero
tickets/cambios/sesiones; su causa no quedó identificada. El caller registra ahora
fases de preparación fijas y códigos de error restringidos, sin mensajes privados.

Diagnóstico posterior del tercer intento: el registro se preparó erróneamente con
`temporaryQuestion=pet`. El catálogo Android contiene `madre`, `barrio`, `amigo` y
`comida`; el `single` de configure falla antes del primer clic. Revisión independiente
confirmada. La comprobación opt-in del APK instalado rechazó `pet` con el error
acotado de catálogo y aceptó `madre`, con sesión local vacía. No se modifica producto,
menú ni registro histórico. El siguiente fixture debe usar la opción real comprobada
y vincular esta preparación a los hashes de ambos APK.

El cuarto intento (`84edfd49`) superó configuración y guarda no secreta, pero falló
en `before_save_secret`. Se confirmó la activación antes de continuar y se desactivó
al terminar: Supabase v33, escritura desactivada. El secreto seguía null/null.
Restitución reanudada `restored`, seis comprobaciones verdaderas, sesión vacía en otro
proceso, journal retirado y fixture eliminado con nueve contadores cero. Siete archivos
se preservaron por hash en
`C:/Users/PC/Desktop/QÜATA/migration-v2/evidence/ACCOUNT-RECOVERY-SECRET/84edfd49-android-attempt4`;
su PNG se retiró del emulador. No acredita aceptación Android.

Se identificó un defecto del productor Android: `SupabaseHttpClient` serializa con
`encodeDefaults=false`, omitiendo `version=1` del DTO general; el backend exige ese
campo. Se introduce sólo para este productor un DTO de cuatro campos obligatorios.
La prueba usa el serializador del cliente real y compara el JSON completo, incluida
la versión numérica. Tres pruebas de contrato y compilación de ambos APK pasan;
revisión independiente sin hallazgos. Login, registro y JSON global no cambian.
Queda ejecutar el recorrido con este APK corregido; el código demuestra el defecto,
pero no se capturó el código HTTP del intento fallido.

El quinto intento (`502e49a6`) produjo en el backend exactamente el secreto planeado
desde el Save de Android, pero el test no confirmó el cierre completo del formulario:
esperaba permanecer en Detalles con feedback visible. El callback Android
`onProfileSaved` navega a Feed. Se ajusta sólo el runner para observar la salida,
capturar y revisar visualmente Feed antes de continuar, y volver mediante la barra
a Cuenta → Detalles. La lectura exige pregunta persistida y respuesta vacía; `saved`
representa ese retorno observado, no un toast que no se haya mostrado. La presencia
de la pestaña Feed por sí sola no es prueba de destino activo.

El quinto intento permanece fallido: no acredita recuperación ni aceptación Android.
Se restablecieron contraseña/secreto, campos ajenos al secreto y sesiones mediante
reanudación exclusiva; seis comprobaciones verdaderas, journal retirado, sesión vacía
en otro proceso y fixture eliminado con nueve contadores cero. Siete archivos con
hashes verificados se preservaron en
`C:/Users/PC/Desktop/QÜATA/migration-v2/evidence/ACCOUNT-RECOVERY-SECRET/502e49a6-android-attempt5`.
El PNG se retiró del emulador. Supabase v35, escritura desactivada y comprobada.
El nuevo test compila y el caller pasa comprobación de sintaxis; falta el recorrido
real con la barrera visual de Feed. No se cambia navegación de producto ni ACCOUNT-DETAILS.

Los intentos sexto y séptimo (`48a8f170`) quedan archivados como fallidos. El sexto
capturó el retorno real a Feed, confirmado visualmente al recuperar el PNG: 1.916.310
bytes superaban el límite de transferencia de 1 MiB. El caller admite ahora 16 MiB
sólo para binarios, manteniendo 1 MiB para texto. El séptimo expiró durante la revisión
de Cuenta (`review_timeout`), antes de Save. Ambos obtuvieron seis comprobaciones
de restitución verdaderas y retirada del journal. Se verificaron después sesión local
vacía en un proceso nuevo, cero sesiones Auth/Web activas, secreto null/null y baseline
sin cambios. El actor se conserva restituido para un nuevo run, no se declara eliminado.

Las evidencias con índices de hashes están en las carpetas
`48a8f170-android-attempt6` (siete archivos) y `48a8f170-android-attempt7` (seis archivos)
del archivo externo de ACCOUNT-RECOVERY-SECRET. Las tres capturas se retiraron del
emulador tras cotejar sus hashes. Supabase v39, escritura desactivada y comprobada.
La revisión tardía del sexto intento no cambia su reporte ni acredita el flujo completo.

Se amplían las tres pausas del caller a 600 s, con idle nativo de 660 s, cierre de
socket a 2690 s y límite global de instrumentación de 2700 s. Las operaciones del
canal mantienen 45 s y los plazos de UI no cambian. Revisión independiente de coherencia,
compilación de instrumentación y sintaxis del caller correctas. Estas son correcciones
del transporte/espera del ensayo; todavía falta ejecutar lectura y recuperación completas.

El octavo intento (`4033031c`) acreditó `account_secret_produced` y la barrera visual
de Feed dentro del run; falló durante la lectura posterior, sin diagnóstico suficiente
para atribuirlo a un paso concreto. Restitución reanudada: seis comprobaciones verdaderas,
journal retirado, secreto null/null, baseline sin cambios y cero sesiones Auth/Web
activas. Se conserva el actor restituido para un run nuevo. Nueve archivos con hashes
verificados están en `4033031c-android-attempt8` del archivo externo; sus capturas se
retiraron del dispositivo. Supabase v41, escritura desactivada y comprobada.

La lectura siguiente reabre Cuenta mediante una Activity nueva y el mismo punto de
entrada ya utilizado por el runner, comprobando perfil y usuario Auth antes y después,
sin nuevo login. Requiere pregunta correcta y respuesta vacía. No acredita navegación
por la barra ni explica retrospectivamente el fallo anterior. El diagnóstico sólo
expone `read_opening`, `read_details` o `read_verification` para una respuesta fallida
con ID coincidente; mantiene incertidumbre y nunca expone excepciones ni valores.
Revisión independiente, cuatro pruebas del canal, compilación de instrumentación y
sintaxis del caller correctas. La aceptación Android completa permanece pendiente.

El noveno intento (`41bee05f`) produjo el secreto y verificó visualmente el retorno
a Feed, pero falló en `read_verification`. El diagnóstico reveló que Android leía
Cuenta con `PROFILE_PUBLIC_SELECT`, sin `secret_question`. La corrección añade una
consulta focal por ID con pregunta y sin respuesta, conserva los directorios públicos,
excluye esta proyección de la reutilización cruzada de caché e invalida perfiles tras
el productor confirmado. Mantiene la caché exacta y la observación. Revisión estática
independiente sin bloqueos, cuatro pruebas JVM y ambos APK compilados correctamente;
las pruebas de solicitudes no cubren almacenamiento de caché ni carreras. Sigue
pendiente verificar el flujo completo en Android real.

Restitución del intento nueve: seis comprobaciones verdaderas, journal retirado,
secreto null/null, baseline sin cambios y cero sesiones Auth/Web activas. Actor
conservado restituido; nueve archivos y hashes verificados en
`41bee05f-android-attempt9` del archivo externo. El resultado funcional sigue fallido.
Supabase v43 y escritura desactivada, comprobados por CLI.

La ejecución Android con producto `bbc5360a` acredita ya el productor y la lectura
de la pregunta sin respuesta (intento 12, captura de Cuenta revisada). Todavía falla
la recuperación, en `before_password_reset`, sin etapa interna demostrada. No es GO
Android. La instrumentación incorpora ahora etapas fijas de recuperación para el
próximo diagnóstico; no revelan valores ni prueban por sí solas cambios en backend.
Cuatro pruebas del canal, compilación de instrumentación y revisión independiente
correctas. Restitución reanudada: seis comprobaciones verdaderas, journal retirado,
baseline intacto y cero sesiones Auth/Web activas. Supabase v47, escritura desactivada.
Diez archivos con hashes verificados en `bbc5360a-android-attempt12` del archivo externo.

Límite del caller: la barrera visual obligatoria verifica ahora Cuenta reabierta con
pregunta y respuesta vacía. No certifica Feed ni continuidad normal de navegación;
la reapertura utiliza el punto de entrada de evidencia existente. El intento 11
permanece rechazado: header visible y contenido negro tras guardar, causa no demostrada.
El intento 10 terminó antes de ejecutar por consola sin stdin persistente; usar PTY
para el caller interactivo. Ambos están archivados como fallidos y restituidos.

El intento 13 (`d56da195`) repite productor y lectura acreditados y localiza el fallo
en `recovery_return`. Antes de restaurar, el hash de contraseña en DB coincidía con
la temporal y no con la original: acredita mutación, no login ni retorno visual.
Restitución reanudada completa, baseline intacto, cero sesiones activas y journal
retirado. Supabase v49 con escritura desactivada; once archivos verificados en
`d56da195-android-attempt13`. El diagnóstico siguiente distingue formulario aún
abierto de destino ausente, sin capturas de superficies desconocidas. Revisión
independiente, cuatro pruebas de canal y compilación de instrumentación correctas.

El intento 14 (`d3fb16d3`) identifica `recovery_still_open`: el formulario permanece
abierto. Productor y lectura siguen acreditados; recuperación completa pendiente.
Restitución reanudada completa, baseline intacto, cero sesiones activas, journal
retirado y Supabase v51 con escritura desactivada. Diez archivos verificados en
`d3fb16d3-android-attempt14`. El próximo diagnóstico clasifica por igualdad exacta
seis mensajes genéricos conocidos (o absent/unclassified) y estado del botón;
no exporta texto arbitrario, entradas ni capturas. Compilación y revisión correctas.

El intento 15 (`450d5edd`) muestra error ausente y Submit deshabilitado al fallar
el retorno. Restitución completa, baseline intacto, cero sesiones activas y journal
retirado; Supabase v53 con escritura desactivada. Once archivos verificados en
`450d5edd-android-attempt15`. Los metadatos HTTP observados no correlacionan acciones
individuales y no prueban por sí solos el resultado del reset.

El aislamiento local del wrapper Android reprodujo dos fallos por Toast fuera del
hilo principal, antes de ejecutar onBack. La corrección usa rememberCoroutineScope
y Dispatchers.Main.immediate para aviso y navegación, cancelados con la composición.
Tres pruebas Android locales pasan: respuesta inmediata, diferida y abandono durante
la petición; onBack verifica el Looper principal. Ambos APK compilan y la revisión
independiente no encuentra bloqueos. No sustituye el E2E real todavía pendiente.

**GO local Android focal**, Product/Runner SHA `1394c8471ff26674c402f97bd4efa6b02b44975d`:
el intento 16 acredita productor real, lectura permitida sin respuesta y recuperación
autorizada, incluido login de verificación con contraseña temporal. Captura del retorno
a Login vacío con aviso «Password updated» revisada. Instrumentación nativa terminada,
seis comprobaciones de restitución verdaderas, nueva sesión local vacía, baseline intacto,
cero sesiones Auth/Web activas y journal retirado. Revisión independiente aprobada.
Doce archivos con hashes verificados en `1394c847-android` del archivo externo.
Supabase v55 con escritura desactivada. Actor restituido retenido para preparación iOS.
No certifica Feed ni continuidad normal de navegación: Cuenta se reabre por la entrada
de evidencia existente. iOS y certificación/integración final de la unidad pendientes.

Antes de candidate-final: revisión independiente, correcciones, evidencia local
proporcional y head congelado. Después: certificación real, merge y cierre inmediato
del inventario maestro con límites explícitos, sin promover padres ni vecinos.
