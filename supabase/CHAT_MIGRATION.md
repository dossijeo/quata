# Migracion de chat Better Messages a Supabase

## Hallazgos del helper actual

El backend Better Messages se usa para:

- Sesion/identidad: `syncSession`, `setProfileContext`, `getUnreadCount`, lookup de `profile_id -> wp_user_id`, nonce REST y cookies.
- Inbox/polling: `checkNew`, cursor `lastUpdate`, hilos visibles, cache local, notificaciones nativas y conteo de no leidos.
- Hilos: abrir/crear privado, crear grupo, crear chat de comunidad, SOS, cambiar asunto, mute, abandonar, ocultar/borrar/restaurar.
- Participantes: invitar miembros, promover moderador, detectar grupos, permisos `allowInvite`.
- Mensajes: enviar texto, responder, editar, borrar, reenviar, favoritos.
- Adjuntos: subir archivo, asociar archivos a mensajes y listar adjuntos por tipo.
- Estado por usuario: lectura, favoritos, conversaciones abandonadas/ocultas, mutes y cache.

## Migraciones creadas

Ejecutar en este orden:

1. `supabase/migrations/20260628_0001_chat_schema.sql`
2. `supabase/migrations/20260628_0002_chat_rpc.sql`

El esquema usa `public.community_profiles.id` como identidad principal, que es lo que hoy guarda `AuthSession.userId`. Tambien agrega `community_profiles.auth_user_id` para enlazar con `auth.users(id)` cuando se active Supabase Auth real.

## Tablas nuevas

- `chat_threads`: hilo/conversacion, tipo `private | group | wall | sos`, asunto, comunidad, resumen y metadata.
- `chat_private_threads`: par unico de perfiles para chats 1:1.
- `chat_participants`: estado por usuario: rol, lectura, mute, oculto, borrado, abandonado.
- `chat_messages`: mensajes, replies, forward, edit/delete soft.
- `chat_attachments`: adjuntos ya subidos a Storage y asociados luego a mensajes.
- `chat_message_favorites`: favoritos por usuario.
- `chat_message_reactions`: preparado para reacciones.
- `chat_message_reads`: lecturas por mensaje.
- `chat_profile_blocks`: bloqueos globales o por hilo.
- `chat_events`: auditoria/cursor de cambios.
- `chat_sos_events` y `chat_sos_recipients`: eventos SOS asociados al hilo.

## RPC endpoints

PostgREST los expone como `POST /rest/v1/rpc/<nombre>`.

- `quata_chat_get_inbox`: reemplaza `loadInbox`/lista de conversaciones.
- `quata_chat_check_new`: reemplaza `checkNew`.
- `quata_chat_get_thread`: reemplaza `getThread`.
- `quata_chat_get_or_create_private_thread`: reemplaza `getPrivateThread`/`getPrivateUrl`.
- `quata_chat_start_thread`: reemplaza `thread/new` para grupos, comunidad y SOS base.
- `quata_chat_send_message`: reemplaza `thread/{id}/send`.
- `quata_chat_register_attachment`: reemplaza la parte de registro de `upload`.
- `quata_chat_send_files`: alias de envio con adjuntos.
- `quata_chat_list_attachments`: reemplaza `thread/{id}/attachments`.
- `quata_chat_set_favorite` y `quata_chat_get_favorites`: reemplazan favorito/getFavorited.
- `quata_chat_edit_message` y `quata_chat_delete_messages`: reemplazan save/deleteMessages.
- `quata_chat_forward_message`: reemplaza forward.
- `quata_chat_change_subject`: reemplaza changeSubject.
- `quata_chat_set_muted`: reemplaza mute/unmute.
- `quata_chat_set_member_invites_enabled`: cubre el permiso que Android aun marcaba como no disponible.
- `quata_chat_add_participants`, `quata_chat_promote_moderator`, `quata_chat_remove_participant`, `quata_chat_block_participant`.
- `quata_chat_leave_thread`, `quata_chat_delete_thread`, `quata_chat_restore_thread`.
- `quata_chat_mark_thread_read`: mueve el estado local de lectura al backend.
- `quata_chat_send_sos`: crea/reusa hilo SOS y registra el evento.

## Identidad y seguridad

Hoy el cliente usa la anon key como bearer y un token local `supabase-profile:<profile_id>`. Por eso los RPC aceptan `p_actor_profile_id` como compatibilidad.

Cuando haya Supabase Auth real:

1. Rellenar `community_profiles.auth_user_id`.
2. Enviar el access token real como `Authorization: Bearer <jwt>`.
3. Seguir mandando `p_actor_profile_id` durante la transicion; la funcion lo validara contra `auth.uid()`.
4. Despues se puede hacer opcional en el cliente.

Las politicas RLS directas estan pensadas para usuarios `authenticated`. Las llamadas actuales con `anon` deben ir por RPC.

## Preflight recomendado

Antes de ejecutar en produccion, confirmar que las FK existentes tienen tipo UUID:

```sql
select table_name, column_name, data_type, udt_name
from information_schema.columns
where table_schema = 'public'
  and table_name in (
    'community_profiles',
    'community_walls',
    'community_emergency_contacts'
  )
  and column_name in ('id', 'profile_id', 'contact_profile_id', 'wall_id');
```

Si `community_profiles.id` no es `uuid`, hay que adaptar las FK y parametros `uuid` de las migraciones a ese tipo antes de aplicarlas.

## Ejemplo de llamada RPC transicional

```json
POST /rest/v1/rpc/quata_chat_send_message
{
  "p_actor_profile_id": "PROFILE_UUID",
  "p_thread_id": 123,
  "p_message": "Hola",
  "p_file_ids": [],
  "p_reply_to_message_id": null
}
```

## Adjuntos

La migracion crea el bucket publico `chat-attachments`. Flujo esperado:

1. Subir el binario a Supabase Storage.
2. Llamar a `quata_chat_register_attachment` con `file_url`, `storage_path`, `mime_type`, etc.
3. Llamar a `quata_chat_send_message` o `quata_chat_send_files` con el `id` devuelto.

Si el cliente sigue usando anon key para Storage, hara falta una politica de subida anon temporal o, mejor, un endpoint server-side de signed upload.
