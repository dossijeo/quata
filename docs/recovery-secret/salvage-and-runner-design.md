# ACCOUNT-RECOVERY-SECRET: estado operativo

## Alcance y referencia

Unidad `ACCOUNT-RECOVERY-SECRET`, requisito `VERIFIED_ANDROID`, aceptación E2E
pendiente. Cuenta debe configurar pregunta/respuesta; recuperación debe consumir
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
activar/desactivar para el ensayo, la revisión activa es v29 con el mismo código. La escritura sigue
desactivada; la sonda sin bearer devuelve `401/authentication_required`. Las sondas
de contratos anteriores conservaron sus respuestas. La sonda con una sesión Web nueva
del fixture también confirmó el 503 y limpió sus sesiones y journal. La escritura
permanece desactivada tras los tres recorridos focales y sus restituciones.

La propuesta hashed retirada se conserva únicamente en el historial Git, por ejemplo
en el documento de dependencia del commit `8b2da0ff`. No forma parte del plan operativo.
El contrato hash/pepper de main tampoco acredita el paquete compatible propuesto.

## Implementación disponible

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

Antes de candidate-final: revisión independiente, correcciones, evidencia local
proporcional y head congelado. Después: certificación real, merge y cierre inmediato
del inventario maestro con límites explícitos, sin promover padres ni vecinos.
