# Accesibilidad Web real (WEB-TEST-001)

Quata Web usa Compose Multiplatform sobre Kotlin/Wasm. El DOM visual contiene un
`canvas`, pero Compose 1.10.0 publica sus nodos semánticos al árbol de
accesibilidad de Chrome. Los navegadores y Playwright pueden, por tanto, buscar e
interactuar con los controles reales mediante rol, nombre y teclado aunque no
exista un `button` o `input` HTML paralelo.

No hay contrato DOM auxiliar, shadow root, marcador `aria-hidden` ni proxy de
interacción. `ComposeViewport` sigue siendo la única fuente de entrada y de
estado.

## Controles cubiertos

El smoke de Chrome (`scripts/web-browser-smoke.mjs`) consulta
`Accessibility.getFullAXTree` del proceso real de Chrome y exige:

| Control | Rol AX | Nombre AX |
| --- | --- | --- |
| Teléfono de acceso | `textbox` | `Teléfono` |
| Contraseña de acceso | `textbox` | `Contraseña` |
| Enviar acceso | `button` | `Entrar` |

El mismo smoke envía `Tab` a Chrome y comprueba que el árbol AX informa un nodo
con foco. No introduce credenciales ni permite red externa. El recorrido
hermético de `scripts/web-authenticated-browser-e2e.mjs` crea una sesión local
de un solo uso (sin red ni cuenta real) y ejerce además los controles reales de
Chat y Logout por rol, nombre y teclado:

| Control | Rol AX | Nombre AX | Estado/interacción verificada |
| --- | --- | --- | --- |
| Mensaje de Chat | `textbox` | `Mensaje` | escribe contenido local |
| Enviar Chat | `button` | `Enviar` | deshabilitado vacío, habilitado tras escribir y activado con Enter |
| Cerrar sesión | `button` | `Cerrar sesión` | foco y activación con Enter; sesión local eliminada |

Los campos comunes `PhoneInputSection` y `QuataTextField` publican su etiqueta
como `contentDescription`; esto no altera su presentación ni comportamiento en
Android, iOS o Web, y da a Chrome un nombre persistente cuando el placeholder ya
no se muestra.

## Ejecutar

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :web:wasmJsBrowserDistribution --no-daemon
node scripts/web-browser-smoke.mjs
```

Las ampliaciones posteriores deben usar el mismo árbol AX y un backend local
hermético; no deben reintroducir elementos DOM que dupliquen la UI Compose.
