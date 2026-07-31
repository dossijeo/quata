# `quata-profile-bridge`: contrato de mutaciones de Cuenta

Web e iOS no escriben directamente en `community_profiles` ni en
`community_emergency_contacts`. Mientras este bridge no esté desplegado y validado, los clientes
fallan de forma explícita con `*_profile_bridge_unavailable`, antes de emitir una mutación.

## Autenticación y autorización

- El endpoint recibe `Authorization: Bearer <access token>` y verifica el token con
  `supabase.auth.getUser(token)` en el servidor.
- Ignora cualquier `profile_id` enviado por el cliente. Deriva el perfil exclusivamente de
  `auth_user_id` del usuario autenticado; si no hay una correspondencia única, responde 403.
- Opera con credenciales de servidor sólo después de esa comprobación. Nunca acepta una
  `service_role` desde el cliente.
- Todas las respuestas de mutación representan el estado persistido o un error; no hay éxito
  optimista ni persistencia local de contraseña/respuesta secreta.

## Operaciones

`update_profile`

- Acepta únicamente display name, barrio, prefijo/teléfono y referencia de avatar ya subida.
- Aplica allow-list de columnas y deriva el `id` como arriba.

`replace_emergency_contacts`

- Acepta de cero a cinco IDs de perfil, normalizados y distintos.
- Rechaza IDs que no correspondan a perfiles existentes y el ID del propio actor.
- Debe ejecutarse como una transacción de base de datos (borrado + inserción) o, si el entorno
  no expone transacciones, como insert-first con compensación demostrable: conservar el conjunto
  previo, insertar el nuevo, validar cardinalidad, y restaurar el previo si falla cualquier paso.
  Nunca puede dejar al usuario sin sus contactos por un DELETE seguido de POST fallido.

`update_password`

- No escribe password en tablas de perfil, logs ni preferencias. Usa la API administrativa de
  Auth sólo después de autenticar el bearer y resolver su `auth_user_id`.

## Gate de habilitación

Antes de conectar los clientes al bridge: pruebas de token inválido, actor ajeno, ID de contacto
ajeno/inexistente, rollback de contactos, actualización persistida y observabilidad sin secretos.
No se cambia esquema ni RLS como parte de este trabajo.
