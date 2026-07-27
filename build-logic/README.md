# Convenciones Gradle de Quata

`quata.kmp-compose-feature` es un piloto incremental para los módulos de
feature Compose Multiplatform. Centraliza únicamente los cuatro plugins y los
targets no Android soportados que son idénticos en los features:

- iOS x64, arm64 y simulator arm64;
- Wasm/JS con navegador.

Cada módulo conserva `androidLibrary` para declarar su `namespace`, `compileSdk`
y `minSdk`, y conserva todos sus `sourceSets` y dependencias. Así se evita que
una convención introduzca dependencias implícitas. JavaScript IR no forma parte
de la convención tras JSIR-001; el único target Web soportado es Wasm.

## Adoptantes actuales

`:feature:settings` fue el piloto inicial. `:feature:auth` es el segundo lote;
ambos conservan todas sus dependencias declaradas por el módulo.

## Rollout

Antes de migrar otro módulo, verificar que el piloto mantiene sus targets y
dependencias y ejecutar los gates del lote: `:feature:settings:compileKotlinWasmJs`,
`:feature:settings:compileAndroidMain` y el enlace iOS en CI. La migración de
cada módulo debe ser un lote separado; `core`, `designsystem`, `app`, `web` e
`ios-shared` no están cubiertos por esta convención.
