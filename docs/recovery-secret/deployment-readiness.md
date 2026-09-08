# ACCOUNT-RECOVERY-SECRET: dependencia de backend

Estado: propuesta para revisión; no autorizada ni aplicada. Auditoría 2026-09-08.
El salvage y su runner no acreditan aún el recorrido real.

## Estado observado

- Función `quata-auth-bridge` ACTIVE v21, actualizada 2026-07-25T19:53:31Z.
  Fuente descargada SHA-256 `d57969b79deca66926e6e78b575c556e2e2812d5d13dc4a85998f93428e0dc01`.
- Petición `{action: "update_recovery_secret", version: 1}` sin bearer ni identidad:
  HTTP 400 `password_required`; la acción no existe en esa fuente.
- `information_schema.columns` confirma `secret_question` y `secret_answer` de tipo
  text, y `auth_user_id` uuid. No existe `secret_answer_hash`.
- Supabase CLI enumera nombres de secretos; no existe `QUATA_WEB_REGISTRATION_PEPPER`.
  No se han leído ni exportado valores de secretos de despliegue.

## Cambio mínimo que requiere revisión y autorización separadas

1. Añadir una columna nullable, sin backfill, cambios de RLS ni de grants:

   ```sql
   begin;
   set local lock_timeout = '3s';
   alter table public.community_profiles add column secret_answer_hash text;
   commit;
   ```

   Antes de ejecutar, volver a comprobar ausencia/tipo: un cambio concurrente detiene
   la operación. No ejecutar la migración global `20260726171004_web_registration_contract.sql`:
   incluye ledger y precondiciones de registro ajenos a esta unidad.
2. Crear una vez el pepper de servidor con entropía criptográfica y protección privada.
   No sustituir un valor que haya aparecido concurrentemente, ni imprimirlo, guardarlo
   en evidencia pública o incorporarlo al cliente. Conservarlo para poder verificar
   todos los hashes producidos; no rotarlo como parte de cleanup.
3. Revisar un paquete focal basado en v21: escritura autenticada de los tres campos,
   lectura del hash para el consumidor y verificación de respuestas hashed conservando
   el consumidor legacy. Incluir el control de versión y fallo sin JWT válido antes
   del lookup público. Verificar que una identidad sin perfil no reciba falso éxito.
   Paquete de revisión preparado en el directorio ignorado
   `build-reports/account-recovery-secret-salvage/deployment-review`, con generador
   verificando el hash v21 y sin despliegue. El productor comprueba exactamente un
   actor antes de escribir, usa id y auth_user_id conjuntamente y exige una fila
   actualizada. La auditoría de datos encontró cero grupos auth_user_id duplicados.

No desplegar `main` entero para conseguir esa acción: frente a v21 también cambia
la aceptación de API keys, el hashing de contraseñas, el secreto de derivación Auth,
metadatos de sesión y cuarentena de registro. Esos cambios requieren su propia revisión.

## Restitución y compatibilidad

La v21 no puede recuperar perfiles que sólo contengan `secret_answer_hash`. Por ello
volver a desplegar v21 después de una escritura hashed no es un rollback funcional
seguro por sí solo. Antes de cualquier activación hay que decidir y ensayar el rollback
con perfiles legacy y hashed, además de verificar los clientes publicados realmente
identificados. La referencia histórica Android no prueba la identidad del APK publicado.

Durante la prueba focal, restaurar primero la contraseña mediante el consumidor que
entiende el secreto temporal; verificar login original; después restaurar los tres
campos del snapshot exacto. No eliminar la columna ni el pepper en cleanup. La vuelta
a v21 sólo puede considerarse si se demuestra que no quedan consumidores ni perfiles
dependientes del nuevo formato; de otro modo se necesita una corrección hacia delante.
No restaurar globalmente perfiles a partir de snapshots de cuentas de prueba.

## Stop conditions

No ejecutar el productor real mientras falte cualquiera de las tres precondiciones,
el journal privado probado o una estrategia de restitución compatible. No degradar el
helper para ignorar la columna ausente, ni escribir SQL como sustituto del productor.
Conservar ACCOUNT-DETAILS y el GO histórico de SCR-AUTH-RECOVERY. Esta auditoría no
cambia el inventario a GO ni autoriza despliegue, migraciones o activación.

## Paquete revisado y comprobaciones locales

SHA-256 de `supabase/functions/quata-auth-bridge/index.ts`:
`03776d1a70a9cf2299665e435b4a040db2c0abf3133f7e6f8fdc40c3bafc1729`.
SHA-256 de `supabase/config.toml`:
`d9327ada00ffcb375f48059f7150636f55d2f60f827145a07578d94c69233191`.
El manifest del paquete registra también el hash del módulo compartido copiado de main.
CLI confirma `verify_jwt=false` en v21; el paquete conserva esa configuración.

Nueve pruebas locales del handler real, con Auth/PostgREST simulados, pasan: versión,
JWT, actor ausente/duplicado, identidad impuesta por servidor, pérdida concurrente del
vínculo, flag ausente, pepper ausente/corto, API key, lectura permitida, consumidor legacy,
consumidor hashed y reversión compatible. No acreditan una operación real de Supabase.
Deno 2.9.6 empaqueta correctamente 45 módulos mediante:

```sh
deno bundle --no-config --no-lock --platform=deno --output auth-bridge-review.bundle.js supabase/functions/quata-auth-bridge/index.ts
```

El revisor independiente confirmó la coherencia del diff focal contra v21. El productor
está desactivado por defecto: sólo escribe con `QUATA_RECOVERY_SECRET_WRITE_ENABLED=true`.
La reversión compatible utiliza el mismo artefacto y pone ese flag a false; no elimina
el consumidor hashed, la columna ni el pepper.

Tras autorización explícita y revalidación de precondiciones, los comandos focales,
desde el directorio del paquete, serían:

```sh
supabase secrets set --project-ref yrrlankpwmhluexshxnw --env-file <archivo-privado-con-pepper-y-flag-false>
supabase functions deploy quata-auth-bridge --project-ref yrrlankpwmhluexshxnw --no-verify-jwt --use-api
supabase secrets set QUATA_RECOVERY_SECRET_WRITE_ENABLED=true --project-ref yrrlankpwmhluexshxnw
```

Reversión de activación:

```sh
supabase secrets set QUATA_RECOVERY_SECRET_WRITE_ENABLED=false --project-ref yrrlankpwmhluexshxnw
```

No se han ejecutado esos comandos. El journal privado DPAPI y la restitución del snapshot
ya tienen ensayo local con datos sintéticos y revisión independiente, incluida exclusión
entre reaperturas. Antes de solicitar autorización sigue pendiente integrarlos con el
registro previo de sesiones y mutaciones del runner, concretar los checks reales tras
despliegue/activación y resolver la compatibilidad con el cliente publicado. La aprobación
debe abarcar explícitamente columna, pepper, función y activación.
