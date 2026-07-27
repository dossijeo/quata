# Evaluación de la migración Kotlin/Compose Multiplatform

Fecha de la auditoría: 2026-07-25  
Commit auditado: `bd8a73b03b1139024f9e0447b0d452f156267578` (`main`)

> **Documento histórico.** Esta evaluación conserva la línea base anterior a la
> integración final de la migración. Para el estado vigente y sus evidencias de
> validación, consulta `MULTIPLATFORM_MIGRATION_BOARD.md` y
> `MULTIPLATFORM_VALIDATION_EVIDENCE.md`.

## Resumen ejecutivo

La migración está bien encaminada y no es una migración nominal: existe código de dominio,
estado, ViewModels y UI Compose realmente compartido; Android consume los módulos KMP; Web
tiene un ejecutable Wasm y un composition root propio; iOS tiene un host UIKit que presenta
controladores Compose exportados por Kotlin/Native.

La principal diferencia entre el estado aparente y el real es que compilar un módulo para una
plataforma no significa que la feature esté conectada, tenga repositorio operativo o posea
paridad funcional con Android. Aplicando ese criterio, la estimación es:

| Ámbito | Completitud estimada | Confianza | Lectura |
| --- | ---: | ---: | --- |
| Migración global a KMP | **58 %** | ±7 puntos | Arquitectura compartida sólida, pero queda mucho comportamiento Android, integración y validación E2E. |
| Versión Web | **62 %** | ±8 puntos | Host amplio y ejecutable real; varias áreas siguen siendo parciales, locales o de solo lectura. |
| Versión iOS | **40 %** | ±8 puntos | Toolchain, framework y Auth/Feed/Chat existen; la mayoría de features aún no está compuesta en la app. |

Estas cifras miden paridad funcional con la aplicación Android, no solo cantidad de archivos ni
capacidad de compilación.

## Alcance y método

Se revisaron:

- configuración raíz y de todos los módulos Gradle;
- source sets `commonMain`, `androidMain`, `wasmJsMain`, `jsMain` e `iosMain`;
- dependencias reales del host Android, del ejecutable Web y del framework iOS;
- composition roots Web e iOS;
- repositorios y adaptadores que declaran operaciones no implementadas;
- documentación y artefactos de CI existentes;
- pruebas del repositorio y validaciones de compilación ejecutadas durante esta auditoría.

Para evitar una cifra engañosa, el lector Office vendorizado se considera deuda/bloqueo de
plataforma, pero sus aproximadamente 429.000 líneas Java no se incluyen en el denominador del
código propio. Incluirlas reduciría artificialmente cualquier porcentaje de migración y mezclaría
producto con una biblioteca de terceros.

## Evidencia cuantitativa

### Estructura de código

Inventario aproximado, excluyendo directorios `build`:

| Área | Kotlin | Java | Líneas Kotlin | Líneas Java |
| --- | ---: | ---: | ---: | ---: |
| `app` Android | 161 archivos | 0 | 37.812 | 0 |
| `core` | 129 | 0 | 6.914 | 0 |
| `designsystem` | 75 | 0 | 7.866 | 0 |
| `feature/*` | 288 | 0 | 19.771 | 0 |
| `web` | 37 | 0 | 4.424 | 0 |
| `document-reader` | 35 | 2.129 | 3.972 | 429.363 |

En los módulos KMP se localizaron aproximadamente 26.000 líneas en `commonMain`, frente a
37.700 líneas Kotlin del host/código Android. El cociente bruto de código propio compartido sobre
compartido + Android es cercano al 41 %. No debe interpretarse como completitud funcional:
parte del código Android debe seguir siendo específico de plataforma y parte de la UI común ya
es reutilizada por Android.

La integración Android con KMP es real: se detectaron 393 imports desde `app` hacia paquetes de
features o UI compartida, repartidos en 51 archivos que importan módulos `feature/*`.

### Source sets relevantes

- `core/commonMain`: 56 archivos, ~1.680 líneas.
- `core/iosMain`: 32 archivos, ~2.393 líneas.
- `core/wasmJsMain`: 19 archivos, ~1.762 líneas.
- `designsystem/commonMain`: 66 archivos, ~7.776 líneas.
- `feature/*/commonMain`: alrededor de 250 archivos y ~18.500 líneas.
- `feature/*/iosMain`: 21 archivos y ~2.547 líneas.
- `web/wasmJsMain`: 33 archivos y ~4.211 líneas.

La cantidad de código iOS de adaptadores es significativa, pero la superficie efectivamente
instalada en `iosApp` es menor que el conjunto que compila.

## Lo que se está haciendo correctamente

### 1. Fronteras de plataforma explícitas

Los contratos de cámara, archivos, compartir, audio, ubicación, permisos, documentos,
notificaciones y preferencias viven en `core/commonMain`. Los adaptadores Web/iOS devuelven
`PlatformResult.Unsupported` cuando falta una capacidad o un host, en lugar de fabricar éxito.
Es una decisión correcta para una migración gradual.

### 2. UI realmente compartida

`designsystem` y los módulos de feature contienen Compose en `commonMain`; no se está limitando
la migración a modelos o DTO. Android consume esos módulos y Web los monta desde `ComposeViewport`.
Esto permite compartir comportamiento visual y estado, que es el objetivo apropiado de Compose
Multiplatform.

### 3. Hosts finos y adaptadores concretos

- Android conserva `:app` como host y depende de todos los módulos KMP.
- Web tiene un ejecutable Wasm dedicado y compone repositorios/servicios de navegador.
- iOS mantiene UIKit para ciclo de vida y presentación, pero las pantallas Auth, Feed y Chat
  proceden de Compose/Kotlin.

Mantener UIKit/Swift en el borde y no intentar compartir el ciclo de vida completo es una buena
decisión.

### 4. Seguridad razonable en los bordes

Se observan decisiones prudentes en URI, `FileProvider`, Keychain, claves publicables,
normalización de deep links, selector de documentos, Blob URLs y resultados no soportados. La
migración no está usando credenciales privilegiadas en `commonMain`.

### 5. Web no oculta operaciones ausentes

Las mutaciones no verificadas fallan explícitamente en Feed, Communities, Profile y otras áreas.
Es preferible a una implementación local que aparente haber actualizado datos remotos.

## Problemas arquitectónicos y errores de concepto

### P0. `feature:feed` se está convirtiendo en el módulo paraguas de iOS

El framework se llama `QuataFeed`, pero desde su configuración iOS exporta `core`, `auth`,
`chat` y `notifications`. Además, `feature:feed/iosMain` declara dependencias API hacia esas
features.

Esto invierte la dirección esperable de dependencias: Feed pasa a conocer features hermanas para
poder actuar como framework de aplicación. A medida que se añadan Profile, Official, Settings,
Communities y Composer, el módulo Feed terminará siendo un composition root oculto.

Recomendación: crear un módulo paraguas explícito, por ejemplo `:shared-app` o `:ios-shared`,
responsable del framework único y de exportar las features. `feature:feed` debería volver a
depender solo de sus contratos y capas inferiores.

### P0. Compilación iOS y disponibilidad en la app se están confundiendo

Todos los módulos de feature declaran targets iOS, pero el framework consumido por Swift solo
exporta Core/Auth/Feed/Chat/Notifications. En el host actual:

- Auth, Feed y Chat se componen con repositorios reales cuando hay configuración/sesión.
- Notifications tiene soporte de ruta/factory, pero no se instala un repositorio en el arranque.
- Official tiene una factory prevista, pero no se instala.
- Profile/SOS, Communities, Composer, Settings, WhatsNew y External Share no forman parte de la
  navegación efectiva de `iosApp`.

Por tanto, “compila para iOS” debe separarse en el tablero de “exportado”, “compuesto”,
“navegable”, “backend real” y “E2E validado”.

### P0. Web mezcla features reales, parciales y locales bajo la misma navegación

El host Web ofrece rutas para Feed, Chat, Notifications, Profile, Composer, Communities,
Official, Settings y WhatsNew, pero su semántica no es homogénea:

- Feed lee datos reales, pero like, report, comentario y borrado no están implementados.
- Communities lee directorio, pero chat, follow, report, roles y chat privado fallan como no
  implementados; los comentarios están desactivados desde el host y el ranking se entrega vacío.
- Profile guarda nombre, barrio, teléfono y SOS en preferencias locales del navegador. No
  representa el perfil remoto autenticado.
- Auth no implementa registro.
- Official conserva mutaciones no implementadas.
- Composer sí contiene publicación y subida Web, pero todavía depende de contratos/RLS no
  validados E2E.

El problema no es la migración incremental, sino presentar el mismo nivel visual sin una
declaración de capability suficientemente visible. Debe existir una matriz de capacidades por
feature que el host use para ocultar, desactivar o etiquetar funciones parciales.

### P1. Persistencia local de Profile Web puede divergir del servidor

`WebProfileRepository` implementa `ProfileRepository` sobre `PreferenceStore`. Tras un guardado,
la UI puede indicar éxito aunque el perfil remoto no haya cambiado. Esto es aceptable solo como
prototipo explícito; no como implementación productiva.

Recomendación: conectar el gateway PostgREST ya existente, mantener almacenamiento local solo
como caché y no emitir “Cambios guardados” hasta confirmar el servidor.

### P1. Hay demasiada lógica residual en el módulo Android monolítico

`app` conserva unas 37.800 líneas Kotlin. Los bloques mayores son `core` (~13.700), Chat
(~6.300), Post Composer (~5.800), data (~2.600), Official (~2.000), Communities (~1.600) y Feed
(~1.400).

Esto no invalida el patrón strangler usado, pero crea riesgo de dos implementaciones paralelas:
la común y la Android original. Cada lote debería cerrar con:

1. host Android consumiendo la implementación común;
2. pruebas de regresión;
3. eliminación de la implementación desplazada;
4. actualización de métricas.

Mover código a `commonMain` sin retirar el anterior no debe aumentar el porcentaje de
completitud.

### P1. El transporte/backend compartido es inconsistente

Feed comparte protocolo, mapping y polling mediante `FeedReadTransport`, pero otras features
mantienen repositorios Web/iOS separados con lógica de serialización y políticas similares.
Esto favorece divergencias de campos, errores y autenticación.

Recomendación: compartir modelos wire, mapping, políticas de error/paginación y casos de uso;
mantener únicamente HTTP/URLSession/fetch, almacenamiento seguro y APIs del sistema en adaptadores
de plataforma.

### P1. Estrategia de localización incompleta

Los módulos Compose no tienen recursos comunes significativos (`generateResourceAccessors...`
aparece repetidamente como `NO-SOURCE`/`SKIPPED`) y existen muchas cadenas españolas
hardcodeadas en hosts y contenido compartido. Además, seis archivos contienen texto con mojibake
real (`Ã`), incluido `Main.kt`, Composer Web, pruebas de Feed y un aviso iOS.

Antes de ampliar las pantallas iOS/Web conviene adoptar Compose Resources o un contrato común de
strings. Posponerlo multiplicará la deuda y hará difícil validar accesibilidad/localización.

### P1. La frontera Wasm genera un volumen alto de warnings experimentales

La compilación Web emite numerosas advertencias por uso de `ExperimentalWasmJsInterop`, además de
warnings de `expect/actual` beta y APIs Compose obsoletas.

No son fallos de compilación, pero el ruido impide usar warnings como señal de regresión.
Recomendación:

- encapsular interop JS en pocos archivos/adaptadores;
- aplicar opt-in de forma explícita y localizada;
- corregir deprecaciones reales;
- establecer un presupuesto de warnings que solo pueda disminuir.

### P1. El bundle Web es pesado

La distribución de producción generada durante la auditoría contiene un Wasm de aproximadamente
18,2 MiB y Webpack advierte que el entrypoint principal (~603 KiB de JavaScript) supera el límite
recomendado. El smoke local arranca, pero esto no mide tiempo de descarga, compilación Wasm,
memoria ni experiencia en móvil/red lenta.

Recomendación: establecer presupuestos de tamaño y Web Vitals, analizar el peso de Skiko,
`materialIconsExtended`, el visor documental y features cargadas de forma incondicional, y
evaluar carga diferida donde sea compatible con Compose/Wasm.

### P2. Targets JS y Wasm duplicados sin producto JS

Los módulos declaran `js(IR)` y `wasmJs`, pero el único host Web ejecutable consume Wasm.
`jsMain` contiene muy poco código y no existe una aplicación JS equivalente.

Debe decidirse si JS es fallback soportado. Si no lo es, eliminar el target reduce matriz,
tiempo de CI y riesgo de adaptadores divergentes. Si sí lo es, necesita host, pruebas y criterio
de publicación propios.

### P2. Repetición de Gradle

Los 12 módulos de feature repiten casi la misma declaración de plugins, targets y dependencias
Compose. Un convention plugin en `build-logic` reduciría drift de versiones y targets.

### P2. Documentación operativa desincronizada

Hay comentarios y documentos que describen módulos “desconectados” aunque `app` ya depende de
ellos, y artefactos iOS anteriores que no coinciden con la descripción más reciente del
toolchain. La documentación es extensa y útil, pero debe derivar su estado de checks
automatizados o del commit exacto.

## Estado por plataforma

### Android

Android sigue siendo la referencia funcional. Consume todos los módulos KMP y conserva los
adaptadores complejos: Media3, cámara, WorkManager, Firebase, Vosk, edición/exportación,
notificaciones y el lector de documentos.

La migración Android→común es tangible, pero `app` todavía es un monolito de plataforma
considerable. El lector Office vendorizado continúa siendo totalmente Android/Java.

### Web

Fortalezas:

- ejecutable Kotlin/Wasm real;
- routing para las áreas principales;
- Auth/login/restauración/logout y recuperación;
- Feed/Official de lectura;
- Chat con envío, adjuntos, audio y polling;
- Composer con selección/subida/publicación;
- PWA, Web Share Target, push/service worker y visor documental;
- adaptadores de cámara, archivos, ubicación, preferencias, audio y compartir.

Pendiente para paridad:

- registro y lifecycle Auth completo;
- mutaciones Feed/Official;
- Profile remoto;
- Communities: follow, roles, chat, comentarios, ranking y perfiles completos;
- Realtime/caché coherente con Android;
- E2E autenticado contra Supabase/RLS;
- pruebas cross-browser, accesibilidad, responsive y PWA instalada;
- corrección de mojibake y localización.

Estimación Web: **62 %**.

### iOS

Fortalezas:

- targets Kotlin/Native en todos los módulos;
- host UIKit real;
- framework Compose enlazable;
- Keychain y adaptadores para cámara, picker, Quick Look, audio, ubicación, contactos,
  compartir y notificaciones;
- Auth, restauración de sesión, Feed y Chat con composition root real.

Pendiente para paridad:

- módulo paraguas correcto para el framework;
- navegación general y shell de aplicación;
- composición efectiva de Official, Notifications, Profile/SOS, Communities, Composer,
  Settings, WhatsNew y External Share;
- descarga segura de adjuntos remotos de Chat;
- mutaciones y repositorios de varias features;
- pruebas de permisos y dispositivos;
- E2E autenticado;
- firma, archivo, IPA y distribución.

Estimación iOS: **40 %**.

## Calidad y pruebas

Se localizaron 46 archivos de prueba entre `app`, módulos KMP, Web, iOS y lector, con unas 2.466
líneas. Hay 162 declaraciones `@Test`/`func test`, pero la cobertura se concentra en políticas,
mapeos y fronteras. Faltan recorridos funcionales de plataforma y backend.

Durante esta auditoría, `:web:wasmJsBrowserDistribution` terminó correctamente y el smoke de
navegador pasó las rutas Auth, Feed, Chat, Official, Settings y Share Target. Es una evidencia
válida del launcher y de rutas sin backend autenticado; no demuestra paridad funcional ni E2E
contra Supabase.

También terminó correctamente `:app:assembleDebug`. La build Android confirmó, además de los
warnings `expect/actual`, 89 warnings Javac procedentes principalmente del lector vendorizado
(deprecaciones sin anotación, APIs obsoletas y operaciones unchecked). El APK compilable prueba
integración estática; en esta auditoría no se instaló ni recorrió la app en emulador.

La última evidencia iOS descargada que se inspeccionó compila Kotlin/Native y el host Xcode, pero
su XCTest falla porque Xcode seleccionó “My Mac” para un UI test. El workflow actual contiene
selección explícita de simulador. La ejecución correspondiente al SHA auditado fue cancelada por
la política de concurrencia al avanzar `main`, y existe una ejecución del `main` remoto más
reciente en curso al redactar esta sección. La documentación no debe declarar XCTest verde para
este SHA sin una ejecución completa y asociada exactamente a él.

## Modelo de estimación

La cifra global pondera:

| Dimensión | Peso | Estado estimado |
| --- | ---: | ---: |
| Modularización y fronteras KMP | 25 % | 75 % |
| Dominio/estado/UI extraídos y adoptados | 30 % | 60 % |
| Adaptadores de plataforma | 20 % | 60 % |
| Integración funcional de hosts | 15 % | 45 % |
| Pruebas, E2E y release | 10 % | 35 % |

El resultado ponderado es aproximadamente 59 %; se comunica como **58 %** para reflejar la
incertidumbre en paridad funcional y evitar falsa precisión.

## Plan recomendado

### Próximo bloque P0

1. Crear `:ios-shared`/`:shared-app` y mover allí la generación/exportación del framework.
2. Definir una matriz versionada por feature: `compila`, `exportada`, `compuesta`, `navegable`,
   `backend real`, `E2E`.
3. Corregir la CI iOS hasta obtener XCTest verde en el commit de `main`, sin reutilizar evidencia
   de otro commit.
4. Sustituir Profile Web local por gateway remoto o marcarlo claramente como no productivo.

### Bloque P1

1. Completar una vertical por plataforma antes de abrir otra: repositorio, UI, navegación,
   mutaciones, errores y E2E.
2. Priorizar iOS Notifications/Official y luego Profile, porque parte de sus contratos ya existe.
3. Cerrar Web Feed/Official/Profile/Communities contra RLS y datos efímeros.
4. Introducir recursos comunes/localización y corregir mojibake.
5. Reducir warnings Wasm y deprecaciones Compose.

### Bloque P2

1. Decidir si `js(IR)` es producto o eliminarlo.
2. Crear convention plugins KMP/Compose.
3. Automatizar métricas de source sets, operaciones `not_implemented` y features conectadas.
4. Mantener el lector Android como adaptador aislado hasta que exista una estrategia separada de
   documentos multiplataforma; no mezclar sus 429.000 líneas con la migración ordinaria de
   features.

## Criterio de “migración terminada”

La migración no debería considerarse terminada cuando todos los módulos compilen, sino cuando:

- Android, Web e iOS usan el mismo dominio/estado/UI para cada feature;
- cada host inyecta adaptadores reales y no repositorios de demostración/locales;
- las capacidades ausentes se reflejan en UI y contrato;
- los recorridos críticos autenticados pasan E2E por plataforma;
- existe validación de permisos, archivos, media y deep links;
- Web tiene distribución/smoke/E2E y iOS build/XCTest/archive reproducibles;
- las implementaciones Android desplazadas se han eliminado;
- la documentación está asociada al SHA que valida.
