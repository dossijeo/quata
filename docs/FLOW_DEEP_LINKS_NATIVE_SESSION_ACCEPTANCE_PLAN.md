# FLOW-DEEP-LINKS: renovación y rechazo de sesión nativa

Estado: preparación; sin ejecución real ni GO. Inspección de fuentes sobre
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
   Determinar el mecanismo de observación del transporte nativo antes de ejecutar:
   una consulta DB o un cambio de token aislado no prueba número/orden de envíos.
5. Con renovación válida, exigir hilo/mensaje/foco exactos y salida sin reapertura.
   Con rechazo, exigir evidencia del rechazo real y barrera sin contenido privado;
   distinguir sesión local conservada, eliminada y transporte todavía incierto.
6. Retirar sólo el snapshot final verificado, o acreditar ausencia nativa si el
   producto lo eliminó. Auditar efectos y retirar fixtures/journals/leases mediante
   el protocolo existente. No pasar el snapshot original al clear si hubo rotación.

Antes de ejecutar con backend, cubrir sintéticamente respuesta perdida, identidad
inesperada, renovación anticipada, fallo de persistencia/ACK y clear con snapshot
equivocado; revisión independiente del coordinador y del transporte completo.
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
este preparador a fixtures reales: faltan instalación, observación del transporte
y cierre del ciclo; no se ha ejecutado una renovación ni revocación nativa real.

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
aceptación integrada; siguen pendientes transporte observado y cierre tras rotación.

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
remota, observación del refresh y cierre completo permanecen pendientes.

`verifyNativeDeepLinkExpiryIdentity` añade la verificación preparatoria del token
ya persistido: GET a Auth y consulta de la sesión original única, perfil activo y
marca de propiedad del fixture. Registra intención antes de la lectura remota y
conserva el recibo original. Sólo devuelve `identityVerified: true` junto con
`refreshObserved: false`; no acredita número/orden de peticiones ni permite ACK o
cierre. Revisión independiente estática favorable y 14 pruebas sintéticas pasan,
incluidos actor distinto, sesiones adicionales, respuesta perdida y fallo de disco.
Todavía no se ha invocado este verificador contra un ensayo real de renovación.
