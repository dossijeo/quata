# CI de PR para Web/Wasm y Android

El workflow `web-android-pr.yml` valida los cambios que afectan a Android o a
los módulos multiplataforma. Usa JDK 17, Node.js 20.19.0 y Chrome 150, y ejecuta
los siguientes gates sin credenciales ni acceso a Supabase:

- `:web:wasmJsTest`, distribución Web de producción y smoke local con DocMentis;
- matriz explícita de `:app:testDebugUnitTest`, host test Android de `core` y
  los `wasmJsTest` de los módulos que contienen pruebas comunes;
- `:app:lintDebug` y `:app:assembleDebug`.

Los artefactos conservan JUnit, lint, métricas y logs del smoke, inventario del
bundle, distribución Web y APK debug durante 14 días. Los límites de cada job y
de los pasos costosos evitan consumir indefinidamente la cuota si Kotlin/Wasm,
`wasm-opt`, Chrome o Android lint quedan bloqueados.

## Requisito externo pendiente

El workflow sólo versiona y nombra los checks. Convertirlos en checks requeridos
de la rama protegida sigue pendiente en la configuración de GitHub: una persona
con permisos de administración debe habilitar branch protection o rulesets para
que un PR afectado no pueda integrarse hasta que todos estos jobs estén verdes.
Ese cambio externo no se puede acreditar mediante un commit del repositorio.
