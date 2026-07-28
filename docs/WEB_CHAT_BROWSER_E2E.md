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

Su modo actual sólo inspecciona: congela y valida el manifiesto antes de
resolver secretos, carga UUIDs normalizados en tablas temporales y abre una
única transacción `SERIALIZABLE READ ONLY` con advisory lock y timeouts, que
siempre termina en `ROLLBACK`. `-Commit` se rechaza por construcción. La
purga destructiva sigue bloqueada hasta disponer de un servicio separado con
firma/nonce de servidor y una atestación de artifact de GitHub Actions ligada
a `run_id`, hash de manifiesto, SHA candidato y proyecto Supabase. Un JSON
local, incluso con el formato antiguo, no puede acreditar una purga.

La URL y la CA sólo se cargan desde los ficheros configurados por
`QUATA_E2E_PURGE_DB_URL_FILE` y `QUATA_E2E_PURGE_DB_CA_FILE`; no se imprimen.
`scripts/attest-web-chat-exact-purge.mjs` permanece bloqueado por construcción
hasta que exista esa integración de firma. Una bandera manual `verified` no
puede acreditar el E2E.

## Estado de validación local

Los resultados históricos o locales de compilación, smoke y preflight no
acreditan una purga live actual ni se pueden reutilizar como evidencia de este
SHA. No se ha ejecutado una corrida live del candidato con el gate de purga
actual; el gate sólo inspecciona y termina en rollback.

El recorrido Playwright sigue bloqueado antes del primer envío: Compose/Wasm
monta el canvas dentro del shadow DOM, pero Chrome no materializa el
`OutlinedTextField` como textbox localizable, ni siquiera con
`--force-renderer-accessibility`. Por tanto no están verificados mediante UI:
envío A→B, recepción, respuesta, logout ni purga. Tampoco se declara ausencia
de residuos de Auth, perfiles, hilos, participantes, mensajes, adjuntos o
sesiones hasta que exista una ejecución autorizada y una verificación
independiente del servicio de purga.
