# Activación compatible de ACCOUNT-RECOVERY-SECRET

Estado: preparada y revisada localmente; **no desplegada ni autorizada**.
Sustituye la propuesta hashed retirada en `deployment-readiness.md`.

Destino: proyecto Supabase `yrrlankpwmhluexshxnw`, función `quata-auth-bridge`.
Base desplegada v21 SHA-256
`d57969b79deca66926e6e78b575c556e2e2812d5d13dc4a85998f93428e0dc01`.
Paquete local: `build-reports/account-recovery-secret-salvage/deployment-review-compatible`.
SHA-256 de la función propuesta:
`4da3929f0fe4a63b4773402926e9848d8c3fd9509e53f4672905da4b2de0aa6f`.
SHA-256 del config:
`d9327ada00ffcb375f48059f7150636f55d2f60f827145a07578d94c69233191`.

El diff añade sólo el productor autenticado `update_recovery_secret`, versión 1:
valida JWT y campos, resuelve exactamente un perfil por auth_user_id y actualiza
pregunta/respuesta sobre ese id y usuario, comprobando una fila. Respuesta pública
sin secreto. Mantiene consumidor, login, formato de contraseña y admisión de claves
de v21. La escritura está desactivada por defecto. Conserva la deuda de almacenamiento
legacy necesaria para el AAB v32 publicado; no acredita confidencialidad SQL.

No requiere columna, pepper, cambio de RLS, grants, registro ni otros endpoints.
Revisión independiente aprobada. Siete pruebas locales del handler con servicios
simulados pasan. Deno 2.9.6 empaqueta 44 módulos. No es evidencia de backend real.

## Secuencia tras autorización

1. Revalidar v21, hashes, configuración `verify_jwt=false`, columnas legacy y vínculos
   de actor sin duplicados. Si cambió el despliegue o esquema, detener y revisar.
2. Verificar inmediatamente SHA-256 de index.ts y config.toml del directorio exacto
   indicado arriba; abortar ante cualquier diferencia. Fijar escritura desactivada y
   desplegar exclusivamente ese paquete mediante workdir explícito (nunca desde main):

   ```sh
   supabase secrets set QUATA_RECOVERY_SECRET_WRITE_ENABLED=false --project-ref yrrlankpwmhluexshxnw
   supabase functions deploy quata-auth-bridge --project-ref yrrlankpwmhluexshxnw --no-verify-jwt --use-api --workdir "C:/Users/PC/StudioProjects/quata-account-recovery-secret-salvage/build-reports/account-recovery-secret-salvage/deployment-review-compatible"
   ```

3. Comprobar contrato sin sesión (401), rechazo con escritura desactivada (503) y
   lectura pública permitida sin respuesta. Registrar sesiones de comprobación y cleanup.
4. Sólo cuando el runner tenga journal privado persistido del actor autorizado y registro
   previo de sesiones/mutaciones, activar para ejecutar productor real y recuperación:

   ```sh
   supabase secrets set QUATA_RECOVERY_SECRET_WRITE_ENABLED=true --project-ref yrrlankpwmhluexshxnw
   ```

5. Restaurar contraseña original mediante recuperación autorizada; demostrar login;
   después restaurar snapshot explícito `legacy-v32` (secret_question, secret_answer),
   revocar sesiones propias y verificar cleanup antes de retirar journal. Nunca sustituir
   el productor por SQL. El helper soporta ese formato explícito, sin autodetección ni
   fallback ante columna ausente; sus pruebas pasan.

## Reversión

```sh
supabase secrets set QUATA_RECOVERY_SECRET_WRITE_ENABLED=false --project-ref yrrlankpwmhluexshxnw
```

Se conserva el consumidor. Si se necesita restaurar el artefacto v21 verificado,
el formato almacenado sigue siendo compatible; antes se restituyen los cambios propios
del ensayo y se comprueba que no haya un despliegue concurrente. No se sobrescriben datos
de usuarios ajenos ni se elimina ningún campo. Sólo tras verificar nuevamente el SHA-256
v21 se podría restaurar con:

```sh
supabase functions deploy quata-auth-bridge --project-ref yrrlankpwmhluexshxnw --no-verify-jwt --use-api --workdir "C:/Users/PC/StudioProjects/quata-account-recovery-secret-salvage/build-reports/account-recovery-secret-salvage/deployed-v21"
```

La autorización debe cubrir la función
y el flag de activación, además del alcance de ensayo ya autorizado; el operating model
§3 exige aprobación explícita para operaciones aditivas revisadas en Supabase.
