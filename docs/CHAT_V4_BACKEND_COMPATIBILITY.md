# Chat v4: compatibilidad temporal del backend

Web e iOS usan el mismo conjunto de RPC autenticados que Android para conversaciones, SOS,
contactos, grupos, moderación, mensajes, favoritos, reenvío, ocultación y restauración. No se ha
modificado ninguna política RLS, tabla ni función de producción en esta migración.

## Riesgo conocido, aceptado para la migración

Las políticas actuales del backend son más amplias de lo deseable. El cliente sigue incluyendo el
identificador de perfil autenticado en los mismos parámetros `p_actor_profile_id` que Android. El
servidor actual debe seguir siendo la autoridad para comprobar la sesión, la pertenencia al hilo y
los permisos de moderación. Esto es compatible con Android publicado y con la Web anterior.

## Trabajo posterior a la publicación

Después de publicar la Web migrada, auditar y endurecer las políticas/funciones de forma aditiva,
con pruebas de regresión contra Android y la Web anterior. No activar ese endurecimiento desde esta
rama ni usarlo como motivo para deshabilitar funcionalidad Web/iOS.
