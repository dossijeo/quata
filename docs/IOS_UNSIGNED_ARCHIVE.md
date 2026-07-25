# Archive iOS genérico sin firma

Esta comprobación valida que el host Swift y `QuataShared.xcframework` pueden
formar un `.xcarchive` para un dispositivo iOS genérico. Es un carril de
**estructura y enlace de release**, separado de XCTest: no arranca un
simulador, no ejecuta pruebas y no demuestra instalación en un dispositivo.

No exporta un IPA, no firma código, no usa un equipo de Apple Developer ni
consume certificados, perfiles de aprovisionamiento o secretos. Por diseño,
la salida no puede distribuirse ni instalarse como una aplicación de release.

## Ejecución en macOS

Con Xcode, JDK y XcodeGen instalados, desde la raíz del repositorio:

```bash
bash scripts/archive-ios-unsigned.sh
```

El script construye `:ios-shared:assembleQuataSharedDebugXCFramework`, genera
el proyecto Xcode y ejecuta:

```bash
xcodebuild -project iosApp/QuataIos.xcodeproj -scheme QuataIos \
  -configuration Debug -sdk iphoneos -destination "generic/platform=iOS" \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO archive
```

Después exige que el archive contenga `QuataIos.app` y el framework
`QuataShared.framework` embebido. El resultado por defecto se deja en
`build/reports/ios/QuataIos-unsigned.xcarchive`; se puede cambiar mediante
`QUATA_IOS_ARCHIVE_PATH` y `QUATA_IOS_DERIVED_DATA_PATH`.

## Límites explícitos

Este carril acredita únicamente:

- la slice `iosArm64` del framework común;
- el enlace del host para `generic/platform=iOS`;
- la estructura del archive y el framework embebido.

Sigue pendiente una fase de distribución distinta, con autorización expresa:

- certificado de distribución, `DEVELOPMENT_TEAM` y perfil de provisioning;
- firma y `xcodebuild -exportArchive` para obtener IPA;
- instalación y prueba en dispositivo físico;
- configuración pública de runtime y recorridos Auth/Feed reales;
- APNs/push y permisos físicos.

No añadas esos secretos al repositorio, a `project.yml`, a artefactos de CI ni
a logs. Si el entorno macOS no tiene Xcode o XcodeGen, el script aborta antes
de modificar o publicar una salida.
