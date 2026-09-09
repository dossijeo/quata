# Paquete compatible de recuperación

Este workdir contiene el código exacto usado en los recorridos locales del candidato
Android, Web e iOS. La compatibilidad con Android publicado v32 se verificó mediante
análisis de su consumidor; no se presenta como un E2E del AAB publicado.
`manifest.json` identifica fuente, configuración y consumidor v21.
La función mantiene el formato histórico de pregunta/respuesta y añade el productor
autenticado `update_recovery_secret` v1. No requiere migraciones de esquema ni RLS.

Desde la raíz del repositorio, con Node 22.14 o posterior:

```sh
node --test scripts/recovery-compatible-auth-bridge.test.mjs
```

La comparación del consumidor usa el digest de la función `handlePasswordReset`
extraído de la fuente v21 cuyo hash completo consta en el manifest. Los contratos
ejecutan el handler del paquete con servicios simulados; la evidencia real y sus
límites están en `docs/recovery-secret/salvage-and-runner-design.md`.

El despliegue dirigido, tras verificar compatibilidad actual y comunicar su resumen,
usa exclusivamente este workdir y esta función:

```sh
supabase functions deploy quata-auth-bridge --project-ref yrrlankpwmhluexshxnw --use-api --workdir supabase/deployment-packages/account-recovery-secret-v21
```

La configuración conserva `verify_jwt=false`; el handler verifica el bearer del
productor. La escritura exige el secreto `QUATA_RECOVERY_SECRET_WRITE_ENABLED=true`.
Su ausencia o valor `false` devuelve 503 para el productor y conserva recuperación
histórica. La reversión operativa del productor fija ese secreto en `false`.
La ruta habitual `supabase/functions` tiene un contrato diferente y no es el workdir
autorizado para desplegar este paquete compatible. Una nueva revisión exige comprobar
el diff real contra la fuente remota antes de desplegar o activar.
