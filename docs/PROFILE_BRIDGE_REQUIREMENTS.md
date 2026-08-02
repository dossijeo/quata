# Cuenta: acceso vigente y contrato futuro de bridge

## Estado temporal compatible con Android

Android, Web e iOS usan actualmente acceso autenticado directo a PostgREST para guardar el perfil
y sustituir `community_emergency_contacts`. Web e iOS no simulan un bridge inexistente ni fallan
con `*_profile_bridge_unavailable`: replican el acceso que Android tiene en producción.

Los clientes portables aplican estas defensas antes de mutar:

- obtienen el perfil actor de la sesión renovada y rechazan un `profileId` distinto;
- envían el bearer de esa sesión en cada petición;
- el PATCH de perfil usa una lista cerrada de columnas;
- SOS elimina sólo las filas del actor y publica como máximo cinco filas ordenadas;
- la caché local se actualiza únicamente después de que termine toda la operación remota.

Esto es compatibilidad temporal, no una frontera de autorización suficiente. Las políticas RLS
actuales siguen siendo deuda conocida (véase `docs/RLS_FINDINGS.md`) y no se modifican en esta
migración para no romper Android ni la web publicada. Además, el DELETE seguido de POST de SOS no
es transaccional: si POST falla, el servidor puede quedar sin los contactos anteriores, aunque el
cliente conserve su caché. Esta deuda debe resolverse de forma coordinada.

La contraseña no se edita desde Cuenta mientras no exista un contrato seguro y verificable. La UI
lo explica sin impedir guardar los demás campos. La pregunta de recuperación usa el bridge de Auth
ya existente; Web e iOS sólo consideran éxito una respuesta `2xx` cuyo cuerpo sea `{ "ok": true }`.
La baja y eliminación de cuenta en iOS usan la acción autenticada existente
`quata-account-lifecycle`. Los avatares siguen la convención Android:
`community-posts/avatars/<profileId>/<uuid>.jpg`, con bearer y `x-upsert: true`.

## Contrato objetivo `quata-profile-bridge`

El reemplazo futuro del acceso directo debe:

- recibir `Authorization: Bearer <access token>` y verificarlo en servidor;
- ignorar cualquier actor enviado por el cliente y derivarlo sólo del usuario autenticado;
- usar credenciales privilegiadas únicamente después de resolver el actor;
- aceptar en `update_profile` sólo nombre, barrio, prefijo/teléfono y avatar;
- sustituir de cero a cinco contactos distintos, existentes y ajenos al propio actor en una
  transacción, sin dejar vacío el conjunto si falla la inserción;
- actualizar contraseñas exclusivamente mediante Auth, sin persistirlas en perfiles, logs o
  preferencias;
- devolver el estado persistido o un error explícito, sin éxito optimista.

Antes de migrar los clientes al bridge se requieren pruebas de bearer inválido, actor ajeno,
contactos inválidos, rollback, persistencia real y observabilidad sin secretos. Su despliegue debe
coordinarse con el endurecimiento de RLS; este trabajo no cambia esquema, grants, datos ni políticas.
