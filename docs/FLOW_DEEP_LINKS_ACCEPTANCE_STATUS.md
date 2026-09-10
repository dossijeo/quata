# FLOW-DEEP-LINKS: estado de aceptación focal

Estado: **parcial; sin candidate-final ni GO integrado**. El inventario maestro
permanece pendiente. Esta matriz resume resultados actuales; no sustituye los
reportes y capturas ni promueve CHAT-FOCUSED-MESSAGE o FLOW-SHELL-NAV.

Último producto Web con evidencia real: `edbb970b0108d0e6804f7a010865edfcdfaecded`.
Distribución: `39a6b782a1be4c0fe010d1dc3ed1e4a455e67933dea09a7ce9747515a2f724a5`.
El ensayo de hilo inexistente usa este binario y verifica la presentación corregida.
Revocación, renovación válida y mensaje ausente conservan producto `3bcfec15`,
distribución `1253d2b7…`. Los recorridos públicos y login real conservan producto
`c252e00035e97065726fc51aee5a4d6469975324`, distribución
`ca9990b2840087bfcb31508d65968a722bf7fdb38e8af594a8b7f1e0fc9019c4`.
Chat con sesión preinyectada sin renovación conserva producto `8cde7edf…`,
distribución `7c743c4f…`. Hashes completos y límites en el plan de sesión.
Ningún resultado se transfiere al head final por inferencia.

| Recorrido Web | Frío | Caliente | Salida / recarga | Límite pendiente |
| --- | --- | --- | --- | --- |
| Feed, post existente | Renovado en c252e000 | Renovado en c252e000; mismo documento | Lista sin reapertura; cero errores | Certificación de candidata final pendiente |
| Feed, post inexistente | Comprobado en c252e000 | Comprobado en c252e000; mismo documento | Reintento focal, vuelta y recarga sin reapertura; cero errores | No acredita fallo de red ni otras plataformas |
| Oficial, post existente | Renovado en c252e000 | Renovado en c252e000; mismo documento | Lista sin reapertura; cero errores | No acredita reproducción multimedia; capturas con placeholder de vídeo |
| Oficial, post inexistente | Renovado en c252e000 | Renovado en c252e000; mismo documento | Reintento HTTP 200 sin filas, vuelta y recarga a Oficial; cero errores | No acredita fallo de red |
| Enlaces sin ID: post-, official-, chat- | Feed visible comprobado en c252e000 | Feed visible comprobado en c252e000 | Recarga resuelve Feed; hash original conservado | No generalizar a todo enlace malformado |
| Chat, hilo/mensaje propio, sesión válida | Comprobado | Comprobado; mismo documento | Un foco visible; salida/recarga sin reapertura | Destino inexistente y sesión expirada; ver fila anónima para login |
| Chat anónimo | Barrera de acceso comprobada | Barrera, continuación con login real y login posterior a cancelar comprobados en c252e000 | Hilo/mensaje exactos al continuar; tras cancelar, cierre normal de Novedades y Feed sin foco residual en mismo documento | Login por bridge de repositorio, no Submit manual; ventana acotada; no demuestra ausencia universal de peticiones privadas |
| Chat, metadato local de sesión vencido | Renovado en 3bcfec15 con runner e57f0b36: un refresh real, recibo verificado y mensaje exacto visible | Pendiente | Vuelta y recarga sin reapertura ni segundo refresh; limpieza completa | No acredita JWT realmente vencido ni sesión revocada; preservado el primer ensayo fallido |
| Chat, mensaje ausente en hilo propio | Comprobado en 3bcfec15: RPC exitoso e historia del fixture agotada, cero foco | Comprobado; mismo documento | Vuelta y recarga a Chat sin reapertura; limpieza completa | No acredita hilo inexistente ni aviso explícito; ventana acotada |
| Chat, hilo inexistente | Comprobado en edbb970b: fallo de lectura legible único, sin mensajes ni foco | Comprobado; mismo documento | Vuelta/recarga a Chat sin reapertura; limpieza completa | No se ejecutó Retry; ausencia probada por DB, no inferida del 403; sin aviso específico de inexistencia |
| Chat, sesión propia revocada | PASS en 3bcfec15: un rechazo entregado/verificado, barrera anónima sobre Feed, cero foco/ruta Chat observados | Pendiente | Limpieza automática completa | Ventana de dos segundos; sin claim de JWT vencido anticipadamente ni otras plataformas; primer fallo conservado |

Los recorridos públicos usan lectura anónima de publicaciones existentes. Chat
usa perfiles, sesiones, hilo y mensaje temporales propios. Último run
`a8709686-2ff9-4274-b602-b6a77373f115`: hilo ausente con presentación corregida PASS,
limpieza automática verificada y journals/lock retirados.
Las aceptaciones anteriores conservan sus límites y procedencia. Estos runs Web
no tienen mutaciones ni restituciones pendientes; el estado de ensayos iOS se
registra por separado. Detalle y límites en
[el plan de sesión](FLOW_DEEP_LINKS_SESSION_ACCEPTANCE_PLAN.md).

Las observaciones Web no prueban segundo plano/primer plano del sistema,
service workers ni recepción de push. El plazo observado tras salida es acotado;
no es una garantía indefinida. Los marcadores de diagnóstico no reemplazan la
inspección visual de las capturas.

## Android e iOS

Estado iOS Chat actual: **aceptación local de custom scheme con sesión válida
importada, frío/caliente y vuelta**, producto `edbb970b`. Los runs
`6b05776c-d8e7-4694-a155-f6a27cd1141f` y
`75ea641f-a3d9-4410-b9d6-8afffcdcbb6c` fallaron y quedaron completamente
reconciliados. El segundo acredita entrega fría al proceso y su jerarquía
guardada contiene la burbuja exacta seleccionada; falló un selector que exigía
igualdad con la etiqueta Compose, mientras iOS añadía los textos hijos.
Corrección del observador revisada en `a9a86189`, build-for-testing y watchdog 0.
Se mantienen producto y duración del resaltado. El ensayo corregido se registra en
`build-reports/flow-deep-links/ios-owned-chat-7caf2a5a-03e2-472a-bb61-c4c6d5864285/`;
run `b5d489cb-8fae-420b-b861-af32a06b6152`, terminal 0, PASS y cleanupComplete
true. Los dos XCTest/watchdog terminaron 0; PID 40099 conservado tras entrega
y durante el tramo caliente. Las cuatro capturas inspeccionadas por orquestador
y revisor muestran mensaje exacto resaltado y listado al volver. Private Windows
vacío, inputs nativos retirados y simulador apagado. El plan de sesión conserva
fingerprints y límites: no acredita login nativo, sesión inválida, Universal
Links ni reapertura tras reiniciar. Sin GO integrado ni promoción de vecinos.

Preflight Android renovado sobre fuente `302542a4`: `:app:assembleDebug` PASS
en 1m50s, APK SHA-256 `635daf1b12c598081956deb4828f47a33de9823ffbc683fac962a3e7f1b3dc20`.
Instalado correctamente en AVD nuevo y aislado `QuataDeepLinksApi35`,
`emulator-5560`, después de confirmar `sys.boot_completed=1`. El primer intento
de instalación durante el arranque falló por servicio de almacenamiento aún
inicializándose; no se interpreta como fallo del APK. `pm get-app-links` devuelve
`verified` para `egquata.com` y `www.egquata.com`. No había proceso de Qüata.
La apertura con `adb shell am start` no se ejecutó: el control automático de
ejecución la rechazó como `blocked by policy`, incluso sin detener la app.
No se eludió mediante otro mecanismo. Reporte local
`build-reports/flow-deep-links/android-302542a4-preflight.json`.
Build, instalación y verificación de dominios no acreditan recepción de la URL.

- Android: existen build e instalación previos, pero falta completar la recepción
  real fría/caliente de los tres tipos y sus salidas sobre la candidata. Un build
  o entrega explícita al paquete no acredita App Links público/chooser.
- iOS: existe la corrección y pruebas del orden de restauración/entrega del enlace,
  pero faltan recorridos externos reales completos en el host. Se valida custom
  scheme; no inferir Universal Links sin Associated Domains.

Avance iOS tras el reinicio: producto `12128cb0`, framework raster x86_64
`5ba878e424dc76b6767e3f58bae6ea93b9dd50e68d3804ba434a91c87b445ae1`.
Framework, build-for-testing SimulatorSigned, recursos y firma pasan. En el
simulador dedicado `F2E1EA50-FBAD-443C-A98F-2A576C14C70B`, `simctl openurl`
entrega el enlace Feed `e3aa9c1e-a458-4d3b-a35e-4cbd3b4e858b` y aparece el aviso
SpringBoard. El observador XCTest nuevo pasa 1/1, sin iniciar ni activar la app:
gestiona opcionalmente Abrir/Open y observa `feed.detail.chrome`, sin host Auth.
La captura posterior inspeccionada muestra detalle de publicación, autor JO y
la imagen esperada. Esto no prueba por sí solo ID exacto, entrega caliente,
consumo único ni salida. Revisión independiente estática aprobada con corrección
del comentario para no afirmar que un aviso opcional se verifica siempre.
Resultado remoto: `build/reports/ios/deep-links-external-feed-observer-12128cb0.xcresult`;
capturas locales: `build-reports/flow-deep-links/ios-feed-{first,observed}-12128cb0.png`.
El observador se compiló como adición de test sobre ese producto; no se transfirió
la evidencia de otros targets a esta comprobación.

Oficial caliente sobre el mismo producto iOS: enlace a
`9779260c-e5b8-488e-aa04-0c11cc33654e`, con PID `3225` idéntico antes de
`simctl openurl`, después de la entrega y después del observador XCTest (1/1 PASS).
La captura `ios-official-warm-12128cb0.png` muestra «Lanzamiento musical» y su
detalle. La extensión opt-in `CHECK_BACK`, revisada independientemente, pasa 1/1:
pulsa `official.detail.back`, exige desaparición del chrome y presencia del host
Oficial. `ios-official-back-12128cb0.png` confirma visualmente el listado sin
cabecera de detalle. Resultados remotos `deep-links-external-official-observer-12128cb0.xcresult`
y `deep-links-external-official-back-12128cb0.xcresult` en `build/reports/ios`.
La posterior orden `simctl launch` sin URL conserva PID; no equivale a un ciclo
completo de segundo plano ni acredita persistencia tras terminación/reinicio.
No se ha reproducido vídeo ni ejecutado acciones de escritura.

Feed caliente y vuelta: mismo PID `3225` antes/después de entregar el enlace y
tras el observador con `CHECK_BACK`; 1/1 PASS en
`deep-links-external-feed-back-12128cb0.xcresult`. Sus dos adjuntos exportados a
`build-reports/flow-deep-links/deep-links-feed-back-attachments-12128cb0`
se inspeccionaron: detalle con JO/imagen esperada y Feed sin cabecera de detalle.
Después se terminó la app, se comprobó ausencia de su entrada launchctl y se
abrió sin URL (nuevo PID `4535`). La captura `ios-feed-relaunch-12128cb0.png`
muestra Feed sin reapertura del detalle. Es una observación de ese arranque,
no una garantía temporal indefinida ni una prueba de otros destinos o sesiones.

Oficial frío: se termina la app, se comprueba ausencia de proceso y se entrega
el enlace antes del observador. `deep-links-external-official-cold-12128cb0.xcresult`
pasa con vuelta; sus dos capturas en `deep-links-official-cold-attachments-12128cb0`
muestran «Lanzamiento musical» en detalle y luego el listado, inspeccionadas.

Feed inexistente, entrega sobre app en ejecución: el observador ahora permite
exigir un texto público concreto antes de capturar. Con el ID
`00000000-0000-4000-8000-000000000001`, espera el mensaje completo «Esta publicación
ya no está disponible.» y comprueba vuelta. Pasa en
`deep-links-external-feed-missing-12128cb0.xcresult`; adjuntos inspeccionados en
`deep-links-feed-missing-attachments-12128cb0` muestran mensaje/Reintentar y Feed
tras volver. No acredita todavía reintento, arranque frío ni continuidad de PID
para este caso. La revisión independiente aprueba la espera textual con el límite
de que presencia accesible no sustituye revisión visual ni acredita un ID por sí sola.

Renovación de inexistentes completada sin cambiar el observador: Oficial sobre
app en ejecución pasa mensaje terminal y vuelta; después Feed y Oficial pasan
desde app terminada, comprobando ausencia de proceso antes de cada URL.
Resultados 1/1 por ensayo: `deep-links-external-official-missing-12128cb0.xcresult`,
`deep-links-external-feed-missing-cold-12128cb0.xcresult` y
`deep-links-external-official-missing-cold-12128cb0.xcresult`.
Los seis adjuntos en `deep-links-official-missing-attachments-12128cb0` y
`deep-links-{feed,official}-missing-cold-attachments-12128cb0` se inspeccionaron:
mensajes terminales visibles y vuelta a los listados. Reintentar está visible,
pero su ejecución sigue pendiente; tampoco se infiere continuidad de PID en los
ensayos de inexistentes sobre app en ejecución. El límite previo de frío pendiente
queda resuelto para esos dos destinos, sin promoción de Chat ni de toda la unidad.

Enlaces iOS sin ID (`#post-`, `#official-`, `#chat-`), mismo producto: las tres
entregas calientes conservan PID y el listado Oficial; capturas inspeccionadas
`empty-{post,official,chat}--warm-12128cb0.png`. El dispatcher rechaza el destino
ausente y el host ignora ese resultado, por lo que no se exige redirección a Feed
en caliente. En frío se terminó la app y se comprobó ausencia de proceso antes
de cada entrega. La captura de Chat a los cinco segundos muestra Feed; las de
post/official aún muestran splash y no acreditan el resultado final. Se conservaron
y se repitieron únicamente esos dos arranques, con captura a los quince segundos:
`empty-{post,official}--cold-after-wait-12128cb0.png`, ambas con Feed descubierto.
Las ocho capturas están en `build-reports/flow-deep-links` y se inspeccionaron.
Son observaciones visuales acotadas, sin aserción automática de finalización ni
garantía temporal indefinida; no cubren todas las clases de URL malformada.
No se modificó producto ni se hicieron mutaciones de cuentas o backend.

## Evidencia descartada y procedencia

El bundle anterior `139a381f…` fallaba al volver del detalle Feed con `illegal cast`.
El mismo código y backport pasan tras recompilación completa. No se ha aislado
una causa exclusivamente incremental frente a caché/reutilización de tareas.
La receta de [backport](../third_party/compose-ui-web/README.md) recoge la
recompilación requerida al introducirlo o sustituirlo. No reutilizar aquel bundle
fallido como candidata ni trasladar su evidencia al nuevo fingerprint.

Un ensayo anterior de Chat devolvió PASS con el splash en las capturas; se conserva
como contradicción detectada. Producto y runner se corrigieron para exigir foco
con la conversación descubierta. No se considera aceptación de ese ensayo.

El [historial focal](FLOW_DEEP_LINKS_CHAT_FIXTURE_PLAN.md) detalla commits, runs,
comparaciones, límites y rutas locales de reportes. Los resultados vigentes están
en `build-reports/flow-deep-links/web-public-full-rebuild-8cde7edf` y
`build-reports/flow-deep-links/web-chat-clean-1b145e22-ac3a-4636-8ed2-83fcffd2ee14`.

## Cancelación del acceso anónimo Web

Sobre `c252e000` / distribución `ca9990b2840087bfcb31508d65968a722bf7fdb38e8af594a8b7f1e0fc9019c4`,
el enlace Chat caliente muestra «Ya tengo cuenta» en el mismo documento, sin
peticiones privadas ni errores. Escape no cerró el diálogo; no se presupone que
equivalga a Back en Compose Web. La búsqueda por rol `dialog` no encontró el nodo.
El descubrimiento posterior de ancestros del botón sí encontró el contenedor
visual, pero el observador de pulsación exterior falló antes de pulsar al buscar
«Registrar», cuando el código Web usa «Crear cuenta». Se conserva el diagnóstico
`chat-anonymous-semantic-backdrop-cancel-report.json` junto a
`chat-anonymous-dialog-discovery.json` en `web-feed-missing-c252e000`.
Todos los recursos se cerraron y no hubo mutaciones. La cancelación continúa
pendiente: estos fallos del observador no demuestran un fallo de producto.
Se detiene la cadena de intentos de interacción; antes de promover otro runner,
reconciliar el contrato real y las anclas semánticas del diálogo común.

Reconciliación completada: se inspeccionó `QuataAuthRequiredDialogContent` y el
DOM del ancestro del botón de login. El título y «Crear cuenta» identifican el
contenedor visible; el punto exterior se deriva de sus límites actuales, sin
coordenadas fijas. `observe-chat-contract-backdrop.mjs` pasa con una pulsación:
desaparece el diálogo, se resuelve Feed con hash vacío y no reaparece al recargar.
Reporte `chat-anonymous-contract-backdrop-cancel-report.json` y capturas
`chat-anonymous-contract-backdrop-{cancelled,reloaded}.png` en la misma carpeta;
ambas capturas muestran Feed descubierto y fueron inspeccionadas.
La revisión independiente acepta el ensayo focal sin nuevas anclas de producto.
Su alcance es producto `c252e000` y fingerprint indicado, no un head posterior
por inferencia. Cero errores y cero solicitudes a las cuatro rutas vigiladas
(`chat_threads`, `chat_messages`, `messages`, `conversations`); esto no prueba
ausencia universal de peticiones privadas. `mutations: 0` describe el recorrido
sin credenciales, no un contador instrumental de backend. Los recursos se cerraron.
No acredita continuación tras login, autorización de Chat ni cancelación iOS/Android.

## Cierre aún requerido

Ensayo real de continuación Web, runner `906b1e43`, producto `c252e000` y
distribución `ca9990b2…`: run `9e77e8d7-8e88-4623-ae9c-227fe51f5312` PASS,
proceso PID 19620 terminado con código 0. Contexto anónimo → enlace → aviso →
Login → repositorio real del producto → hilo `2528`, mensaje `11158`, un solo
episodio de foco descubierto, mismo documento y cero errores. Las capturas del
aviso y resultado se inspeccionaron: el mensaje propio está resaltado sin splash
ni UGC cubriéndolo. Carpeta local
`web-auth-resume-13840676-6811-4a5e-af1a-b561d1776fd9` bajo `build-reports/flow-deep-links`.
Reporte `cleanupComplete: true`; carpeta privada vacía tras verificar retiro de
las sesiones, hilo y perfiles propios. Preflight conservó fingerprint DB y hashes
de auth-bridge v79 / push v85. No hubo despliegue. Login por bridge de repositorio,
sin atribuir escritura ni Submit manual del formulario. No acredita sesión
expirada ni Android/iOS. La mención genérica a cancelación en `limits` del reporte
no corresponde a este modo y no se usa como evidencia de cancelación.

Primer ensayo real de login posterior a cancelar: run
`63814d6c-b1b2-4787-8db0-d75f9f08a32f`, carpeta
`web-auth-cancel-822edf28-7895-44de-9a49-d000d87a3f1c`, falla en observación de UI
sin errores de página. Proceso PID 23356 terminado con código 1; limpieza completa
y carpeta privada vacía. No demuestra aún defecto de producto: el reporte no
identificaba qué condición falló. Se añadió diagnóstico de etapa, estado readonly
acotado a 2 s y captura acotada a 5 s, conservando la respuesta real para registrar
recibo y limpiar. Revisión independiente aprobada y cinco tests Chrome verdes.
El reporte original se conserva y no se convierte en PASS.

Caso real de cancelación finalmente verificado con la política de arranque:
run `cae2c6b3-7bf9-40be-9fbb-df8aa55f17c0`, runner `836cdc21`, producto
`c252e000` / `ca9990b2…`. Carpeta
`web-auth-cancel-startup-cf3799f9-75f8-4178-bcdf-40cb1acfb6e4` bajo
`build-reports/flow-deep-links`. Anónimo → enlace → cancelar → Login posterior
real → Novedades → cierre por ancla → Feed. Mismo documento, sin recarga ni
navegación forzada a Feed, cero episodios de foco desde el inicio y cero errores.
Se inspeccionaron las tres capturas: aviso, Novedades y Feed final. Proceso PID
24080 termina con código 0 y `cleanupComplete: true`; carpeta privada vacía.
Se verifica sólo la ventana observada (2 s tras cerrar Novedades), no una garantía
indefinida. No acredita formulario manual, sesión expirada ni otras plataformas.
Revisión independiente de capturas, reporte y cierre: GO local para este recorrido,
igual que para la continuación tras login. No es GO integrado de FLOW-DEEP-LINKS.

Segundo ensayo de cancelación, con diagnóstico: run
`750f5cde-bccc-4f04-9aca-b9a42a2d74a0`, carpeta
`web-auth-cancel-diagnostic-1931ddff-35f9-4d28-a1de-4f1914d56cf1`, termina con
código 1 y limpieza completa; carpeta privada vacía. Falla `final_state` porque
la ruta es `whats-new`, sin destino Auth ni episodios de selección y con login
real confirmado. La captura inspeccionada muestra Novedades del primer acceso.
El contrato de arranque de Main presenta esa pantalla al volver autenticado a
Feed; no la presenta al continuar al Chat. Se conserva el ensayo como fallido.
La revisión independiente confirma que debe contemplarse la presentación opcional,
cerrarla una vez por `whats-new-dismiss` y exigir después Feed sin foco residual
en el mismo documento. No se suprime Novedades ni se cambia producto para el ensayo.

Runner adaptado al contrato: observa hasta 5 s la presentación opcional, cierra
una vez por su ancla y comprueba Feed durante 2 s, además de exigir cero episodios
de selección desde el inicio. Todo queda bajo el plazo global de observación.
Una señal de expiración impide pulsaciones tardías tras esperar captura/bounds.
Revisión independiente aprobada; siete casos Chrome verdes y el negativo focal
renovado confirma que bounds comenzó y terminó después del plazo, manteniendo
el contexto vivo, sin emitir el click tardío. Reporte
`web-auth-no-late-click-tests-e9adf1a7.log`. No es todavía aceptación real del caso.

Oficial existente Web renovado en `c252e000` / `ca9990b2…`, destino
`9779260c-e5b8-488e-aa04-0c11cc33654e`, «Lanzamiento musical». El primer
observador encontró el título tanto en chrome como en tarjeta: se acotó al
chrome. `official-existing-scoped-report.json` conserva el recorrido frío
completo, con respuesta focal HTTP 200, ID/título, vuelta y recarga; su resultado
global es fallido porque el tramo caliente esperaba incorrectamente otra
petición focal. `OfficialFeedViewModel` reutiliza el post ya presente en lista.
`official-existing-warm-cached-report.json` pasa el tramo caliente: listado
HTTP 200 con el ID/título exactos, tarjeta semántica del ID visible, chrome,
mismo documento, vuelta y recarga sin detalle. Cero errores; recursos cerrados.
Los seis PNG `official-existing-{cold,warm}-{detail,back,reload}.png` en
`web-feed-missing-c252e000` se inspeccionaron. Muestran contenido y navegación,
con placeholder de vídeo: no se atribuye reproducción ni carga de thumbnail.

La revisión independiente del pendiente de autenticación de Chat identifica dos
recorridos aún necesarios con fixture propio: (1) anónimo → enlace → login real
→ hilo/mensaje exactos con foco descubierto; (2) anónimo → enlace → cancelar →
login posterior → Feed sin hilo/foco residual, comprobado antes de recargar.
Reutilizar el fixture y sus recibos, con ticket registrado antes de cada login
y restitución verificada entre casos. La preparación actual inyecta sesión;
no sirve para demostrar esas transiciones. El bridge `__quataAuthE2eProduct.login`
atraviesa repositorio y `completeLogin`, pero no acredita escritura/Submit manual.
No usar `restore()` ni navegación manual al destino después del login, porque
ocultarían fallos de continuación. No se han ejecutado esas mutaciones todavía.

Preparación del coordinador para esos casos: `loginDeepLinkSession` admite un
transporte de login separado del transporte que verifica el recibo. La auditoría
del propietario y de ausencia de sesiones, y el checkpoint `requestStarted`,
siguen precediendo al callback. La pareja opt-in `ui.prepareLogin` / `ui.requestLogin`
prepara el destino propio antes de autenticar; el recorrido de sesión inyectada
conserva su orden anterior. Quince tests focales pasan, incluidos respuesta perdida,
prohibición de repetir ticket, transporte independiente y configuración incompleta.
Revisión independiente estática sin bloqueantes. Falta el adaptador browser y su
validación: debe devolver la respuesta real de un solo login, limitar la espera
y conservar incertidumbre si pierde respuesta o quedan operaciones pendientes.
Esto es preparación del runner, no evidencia de autenticación ni aceptación E2E.

Adaptador browser conectado, opt-in `authenticationMode: resume | cancel`:
contexto nuevo sin inyección de sesión, aviso real, entrada a Login y llamada al
bridge del repositorio de producto. Observa la respuesta HTTP de un solo login
y coteja los campos que envía realmente Web (sin exigir `profile_id` en petición;
la identidad final sigue verificándose en respuesta/recibo). Captura el foco
antes de verificar recibos remotos para no perder su estado transitorio.
La observación está acotada a 45 segundos; si falla o se bloquea, devuelve la
respuesta recibida para registrarla y no permite que una tarea tardía publique PASS.
Revisión independiente estática aprobada tras esas dos correcciones.
Validación local sintética: cinco tests del transporte y cinco de Chrome pasan,
incluidos foco ausente, foco residual tras cancelar y evaluación bloqueada.
También pasan los seis casos del runner browser anterior. Reportes locales
`web-auth-bounded-tests-1314c81e.log` y `web-auth-adapter-tests-1314c81e.log` bajo
`build-reports/flow-deep-links`. Los casos Chrome usan HTML/backend sintéticos:
validan el runner, nunca sustituyen aceptación Qüata. Todavía no se han creado
perfiles ni sesiones reales para estos dos recorridos.

Feed existente Web frío renovado en `c252e000` / `ca9990b2…`: contexto nuevo,
URL inicial al post `e3aa9c1e-a458-4d3b-a35e-4cbd3b4e858b`, espera de aparición
y desaparición del splash, comprobación del ID resuelto y body exactos. Vuelta
por el ancla `feed.detail.back`, Feed sin detalle y recarga sin reapertura; cero
errores y recursos cerrados. `web-feed-cold-semantic-diagnostic-c252e000.json`
y las tres capturas `web-feed-cold-semantic-{back,after-back,after-reload}-c252e000.png`
en `web-feed-missing-c252e000` se inspeccionaron: detalle JO y Feed tras salir.
`sameDocument` en ese reporte sólo indica que no hubo recarga durante la
observación; no convierte este ensayo de URL inicial en entrega caliente.

Evidencia adicional actual: `build-reports/flow-deep-links/web-feed-missing-c252e000`.
`official-report.json` y capturas `official-*-missing.png` / `official-cold-back.png`
verifican el estado terminal, reintento y salida de Oficial, con inspección visual.
`malformed-visible-report.json` verifica seis resoluciones internas de enlaces
sin ID. Sólo sus tres capturas calientes acreditan Feed visible: las frías siguen
mostrando splash. El selector de ausencia de splash del observador no basta;
conservar ambos ensayos como diagnóstico y resolver la espera antes de aceptar
el caso frío. No hay cambio de producto ni mutaciones en estos ensayos.

Resolución posterior al reinicio del PC: `malformed-lifecycle-report.json` exige
que el splash aparezca antes de esperar su desaparición en el arranque frío.
Las tres capturas frías resultantes muestran Feed descubierto y se inspeccionaron
visualmente. Seis recorridos pasan, con mismo documento en caliente, recarga a
Feed, cero errores y recursos cerrados. La espera anterior podía acabar antes
de que Compose montara el splash; no era evidencia de un bloqueo del producto.

El reporte verifica HTTP 200 sin filas tanto al abrir como al reintentar; las
capturas fría y caliente muestran el mensaje de publicación no disponible y el
botón Reintentar. La captura de vuelta muestra Feed. La regresión de publicación
existente verifica destino exacto, mismo documento, vuelta y recarga sin detalle
ni errores. Recursos cerrados y cero mutaciones. Validación común: seis tests
focales y 57 tests totales de Feed, sin fallos ni omitidos; build Web correcto.

Completar los casos pendientes y plataformas; revisión independiente del head
exacto; correcciones; congelación; candidate-final; auto-merge; fast gates verdes
y jobs finales reales; integración certificada y reconciliación inmediata del
inventario con main. Ninguna de esas etapas se presume por los PASS locales.
