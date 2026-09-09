# Coordinadores preparados de ACCOUNT-RECOVERY-SECRET

Los ejecutables versionados son `scripts/account-recovery-secret-prepared-web.mjs`,
`account-recovery-secret-prepared-android.mjs` y `account-recovery-secret-prepared-ios.mjs`.
Se extraen de los coordinadores locales que produjeron la evidencia histórica; mantienen
los adaptadores focales y el core de snapshot/restore. No reutilizan ACCOUNT-DETAILS como dueño.
La revisión independiente de la extracción fue estática: no equivale a ejecución real sobre este SHA.

## Entrada y ejecución

`node scripts/account-recovery-secret-prepared-<platform>.mjs <private-input.json>`

El archivo privado conserva los campos existentes: `ledger`, `backendUrl`, `publicKey`,
`evidenceDirectory`, `reportPath` y, para iOS, `displayName`, `questionLabel`, `builtSourceSha`,
`macSourceSha`, `stepScriptSha256`, `macArtifacts`, `publicConfigurationSha256`.
El ledger debe apuntar a un journal preparado y a una fixture propia vigente. No se reutilizan
fixtures eliminadas ni journals de otras identidades. Los secretos permanecen en el journal
privado; nunca en argumentos, configuración versionada ni stdout.

Se añade `runtime`, con rutas absolutas explícitas:

| Plataforma | Campos de runtime |
|---|---|
| Todas | `root`, `dependencyPackage` (package.json que resuelve pg), `databaseUrlFile`, `databaseCaFile` |
| Web | `distribution`, `preparationManifest` (sourceRevision + assets), `browserExecutable`; dependencyPackage también resuelve playwright-core |
| Android | `adbExecutable`, `preparationManifest` (sourceRevision + artifacts), `serial`, `avdName`, `androidApi` (cadena, por ejemplo `"28"`) |
| iOS | `sshExecutable`, `sshHost`, `macWorktree`, `simulator` (UDID completo), `simulatorName`, `resourceDirectory` (directorio existente para lock/ledger) |

Las rutas Mac admiten únicamente ruta absoluta sin espacios, metacaracteres ni `..`, porque los
comandos del transporte existente se construyen con esa restricción. SSH recibe sólo las variables
permitidas por `recoveryTransportEnvironment`, no credenciales de aplicación del entorno.
La conexión PostgreSQL mantiene TLS con CA explícita y verificación obligatoria.
Los artefactos se validan por hash/revisión; cambiar runtime no autoriza a rebautizar evidencia antigua.

## Protocolo y cierre

Los eventos JSON `ready` y `visual_review_ready` siguen requiriendo las respuestas exactas por stdin
que espera cada coordinador. El operador revisa realmente las capturas antes de confirmar;
no automatizar estas confirmaciones con respuestas incondicionales.
El core conserva producer → lectura permitida → recuperación autorizada → restauración.
La restauración, recibos de sesión, cierre de recursos y purga privada siguen siendo requisitos de PASS.
Un timeout de observación no permite arrancar otro runner mientras el anterior pueda estar activo.

Los guardas de inicialización imprimen sólo categorías fijas de error. Un lock iOS conservado tras
fallo requiere inspeccionar el proceso, el ledger y el journal; no se elimina por su antigüedad.
No ejecutar estos coordinadores como un smoke: pueden mutar la fixture autorizada después del preflight
cuando se confirma `execute`. La prueba sintética Compose/Maestro sigue separada y no invoca estos scripts.

## Cambios frente a los coordinadores históricos

Sólo resolución de imports, configuración de rutas/dispositivos, entorno SSH permitido, errores de
arranque sanitizados en Web/Android y comparación exacta del nombre AVD (antes startsWith).
No se cambia el runner XCTest ni se añaden gestos. El nuevo runtime tiene pruebas de rechazo de
inyección/traversal, identidad explícita de dispositivo, filtrado de entorno y fallos iniciales
sin exposición de rutas privadas. La evidencia histórica mantiene sus Product/Evidence SHA originales;
la candidata final debe ejecutar las copias versionadas sobre su revisión congelada.
