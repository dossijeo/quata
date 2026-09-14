# Firma local iOS — Personal Team

Identidad validada por el propietario el 14 de septiembre de 2026 en la VM macOS
`gabriel@192.168.1.109`: Xcode 26.6 (17F113), SDK iPhoneOS 26.5, llavero
`/Users/gabriel/Library/Keychains/login.keychain-db`.

El equipo es **Gabriel Fernandez Robles (Personal Team)**, Team ID `28X9AG24SW`,
con `isFreeProvisioningTeam = 1`. La huella de identidad codesign es
`F8EF844BE964B1CE2B7132BBCDCD6509839B70FC`. El certificado Apple Development
vence el 14 de septiembre de 2027 a las 12:54:23 UTC. Una etiqueta antigua de
Accounts no sustituye estos identificadores criptográficos.

Runbook y preflight integrados en [PR #332](https://github.com/dossijeo/quata/pull/332),
merge `ba09c6f183128d330dae502462f1d0f913e20f65` (14 de septiembre de 2026).
Certificación final: [Web/Android](https://github.com/dossijeo/quata/actions/runs/34886020923),
[iOS](https://github.com/dossijeo/quata/actions/runs/34886020652) y
[CodeQL](https://github.com/dossijeo/quata/actions/runs/34885774900), todos SUCCESS.
Este cierre acredita la integración del preflight y la documentación, con los límites
de aprovisionamiento, entrega y distribución descritos a continuación.

## Preflight local

Desde el checkout de la VM:

```sh
bash scripts/preflight-ios-local-development-signing.sh
```

Comprueba la huella entre las identidades válidas del llavero sin imprimir su listado,
importar el P12 ni leer contraseñas. Un PASS acredita presencia de la identidad; no
acredita perfiles iOS, instalación, entrega APNs ni distribución.

## Rutas de ejecución

| Ruta | Configuración | Alcance |
| --- | --- | --- |
| Simulador | Lane existente sin identidad Apple; cuando el runtime exige firma ad hoc, usar el helper de simulador existente | El simulador no registra un dispositivo físico ni certifica aprovisionamiento. |
| Dispositivo local gratuito | `SimulatorSigned`, automatic signing y Apple Development | Sin entitlements restringidos en app y extensión; requiere dispositivo registrado y perfiles vigentes. |
| Release/Archive de distribución | Release manual con credenciales futuras de membresía pagada | Mantener `QUATA_DEVELOPMENT_TEAM`, `QUATA_IOS_APP_PROVISIONING_PROFILE` y `QUATA_IOS_SHARE_EXTENSION_PROVISIONING_PROFILE` del entorno de distribución. |

Para el build local de dispositivo, pasar a `xcodebuild`:

```text
-configuration SimulatorSigned
DEVELOPMENT_TEAM=28X9AG24SW
CODE_SIGN_STYLE=Automatic
CODE_SIGN_IDENTITY=Apple Development
PROVISIONING_PROFILE_SPECIFIER=
```

La lane conserva `com.quata.ios` y `com.quata.ios.shareextension`. La configuración
`SimulatorSigned` elimina `CODE_SIGN_ENTITLEMENTS` de ambos targets. Su nombre no
implica que deba usarse el SDK de simulador para un dispositivo: el build de iPhone
requiere destino/SDK iOS y un framework Kotlin compatible con dispositivo.

El Personal Team no admite Push Notifications ni App Groups. Este build no certifica
APNs ni el intercambio mediante `group.com.quata.ios.share`; no eliminar capacidades
de Release para hacerlo pasar. Tampoco sirve para App Store Connect o TestFlight.

## Perfiles y recuperación

Apple no emitió perfiles iOS porque no había iPhone/iPad físico registrado. El siguiente
paso es presentar un dispositivo real a Xcode, registrarlo y regenerar los perfiles;
no recrear el certificado. Según la validación del propietario, los App IDs,
dispositivos y perfiles gratuitos requieren renovación a los siete días, mientras
la identidad actual vence en 2027.

El backup privado del host está en
`C:\Users\PC\Desktop\QÜATA\Apple-Signing-Backup-2026-09-14`, protegido por ACL NTFS.
Contiene `Quata-Apple-Development.p12`, el certificado público, metadatos y hashes.
`IMPORT-PASSWORD.txt` sólo se utiliza privadamente para recuperación: no imprimirlo,
copiarlo al repositorio ni subirlo a logs o artefactos. La VM actual ya tiene la
identidad instalada y no necesita importar el P12.

No configurar secrets permanentes de GitHub Actions ni un gate de distribución con
esta identidad gratuita. Una futura membresía pagada tendrá sus credenciales de CI
específicas. Los requisitos de entrega real siguen en
[IOS_APNS_PRODUCTION_REQUIREMENTS.md](IOS_APNS_PRODUCTION_REQUIREMENTS.md).
