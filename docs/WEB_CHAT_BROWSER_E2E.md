# Chat Web autenticado en navegador

`scripts/run-web-chat-browser-e2e.ps1` recorre Chat sobre la distribución
Compose/Wasm mediante dos contextos Playwright completamente separados.

El recorrido:

1. inicia sesión con dos cuentas aisladas por `quata-auth-bridge`;
2. abre o recupera su conversación privada mediante el RPC aprobado;
3. restaura cada sesión en un contexto de navegador independiente;
4. abre el deep link Chat real en ambas ventanas;
5. A envía texto desde el compositor Compose;
6. B recibe el texto en la UI dentro de un presupuesto de 45 segundos;
7. B selecciona el mensaje, activa `Responder` y envía la respuesta desde la UI;
8. A recibe la respuesta y el runner confirma por RPC su enlace al mensaje original;
9. ambas ventanas ejecutan `Cerrar sesión`;
10. se solicita el cleanup lógico y queda obligatoria la purga dura externa de
    cuentas, Auth y filas Chat.

No se declara Realtime: `WebChatRepository` usa polling cada 30 segundos. El
presupuesto de 45 segundos verifica ese comportamiento sin presentarlo como
entrega inmediata.

## Precondiciones

- distribución `web/build/dist/wasmJs/productionExecutable` del SHA candidato;
- `npm install --ignore-scripts` para instalar `playwright-core`;
- Chrome disponible en la ruta habitual de Windows;
- dos cuentas exclusivas con scope `isolated_sb04_account`;
- plan de purga dura autorizado y verificable;
- variables públicas Supabase y credenciales efímeras sólo en el proceso.

```powershell
.\scripts\run-web-chat-browser-e2e.ps1 `
  -AllowExistingTestData `
  -AllowChatMutation
```

Antes de ejecutar, el manager debe aprobar la corrida en ese mismo proceso:

```powershell
$env:QUATA_E2E_CHAT_MANAGER_AUTHORIZATION = "MANAGER_APPROVED_ISOLATED_CHAT_E2E"
```

El runner falla cerrado sin esa autorizacion, incluso cuando las credenciales y
los dos scopes aislados ya estan presentes. Un recorrido sin una verificacion
independiente de la purga dura tambien falla: el runner no usa credenciales
privilegiadas ni borra cuentas o filas por su cuenta.

El wrapper aborta antes de red o DML si falta cualquiera de los dos opt-in, los
scopes aislados o el contrato `approved_isolated_account_purge`. El informe no
contiene tokens, contraseñas, teléfonos, IDs ni textos marcadores.
Si falla cualquier paso del cleanup lógico, el recorrido también termina con
estado fallido y código de salida distinto de cero; la purga dura externa sigue
siendo obligatoria.

No se prueban adjuntos en este recorrido: SB-05 ya valida Storage y su cleanup.
Agregar un archivo a la prueba de UI sólo será aceptable cuando la purga de
objeto y fila sea parte de la misma ejecución y pueda verificarse después.

## Purga exacta posterior

La prueba UI y la purga son fases distintas. Antes de una purga, el operador
crea un manifiesto inmutable para ese `run_id`: contiene los dos UUID de Auth y
perfil, su mapping, el scope `isolated_sb04_account`, la provenance del runner
y el hash canónico del manifiesto. El script
`scripts/run-web-chat-exact-purge-gate.ps1` no acepta teléfonos, marcadores,
prefijos, filtros temporales ni una URL de base de datos como argumento.

Su modo por defecto sólo inspecciona catálogo y conteos exactos y finaliza una
transacción `READ ONLY` con `ROLLBACK`. Un commit exige a la vez `-Commit`,
`-ApproveExactIdPurge` y la autorización de proceso
`QUATA_E2E_CHAT_PURGE_COMMIT_AUTHORIZATION=MANAGER_APPROVED_EXACT_ID_PURGE`.
Para cada par exacto llama al RPC de ciclo de vida; `auth.users` queda como
última relación destructiva y su ausencia se comprueba dentro de la misma
transacción. FKs desconocidas, `RESTRICT`/`NO ACTION` o adjuntos sin inventario
abortan antes de un commit.

La URL y la CA sólo se cargan desde los ficheros configurados por
`QUATA_E2E_PURGE_DB_URL_FILE` y `QUATA_E2E_PURGE_DB_CA_FILE`; no se imprimen.
Tras un commit, `scripts/attest-web-chat-exact-purge.mjs` une el informe de UI y
la evidencia redacted. Una bandera manual `verified` no puede acreditar el E2E.

## Estado de validación local

Sobre `1d604ab3`, `:web:wasmJsTest`,
`:web:wasmJsBrowserDistribution` y el smoke base de Chrome pasaron. El
preflight remoto también confirmó login con dos cuentas aisladas y creación
del hilo privado.

El recorrido Playwright permanece bloqueado antes del primer envío: Compose/Wasm
monta correctamente su canvas dentro del shadow DOM y conserva sesión, runtime y
ruta Chat, pero Chrome no materializa el `OutlinedTextField` como textbox
localizable, ni siquiera con `--force-renderer-accessibility`. No hubo excepciones
de runtime. Por tanto esta evidencia no acredita envío, recepción, reply ni
logout mediante UI. Todos los fixtures de cada intento se eliminaron a través de
`quata-account-lifecycle` y se verificó por base de datos la ausencia de Auth,
perfiles, hilos, participantes, mensajes, adjuntos y sesiones web.
La comprobación final arrojó cero residuos en todas esas superficies.
