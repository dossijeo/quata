# Compilación iOS en GitHub Actions

El workflow `.github/workflows/ios-build.yml` es el carril reproducible de
compilación iOS. No firma ni publica la aplicación. El `main` documental actual
es `185960769011f03d644130012fe8051ad536e9bf`.

## Corte acreditado

El run [`30425431607`](https://github.com/dossijeo/quata/actions/runs/30425431607),
job `90490809295`, terminó `success` sobre el SHA exacto
`ba6a72a1649239a4abf7408d63712d206a5d0a4c` (PR #102). Acredita:

1. contratos del workflow, runtime público, backup y matriz de simulador;
2. compilación de todos los targets Kotlin/Native;
3. enlace de framework y XCFramework;
4. generación y build del host Swift y Share Extension;
5. XCTest de simulador;
6. archive genérico sin firma, diagnósticos y artefactos.

La PR #102 se integró por `32f1bb65`; el `main` actual contiene además #103 y
#104. Una ejecución verde se atribuye sólo a su `headSha`, por lo que las futuras
modificaciones de Kotlin/Native, Swift, Xcode o workflow deben relanzar el carril.

Esto no acredita una IPA firmada, perfiles, Team, App Group operativo, APNs,
TestFlight, hardware físico, autenticación real ni datos de Supabase.

## Simulador Intel suplementario

La matriz pública integrada serializa iOS 18.3 e iOS 26.5 con un lock atómico,
configuración pública temporal restaurable y limpieza final. Ha observado Feed
público HTTPS 200, procesos esperados vivos, logs filtrados por PID, ausencia de
crash/fatal en el alcance observado y capturas/OCR. Es evidencia funcional
suplementaria de una VM Intel con CPU-raster; no es un SLA ni una medida de
rendimiento de producto.

La autenticación visual se valida en un carril distinto. Hasta que concluya,
login, refresh y logout siguen en HOLD aunque los contratos de rutas pasen.

## Toolchain y arquitectura

El corte mantiene Kotlin `2.2.21` y Compose Multiplatform `1.10.0`. El intento de
Kotlin `2.3.20` y Compose `1.11.0` fue rechazado: Compose 1.11 deja de resolver
los artefactos `iosX64` requeridos por el carril Intel y no existe evidencia de
compatibilidad con Skiko CPU-raster. CI conserva el test Metal estricto; raster
CPU sólo sirve como comprobación suplementaria local.

## Operación

Desde PowerShell, con una rama ya subida:

```powershell
.\scripts\run-ios-ci.ps1 -Ref nombre-de-rama
```

El script despacha el workflow, espera su conclusión y descarga el artefacto en
`build-reports/ios/<run-id>`. Una ejecución verde sólo es válida para su SHA.

## Próximos gates

1. Concluir autenticación iOS aislada con limpieza y evidencia no secreta.
2. Configurar Team, certificados, perfiles y App Group para obtener archive/IPA
   firmado. El archive actual es deliberadamente sin firma.
3. Implementar y validar APNs en dispositivo físico según
   [IOS_APNS_PRODUCTION_REQUIREMENTS.md](IOS_APNS_PRODUCTION_REQUIREMENTS.md).
