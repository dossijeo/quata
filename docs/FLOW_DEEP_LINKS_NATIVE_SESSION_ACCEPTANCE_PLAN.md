# FLOW-DEEP-LINKS: renovación y rechazo de sesión nativa

Estado: GO local de renovación iOS fría en `29456502` y entrega caliente tras
preludio en `fcd0afb9`, y Android frío en `62a826a1`. Rechazo Android frío con
cancelación tiene GO local en `a7171157`, y rechazo iOS frío en `67482ac5`.
Login posterior a cancelar tiene GO local Android en `e2d4b8a6`; falta iOS.
Sin GO integrado.
Los checkpoints preparatorios se conservan
a continuación con sus límites históricos. Inspección inicial de fuentes sobre
`5c8ac5e8ae01370df79ce9a1d14b7173099a7443`. Aplica el modelo operativo vigente;
este plan concreta el ensayo pendiente, sin añadir reglas de producto.

## Comportamiento que se debe observar

`SessionManager.validateFreshSession()` rechaza la composición autenticada si
la renovación falla o devuelve otra sesión próxima a vencer. Conserva el snapshot
anterior. `IosFeedRuntimeBootstrap.validateRestoredSession` utiliza esa
validación mediante `IosRenewableAuthSession.validatedRestoredSession()`.
`IosSupabaseAuthSessionRefresher` transforma un fallo HTTP en resultado nulo;
no demuestra por sí mismo revocación ni borrado de Keychain. El camino de peticiones
`currentSession()` usa otra política (`ensureFreshSession`), que puede devolver
la sesión anterior tras una renovación fallida. No transferir la aceptación del
arranque frío a una app ya autenticada.

En Android, `SupabaseHttpClient.refreshCurrentSession()` borra la sesión ante
HTTP 400/401 y aplica un cooldown tras fallo. `QuataApp` también programa la
renovación por tiempo y al activar la app. Por ello, el ensayo caliente debe
establecer cuándo se renovó respecto de la entrega externa; un token nuevo al
final no prueba que el enlace disparase la renovación.

Estas diferencias describen las fuentes actuales, no un GO de paridad. La
observación real debe comprobar barrera pública, destino privado y retorno;
una discrepancia con el contrato común requiere corrección antes de promover.

## Límite del preparador actual

`prepareIosDeepLinkSession` (también usado por Android) exige que `expiresAt`
coincida con el JWT y con la respuesta privada original, con más de 900 segundos
de validez. `runIosDeepLinkSessionStep` sólo admite instalar esa respuesta y
retirar exactamente el mismo snapshot. El preparador y la rama `install` rechazan
tickets ya renovados o revocados; la rama `clear` permite retirar el snapshot
instalado aunque el ticket registre renovación/revocación. No relajar las guardas
de instalación para introducir un metadato vencido.

`validateOwnedNativeSessionReceipt` también exige igualdad de expiración con el
JWT. El snapshot deliberadamente vencido necesita una representación distinta
del recibo original y del snapshot renovado. La lectura privada existente puede
reutilizarse sólo donde sus comprobaciones sigan siendo verdaderas.

## Ensayo a implementar

1. Crear únicamente actores y sesión propios, con identidad remota verificada y
   recibos privados duraderos. Conservar el snapshot original sin modificarlo.
2. Registrar por separado la transformación exclusiva de `expiresAt` al pasado,
   su snapshot esperado y la instalación nativa verificada. No modificar el JWT
   ni afirmar vencimiento criptográfico. Para rechazo, revocar exclusivamente la
   sesión propia por sus recibos y verificar ausencia remota antes de entregar.
3. Entregar el enlace externamente: frío sin PID previo; caliente con continuidad
   de PID y estado temporal verificable. Observar la renovación del producto,
   no invocar el refresher desde un test en sustitución del recorrido.
4. Persistir privadamente la respuesta/snapshot observado antes de reconocer su
   recepción. Verificar actor y sesión Auth contra el recibo original; un cambio
   de identidad o transporte incierto conserva los journals para reconciliación.
   La combinación de instalación verificada, ventana/PID controlados, snapshot
   rotado, Auth/identidad y UI puede acreditar renovación durante el recorrido.
   No prueba que el enlace disparase exactamente una petición: número/orden de
   envíos sólo se afirman con observación adicional que los demuestre.
5. Con renovación válida, exigir hilo/mensaje/foco exactos y salida sin reapertura.
   Con rechazo, exigir evidencia del rechazo real y barrera sin contenido privado;
   distinguir sesión local conservada, eliminada y transporte todavía incierto.
6. Retirar sólo el snapshot final verificado, o acreditar ausencia nativa si el
   producto lo eliminó. Auditar efectos y retirar fixtures/journals/leases mediante
   el protocolo existente. No pasar el snapshot original al clear si hubo rotación.

Antes de ejecutar con backend, cubrir sintéticamente respuesta perdida, identidad
inesperada, renovación anticipada, fallo de persistencia/ACK y clear con snapshot
equivocado; revisión independiente del coordinador y del transporte privado de
custodia completo. No se exige instrumentar las peticiones HTTP del producto para
afirmaciones que no dependen de su número u orden.
Los ensayos válidos y los fallos ya cerrados conservan sus reportes originales.

La matriz [de aceptación](FLOW_DEEP_LINKS_ACCEPTANCE_STATUS.md) sigue pendiente
para estas variantes y para la candidata integrada. Este documento no acredita
renovación, revocación, ausencia de peticiones privadas ni equivalencia Android/iOS.

## Preparación implementada

`scripts/e2e-fixtures/chat-deep-link-native-expiry.mjs` conserva el recibo de
sesión importada de bridge y registra aparte los snapshots original y con metadato
vencido, después de las comprobaciones Auth/DB existentes y con relectura exacta
del checkpoint. Su clasificador distingue original, vencido sin cambios y posible
renovado todavía no verificado; no autoriza ACK ni limpieza. Esto no acredita Login
nativo. La guarda `nativeSessionRenewal` impide que el cierre anterior acepte un
ensayo preparado o parcial en cualquiera de las dos plataformas.

Revisión independiente estática favorable, limitada a preparación. Los 29 contratos
focales de sesión, custodia y residuos pasan con backend simulado. No conectar aún
este preparador a fixtures reales en ese checkpoint: faltaban instalación y
cierre del ciclo; no se había ejecutado una renovación ni revocación nativa real.

El coordinador `installNativeDeepLinkExpiry` registra y relee la intención antes
de enviar un único comando privado `install-expired`, con expiración original y
local separadas. Exige recibo exacto y checkpoint final; una respuesta perdida o
persistencia incierta impide repetir. El registro completo del fixture se reduce
a los campos explícitos del comando, sin trasladar contraseña ni teléfono.
Revisión independiente estática favorable y 34 contratos focales pasan.
Esos contratos del coordinador usan transporte simulado y no acreditan por sí
solos instalación en dispositivo ni cierre del ciclo.

El adaptador iOS `40552bf567fe9c5baa41b154b8b36682c92c2e9f` incorpora
`install-expired`/`clear-expired`: contrasta el JWT con `originalExpiresAt`, exige
metadato local vencido y validez original suficiente al instalar, y retira sólo
el snapshot transformado exacto. No retira una sesión rotada. Las etapas normales
rechazan el campo adicional; no cambia su instalación. El adaptador Android se
registra a continuación.

Build firmado x86_64, recursos y manifest verificados. Probe
`8fa62db5-7dbc-495c-bc68-c9c0a95c86b6`, paso
`c4dcaeda-53d7-4aa4-b232-c6d5903cb1bd`: dos XCTest ejecutados sin fallos,
guardas privadas y servicio Keychain sintético aislado, cierre terminal 0 y
limpieza completa. Reporte/log locales `build-reports/flow-deep-links/ios-expiry-probe-40552bf5.{json,log}`
y manifest `ios-expiry-build-40552bf5.json` bajo la misma raíz; revisión independiente
favorable sólo a preparación y cierre. También pasan 33 contratos Node y seis
tests Python del worker. No acredita sesión real, renovación, revocación ni
aceptación integrada; siguen pendientes ensayo real y cierre tras rotación.

El adaptador Android `e93498e33260216569c9a0e591d8df12e1592bc5` admite los mismos
comandos por socket privado y contrasta la expiración original con el JWT. Exige
metadato vencido y validez original superior a 900 segundos para `install-expired`;
la instalación normal conserva sus 120 segundos y rechaza el campo adicional.
La retirada sigue exigiendo coincidencia exacta del snapshot cifrado.

APK de pruebas `058182f7ffff120046d9f2d8ff72c98b13dc50b6612a6a9b0b1f4c5d06376915`,
build correcto y 13 contratos Node pasan. En el AVD dedicado, la guarda
`rejectsMixedReceiptAndReplacedSession` pasa con clave temporal aislada, incluyendo
metadato vencido y rechazo de JWT/identidad mezclados. Probes vacíos inicial/final,
proceso terminal 0, cierre completo y revisión independiente favorable. Reporte,
logs y APK anteriores preservados en
`build-reports/flow-deep-links/android-expiry-guard-b5090280`.
La guarda no instala un snapshot vencido real en las preferencias de la app;
no acredita renovación ni cierre tras rotación. La app instalada se conserva.

La lectura preparatoria `readNativeDeepLinkExpiry` conserva intención y respuesta
privada, con relectura del journal antes de clasificar. Nunca reconoce recepción
ni autoriza limpieza; devuelve `remoteVerified: false`. El worker iOS permite
`read-owned` después de `install-expired` sólo para el propietario instalado y
exige el mismo `authSessionId` en la respuesta antes de sustituir el snapshot.
El intercambio privado permanece hasta ACK y el snapshot anterior ya no sirve
para limpiar uno rotado. Revisión independiente estática favorable; 27 contratos
Node y siete tests Python sintéticos pasan. No se ha ejecutado esta lectura en
dispositivo. La lectura nativa actual rechaza expiración local distinta del JWT:
el caso vencido sin cambios sigue necesitando una vía explícita. Verificación
remota, ensayo real y cierre completo permanecían pendientes en ese checkpoint.

`verifyNativeDeepLinkExpiryIdentity` añade la verificación preparatoria del token
ya persistido: GET a Auth y consulta de la sesión original única, perfil activo y
marca de propiedad del fixture. Registra intención antes de la lectura remota y
conserva el recibo original. Sólo devuelve `identityVerified: true` junto con
`refreshObserved: false`; no acredita número/orden de peticiones ni permite ACK o
cierre. Revisión independiente estática favorable y 14 pruebas sintéticas pasan,
incluidos actor distinto, sesiones adicionales, respuesta perdida y fallo de disco.
Todavía no se ha invocado este verificador contra un ensayo real de renovación.

## Alcance de la siguiente ejecución

La revisión independiente del inventario (fila `FLOW-DEEP-LINKS`) y del modelo
operativo confirma que consumo único corresponde al enlace. No exige contar
cada petición nativa de renovación. Se completa primero custodia y ensayo válido:
snapshot vencido instalado, proceso/ventana controlados, ningún refresher del
harness, snapshot rotado aceptado por Auth de la misma sesión/actor, destino y
salida reales, y cierre exacto. Sin afirmar refresh disparado exclusivamente por
el enlace, ausencia de renovación anticipada en caliente ni cero peticiones privadas.
El caso de revocación conserva su requisito probatorio: barrera y ausencia DB
por sí solas no acreditan un rechazo HTTP real.

Como investigación complementaria, Supabase documenta el evento `token_refreshed`
y el almacenamiento opcional de auditoría en Postgres
([documentación oficial](https://supabase.com/docs/guides/auth/audit-logs)).
La inspección local `build-reports/flow-deep-links/native-refresh-schema.json`
confirmó sólo el esquema de auditoría/sesiones/tokens, sin leer filas Auth.
No acredita que la auditoría esté habilitada ni el comportamiento de la versión
desplegada. No se añade auditoría o contadores del servidor como gate obligatorio.

El ACK preparatorio iOS `acknowledgeNativeDeepLinkExpiryRead` exige lectura e
identidad remota verificadas, persiste intención y relee antes de enviarlo, y
acepta sólo el recibo exacto de ese paso. Una respuesta incierta conserva el
journal y prohíbe replay. Revisión independiente estática favorable y 29 contratos
Node pasan. No reconoce Android ni declara la custodia cerrada: sólo retira el
intercambio privado del worker; la sesión nativa requiere todavía clear exacto.
No se ha ejecutado este ACK en un ensayo real de renovación.

`clearNativeDeepLinkExpiry` deriva el comando únicamente del snapshot renovado
ya verificado, exige operaciones resueltas y ACK previo en iOS, y registra y relee
intención y resultado. `nativeDeepLinkExpiryCustodySettled` comprueba la cadena
original/install/read/identidad/ACK/clear y los pasos distintos antes de aceptar
custodia del dispositivo. Se conecta a las guardas existentes de ambas plataformas;
no sustituye cierre de canal, auditoría de efectos, retirada de fixtures ni UI.
Revisión independiente estática favorable, 34 contratos de custodia y 15 de
residuos/limpieza pasan. Una respuesta incierta sigue bloqueando replay y cierre.
El ciclo real de renovación aún no se ha ejecutado.

El coordinador conecta ahora esa cadena mediante `nativeRenewalMode: 'cold'`
en iOS: instala metadatos vencidos, observa el destino y Back en frío, verifica
el snapshot renovado, reconoce su lectura y limpia la sesión exacta antes del
cierre del canal y la retirada de fixtures. No invoca renovación desde el harness.
El modo rechaza combinaciones con login UI, destinos negativos o Android y no
ejecuta ni acredita renovación en caliente. La revisión independiente preparatoria
es favorable; 22 contratos pasan, incluido un proceso aislado que recorre seis
escenarios sintéticos con la máquina de custodia real. Los fallos de instalación,
observación, lectura, ACK o clear conservan los journals y bloquean la retirada.
Sigue pendiente el ensayo real con manifiesto nativo actualizado y cierre verificado.

## Ensayo iOS frío cerrado, 12 de septiembre de 2026

El run `c8be6efa-e74c-4ef6-a506-69e28e3bb710`, producto
`29456502c3b4119c127a93163ac413ba5c3033cb`, completa el coordinador anterior:
PASS, identidad remota verificada, clear exacto, canal y fixtures cerrados,
proceso terminal 0 y directorio privado vacío. El mensaje `11223` del hilo `2587`
queda enfocado tras entrega externa sin PID previo; PID `75230` continuo hasta
Back a Chats. Dos capturas revisadas por el orquestador y un revisor independiente
acreditan mensaje y salida. GO local frío; no JWT criptográficamente vencido,
conteo HTTP, causalidad exclusiva del enlace, caliente ni candidata integrada.

Procedencia: `build-reports/flow-deep-links/ios-native-renewal-cold-516410f7-d27a-445c-9ec3-b51755da16a7/`
contiene report, process, delivery y `visual-export/manifest.json` con hashes de
ambas PNG. `remoteVerified: false` y `renewed_snapshot_unverified` son el resultado
estructural anterior; `identity.identityVerified: true` registra la verificación
remota posterior. `refreshObserved: false` no afirma observación de peticiones HTTP.

## Preparación de entrega caliente tras preludio público

`nativeRenewalMode: 'warm'` conecta el mismo ciclo de custodia con un preludio
explícito: después de instalar metadatos vencidos, el worker lanza la app sin
argumentos de fixture, registra su PID y exige el mismo proceso antes de entregar
el enlace y hasta Back. El observador debe estar listo antes de la entrega.
El recibo distingue `renewalPrelude: true`; no simula un Chat previo ni reutiliza
la aceptación fría. Este modo excluye destinos negativos y sesiones no preparadas.

La validación del arranque puede renovar durante el preludio. Por ello, el alcance
es renovación durante preludio/recorrido con entrega caliente; no sesión todavía
vencida al recibir el enlace ni renovación causada exclusivamente por él. La fila
`FLOW-DEEP-LINKS` exige app abierta/cerrada, sin imponer todas las combinaciones de
expiración y lifecycle. No se añade un mutador de sesión con el proceso vivo para
extender esa exigencia. El ensayo real de esta variante permanece pendiente.

## Ensayo iOS caliente tras preludio cerrado, 12 de septiembre de 2026

Run `28db5b0a-e727-45ed-87e4-e3789e36f7f6`, producto
`fcd0afb992801b994f2ac67c42b9786e5055bfb8`: PASS y limpieza completa, identidad
remota del snapshot renovado verificada, clear exacto y canal/fixtures cerrados.
El proceso termina con código 0 y el directorio privado queda vacío. El PID
`76969` permanece desde el preludio público hasta entrega, foco del mensaje
`11224` del hilo `2588` y Back a Chats. GO local tras revisión independiente
de recibos y dos capturas; simulador dedicado apagado y estable preservado.

Procedencia: `build-reports/flow-deep-links/ios-native-renewal-warm-36dd3cbf-3970-4a24-9aae-a8aa0acfb0b6/`
contiene report, process, delivery y `visual-export/manifest.json`. Hash PNG del
mensaje: `9d896abc5671fa159989135b156d68bd7e8a682cea9e9e2c5f0328e0dffbf98d`;
retorno: `602f31abd019ac9aca0a37a24598727e5a7ff336c05b330f059460796b255d0e`.
Se acredita renovación durante preludio/recorrido y entrega caliente. No se
acredita sesión todavía vencida al entregar, conteo HTTP, causalidad exclusiva
del enlace, rechazo ni candidata integrada. El ensayo frío anterior se conserva.

## Integración preparatoria Android fría

El canal Android conecta instalación vencida, lectura del snapshot rotado y clear
exacto; exige propietario y sesión Auth originales, tokens rotados y pasos nuevos.
Una respuesta incierta conserva la lease. El coordinador prepara la plataforma
Android, verifica identidad remota y limpia sin ACK iOS; el transporte Android
ya exige respuesta privada y finalización de la instrumentación. Tras cerrar la
sesión y el canal, verifica y retira los registros nativos propios mediante el
protocolo de residuos existente antes de retirar perfiles y journals.

El modo es exclusivamente frío, separado de la observación fría/caliente ordinaria.
Revisión independiente preparatoria favorable; los escenarios sintéticos cubren
el ciclo completo y ausencia de retiro prematuro ante fallos. Aún no acredita
renovación real Android. Antes del ensayo hace falta compilar el producto actual:
`LoginForm.kt` común cambió desde el APK Android conservado de `5925822b`.

## Ensayo Android frío cerrado, 12 de septiembre de 2026

Run `f57723e6-e753-4fd3-bd06-9903a9ab08e0`, producto
`62a826a12b6b80a6c8037fb5094e76bfa83e4eef`: instalación de metadatos vencidos,
snapshot rotado con identidad remota verificada, mensaje `11225` del hilo `2589`
enfocado y Back a Feed. Entrega HTTPS implícita desde otro UID, sin paquete ni
componente explícitos; app inicialmente sin PID y PID `9758` tras observación.
PASS, proceso terminal 0 y limpieza completa: cinco pasos de custodia exitosos,
forwards retirados y directorio privado vacío. GO local independiente de tres PNG
y recibos; no conteo HTTP, causalidad exclusiva del enlace, caliente ni integración.

APK compilado sobre ese SHA:
`1eb27454e0f03710fd86e1f6bd855e9355e6f5b86bd6d3e9ea509f1be679405a`.
Custodia `058182f7ffff120046d9f2d8ff72c98b13dc50b6612a6a9b0b1f4c5d06376915`
y emisor `10554afe0200f4cc2ad1484ab7c1219827bd46348ec1544712ff3482f266e31f`
permanecen sin cambios. Se archivaron los APK anteriores antes del build y la
instalación verificó las sondas vacías inicial/final.

Procedencia: `build-reports/flow-deep-links/android-native-renewal-cold-c5814286-abc7-4813-ae7e-ee250cdd9a43/`.
Las capturas están en `chat-cold-4b8af024-389c-4b51-9912-3e2eea85db6d/device/`:
hash de `focused.png`, `25c9a3ee6c387927f2cc8faeb02127b12f416b07ff97177e2fd91e543cdee91a`;
de `back.png`, `b5db1e9ca452b58af70eb1dcc0245cf3c2478717aba28dfcc39ad946d3beb6e1`.
El metadato vencido no equivale a JWT criptográficamente vencido; la verificación
remota posterior está en `identity.identityVerified`, separada del clasificador.

## Rechazo Android frío cerrado, 12 de septiembre de 2026

Run `bd8cbfe3-c740-4944-a6f5-3247436d801f`, coordinador
`a71711573f281a6be80f02b27cab165cd6e1516f`, APK y tests conservados del ensayo
`62a826a1` anterior, con sus tres hashes exactos verificados. El diff posterior
sólo afecta documentación y coordinadores/tests Node; no fuentes compiladas Android.

Tras instalar el metadato vencido, se revocó exclusivamente la sesión original
de la fixture con registro durable y auditoría remota. Entrega HTTPS implícita
desde el emisor de otro UID al hilo `2590`, mensaje `11226`, sin PID previo.
El producto registró HTTP 400 en PID `10585`, timestamp `1789248856.005`, dentro
de `1789248849.637315700..1789248868.198808800`. Sólo se conserva el testigo
numérico normalizado, sin cuerpo HTTP ni logcat completo. Barrera pública sobre
Feed, cancelación y Feed sin Chat durante dos segundos.

PASS y proceso terminal 0; cuatro pasos de custodia exitosos, incluido probe
de ausencia tras rechazo y probe final, forwards retirados y privado vacío.
Orquestador y revisor independiente inspeccionaron las dos PNG: GO local.
No acredita login posterior, rechazo iOS, conteo HTTP, continuidad desde el
primer PID, vencimiento criptográfico del JWT ni candidata integrada.

Procedencia: `build-reports/flow-deep-links/android-native-rejection-cold-8687afc7-65f3-4a5b-b319-85c86c66989b/`.
Capturas en `chat-cold-bb941cea-9e12-41c0-bf1f-746f1a8c3bc9/device/`:
`auth-gate.png`, `8165181ea1738213fd6fe963c3d4153c93845f7a597f3eb6420e127757b68a71`;
`back.png`, `af3cc6b58741a5b5be8e18def7b7c62a93d84ca3508b1e25f76b91d5e4306ed9`.

## Rechazo iOS frío cerrado, 13 de septiembre de 2026

Run `4ca85950-42ba-4787-9887-ab8c8b28cc1f`, producto compilado y worker verificado
sobre `67482ac5981280095c9861417035d9b6eef91abc`: PASS y limpieza completa.
Instalación de metadatos vencidos y revocación exclusiva de la sesión propia,
con recibos duraderos y auditoría remota. El arranque frío mantiene el simulador
encendido, pero termina la app de custodia antes del ACK y exige ausencia de PID
antes de entregar el enlace externo. No equivale a reiniciar el sistema operativo.

El observador llegó a READY antes de la entrega. PID `2170` estable durante la
observación; HTTP 400 en `1789268214667062000` ns, dentro de la ventana original
`1789268209689814000..1789268242860703000` ns. La consulta única conservó su límite
de 15 segundos y devolvió una fila de 1.287 bytes; el lector completo tardó
9,343 segundos. Sólo se conservaron el testigo normalizado y fases/conteos del
diagnóstico, sin salida bruta de logs. No se deduce un conteo exhaustivo de HTTP.

El test nativo pasó en 32,385 segundos: barrera sobre Feed, cancelación y Feed
sin Chat durante la ventana de dos segundos. Orquestador y revisor independiente
inspeccionaron las dos PNG y sus hashes: GO local. Clear exacto, cierre nativo,
fixtures retiradas, proceso terminal 0 y directorio privado vacío; simulador
candidato apagado y estable preservado. No acredita login posterior, vencimiento
criptográfico del JWT, causalidad exclusiva del enlace ni candidata integrada.

Procedencia: `build-reports/flow-deep-links/ios-native-rejection-cold-1106189f-ef02-4aa7-9391-443543c49ee1/`.
Paso de observación `51888d61-4b97-43a9-bfdc-9b061b43a693`; capturas en `visuals/`:
`native-rejection-delivered-gate.png`,
`f149dde574277039aab0bbb882917fdfedabba7a94088ce4a45d7a4f43e76ed6`;
`native-rejection-cancelled-feed.png`,
`6644ccf927a042a9459a2029137c5820cefda89e592dfc7f4f5787cbd1a2ce97`.
Los ensayos fallidos anteriores conservan sus reportes originales y cierres
de reconciliación separados; no se combinan con este run para acreditar aceptación.

## Cancelación y login posterior Android cerrados, 13 de septiembre de 2026

Run `a9d6b9bd-d4a7-4de6-8e40-adddbeb778bf`, variante `cancel-then-feed`,
coordinador y emisor de pruebas en `e2d4b8a6a4208a66bf3d37c56fc6807a76b004e6`.
APK de producto conservado de `62a826a1`, hash
`1eb27454e0f03710fd86e1f6bd855e9355e6f5b86bd6d3e9ea509f1be679405a`;
emisor de pruebas recompilado, hash
`24c2e8187168317f21555e894315a267165f24d753b997e7299b681f1e047d48`.

Entrega externa fría al mensaje `11231`, cancelación de la barrera y Feed.
Una acción anónima Like abre una barrera nueva, sin encolar el Like. Un único
Submit nativo autentica y termina en Feed, sin recuperar el Chat abandonado,
durante la ventana final de dos segundos. PID `13023` igual antes y después
de la observación. Identidad Auth remota verificada, una sesión nativa y ninguna
Web; lectura, clear y retirada de fixtures completos. Proceso terminal 0,
privado vacío, tres PNG inspeccionadas por orquestador y revisor: GO local Android.
No acredita iOS, arranque caliente ni candidata integrada.

Procedencia: `build-reports/flow-deep-links/android-native-login-cold-cancel-a0cb807f-5c0d-485e-b8f8-f89c0a309361/`.
Paso `native-login-0334e2b2-db66-4d21-992d-b95cc6593762`, capturas en `device/`:
`gate.png`, `accb2cc65fa707e30fbf39178448c98c7b93704dbd6faf36c6dd8533a7f39da5`;
`cancelled-feed.png`, `58ce208d79db92d15d9c65cc08d56d089f2455fba0b0b2824914785a7b557ad3`;
`authenticated-feed.png`, `f42c109992e8b6e44766e45300dac9ee7a7ba8d120efb94d338845ded8fe6597`.

El ensayo equivalente iOS `f0a10edf-0b85-40df-aef4-7c8eecae5c03` sobre el mismo
head falló al exigir Feed tras Login. OCR del último fotograma de su vídeo XCTest
identificó Novedades y Continuar, coherente con la presentación normal posterior
a Login. No acredita que reapareciera el Chat ni aceptación funcional.
Se conservó el reporte fallido y se reconcilió por separado: lectura propia
`5aaa72fd-f25e-40d6-be01-f4c1432dbf81`, identidad remota y snapshot duraderos,
ACK, clear exacto `6e4af35e-f876-472d-80f8-196ec37ad99e`, cierre nativo terminal 0,
fixtures e input privados retirados, directorio privado vacío. El observador
se ajusta para cerrar Novedades mediante su control público una sola vez,
manteniendo el límite original de 45 segundos y la ausencia del Chat cancelado.
Quedan pendientes recompilación y ensayo iOS nuevo; el fallo no se convierte en PASS.

El siguiente ensayo, `3cb4fdf8-115c-453c-91f2-5a94b630814e` sobre `606fa15d`,
también conserva NO-GO: entrega externa correcta, Novedades detectada tras Login,
pero búsqueda de `whats-new-dismiss` fallida antes de cerrar la pantalla. El
observador terminó en 75,446 segundos. Los snapshots AX adjuntos son parciales
y no identifican el control; no justifican sustituirlo por otro selector.
La reconciliación separada cerró lectura `761894a0-0cad-4c5c-bafe-ae8aab539c76`,
ACK y clear exacto `931398a3-b774-4aed-8ca7-ef9dbb8aea25`, con identidad remota,
proceso terminal 0, fixtures e input retirados y privado vacío.

Antes de otro Login se prepara `testInspectPublicWhatsNewControlAccessibility`,
opt-in sobre la fixture pública existente: registra controles AX y coincidencias
de selectores, sin valores de campos ni Submit. No cambia el selector del ensayo
real. Su resultado sólo describe la fixture/build observados; debe contrastarse
con la pantalla normal antes de renovar la aceptación funcional.

Diagnóstico `ecd52b58-8ffa-41c1-9fb8-f03ea82461f8`, producto/runner `6a7d6920`:
PASS en 30,389 segundos, probes inicial/final de sesión vacía y cierre terminal 0.
El JSON AX confirma `whats-new-dismiss`, etiqueta `dismiss_whats_new`, habilitado
y pulsable; no corresponde sustituir el selector. PNG y JSON conservados en
`build-reports/flow-deep-links/ios-whats-new-ax-6a7d6920/`, hashes respectivos
`ada9169ac9cd041765321ec91df57b47931ec621b85b9e593c432cdd13e4c4a0` y
`65efc1733b791115f411df6144eb5a0595e7b0ffe1f10d48677860860edf9cf9`.
`WhatsNewScreenHost` carga las versiones de forma asíncrona antes de montar el
control común. El observador conserva el selector y espera su disponibilidad
dentro del mismo límite de 45 segundos; mantiene una sola pulsación, Auth ausente
y las guardas de Chat/barrera. Esto trata una transición legítima, sin demostrar
la causa exclusiva del fallo anterior. Falta recompilar y pasar el recorrido real.

Ensayo `e039b272-e1b4-48aa-a634-f1c60e75f6ff`, head `97ac77ce`: el observador
automático emitió PASS, pero la captura final muestra Novedades, no Feed. La
revisión visual conserva **NO-GO**. Reporte original intacto y dictamen separado
en `ios-native-login-cold-cancel-a601321f-3dc5-4ab7-8289-460560ada6fe/visual-review.json`
bajo `build-reports/flow-deep-links`; PNG final SHA256
`51360f1b388c844315eb4c84c2e47e0375c2f7826fdc3484671925d0b051ffa9`.
El proceso terminó con código 0, limpieza completa y privado vacío. No se
reutiliza este PASS automático como aceptación del retorno a Feed.

La evaluación de Novedades posterior a Login es asíncrona. El observador sólo
exigía el identificador del host Feed y no excluía Novedades al estabilizar ni
comprobaba el estado después de capturar. Se exige ahora contenido común Feed
pulsable y ausencia de Novedades, Auth, barrera y Chat durante los dos segundos
existentes y alrededor de la captura, dentro del mismo límite de 45 segundos.
Se conserva un único Submit y cierre de Novedades. La prueba del router contrasta
controlador montado y metadatos durante Feed → Novedades → Feed tras el refresco
de autenticación; no se atribuye corrupción de rutas al producto sin evidencia.
La aceptación iOS sigue pendiente de compilación y ensayo visual real.
