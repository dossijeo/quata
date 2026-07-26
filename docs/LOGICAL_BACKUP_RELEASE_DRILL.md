# Backup lógico previo al release

`scripts/new-db-logical-backup.ps1` toma una copia lógica cifrada antes de un
release de RLS. Es una puerta adicional, no sustituye PITR de Supabase.

- Lee la conexión exclusivamente desde un fichero y exige `sslmode=verify-full`.
- Exige una CA PEM explícita, nunca desactiva la validación TLS y no incluye URL,
  host, usuario, contraseña ni valores de negocio en el manifiesto.
- `pg_dump` se canaliza directamente al cifrador: no se crea un dump plano en
  disco. El resultado se restringe al usuario actual en Windows. Usa AES-256-GCM;
  la clave base64 de 32 bytes se mantiene fuera del
  repositorio y fuera del directorio del backup.
- El modo `Full` (predeterminado) genera un `pg_dump` custom completo. `Critical`
  conserva el esquema completo más datos de `community_comments` y
  `official_post_likes`, de modo que incluye sus objetos dependientes.

Ejemplo (no pegar secretos en consola ni en CI):

```powershell
.\scripts\new-db-logical-backup.ps1 `
  -DbUrlFile 'C:\seguro\supabase-db-url.txt' `
  -TlsCaFile 'C:\seguro\supabase-pooler-ca.pem' `
  -EncryptionKeyFile 'C:\seguro\release-backup.key' `
  -OutRoot 'D:\restricted-backups\quata'
```

La restauración se prueba antes de autorizar DDL:

```powershell
.\scripts\restore-db-logical-backup-drill.ps1 `
  -BackupSet 'D:\restricted-backups\quata\release-…' `
  -EncryptionKeyFile 'C:\seguro\release-backup.key'
```

El drill crea y borra un PostgreSQL 17 Docker. Falla de forma cerrada si el
tag AEAD no valida al descifrar, el checksum SHA-256 cifrado o plano no coincide,
`pg_restore` falla o la presencia de ambas tablas no se verifica.
No se debe ejecutar un backup real si no hay un volumen local restringido y una
clave custodiada separadamente. Conservar el set cifrado hasta cerrar el release
y confirmar PITR/rollback; nunca subirlo a Git, artefactos CI ni almacenamiento
compartido sin cifrado equivalente.

El drill local prueba el formato, TLS `verify-full` del cliente, cifrado y la
restauración de un dump PostgreSQL 17 ordinario con las dos tablas afectadas.
No prueba que una restauración completa de Supabase sea portable a PostgreSQL
vanilla: extensiones, roles, Storage y servicios gestionados requieren un drill
en un proyecto Supabase aislado. Para comprobar también la integridad de datos,
proporcionar los dos conteos pre-release observados al drill mediante
`-ExpectedCommunityComments` y `-ExpectedOfficialPostLikes`.
