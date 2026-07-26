# Diagnóstico de arranque Android

Las marcas de arranque de `app` existen exclusivamente en variantes `debug`. No modifican la
inicialización, la configuración de WorkManager, la inyección ni el manifiesto. Todas usan el tag
estable `QuataStartup`, el reloj monotónico `elapsedRealtime` y secciones `Trace`
`QuataStartup:*`; no incluyen usuario, URI, contenido de intents, tokens ni datos de red.

## Captura rápida con logcat

Con el emulador o dispositivo ya conectado:

```powershell
$adb = 'C:\Users\PC\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb logcat -c
& $adb shell am force-stop com.quata
& $adb shell am start -n com.quata/.MainActivity
& $adb logcat -d -v threadtime -s QuataStartup:D '*:S'
```

Las fases esperadas, si el proceso alcanza cada una, son `application.onCreate`,
`application.container`, `appContainer`, `mainActivity.onCreate`, `mainActivity.hostsAttached`,
`mainActivity.composeContentInstalled` y `mainActivity.onResume`. La ausencia de la siguiente
marca delimita la última fase alcanzada; no prueba por sí sola la causa de un bloqueo.

## Resultado observado en API 37 (2026-07-25)

Con el APK `debug` que contiene estas marcas, la instalación con `adb install -r` terminó con
`Success`. Tras limpiar logcat, detener el paquete y ejecutar
`am start -W -n com.quata/.MainActivity`, la actividad devolvió `Status: ok` y `WaitTime: 30517`,
pero no hubo ninguna línea `QuataStartup`, el buffer `crash` estaba vacío y `pidof com.quata` no
devolvió PID seis segundos después. El log de sistema sí registró la creación del proceso y el
inicio de su vinculación a la aplicación.

Ese resultado sólo establece que no se alcanzó la primera marca, situada inmediatamente después
de `Application.onCreate` de Android; no identifica una causa ni autoriza cambios de
inicialización. Debe repetirse la captura con el sistema estable antes de atribuir el bloqueo a
la aplicación, al emulador o a una dependencia concreta.

## Reconciliación A/B de ola 2 (2026-07-26)

Sobre el mismo emulador API-37, ola 1 (`587789ff`) tardó 25,392 s y ola 2
(`9cc84dc2`) 21,159 s. Ambas conservaron el proceso y dejaron vacíos los
buffers de crash/ANR. El resultado se clasifica `environment_both_slow`: valida
ausencia de regresión funcional observada, no acredita una mejora de
rendimiento ni un SLO de arranque.

## Perfetto

En Android Studio, inicia **Profiler > System Trace**, arranca Quata y busca las secciones
`QuataStartup:*`. Para captura por consola puede enviarse esta configuración de texto a Perfetto:

```powershell
$config = @'
buffers: { size_kb: 8192 fill_policy: RING_BUFFER }
data_sources: {
  config {
    name: "linux.ftrace"
    ftrace_config {
      atrace_categories: "am"
      atrace_categories: "wm"
      atrace_categories: "view"
      atrace_categories: "gfx"
      atrace_apps: "com.quata"
    }
  }
}
duration_ms: 15000
'@
$config | & $adb shell perfetto --txt -c - -o /data/misc/perfetto-traces/quata-startup.perfetto-trace
& $adb pull /data/misc/perfetto-traces/quata-startup.perfetto-trace build-reports/quata-startup.perfetto-trace
```

Inicia la captura y lanza la actividad durante la ventana de 15 segundos. El fichero descargado
se abre en https://ui.perfetto.dev o desde Android Studio.

## Retirada

Después de aislar el problema, elimina `AndroidStartupDiagnostics.kt`, sus importaciones y sus
llamadas de `QuataApp`, `AppContainer` y `MainActivity`; elimina también este documento si ya no
describe instrumentación activa. No dejar estas marcas como telemetría de producción: están
protegidas por `BuildConfig.DEBUG`, pero son intencionalmente temporales.
