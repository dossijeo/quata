param(
  [Parameter(Mandatory=$true)][string]$BackupSet,
  [Parameter(Mandatory=$true)][string]$EncryptionKeyFile,
  [int]$ExpectedChatAttachments = 409
)
$ErrorActionPreference='Stop'
$manifestPath=Join-Path $BackupSet 'manifest.json'
$manifest=Get-Content -LiteralPath $manifestPath -Raw|ConvertFrom-Json
if($manifest.format -ne 'quata-logical-backup-v1' -or $manifest.scope -ne 'Full'){throw 'backup_manifest_invalid'}
$artifact=@($manifest.artifacts|Where-Object name -eq 'database.dump.enc')
if($artifact.Count -ne 1){throw 'backup_full_artifact_missing'}
$encrypted=Join-Path $BackupSet $artifact[0].name
if((Get-FileHash -Algorithm SHA256 -LiteralPath $encrypted).Hash.ToLowerInvariant() -cne $artifact[0].ciphertextSha256){throw 'backup_ciphertext_checksum_mismatch'}
$work=Join-Path ([IO.Path]::GetTempPath()) ('quata-chat-attachments-restore-'+[guid]::NewGuid().ToString('N'))
$name='quata-chat-restore-'+[guid]::NewGuid().ToString('N').Substring(0,10)
$password=[guid]::NewGuid().ToString('N')
try{
  New-Item -ItemType Directory -Path $work|Out-Null
  $identity=[Security.Principal.WindowsIdentity]::GetCurrent().Name; & icacls $work /inheritance:r /grant:r "${identity}:(OI)(CI)F" *> $null
  if($LASTEXITCODE -ne 0){throw 'restore_directory_acl_failed'}
  $plain=Join-Path $work 'database.dump'
  node (Join-Path $PSScriptRoot 'db-backup-crypto.mjs') decrypt $encrypted $plain $EncryptionKeyFile *> $null
  if($LASTEXITCODE -ne 0){throw 'backup_decryption_failed'}
  if((Get-FileHash -Algorithm SHA256 -LiteralPath $plain).Hash.ToLowerInvariant() -cne $artifact[0].plaintextSha256){throw 'backup_plaintext_checksum_mismatch'}
  $toc=@(& docker run --rm -v "${work}:/backup:ro" postgres:17-alpine pg_restore --list /backup/database.dump 2>$null)
  if($LASTEXITCODE -ne 0){throw 'backup_toc_unreadable'}
  foreach($required in @('TABLE public chat_attachments','TABLE DATA public chat_attachments','TABLE storage buckets','TABLE storage objects','FUNCTION public quata_chat_attachment_json','FUNCTION public quata_chat_message_json','FUNCTION public quata_chat_thread_json')){
    if(-not @($toc|Where-Object {$_ -match [regex]::Escape($required)}).Count){throw "backup_toc_missing:$required"}
  }
  & docker run -d --rm --name $name -e "POSTGRES_PASSWORD=$password" -v "${work}:/backup:ro" postgres:17-alpine 2>$null|Out-Null
  if($LASTEXITCODE -ne 0){throw 'restore_container_start_failed'}
  $ready=$false;foreach($i in 1..30){& docker exec $name pg_isready -U postgres 2>$null|Out-Null;if($LASTEXITCODE -eq 0){$ready=$true;break};Start-Sleep -Milliseconds 500};if(-not $ready){throw 'restore_container_not_ready'}
  & docker exec -e "PGPASSWORD=$password" $name pg_restore -U postgres -d postgres --no-owner --no-acl --section=pre-data --table=chat_attachments /backup/database.dump 2>$null|Out-Null
  if($LASTEXITCODE -ne 0){throw 'chat_attachments_schema_restore_failed'}
  & docker exec -e "PGPASSWORD=$password" $name pg_restore -U postgres -d postgres --no-owner --no-acl --section=data --table=chat_attachments /backup/database.dump 2>$null|Out-Null
  if($LASTEXITCODE -ne 0){throw 'chat_attachments_data_restore_failed'}
  $countRaw=@(& docker exec -e "PGPASSWORD=$password" $name psql -U postgres -d postgres -Atqc 'select count(*) from public.chat_attachments' 2>&1)
  if($LASTEXITCODE -ne 0){throw 'chat_attachments_count_failed'}
  $count=($countRaw -join '').Trim()
  if($count -cne "$ExpectedChatAttachments"){throw 'chat_attachments_restore_count_mismatch'}
  Write-Output 'CHAT_ATTACHMENT_METADATA_BACKUP_RESTORE_DRILL_PASS'
}finally{
  docker rm -f $name 2>$null|Out-Null
  $resolved=[IO.Path]::GetFullPath($work)
  $temp=[IO.Path]::GetFullPath([IO.Path]::GetTempPath())
  if(-not $resolved.StartsWith($temp,[StringComparison]::OrdinalIgnoreCase) -or (Split-Path $resolved -Leaf) -notlike 'quata-chat-attachments-restore-*'){throw 'unsafe_restore_cleanup_path'}
  if(Test-Path -LiteralPath $resolved){Remove-Item -LiteralPath $resolved -Recurse -Force}
}
