# SB-04: Chat autenticado entre dos usuarios aislados

\`scripts/run-supabase-e2e-sb04.ps1\` recorre con dos cuentas E2E aisladas los
contratos PostgREST usados por los repositorios comunes/Web: abrir o recuperar
un hilo privado, inbox, detalle, mensaje, reply, lectura y silenciado. Sólo usa
la clave publicable y los JWT de cada cuenta: no acepta secretos, URL, perfiles,
hilos ni mensajes como argumentos; tampoco usa service-role, SQL, DDL ni cambia
funciones o políticas.

## Precondición de seguridad

SB-04 es mutante. Las RPC actuales \`quata_chat_delete_messages\` y
\`quata_chat_delete_thread\` sólo hacen borrado lógico: conservan filas, estados
y eventos. Por tanto no son una limpieza suficiente de dos pruebas E2E. Antes de
cualquier red o mutación, el runner exige que un operador haya aprobado la purga
externa autorizada de las dos cuentas aisladas **y de todos sus datos Chat**.
Sin esa garantía, \`QUATA_E2E_CHAT_EXTERNAL_HARD_CLEANUP\` no puede tener el
valor exacto \`approved_isolated_account_purge\` y el runner aborta antes de
autenticarse, crear hilo o enviar mensaje.

El consentimiento tiene dos capas y ambas se validan antes de red: el contrato
de purga y \`QUATA_E2E_CHAT_A_E2E_SCOPE\` / \`QUATA_E2E_CHAT_B_E2E_SCOPE\`,
ambos con el valor exacto \`isolated_sb04_account\`. Además, el runner rechaza
los dos mismos teléfonos y, tras los dos logins, el mismo perfil. No acepta una
clave \`service_role\` ni \`sb_secret_*\`; una clave JWT cuyo rol sea
\`service_role\` también se rechaza antes de cualquier petición.

El runner solicita el borrado lógico y revoca ambas sesiones. Incluso cuando
todos los pasos pasan, el informe queda como
\`passed_with_external_cleanup_pending\`: el operador debe efectuar la purga
externa y anotarla en el tablero. Si una operación falla, queda
\`rollback_pending\`; no se debe ejecutar un lote posterior hasta completar la
limpieza. Nunca se declara una purga externa como realizada por el runner.

## Ejecución

\`\`\`powershell
$env:QUATA_SUPABASE_URL = 'https://<project-ref>.supabase.co'
$env:QUATA_SUPABASE_PUBLISHABLE_KEY = '<publishable-key>'
$env:QUATA_E2E_CHAT_A_COUNTRY_CODE = '<country-code-a>'
$env:QUATA_E2E_CHAT_A_PHONE = '<isolated-e2e-phone-a>'
$env:QUATA_E2E_CHAT_A_PASSWORD = '<isolated-e2e-password-a>'
$env:QUATA_E2E_CHAT_B_COUNTRY_CODE = '<country-code-b>'
$env:QUATA_E2E_CHAT_B_PHONE = '<isolated-e2e-phone-b>'
$env:QUATA_E2E_CHAT_B_PASSWORD = '<isolated-e2e-password-b>'
$env:QUATA_E2E_CHAT_A_E2E_SCOPE = 'isolated_sb04_account'
$env:QUATA_E2E_CHAT_B_E2E_SCOPE = 'isolated_sb04_account'
$env:QUATA_E2E_CHAT_EXTERNAL_HARD_CLEANUP = 'approved_isolated_account_purge'
.\scripts\run-supabase-e2e-sb04.ps1 -AllowExistingTestData -AllowChatMutation
\`\`\`

Las cuentas deben ser exclusivas de SB-04 y no compartir un hilo privado previo:
\`get_or_create_private_thread\` puede reutilizarlo. El informe local sólo
contiene nombres de pasos y estado de limpieza, nunca URL, teléfonos,
contraseñas, tokens ni IDs de perfil/hilo/mensaje.

El ejecutable Node requiere asimismo los dos switches explícitos; no se debe
invocar directamente para omitir el preflight de PowerShell.
