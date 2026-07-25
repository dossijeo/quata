# SB-05: adjuntos Chat y Supabase Storage

`scripts/run-supabase-e2e-sb05.ps1` valida con dos cuentas E2E aisladas el
recorrido que consume el adaptador Web/KMP: subida de un Blob de texto no
sensible al bucket `chat-attachments`, registro por
`quata_chat_register_attachment`, enlace al mensaje mediante `file_ids`,
lectura de metadata y descarga con el JWT de la segunda cuenta. Nunca recibe
URL, credenciales, tokens, perfiles, rutas ni identificadores por argumentos;
usa exclusivamente la clave publicable y sesiones autenticadas.

La configuración versionada actualmente declara `chat-attachments` como
bucket público y permite la lectura de `storage.objects` a `anon`. Por ello,
la descarga por la segunda sesión demuestra que el adjunto enlazado es
recuperable por el flujo Chat, **no** una restricción de lectura entre
participantes. El runner comprueba también esa lectura pública de forma
explícita para detectar cambios de contrato, y nunca etiqueta ese resultado
como una prueba de aislamiento de Storage.

## Gate de limpieza obligatorio

El runner borra el objeto Storage con la identidad de quien lo subió y exige
que la segunda identidad ya no pueda descargarlo. También solicita el borrado
lógico del mensaje e hilo. Esto **no elimina** la fila `chat_attachments`:
las RPC públicas `quata_chat_delete_messages` y `quata_chat_delete_thread`
conservan las filas y eventos por diseño.

La única limpieza completa disponible en el repositorio es la ruta autorizada
de ciclo de vida de cuenta (`quata_account_delete_data`), restringida a
`service_role` y usada por `quata-account-lifecycle`. Por tanto, antes de la
primera petición, SB-05 exige las dos capas siguientes:

1. Ambas cuentas son exclusivas del lote, marcadas exactamente como
   `isolated_sb05_attachment_account`.
2. Un operador ha autorizado la purga de ambas cuentas a través de ese flujo y
   se ha comprometido a registrar una comprobación posterior de que no queda
   ni la ruta Storage ni la fila de adjunto. Sólo entonces puede establecer
   `QUATA_E2E_SB05_EXTERNAL_HARD_CLEANUP` a
   `approved_isolated_account_purge_and_attachment_verification`.

Sin esas dos capas el script aborta antes de autenticarse, subir un Blob o crear
un mensaje. Incluso si los pasos técnicos pasan, el informe termina en
`passed_with_external_hard_cleanup_pending`: no falsea una verificación de
filas que una clave pública no tiene permiso para consultar tras la purga.
El operador que ejecuta el ciclo de vida de cuenta debe anotar en el tablero la
fecha, commit, perfiles aislados ya eliminados y resultado de la consulta de
verificación, sin guardar IDs, tokens, teléfonos ni contraseñas en Git.

## Ejecución

```powershell
$env:QUATA_SUPABASE_URL = 'https://<project-ref>.supabase.co'
$env:QUATA_SUPABASE_PUBLISHABLE_KEY = '<publishable-key>'
$env:QUATA_E2E_CHAT_A_COUNTRY_CODE = '<country-code-a>'
$env:QUATA_E2E_CHAT_A_PHONE = '<isolated-sb05-phone-a>'
$env:QUATA_E2E_CHAT_A_PASSWORD = '<isolated-sb05-password-a>'
$env:QUATA_E2E_CHAT_B_COUNTRY_CODE = '<country-code-b>'
$env:QUATA_E2E_CHAT_B_PHONE = '<isolated-sb05-phone-b>'
$env:QUATA_E2E_CHAT_B_PASSWORD = '<isolated-sb05-password-b>'
$env:QUATA_E2E_CHAT_A_E2E_SCOPE = 'isolated_sb05_attachment_account'
$env:QUATA_E2E_CHAT_B_E2E_SCOPE = 'isolated_sb05_attachment_account'
$env:QUATA_E2E_SB05_EXTERNAL_HARD_CLEANUP = 'approved_isolated_account_purge_and_attachment_verification'
.\scripts\run-supabase-e2e-sb05.ps1 -AllowExistingTestData -AllowChatAttachmentMutation
```

El fichero de salida local contiene solamente pasos, estados y política de
limpieza. Se crea con permisos de propietario y no incluye secretos, URL,
tokens, teléfonos, rutas, IDs de Storage, perfiles, hilos, mensajes ni
adjuntos. No ejecutar directamente el `.mjs`: el wrapper PowerShell mantiene
la advertencia y el doble opt-in visible.
