# WEB-TEST-001 — puente DOM real de Compose/Wasm

Los nodos semánticos canvas de Compose 1.10 no dan nombre AX a los textboxes.
Para los controles críticos Web, Quata usa la API oficial
`androidx.compose.ui.viewinterop.WebElementView`: los inputs y botones son los
elementos HTML visibles e interactivos que forman parte de la composición.

No hay overlay, host `aria-hidden`, marcador ni control duplicado. Cada elemento
actualiza el mismo estado que consume la pantalla Compose mediante `oninput`/
`onclick`; Android e iOS siguen usando sus controles Compose existentes.

El MRE opt-in `scripts/web-ax-dom-bridge-repro.mjs` conserva la evidencia del
renderer canvas anterior. Las pruebas de recorrido deben usar los elementos
WebElementView por rol/nombre y teclado, nunca el MRE como gate de éxito.
