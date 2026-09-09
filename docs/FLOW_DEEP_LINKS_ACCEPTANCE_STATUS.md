# FLOW-DEEP-LINKS: estado de aceptación focal

Estado: **parcial; sin candidate-final ni GO integrado**. El inventario maestro
permanece pendiente. Esta matriz resume resultados actuales; no sustituye los
reportes y capturas ni promueve CHAT-FOCUSED-MESSAGE o FLOW-SHELL-NAV.

Fuentes de producto Web: `8cde7edf6d63e4c167412f9a08d7b6799639faec`.
Distribución vigente: `7c743c4f95ebfb4d289f13c44f70584ddcdbca13f7146d9663d575f873678ca6`.
Se obtuvo recompilando las 158 tareas sin caché ni compilación incremental.
Un build normal posterior conservó exactamente el mismo fingerprint.

| Recorrido Web | Frío | Caliente | Salida / recarga | Límite pendiente |
| --- | --- | --- | --- | --- |
| Feed, post existente | Comprobado | Comprobado; mismo documento | Lista sin reapertura; cero errores | Post inexistente y enlace malformado |
| Oficial, post existente | Comprobado | Comprobado; mismo documento | Lista sin reapertura; cero errores | No acredita reproducción multimedia |
| Oficial, post inexistente | Estado terminal comprobado | Estado terminal; mismo documento | No crash observado | Retry y salida no aceptados por inferencia |
| Chat, hilo/mensaje propio, sesión válida | Comprobado | Comprobado; mismo documento | Un foco visible; salida/recarga sin reapertura | Destino inexistente, sesión expirada y transición posterior a login |
| Chat anónimo | Barrera de acceso comprobada | Pendiente específico | No acceso privado ni sesión instalada | Cancelación y continuación tras autenticación |

Los recorridos públicos usan lectura anónima de publicaciones existentes. Chat
usa perfiles, sesiones, hilo y mensaje temporales propios. Último run
`075b7195-330d-445f-9df3-2e4327182287`: proceso terminado, limpieza verificada y
journals/lock retirados. No hay mutaciones ni restituciones pendientes.

Las observaciones Web no prueban segundo plano/primer plano del sistema,
service workers ni recepción de push. El plazo observado tras salida es acotado;
no es una garantía indefinida. Los marcadores de diagnóstico no reemplazan la
inspección visual de las capturas.

## Android e iOS

- Android: existen build e instalación previos, pero falta completar la recepción
  real fría/caliente de los tres tipos y sus salidas sobre la candidata. Un build
  o entrega explícita al paquete no acredita App Links público/chooser.
- iOS: existe la corrección y pruebas del orden de restauración/entrega del enlace,
  pero faltan recorridos externos reales completos en el host. Se valida custom
  scheme; no inferir Universal Links sin Associated Domains.

## Evidencia descartada y procedencia

El bundle anterior `139a381f…` fallaba al volver del detalle Feed con `illegal cast`.
El mismo código y backport pasan tras recompilación completa. No se ha aislado
una causa exclusivamente incremental frente a caché/reutilización de tareas.
La receta de [backport](../third_party/compose-ui-web/README.md) recoge la
recompilación requerida al introducirlo o sustituirlo. No reutilizar aquel bundle
fallido como candidata ni trasladar su evidencia al nuevo fingerprint.

Un ensayo anterior de Chat devolvió PASS con el splash en las capturas; se conserva
como contradicción detectada. Producto y runner se corrigieron para exigir foco
con la conversación descubierta. No se considera aceptación de ese ensayo.

El [historial focal](FLOW_DEEP_LINKS_CHAT_FIXTURE_PLAN.md) detalla commits, runs,
comparaciones, límites y rutas locales de reportes. Los resultados vigentes están
en `build-reports/flow-deep-links/web-public-full-rebuild-8cde7edf` y
`build-reports/flow-deep-links/web-chat-clean-1b145e22-ac3a-4636-8ed2-83fcffd2ee14`.

## Cierre aún requerido

Completar los casos pendientes y plataformas; revisión independiente del head
exacto; correcciones; congelación; candidate-final; auto-merge; fast gates verdes
y jobs finales reales; integración certificada y reconciliación inmediata del
inventario con main. Ninguna de esas etapas se presume por los PASS locales.
