# Producto y alcance

## Propósito

Qüata es una red social y comunitaria orientada a conectar personas, comunidades y comunicación oficial dentro de un mismo producto. Combina contenido público con participación autenticada y mecanismos de comunicación privada y de emergencia.

## Superficies del producto

### Contenido público

- Feed general.
- Muro Oficial.
- Directorio de comunidades.
- Perfiles públicos.
- Lectura de publicaciones, comentarios y contenido compartido que el backend marque como público.

Estas superficies deben permanecer visibles sin sesión. Intentar una acción privada abre el diálogo común de autenticación sobre el contenido actual.

### Participación autenticada

- Reacciones, comentarios, follows y reportes.
- Publicación de contenido.
- Conversaciones privadas, grupales y comunitarias.
- Configuración de cuenta y contactos SOS.
- Moderación según rol.
- Registro y ciclo de tokens push.

### Comunicación

- Chat sobre Supabase, RPC y Realtime.
- Mensajes de texto, respuestas, adjuntos y notas de voz.
- Estados de entrega y lectura.
- Conversaciones SOS con tarjetas de ubicación.
- Notificaciones internas y push según plataforma.

### Creación y media

- Publicaciones de texto, imagen y vídeo.
- Editor de imagen y vídeo.
- Posts oficiales enriquecidos y multidioma.
- Avatares, adjuntos y previsualizaciones.
- Visores de imagen, vídeo y documentos.

### Idiomas

La interfaz y los flujos principales contemplan español, inglés y francés. El producto incluye traducción entre Fang y esos idiomas mediante un servicio especializado, además de traducción ES/EN/FR para contenido oficial.

## Referencia durante la migración

La aplicación Android publicada define el comportamiento que debe conservarse: composición visual, reglas, flujos, navegación y contrato de backend. La migración no autoriza a simplificar el producto para que encaje en Web o iOS.

Si una capacidad del sistema no existe en una plataforma, el producto debe ocultarla, deshabilitarla de forma coherente o ofrecer un adaptador equivalente. No puede simular éxito ni mostrar controles conectados a callbacks vacíos.

## Fuera del alcance de una migración de pantalla

- Cambiar por comodidad el contrato backend vigente.
- Endurecer RLS rompiendo clientes publicados.
- Rediseñar la experiencia sin una decisión de producto explícita.
- Sustituir Compose por HTML, UIKit o SwiftUI paralelo para imitar la pantalla.
- Declarar una función terminada sólo porque comparte modelos o ViewModels.
