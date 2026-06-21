# Firma release y App Links

## Package

`com.quata`

## Asset Links

Archivo del proyecto:

`.well-known/assetlinks.json`

Debe publicarse en:

- `https://egquata.com/.well-known/assetlinks.json`
- `https://www.egquata.com/.well-known/assetlinks.json`

## Fingerprints incluidos

Debug:

`47:48:6D:DB:28:96:1F:43:3D:8F:1A:BD:11:21:69:E5:77:C6:A7:52:D9:64:B1:87:DC:89:0A:59:2B:89:C4:D8`

Release:

`67:30:9A:7B:71:D2:D4:BF:06:33:5B:17:BC:DF:74:C7:9B:A1:A6:60:2B:54:1A:B5:E6:A4:31:52:1C:BD:B2:04`

## Dominios declarados en la app

- `egquata.com`
- `www.egquata.com`

## Nota importante

Si Google Play App Signing reemplaza la clave de firma final de la app instalada por usuarios, el fingerprint que debe estar en `assetlinks.json` para producción será el certificado de firma de app mostrado por Play Console, no necesariamente el upload key local.

Tras subir el primer AAB, revisar en Play Console:

`Setup > App integrity > App signing key certificate`

y actualizar `assetlinks.json` si el SHA-256 de Play difiere.
