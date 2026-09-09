# ACCOUNT-RECOVERY-SECRET — piloto de testing por capas

Fecha: 2026-09-09. Decisión: **ADOPTAR PARCIALMENTE**.
Sólo diagnóstico sintético/local; no certificación funcional ni cambio del GO existente.
No se han usado cuentas reales, mutado credenciales/Supabase ni cambiado el binario iOS.

## Separación del recorrido

| Capa | Responsabilidad focal |
|---|---|
| Compose UI | Eventos de los campos, selección/lectura de pregunta, validaciones, estado pendiente/error, navegación común y feedback. |
| E2E de plataforma | Abrir Cuenta autenticada, guardar realmente, reabrir con respuesta vacía, leer sólo pregunta mediante contrato permitido, recuperar, verificar login con contraseña temporal y restaurar. Requiere coordinador/backend real; un repositorio local no lo acredita. |
| Apple específico | Keychain, revocación/limpieza de sesión nativa, transporte/logout iOS y permisos o integración del sistema que no pueda acreditar otro mecanismo. XCTest sigue disponible para esos bordes y para el runner focal vigente mientras no haya sustituto equivalente. |

## Implementación y resultados

**Compose 1.10.0 / Kotlin 2.2.21, ChromeHeadless 152 / Wasm en Windows.**
`RecoverySecretUiPilotTest.kt`: 105 líneas, 2 tests, 1 repositorio local; 2 líneas nuevas de configuración Gradle.
El formulario de producto se conecta al ViewModel real mediante su inyección existente de dispatcher.
Prueba teléfono, pregunta localizada, respuesta y contraseña con `performTextInput`; comprueba validación
inicial, argumentos exactos recibidos por el repositorio, botón deshabilitado mientras espera, error,
reactivación y callback Volver. El segundo test usa `AuthProductHostContent` real para Login → Recuperación → Login.
No prueba el retorno automático después de un reset exitoso, ni el formulario de Cuenta.

Dos ejecuciones finales consecutivas: **16/16 tests PASS**, incluidos los 2 nuevos; 54 s con compilación
y 11 s repitiendo realmente la tarea de tests (`--rerun`, no resultado UP-TO-DATE).
Cero retries, sleeps, coordenadas, menús o helpers de pegado en los tests finales.
El prototipo inicial del host completo falló por esperas con trabajo del ViewModel fuera del reloj
controlado del test; se acotó al formulario + ViewModel y navegación separada sin modificar producto.
También se corrigieron el opt-in de la dependencia y dos usos de API de test durante la compilación.
No se ha ejecutado esta nueva clase en Android ni iOS; commonTest permite reutilizarla, no certifica esos targets.

**Maestro CLI 2.10.0 / iPhone 16, simulador iOS 18.3.1 Intel actual.**
UDID `F2E1EA50-FBAD-443C-A98F-2A576C14C70B`; binario de producto `8823a188462d30c72d77e540e70f4cf26fb131df`.
Instalación aislada con Java 17 existente, sin Cloud/analítica ni cambios de versión del proyecto.
Dos YAML finales: Cuenta 27 líneas + Recuperación 30 líneas = **57 líneas**.
Cero fixtures de aplicación nuevas: reutilizan `profile-legal` y `auth-launch`, ambas locales.
Cero coordenadas, double tap, menús, pegado propio o retries programados; Maestro conserva sus esperas internas.

| Ejecución | Cuenta | Recuperación |
|---|---|---|
| run1 | Inputs/selección correctos; falla `hideKeyboard` | Inputs correctos; falla `hideKeyboard` |
| run2, sin `hideKeyboard` | PASS 55 s, Save/feedback/respuesta sintética ya no visible | Llega al error local; Volver tapado por teclado, no vuelve a Login; FAIL. El proceso no cerró antes del watchdog de 240 s. |
| run3, alcance reducido explícito | PASS 58.780 s | PASS 80.085 s hasta error local; se excluye Volver. Suite 2/2 PASS, 138.889 s. |

Los cuatro `inputText` completaron las tres ejecuciones; teléfono y respuesta tienen aserciones de texto.
La ausencia visible de la respuesta tras Save no demuestra por sí sola vaciado del campo.
La contraseña tiene entrada pero no comprobación exacta en Maestro: no confundir comando completado con
credencial verificada. Cuenta tiene dos pases consecutivos; la suite final reducida sólo tiene un pase completo.
No hay base para afirmar estabilidad del E2E completo ni una mejora medida del tiempo total de certificación.
Se aplica STOP RULE al borde de teclado: no se añaden gestos equivalentes a los workarounds de XCTest.

La fixture de Cuenta sólo ofrece «mantener pregunta actual» y Save simulado; no certifica seleccionar
«madre», persistencia ni lectura posterior desde backend. Recuperación usa un repositorio que no accede
a red y rechaza la operación; no certifica recuperación autorizada. El parámetro de idioma no produjo
localización uniforme del host; se usan tags y no se certifica idioma. Los screenshots muestran estos límites.

## Comparación honesta con XCTest

El archivo focal XCTest tiene **256 líneas** y cubre más: pasos reales, controles privados y dos preflights.
Los dos preflights ocupan 45 líneas; sólo el helper `paste` más su limpieza ocupa aproximadamente 41 líneas,
además de búsqueda, scroll, espera y comprobación. Usa tap + doubleTap + menú Paste y propiedad/limpieza
del portapapeles. Los 57 YAML evitan esa mecánica, pero **no reemplazan** los coordinadores, transporte,
Keychain, verificaciones backend o restauración. Comparar 57 contra 256 no demuestra equivalencia funcional.
Compose aporta otras 105 líneas con un fake y reloj controlado para una capa diferente.

## Decisión y límites para evidencia real

Adoptar ahora Compose UI para la capa común focal. Mantener Maestro como piloto reproducible local;
no convertirlo en propietario del runner real ni introducir secretos en `inputText` por ahora.
Los artefactos `commands.json` contienen los textos sintéticos: antes de usar credenciales sería necesario
acreditar su tratamiento privado, redacción/publicación y purga, además de resolver el recorrido completo
sin hacks. No ampliar XCTest para repetir lógica ya cubierta por Compose; conservar los pasos certificados
que aún se necesitan. No reescribir suites antiguas ni invalidar evidencia previa.

Android ya expone `testTagsAsResourceId` en MainActivity; eso facilita un piloto futuro, no prueba estos YAML.
Maestro Web está en beta y la viabilidad del canvas Compose/Wasm no se ha comprobado. La cobertura Wasm
acreditada aquí viene de Compose UI, no de Maestro.

## Reproducción y artefactos

- `./gradlew :feature:auth:wasmJsBrowserTest`; repetir sólo la tarea con `--rerun`.
- CLI oficial 2.10.0, zip SHA-256 `29b675e10cc12080e445e9bfb2e2b4e4dfb9c0f2e30d5884120d258b5e1cd991`.
- En simulador exclusivo con el binario y las fixtures indicadas: `maestro test --udid <UDID> e2e/maestro/recovery-secret-pilot/`.
  Nunca sustituir estas constantes por cuentas reales; no usar `clearKeychain` o `clearState`.
- Logs locales en `build-reports/account-recovery-secret-salvage/ui-testing-pilot/`: `compose-run4.log`,
  `compose-run5.log`, `maestro-run1`, `maestro-run2`, `maestro-run3` y XML Compose. No se publican como evidencia real.
- Las horas del Mac difieren de las del host Windows; se identifican resultados por run y hashes, no por orden de relojes cruzados.

Fuentes: [Compose Multiplatform UI tests](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-test.html),
[Maestro iOS](https://docs.maestro.dev/get-started/supported-platform/ios),
[inputText](https://docs.maestro.dev/reference/commands-available/inputtext.md),
[artefactos](https://docs.maestro.dev/maestro-flows/workspace-management/test-reports-and-artifacts.md),
[Web](https://docs.maestro.dev/get-started/supported-platform/web-browser.md).
