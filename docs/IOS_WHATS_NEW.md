# What's New en iOS

La vertical iOS usa la pantalla Compose compartida de `feature:whatsnew` y un
catálogo local explícitamente versionado. No consulta Supabase ni simula
sincronización remota.

`IosWhatsNewSeenStateStore` guarda únicamente dos números no sensibles en
`UserDefaults`: la última versión vista y la versión en la que se inicializó el
estado. El repositorio mantiene el progreso de forma monotónica. Si la lectura o
la escritura falla, la pantalla no declara la versión como vista.

`createDefaultIosWhatsNewRuntimeBootstrap` obtiene `CFBundleVersion`,
`CFBundleShortVersionString` y el primer idioma preferido saneado por el host Swift; si falta o
es inválido usa el fallback local explícito `en` del catálogo. Rechaza
metadatos ausentes o sin expandir. El catálogo sólo presenta entradas cuyo
`versionCode` no supera la versión instalada.

El módulo exporta:

- un host de novedades pendientes que persiste el estado antes de cerrar;
- un host de historial completo para About/menú;
- `IosWhatsNewRouteDispatcher`, una frontera inyectable para conectar menú o
  deep route sin introducir navegación UIKit en el módulo.

El wiring concreto con el router de `iosApp` se mantiene en un commit separado
para que pueda integrarse junto con otros cambios concurrentes del launcher.
