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

El wrapper aborta antes de red o DML si falta cualquiera de los dos opt-in, los
scopes aislados o el contrato `approved_isolated_account_purge`. El informe no
contiene tokens, contraseñas, teléfonos, IDs ni textos marcadores.

No se prueban adjuntos en este recorrido: SB-05 ya valida Storage y su cleanup.
Agregar un archivo a la prueba de UI sólo será aceptable cuando la purga de
objeto y fila sea parte de la misma ejecución y pueda verificarse después.
