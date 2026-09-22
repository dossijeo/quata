# RLS-005 — Integridad de follows y contadores

## Estado

Trabajo preparado y validado inicialmente en la rama
`codex/fix-community-follows-integrity`. Tras la reconciliación selectiva del
ledger, las dos unidades forward y sus rollbacks tienen versiones definitivas:

- `20260922202500_community_profile_follows_actor_guard.sql`;
- `20260922203500_community_profile_follow_counter_reconciliation.sql`.

No se han desplegado ni se ha ejecutado DML remoto.

Las plantillas validadas permanecen en `supabase/templates/` y un contrato exige
que los SQL versionados sean idénticos, salvo la sustitución del marcador de
reconciliación. Hay dos unidades independientes:

1. `community_profile_follows_actor_guard.sql.template`;
2. `community_profile_follow_counter_reconciliation.sql.template`.

Cada una tiene rollback propio y transacciones explícitas.

## Evidencia remota de solo lectura

`community_profile_follows` tiene RLS activado, pero cuatro policies públicas:
`allow all` y read/insert/delete con condiciones `true`. `anon` y
`authenticated` tienen todos los privilegios de tabla. No existe trigger de
actor.

El snapshot histórico tenía 107 aristas, 112 perfiles y 74 perfiles con drift.
La repetición read-only del 22 de septiembre observa 129 aristas, 178 perfiles y
86 perfiles con drift (76 en followers y 33 en following), sin self-follow. La
migración calcula todos los conteos y fingerprints en ejecución; no contiene
ninguna de esas cifras como precondición fija. No hay datos en la tabla legacy
`public.follows` según la auditoría histórica.

No hay trigger productor. Android crea/elimina aristas directamente. El detalle
de perfil deriva los tamaños de las listas, pero los directorios
Android/Web/iOS leen los caches a cero. El RPC
`recalculate_profile_follow_counts` existe y estaba ejecutable por
PUBLIC/anon/auth; no tiene call sites de cliente y queda reservado a
`service_role`. `toggle_follow_profile` usa el Auth UUID como profile UUID y
columnas legacy; queda deprecado y no ejecutable por clientes.

## Guard de actor

- SELECT público permanece idéntico.
- Sólo un actor autenticado y activo puede insertar una arista cuyo
  `follower_profile_id` sea su perfil canónico.
- El objetivo también debe estar activo.
- Sólo el dueño activo o un admin activo puede borrar; outsider falla con
  `42501`.
- UPDATE y toda mutación anónima quedan sin grant/policy.
- Un trigger `SECURITY INVOKER` replica la frontera fail-loud.
- `service_role` conserva mantenimiento y cascadas.

El contrato coincide con Android: `toggleProfileFollow` hace GET seguido de
INSERT/DELETE con el profile ID de la sesión. Web/iOS mantienen FollowUser
fail-closed.

La [referencia Android publicada v32](ANDROID_PUBLISHED_REFERENCE_V32.md)
acredita además en el mapping R8 del AAB exacto que ese recorrido obtiene el
actor de `AuthSession`, usa el access token Supabase en el header Bearer y llama
al POST/DELETE directo. La anon key sólo es fallback sin sesión, mientras que
`toggleFollowUser` exige sesión activa. Esto cierra la duda estática de
compatibilidad binaria; el recorrido Android autenticado del rollout sigue
siendo un gate de ejecución antes del despliegue.

El baseline previo al rollout también se ejecutó con el AAB exacto convertido
por bundletool en APK universal y re-firmado sólo para instalación local sobre
Android API 37. El cliente publicado inició sesión, abrió el perfil objetivo,
eliminó la arista mediante la UI y acreditó count backend cero; después volvió
a seguir desde la misma UI y restauró exactamente una arista. La proyección
redactada, sin IDs ni credenciales, está en
`docs/runbooks/migration/evidence/profile-follow-published-v32-baseline-20260922.json`.
Este pase conserva como pendiente únicamente la repetición post-rollout.

## Reconciliación reversible

La segunda plantilla:

1. adquiere advisory lock transaccional;
2. crea batch y snapshot de los contadores anteriores/derivados;
3. guarda profile/mismatch/edge counts y SHA-256 ordenados tanto del conjunto
   de perfiles como de las aristas;
4. instala un trigger `AFTER INSERT OR UPDATE OR DELETE` que recalcula ambos
   lados desde la tabla autoritativa, bloqueando antes los profile IDs en orden
   UUID para evitar lost updates y deadlocks recíprocos;
5. actualiza sólo perfiles con diferencias;
6. exige que rowcount actualizado=mismatch inicial, snapshot completo,
   fingerprints estables y cero mismatches antes de commit;
7. revoca el recálculo manual a PUBLIC/anon/auth.

El rollback toma primero locks `SHARE ROW EXCLUSIVE` sobre aristas y perfiles,
en el mismo orden arista→perfil que el productor, para excluir tráfico normal
antes de validar. Después se niega a restaurar si count/fingerprint de perfiles
o aristas cambió desde el snapshot o si los counters ya no son los derivados
guardados. Si siguen idénticos, restaura los valores anteriores, elimina el
productor y limpia las tablas de auditoría dentro de la misma transacción.

Tras el primer follow real se usa la plantilla forward-safe
`community_profile_follow_counter_producer_decommission.sql.template`: retira
el trigger sin restaurar caches ni borrar snapshot/índices. Su rollback
versionado bloquea temporalmente mutaciones sobre las aristas, reconcilia
ambos contadores desde la tabla autoritativa, exige cero diferencias y sólo
entonces vuelve a instalar el productor.

El marcador definitivo de la reconciliación es
`20260922203500_community_profile_follow_counter_reconciliation`; el contrato
falla si reaparece `__MIGRATION_VERSION__` en cualquier SQL promocionado.

## Evidencia aislada

`scripts/run-community-profile-follows-integrity-test.ps1` aplica y prueba en
PostgreSQL 16 desechable:

- lectura anónima;
- INSERT propio;
- rechazo de spoof, anon e inactivo;
- rechazo de DELETE ajeno;
- DELETE propio y admin;
- snapshot, rowcounts, backfill y producer trigger;
- recalculate RPC denegado a cliente y permitido a servicio;
- dos conexiones concurrentes para inserts/deletes recíprocos y target
  compartido, sin deadlock ni lost update;
- rollback real ralentizado bajo test, con una mutación concurrente que debe
  permanecer bloqueada hasta el commit antes de poder continuar;
- tráfico real durante el decommission, seguido de rollback con reconciliación
  y una nueva mutación mantenida por el productor reactivado;
- rollback de ambas unidades, reproducción controlada del fallo histórico y
  reaplicación segura;
- limpieza.

`scripts/run-community-profile-follows-postgrest-test.ps1` repite por HTTP real
PostgREST 12 actor/anon/admin/inactivo y convergencia de counters.

Resultados:

```text
COMMUNITY_PROFILE_FOLLOWS_INTEGRITY_TEST_OK
COMMUNITY_PROFILE_FOLLOWS_POSTGREST_TEST_OK
```

Los contenedores y redes se eliminan al terminar.

## Gates antes de promoción

- Conservar los timestamps asignados y comprobar que siguen posteriores al ledger remoto.
- Revisión independiente del SQL ya renombrado, sin placeholders.
- Aplicar guard antes de reconciliación.
- Confirmar preflight remoto de sólo lectura y fingerprints aprobados.
- Staging: PostgreSQL/PostgREST más Android API-37 autenticado, feed anónimo,
  toggle/untoggle y cache/realtime.
- No activar FollowUser Web/iOS hasta retirar su contención mediante evidencia
  específica.
- No mezclar este rollout con RLS-004 ni con 171003 mientras sus dependencias
  Android sigan bloqueadas.
