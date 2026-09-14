# FLOW-DEEP-LINKS: ensayo autenticado de Chat

Estado actual: Chat Web con sesión válida comprobado en frío/caliente sobre
Product SHA `8cde7edf`; fixtures restituidos. Las secciones siguientes conservan
el historial de preparación y ensayos, incluidos fallos y límites. No constituye
aceptación completa de FLOW-DEEP-LINKS ni cambia el inventario operativo.

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

Revisión independiente de la fuente: el listener sustituye el owner al añadir
una capa y lo deja `null` al retirarla, sin recuperar el anterior; en ese estado
la sincronización retorna sin retirar el DOM viejo. Es una hipótesis concreta,
todavía pendiente de traza de append/remove para confirmación. El gate común UGC
monta el diálogo también durante `accepted == null`, por lo que una aceptación
remota previa no elimina la creación transitoria de esa capa. No se debe ocultar
el gate ni habilitar acciones sin aceptación para hacer pasar el test.

Siguiente comprobación focal: instrumentación local del ciclo de owners, sin
texto ni credenciales. Si se demuestra la pérdida del owner principal, evaluar
la restauración de owners vivos y la resincronización al retirar la capa, con
regresión de diálogo abierto/cerrado; no aceptar sólo el caso de Chat sin modal.

Comparación local con respuesta UGC retrasada dos segundos: `delayed-terms.json`
registra primero `checking` con IDs `quata-ugc-terms-*`, después `accepted` con
los mismos IDs obsoletos, sin nodos Chat. Refuerza la relación con la retirada del
diálogo, aunque no constituye una traza interna de owners. Cero tráfico externo.

La fuente oficial Maven de `ui-wasm-js:1.10.3` conserva el mismo manejo de un único
owner; no se justifica subir a esa versión esperando una corrección. Se intentó
instrumentar una copia local homónima del listener: la compilación falla por APIs
internas y dependencias no expuestas (`shadow-compile.log`). No prueba sustitución
de la clase del Klib. Revisión independiente descarta esa técnica como backport
seguro incluso si compilase. Copia retirada de producto y conservada sólo entre
diagnósticos ignorados; `:web:compileKotlinWasmJs` vuelve a pasar
(`restored-compile.log`). El bundle certificado para diagnóstico no fue regenerado.

La vía siguiente a evaluar es reconstruir únicamente el artefacto Compose UI Web
de la misma versión con parche focal y procedencia reproducible. No introducir
sombras de clases internas, reinicios de ventana, pérdida de modalidad ni un
árbol HTML alternativo para satisfacer la aceptación.

### Viabilidad del backport aislado

La etiqueta upstream `v1.10.0` resuelve al commit
`b69b7202e3bcecf55cf1467821b529247bf6b043`. Su archivo
`compose/ui/ui/src/webCommonW3C/kotlin/androidx/compose/ui/platform/accessibility/ComposeWebSemanticsListener.kt`
coincide con el source jar 1.10.0 usado por Qüata. Copia Git aislada e ignorada en
`build-reports/flow-deep-links/compose-ui-backport-source`; no se añadió repositorio
Maven ni sustitución de dependencias al proyecto.

Propuesta aún no aplicada: conservar owners vivos, invalidar al añadir/retirar,
restaurar el último y limpiar DOM al quedar ninguno. La revisión independiente
detectó además la necesidad de retirar coherentemente `webNodes`, `nodes` y
`nodeToParent`: sin ello, restaurar A tras A → B → A encuentra cachés sin nodo DOM.
Se incorporó al patch diagnóstico, que pasa `git apply --check --recount`.
Regresiones exigidas: A → B → A con acciones; A → B → C retirando B y luego C;
cero owners y nueva raíz; UGC checking → accepted → Chat sin IDs obsoletos.

La primera compilación upstream sin parche falló en buildSrc por faltar fuentes
benchmark en el checkout parcial. Se añadieron y se inició nuevamente
`:compose:ui:ui:compileKotlinWasmJs -Pcompose.platforms=wasmJs --max-workers=2`.
No hay todavía artefacto de backport validado; no usar esta preparación como GO.

La compilación original upstream **pasa** (`upstream-build-3.log`, 1m03s), tras
corregir el argumento PowerShell a `'-Pcompose.platforms=wasmJs'`. Se prepararon
regresiones de navegador en `OwnerRestorationTest.kt` dentro de la copia aislada:
restauración A → diálogo → A con acción accesible, y retiro de capa intermedia.
La implementación original sigue intacta para obtener primero el fallo real.

Los primeros intentos del test no llegaron a ejecutarlo: faltaban fuentes `kruth`
en el checkout parcial y posteriormente falló la provisión de Node 22 por HTTP503.
Se incorporó `kruth` y se configuró el Node 22.0.0 ya instalado, con launcher Chrome
headless. La configuración local de Node requiere `--no-configure-on-demand` para
aplicarse antes de resolver las tareas; el log del intento 4 confirma esa selección.
Todavía no hay resultado funcional de estas regresiones ni parche aplicado.

### Resultado comparativo del listener

El intento 6 ejecutó los dos tests en ChromeHeadless, pero sólo produjo timeouts
genéricos. El intento 7 añadió el control upstream `CfWA11YTest.a11yButtonClick`,
que pasó, y localizó un error del helper nuevo: `delay` avanzaba el reloj virtual
del entorno antes de renderizar la raíz. Esos fallos no demuestran el defecto.

El intento 8 usa frames reales (`requestAnimationFrame`) y un límite de tres
segundos por condición. Resultado sin parche: **control PASS, dos regresiones
FAIL exactamente en restauración de raíz**. La primera llega a mostrar el diálogo
y falla en `root_restored_0`; la segunda conserva la actualización de la capa
superior tras retirar la intermedia y falla en `nested_root_restored`.
XML conservados en `baseline-stage-frames.xml` y `baseline-control.xml`.

Se aplicó el patch de owners y cachés únicamente en el checkout upstream aislado.
Mismos tres tests, mismo ChromeHeadless: **3 PASS, cero fallos/errores**
(`owner-regression-patched-1.log`, 1m11s; XML `patched-TEST-*.xml`). La primera
regresión repite dos ciclos y ejecuta la acción accesible de la raíz restaurada.
Diff del listener: 12 inserciones y 8 eliminaciones; no cambia su API pública.

Esto valida el parche focal en esos escenarios del listener, no FLOW-DEEP-LINKS.
Siguen pendientes cero owners, empaquetado/procedencia del artefacto y repetición
hermética en Qüata antes de evidencia real. Las dependencias y el bundle de Qüata
siguen intactos; no se ha desplegado nada.

### Empaquetado y ensayo aislado en Qüata

La tercera regresión usa un `CanvasLayersComposeScene` real: cierra el último
owner, exige DOM vacío, crea otra raíz sobre el mismo listener y vuelve a cerrar.
Resultado `owner-regression-patched-2.log`: **4 PASS, cero fallos/errores**
(tres regresiones y el control upstream). XML conservados como `patched2-TEST-*.xml`.

`:compose:ui:ui:wasmJsJar` pasa y produce el Klib parcheado con SHA256
`f79203757b133aacd3ffad129333c9b0daa79d5afe04d24b360ee4cc1ab43911`.
ABI 2.2.0, compilador 2.2.20, metadata 1.4.1, unique name y lista de dependencias
coinciden con el Klib oficial (SHA256
`c4fed8f4ee36d96aa6316cf0c27e205893b301c2be1f0d1137145e1990e1a299`).

El ensayo usa un repositorio local ignorado y una versión explícita
`1.10.0-quata-owner-pilot.1`, mediante init script, sin editar las dependencias
del proyecto ni sustituir artefactos oficiales en caché. El descriptor conserva
dependencias y atributos API/runtime Wasm de la metadata oficial; no publica una
variante de fuentes que pudiera atribuir falsamente el source jar original al
parche. `pilot-resolution.log` confirma la selección en `wasmJsCompileClasspath`.
La primera configuración local omitía los repositorios habituales por
`PREFER_PROJECT`; se corrigió antes de compilar. Un informe Gradle terminado con
éxito pero dependencias `FAILED` no se considera resolución válida.

La distribución original se conserva en
`build-reports/flow-deep-links/local-compose-semantics/original-distribution-52484323`;
su fingerprint se verificó idéntico a `c613c373...256de` antes del build piloto.
La nueva distribución será evidencia experimental con procedencia propia, nunca
evidencia del Product SHA original sin cambios. Pendientes build Qüata, ensayo
hermético UGC → Chat y decisión de integración reproducible. Sin mutaciones remotas.

El build Qüata con el init script **pasa** (3m; `quata-patched-build-1.log`).
Distribución experimental copiada a `patched-distribution-1`, fingerprint
`6cd4993b9d47863ff9c19810db45d0f73bfbaab9d0241333a5cd15c68e7f66f4`;
`pilot-distribution.json` liga fuentes, upstream y Klib. La sustitución afecta a
todos los módulos Wasm de esta invocación; no atribuir el bundle al Product SHA
original sin indicar el parche. Revisión independiente: aislamiento aprobado,
integración todavía pendiente de receta y artefactos reproducibles/versionados.

Dos ejecuciones independientes del navegador hermético (`patched-terms-1.json`,
`patched-terms-2.json`) **pasan**: se observa el diálogo durante `checking`, luego
`accepted` y la burbuja Compose `chat.message.fixture-1` con Role.Button y texto
del mensaje. Se exige unicidad del nodo y ausencia de IDs obsoletos de términos.
El envío local se ejecuta exactamente una vez mediante el input nativo existente;
ambos ensayos registran cero orígenes externos. Captura del primer ensayo revisada:
conversación y burbuja visibles, sin modal residual. No se crearon fixtures remotos.

Es evidencia sintética del mecanismo corregido, no aceptación real de deep links:
el login usa el bridge hermético y el repositorio de Chat es local. Próximo paso:
versionar el backport y su receta mínima, revisar resolución/alcance final y
reconstruir antes de retomar la evidencia focal real.

### Backport versionado

Product SHA `8b7c2b7f4a8cee9ba4dba86378bb1470739d9acf` incorpora el backport
en `third_party/compose-ui-web`, con licencia upstream, parche aplicable mediante
`git apply`, regresiones, receta y repositorio Maven local exclusivo. La versión
`1.10.0-quata-owner.1` sólo sustituye UI en configuraciones Wasm; Klib y descriptor
se verifican por hash antes de resolver. Descriptor normalizado LF, hash
`708e28fbed6f6809e045a6d61967d4dabc12578d803dda618c3afabed7f4a970`.

Revisión independiente aprobada tras corregir CRLF del parche y reservar la
coordenada al repositorio local. Reconstrucción upstream con `--rerun-tasks`:
54 tareas ejecutadas, mismo Klib byte por byte. Build normal Qüata sin init script
PASS; fingerprint del ejecutable `6cd4993b...e7f66f4`, igual al piloto. La resolución
de Android y iOS Simulator sigue seleccionando UI oficial 1.10.0. Una alteración
del descriptor provocó el rechazo esperado por checksum; después se restituyó.

El preflight real incluye ahora `build.gradle.kts`, `settings.gradle.kts`, `gradle`
y `third_party` en la comparación de fuentes: no se puede atribuir la nueva
dependencia al antiguo Product SHA. Sigue pendiente la aceptación real completa
de FLOW-DEEP-LINKS; esta revisión no concede GO de unidad.

### Ensayo real y contradicción visual detectada

Run `08537db3-6757-4954-9830-9df2e2d00cf9`, Product SHA `8b7c2b7f`, produjo
PASS automático cold/warm: hilo/mensaje exactos, nombre accesible, un episodio,
back/reload sin reapertura, cero errores. `cleanupComplete=true` y directorio
privado vacío: perfiles/Auth/sesiones/hilo/mensaje propios reconciliados.

**No se acepta como evidencia visual del destino.** La inspección posterior de
ambas capturas target muestra todavía el splash; las capturas back sí muestran
Chats. El árbol accesible existía bajo una capa opaca y el runner podía aceptar
una selección ya consumida detrás de ella. Se conserva el reporte original,
sin reinterpretar su PASS como GO.

Corrección en preparación: entregar el mensaje focal sólo tras terminar splash,
resolver sesión y aceptar términos; exigir en el runner que la selección esté
activa sin esas capas antes de capturar. Un caso sintético con overlay y selección
que expira antes de retirarlo debe fallar. Repetición real pendiente de revisión
y build del nuevo Product SHA.

### Chat Web: repetición con foco descubierto

Product SHA `8cde7edf6d63e4c167412f9a08d7b6799639faec`, fingerprint
`139a381fb6fd20355669e4a43d3940e3d685e8abf592170c7653d67d832367f7`.
Revisión independiente del ajuste aprobada; build Web PASS (2m15s), seis tests
del runner PASS sin skips, incluido rechazo de selección expirada bajo overlay.

Run `4c7a9c7b-d7c9-4d43-a7f9-21b5ba650449`: **PASS cold y warm**, hilo `2526`,
mensaje `11156`, ID y nombre accesible exactos, selección vigente con coberturas
ausentes, un episodio y retirada del foco. Warm conserva el documento; back y
reload quedan en `chat` sin reapertura; cero errores de página.

Se inspeccionaron las cuatro capturas: ambos target muestran el mensaje del run
resaltado y descubierto; ambos back muestran Chats. Reporte, manifest y capturas
locales en `build-reports/flow-deep-links/web-chat-visible-01f71010-acce-4d61-834a-1323e9b196b2`.
`cleanupComplete=true`, proceso terminado con exit 0 y directorio privado vacío.
No se modificaron cuentas existentes ni se desplegó backend.

Límites conservados: sólo Chat Web con sesión válida y fixtures propios; no
Android/iOS, sesión expirada ni ciclo OS background/foreground. Service workers
bloqueados para contabilidad de peticiones; observación de no reapertura de dos
segundos. No constituye GO de FLOW-DEEP-LINKS ni promueve su fila del inventario.

### Regresión de salida de Feed en la distribución nueva

La repetición pública sobre `8cde7edf` confirma Oficial existente (detalle, back,
reload), Oficial inexistente cold/warm con respuesta vacía y estado terminal, y
barrera de acceso de Chat anónimo. Las capturas se inspeccionaron. No se atribuye
reproducción multimedia a la apertura de un post de Oficial.

Feed alcanza el ID y texto exactos, pero al volver falla con `RuntimeError: illegal
cast` en `FeedScreenHost`. Se reprodujo dos veces; el mismo recorrido contra la
distribución original preservada de `52484323` pasa sin errores. **Esto bloquea
GO de la candidata**, aunque el ensayo focal de Chat anterior haya pasado.
Reportes comparativos en `web-public-8cde7edf` y `web-public-baseline-52484323`.

`FeedScreenHost` y `WebFeedHost` no tienen cambios entre ambos Product SHA.
La revisión independiente recomienda aislar compilación/IR antes de modificar
Feed. El Klib upstream recompilado sin parche tiene hash
`47734d108efc80723c43313dc4e3f972d2536005c0274bee8c850fe524e268ed`:
manifiesto idéntico al oficial salvo fingerprints, pero no es idéntico en bytes.
El listener parcheado quedó restaurado tras obtener ese artefacto comparativo.

Se inicia reconstrucción completa de Qüata con el mismo backport versionado,
`--rerun-tasks --no-build-cache -Pkotlin.incremental=false`, para descartar salidas
incrementales antes de probar artefactos alternativos. La distribución previa
queda preservada en `local-compose-semantics/pre-full-rebuild-8cde7edf`.
No hay fixtures ni operaciones remotas pendientes de estos ensayos públicos.

### Resultado de la reconstrucción controlada

Las 158 tareas se ejecutaron sin caché ni compilación incremental (4m09s), con
el mismo código y el mismo Klib. Nuevo fingerprint
`7c743c4f95ebfb4d289f13c44f70584ddcdbca13f7146d9663d575f873678ca6`.
Feed pasa destino exacto, back y reload sin errores; las tres capturas muestran
detalle y salida correctos. El build normal posterior pasa y conserva exactamente
ese fingerprint. Reporte en `web-public-full-rebuild-8cde7edf`.

Revisión independiente: se resuelve el bloqueo observado para ese recorrido sin
cambiar Feed, pero no se demuestra una causa exclusivamente incremental porque
también cambió la reutilización de tareas/caché. La receta del backport exige
recompilación completa al introducirlo o sustituirlo y nueva evidencia ligada al
fingerprint. Se conservan todos los informes anteriores. Renovación de Oficial,
Chat anónimo y Chat real en curso sobre el nuevo binario; sin GO de unidad.

La renovación termina con PASS de Oficial existente, Oficial inexistente
cold/warm y barrera de Chat anónimo, con capturas revisadas. Se añadieron aperturas
en caliente de Feed y Oficial existentes: mismo documento, destino exacto,
back/reload sin reapertura y cero errores. Reportes `web-*-warm-semantic-*.json`
en `web-public-full-rebuild-8cde7edf`.

Chat real renovado: run `075b7195-330d-445f-9df3-2e4327182287`, hilo `2527`,
mensaje `11157`, PASS cold/warm con selección descubierta, consumo único,
back/reload y cero errores. Capturas target inspeccionadas: mensaje resaltado y
visible. `cleanupComplete=true`, exit 0 y directorio privado vacío. Evidencia en
`web-chat-clean-1b145e22-ac3a-4636-8ed2-83fcffd2ee14`. Mismos límites de sesión
válida, Web y observación acotada; no GO multiplataforma.
