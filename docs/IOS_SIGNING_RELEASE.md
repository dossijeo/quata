# Preparación de firma y runtime iOS

Esta fase conserva en el repositorio sólo el contrato verificable de una distribución
iOS. No crea App IDs, perfiles, certificados, secretos, IPA ni comunicación con Apple.

## Contrato versionado

| Target | Bundle ID | Perfil externo | Entitlements |
| --- | --- | --- | --- |
| `QuataIos` | `com.quata.ios` | `QUATA_IOS_APP_PROVISIONING_PROFILE` | Push y `group.com.quata.ios.share` |
| `QuataShareExtension` | `com.quata.ios.shareextension` | `QUATA_IOS_SHARE_EXTENSION_PROVISIONING_PROFILE` | `group.com.quata.ios.share` |

Ambos targets Release usan firma manual, `Apple Distribution` y
`QUATA_DEVELOPMENT_TEAM`. El entorno APNs de la app se fija a `production` en
Release. Los valores reales se inyectan al proceso de build; no deben añadirse a
`project.yml`, archivos `.xcconfig` versionados, logs ni artefactos.

## Comprobaciones sin Apple Developer

Desde la raíz, en cualquier host con Bash y Python 3:

```bash
bash scripts/check-ios-release-readiness.sh
```

La comprobación estática cruza `project.yml`, ambos `Info.plist`, los dos
entitlements y el uso del App Group por la extensión. No requiere Xcode ni
secretos.

Antes de un archive firmado, el entorno seguro puede hacer fallar temprano los
valores ausentes o sin expandir:

```bash
QUATA_DEVELOPMENT_TEAM=TEAMID \
QUATA_IOS_APP_PROVISIONING_PROFILE='Quata iOS App Store' \
QUATA_IOS_SHARE_EXTENSION_PROVISIONING_PROFILE='Quata Share Extension App Store' \
QUATA_SUPABASE_URL=https://example.supabase.co \
QUATA_SUPABASE_PUBLISHABLE_KEY=public-key \
QUATA_APNS_ENVIRONMENT=production \
bash scripts/check-ios-release-readiness.sh --signed-release
```

Ese modo valida presencia y formato solamente: no comprueba que el Team, los
perfiles o el runtime sean válidos, ni firma código.

## Requisitos externos exactos para IOS-SIGN-001 / IOS-RELEASE-001

1. Acceso de administrador a la cuenta Apple Developer del equipo que publicará Quata.
2. Registrar el App ID `com.quata.ios`, con Push Notifications y App Groups activados,
   y asociarlo a `group.com.quata.ios.share`.
3. Registrar el App ID `com.quata.ios.shareextension`, con App Groups activados,
   y asociarlo al mismo `group.com.quata.ios.share`.
4. Crear o seleccionar un certificado `Apple Distribution` y perfiles de distribución
   separados, uno para cada Bundle ID, con los entitlements anteriores.
5. Mantener en el almacén seguro del runner: `QUATA_DEVELOPMENT_TEAM`,
   `QUATA_IOS_APP_PROVISIONING_PROFILE`,
   `QUATA_IOS_SHARE_EXTENSION_PROVISIONING_PROFILE`, `QUATA_SUPABASE_URL` y
   `QUATA_SUPABASE_PUBLISHABLE_KEY`; suministrar el certificado y los perfiles al
   keychain temporal del runner sin volcarlos en logs.
6. Configurar capacidad APNs de producción y las credenciales del proveedor fuera del
   repositorio; después verificar en un dispositivo físico que el archive firmado contiene
   los dos perfiles, el App Group y `aps-environment=production`.

No ejecutar `xcodebuild -exportArchive`, distribuir un IPA ni subir a App Store Connect
hasta que esos requisitos estén aprobados y disponibles en un entorno de firma autorizado.
