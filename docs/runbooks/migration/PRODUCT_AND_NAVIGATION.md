# Producto y navegación

Detalle del [modelo operativo](../../MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md); forma parte de la misma fuente de verdad, con el mismo alcance y autorizaciones.

## 1. Objetivo y referencia de producto

- Android publicado es la referencia funcional y visual.
- Web e iOS deben montar literalmente las mismas raíces Compose de `commonMain` que Android.
- El objetivo prioritario es que Android, iOS y Web sean, en la medida en que las capacidades de
  cada plataforma lo permitan, funcionalmente equivalentes y visualmente coherentes, compartiendo
  la máxima cantidad posible de código en `commonMain`.
- Este objetivo también actúa como criterio de revisión retroactiva de lo ya migrado: cualquier
  pantalla o flujo en estado `COMÚN CON LÍMITES`, `PARCIAL` o `AUSENTE` debe evaluarse contra esta
  premisa antes de considerarse cerrado.
- En caso de conflicto entre mantener la paridad funcional y optimizar tamaño, tiempos de
  compilación o métricas de rendimiento, prevalece la paridad funcional salvo que exista una
  limitación técnica real de la plataforma.
- No se eliminan, simplifican ni degradan funcionalidades para una plataforma sin aprobación
  explícita. Si una restricción obliga a hacerlo, se documenta la causa y se proponen alternativas
  antes de modificar el comportamiento.
- `commonMain` es la implementación de referencia. Android, iOS y Web solo divergen cuando es
  estrictamente necesario por APIs nativas.
- Si una funcionalidad no puede implementarse igual, se crea una abstracción (`expect`/`actual`,
  interfaces u otro contrato equivalente) antes que eliminarla.
- Los presupuestos de tamaño, bundle, Wasm o CI son ajustables; no justifican por sí solos la
  eliminación de funcionalidades.
- La experiencia del usuario debe permanecer consistente entre plataformas.
- No se aceptan pantallas HTML, hosts simplificados, «cutrescreens», maquetas ni implementaciones
  paralelas que imiten Android.
- Un adaptador de plataforma solo puede contener capacidades realmente específicas del sistema:
  picker/cámara, Keychain, Web Push/APNs, compartir, mapas, Quick Look/DocMentis, codecs,
  reproducción y exportación de medios, permisos y transporte HTTP.
- ViewModels, estado, reglas, navegación de producto, composición visual y eventos pertenecen a
  código común siempre que Android no dependa de una API del sistema.
- Que una pantalla compile o comparta un ViewModel no demuestra que esté migrada.
- No se acepta ningún control visible conectado a `no-op`, datos falsos, borradores locales que se
  presenten como persistencia, repositorios «unavailable» o fallos convertidos en éxito.

## 2. Contrato de navegación y autenticación

### Superficies públicas

- Feed, Comunidades, Oficial, Notificaciones y perfiles públicos se pueden abrir sin sesión.
- Header y navegación principal permanecen visibles durante la navegación anónima por esas rutas.
- Feed nunca solicita autenticación para leer.

### Superficies y acciones privadas

- Chats, Cuenta/SOS y Ajustes privados requieren sesión.
- Publicar, comentar, dar like, reportar y las demás acciones restringidas solicitan sesión aunque
  el contenido que las contiene sea público.
- Una acción restringida anónima muestra primero el diálogo común «Ya tengo cuenta / Registrar»
  sobre el contenido actual.
- Login, Registro y Recuperar contraseña son pantallas completas, fuera del shell principal.
- Tras autenticar, se restaura la ruta y la acción pendientes. Cancelar el diálogo o abandonar Auth
  elimina la acción pendiente; un login posterior no puede ejecutarla de forma accidental.
- Cerrar sesión revoca/limpia la sesión de plataforma y vuelve al Feed público.
- El origen debe conservarse: una acción iniciada desde Feed, Oficial o Comunidades regresa al mismo
  origen, no a una ruta fija.

### Contrato Web específico

- Login y logout Web usan el flujo `web_login` y la integración Web Push descrita en
  `supabase/WEB_PUSH_INTEGRATION.md`.
- Nunca se usa la publishable key como bearer de usuario.
- La sesión debe ser renovable y los fallos HTTP, timeout y cancelación deben propagarse de forma
  honesta.

## 9. Criterios que nunca justifican un atajo

- Backend temporalmente inseguro: se implementa el contrato actual y se documenta la deuda.
- Falta de renderer nativo en una VM: se usa la lane CPU compatible y CI para arquitecturas reales.
- Un test lento o flaky: se diagnostica; no se aumenta timeout, no se omite y no se convierte en
  éxito.
- Un componente difícil de portar: se crea el adaptador real; no se reemplaza por HTML, texto,
  icono genérico o botón inerte.
- CI verde: no sustituye el gate visual ni demuestra por sí solo paridad funcional.
- Captura visual correcta: no sustituye persistencia, backend, navegación o tests.

## 10. Cierre de la migración

La migración global solo se considera terminada cuando:

- todas las pantallas del inventario tienen GO Web e iOS;
- los flujos anónimos y autenticados completos coinciden con Android;
- login/logout Web incluye `web_login` y Web Push;
- firma, configuración, distribución y requisitos de notificaciones iOS están resueltos;
- Android publicado y Web antigua siguen funcionando;
- no queda ningún fallback de producto, no-op visible o backend ficticio;
- existe evidencia exacta y el responsable del producto ha completado su ronda funcional;
- todas las PR aprobadas están fusionadas, el inventario está actualizado y las ramas/worktrees
  temporales están eliminados.
