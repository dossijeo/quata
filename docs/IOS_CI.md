# Compilación iOS en GitHub Actions

El workflow `.github/workflows/ios-build.yml` es el carril reproducible de compilación iOS. No firma ni publica la aplicación.

## Corte acreditado

El run [`30413800836`](https://github.com/dossijeo/quata/actions/runs/30413800836), job `90455727104`, terminó `success` sobre el SHA exacto `c87e82af615a1778092ec3b5ecfc70d1ecd485ea`. Registró 70/70 comprobaciones:

1. compilación Kotlin/Native;
2. enlace de framework y XCFramework;
3. generación y compilación del host Swift y Share Extension;
4. XCTest de simulador;
5. archive genérico sin firma;
6. publicación de logs, resultados y artefactos.

Esto acredita que el código fuente y el host enlazan en el entorno de CI del SHA indicado. No acredita una IPA firmada, perfiles, Team, App Group operativo, APNs, TestFlight, hardware físico, autenticación real ni datos de Supabase.

## Toolchain y arquitectura

El corte mantiene Kotlin `2.2.21` y Compose Multiplatform `1.10.0`. El intento de adoptar Kotlin `2.3.20` y Compose `1.11.0` fue rechazado: Compose 1.11 deja de resolver los artefactos `iosX64` utilizados por el carril Intel, y no existe evidencia de compatibilidad con el Skiko CPU-raster de ese entorno. No se debe subir ni fusionar ese experimento hasta sustituir o retirar explícitamente el carril Intel.

La CI continúa siendo el carril oficial Apple Silicon. El Mac Intel usa `iosX64` y pruebas locales serializadas; no se debe cambiar el `ARCHS` de CI por ese motivo. La rama de relajación Metal fue descartada: CI conserva su test Metal estricto y el raster CPU sólo sirve como comprobación suplementaria local.

## Simulador Intel suplementario

Con el raster CPU disponible, el Feed anónimo se mostró y leyó contra HTTPS con respuesta 200 en iOS 18.3 y iOS 26.5, sin crash observado. Este resultado descarta la pantalla negra previa como evidencia actual de rendering, pero no convierte el carril en una prueba de autenticación: Chat/login sólo se validó por contrato y el control visual remoto permanece bloqueado por TCC/AX sobre SSH.

La estabilidad de relaunch en iOS 26.5 permanece en **HOLD**: una captura a 8 s fue casi negra y a 28 s ya mostraba Feed. Hay repetición y medición en curso; no se declara cierre ni estabilidad de relaunch con esta evidencia.

## Operación

Desde PowerShell, con una rama ya subida:

```powershell
.\scripts\run-ios-ci.ps1 -Ref nombre-de-rama
```

El script despacha el workflow, espera su conclusión y descarga el artefacto en `build-reports/ios/<run-id>`. Una ejecución verde sólo puede acreditarse para su `headSha`; no se reutiliza un run de otra rama o commit.

## Próximos gates

1. Medir y corregir, si procede, la estabilidad de relaunch iOS 26.5.
2. Resolver TCC/AX de la VM y ejecutar rutas visuales autenticadas con cuenta aislada autorizada.
3. Configurar signing, Team, perfiles y App Group en una fase separada.
4. Validar APNs, Share Extension y permisos en un dispositivo físico firmado.
