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
