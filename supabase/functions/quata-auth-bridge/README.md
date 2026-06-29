# quata-auth-bridge

Edge Function para que la app legacy pueda obtener una sesion real de Supabase Auth sin exponer `service_role` en Android.

## Que Hace

1. Recibe telefono/perfil y password legacy.
2. Busca el perfil en `public.community_profiles`.
3. Valida `pass_hash` o `pass_plain`.
4. Crea o actualiza el usuario en Supabase Auth.
5. Rellena `community_profiles.auth_user_id`.
6. Devuelve `session.access_token` y `session.refresh_token`.

Con ese `access_token`, la app puede llamar REST/RPC y Realtime como usuario `authenticated`.

## Variables

Supabase inyecta `SUPABASE_URL` y, segun la generacion del proyecto, claves en formato legacy o diccionario:

- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `SUPABASE_ANON_KEY`
- `SUPABASE_SECRET_KEYS`
- `SUPABASE_PUBLISHABLE_KEYS`

La funcion intenta leer ambos formatos. Si tu app usa una publishable key concreta (`sb_publishable_...`) y quieres fijarla explicitamente, define tambien:

```bash
supabase secrets set QUATA_SUPABASE_PUBLIC_KEY="sb_publishable_..."
```

Opcional pero recomendado:

```bash
supabase secrets set QUATA_AUTH_BRIDGE_API_KEY="sb_publishable_..."
```

Si `QUATA_AUTH_BRIDGE_API_KEY` existe, la funcion exigira que el request envie esa key en `apikey`, `Authorization: Bearer ...` o `x-quata-api-key`.

## Deploy

La funcion debe poder ejecutarse antes de que el usuario tenga JWT, asi que se despliega sin verificacion JWT de Supabase. Esto queda fijado en `supabase/config.toml`; tambien puedes pasar el flag explicitamente:

```bash
supabase functions deploy quata-auth-bridge --no-verify-jwt
```

## Request

```json
{
  "country_code": "34",
  "phone": "600000000",
  "password": "secret"
}
```

Tambien acepta:

```json
{
  "profile_id": "PROFILE_UUID",
  "password": "secret"
}
```

## Response

```json
{
  "profile": {
    "id": "PROFILE_UUID",
    "auth_user_id": "AUTH_USER_UUID",
    "display_name": "Juan"
  },
  "session": {
    "access_token": "...",
    "refresh_token": "...",
    "expires_at": 1782659999
  },
  "user": {
    "id": "AUTH_USER_UUID"
  }
}
```

## Android

La app debe guardar `access_token`, `refresh_token` y `expires_at`.

Para REST/RPC:

```http
apikey: <publishable-or-anon-key>
Authorization: Bearer <access_token>
```

Para Realtime, enviar el mismo `access_token` antes de suscribirse o al renovar token.

## Nota De Seguridad

Esto mantiene compatibilidad legacy, pero mueve la validacion de password al servidor. Cuando se elimine `pass_plain`, bastara con dejar `pass_hash`.
