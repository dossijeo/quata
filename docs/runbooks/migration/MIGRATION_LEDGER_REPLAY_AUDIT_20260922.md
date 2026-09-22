# Auditoría de replay del ledger — 22 de septiembre de 2026

## Resultado

La reconciliación sigue **abierta** y `supabase db push` sigue siendo inseguro.
El replay aislado y comparaciones focales read-only reducen de 29 a 13 las
decisiones históricas sin equivalencia semántica acreditada. Cuatro archivos
formados íntegramente por `CREATE OR REPLACE FUNCTION` y, cuando corresponde,
`GRANT`, quedan acreditados por replay:

- `20260629_0011_chat_demote_moderator.sql`;
- `20260630_0013_chat_conversation_candidates.sql`;
- `20260630_0014_chat_cleanup_empty_private_threads.sql`;
- `20260714_0005_chat_reply_snapshots.sql`.

En esos cuatro casos el replay dejó idénticos el digest completo del catálogo
auditado y el digest de todas las tablas y secuencias de aplicación. El catálogo
incluye firma, cuerpo, atributos, configuración y ACL de funciones. Como esos
archivos no contienen DDL condicional ni DML, el resultado acredita la semántica
versionada presente, sin inferirla a partir de un marcador parcial.

`20260628_0003_auth_bridge_support.sql` queda también como
`verified_applied_semantics`: su único `DO` crea cinco índices condicionales y
las cinco columnas existen en el remoto. El audit read-only comparó para cada
rama nombre, columna, predicado parcial y estado `valid/ready`; las cinco
definiciones son exactas. La evidencia reproducible está en
[`auth-bridge-semantics-20260922.json`](evidence/auth-bridge-semantics-20260922.json)
y no afirma ejecución histórica.

`20260709_0003_official_post_soft_delete_policy.sql` queda igualmente
acreditado. El audit remoto read-only comprobó una única política con comando
`SELECT`, modo permisivo, roles `anon` y `authenticated`, expresión `USING`
exacta y ausencia de `WITH CHECK`. En el replay aislado, `DROP POLICY` seguido
de `CREATE POLICY` cambió el OID del objeto y el orden interno del array de OID
de roles, pero no el conjunto de roles, la definición normalizada ni ningún
digest de datos. El orden de ese array no altera la pertenencia de roles. La
evidencia reproducible está en
[`official-soft-delete-policy-semantics-20260922.json`](evidence/official-soft-delete-policy-semantics-20260922.json).

La decisión separada `20260808_0001_official_posts_actor_guard.sql` queda
reconciliada sin inventar una fila de ledger. PR #195 registró la aplicación
manual del SQL versionado exacto. El replay directo confirma que el archivo no
es idempotente porque vuelve a crear tres políticas con sus nombres actuales;
en una restauración aislada se retiraron sólo esas tres políticas y las 25
sentencias reprodujeron funciones, ACL, RLS y políticas exactas sin cambiar
ningún digest de datos. Un audit remoto read-only confirmó los mismos efectos.
Evidencia:
[`official-actor-guard-semantics-20260922.json`](evidence/official-actor-guard-semantics-20260922.json).

`20260702_0004_official_read_more_label.sql` queda también reconciliado. Su
replay fue un no-op completo de catálogo y datos, y el audit remoto read-only
comprobó la única columna con tipo `text`, `NOT NULL`, sin identidad ni
generación, colación por defecto y comentario exacto. El default original
`Leer mas` es el único efecto sustituido: la sentencia 3 exacta de
`20260709_0002_official_post_languages.sql` lo cambia a `read_more`, que es el
valor observado. Esa migración posterior conserva abiertos sus demás efectos.
Evidencia:
[`official-read-more-label-semantics-20260922.json`](evidence/official-read-more-label-semantics-20260922.json).

`20260703_0001_admin_delete_posts.sql` queda reconciliado por efecto. La
política admin DELETE de Community permanece exacta; las dos políticas antiguas
de Official están ausentes porque las sentencias 17/18 y 20/21 exactas del actor
guard las retiran y sustituyen. El replay antiguo cambia catálogo al recrearlas,
pero no datos. El audit conserva además la divergencia separada de Community:
dos políticas DELETE adicionales aplican a PUBLIC y `anon` tiene DELETE de
tabla, por lo que esta decisión no afirma autorización efectiva owner-only.
Evidencia:
[`admin-delete-posts-semantics-20260922.json`](evidence/admin-delete-posts-semantics-20260922.json).

La única sentencia de `20260629_0009_chat_push_pg_net_body.sql` queda ligada a
su cadena de sustitución versionada. El cuerpo final de
`quata_enqueue_chat_push()` procede de la sentencia 5 de
`20260714_0002_chat_push_reliability.sql`; su canonicalización en PostgreSQL 17
produce el mismo hash que la función remota, con firma, retorno, lenguaje,
atributos y `search_path` exactos. No se invocaron pg_net, Vault ni el proveedor.
Evidencia:
[`chat-push-function-supersession-20260922.json`](evidence/chat-push-function-supersession-20260922.json).

Las dos sentencias de `20260628_0005_chat_open_community_thread.sql` quedan
ligadas a las sentencias 2 y 4 exactas de su sucesor Community. La definición
canonicalizada de la función sucesora coincide con el remoto en cuerpo, firma,
retorno, atributos y `search_path`; los grants a `anon` y `authenticated`
también están presentes. El helper y el DML de backfill del archivo sucesor
siguen abiertos. Evidencia:
[`chat-open-community-thread-supersession-20260922.json`](evidence/chat-open-community-thread-supersession-20260922.json).

Las dos sentencias de `20260628_0006_chat_shared_attachment_sender.sql` quedan
ligadas a la sentencia 30 exacta del sucesor de estado de conversación y al ACL
remoto preservado por `CREATE OR REPLACE FUNCTION`. La definición
canonicalizada coincide con el remoto y conserva `EXECUTE` para `anon` y
`authenticated`. Las otras 33 sentencias del sucesor siguen abiertas. Evidencia:
[`chat-shared-attachment-sender-supersession-20260922.json`](evidence/chat-shared-attachment-sender-supersession-20260922.json).

Las cinco sentencias de `20260629_0010_push_token_disable_invalid.sql` quedan
ligadas a las dos columnas y el índice parcial remotos, y a las sentencias 1–3
exactas del sucesor multidevice. La función canonicalizada coincide con el
remoto; PUBLIC carece de `EXECUTE` y `authenticated` lo conserva. Los grants
directos adicionales a `anon` y `service_role` quedan registrados, sin
atribuirlos a la migración fuente. Las otras 15 sentencias del sucesor siguen
abiertas. Evidencia:
[`push-token-disable-invalid-supersession-20260922.json`](evidence/push-token-disable-invalid-supersession-20260922.json).

Las ocho sentencias de `20260630_0012_chat_message_idempotency.sql` quedan
ligadas a la columna y el índice parcial únicos remotos, a tres definiciones
canonicalizadas exactas, a los grants requeridos y a la ausencia de la firma
antigua. Los cuerpos posteriores son las sentencias 24 y 1 exactas de sus
respectivos sucesores; `send_files` conserva el cuerpo fuente. Los demás efectos
del sucesor de estado de conversación siguen abiertos. Evidencia:
[`chat-message-idempotency-supersession-20260922.json`](evidence/chat-message-idempotency-supersession-20260922.json).

Las cinco sentencias de `20260701_0001_chat_push_attachment_trigger.sql`
quedan ligadas a la función sucesora final y a los dos triggers remotos exactos,
habilitados sobre `chat_messages` y `chat_attachments`. La canonicalización de
la función se reutiliza de su evidencia focal ya revisada; no se invocaron
pg_net, Vault ni el proveedor. Los demás efectos del sucesor de fiabilidad push
siguen abiertos. Evidencia:
[`chat-push-attachment-trigger-supersession-20260922.json`](evidence/chat-push-attachment-trigger-supersession-20260922.json).

Las cinco sentencias de `20260714_0002_chat_push_reliability.sql` quedan
ligadas a dos definiciones remotas exactas y al ACL directo de desregistro:
PUBLIC y `anon` carecen de `EXECUTE`, mientras `authenticated` lo conserva. El
grant adicional a `service_role` queda registrado como estado actual. No se
consultaron valores de Vault ni se invocaron pg_net o el proveedor. Evidencia:
[`chat-push-reliability-20260922.json`](evidence/chat-push-reliability-20260922.json).

Las 25 sentencias duraderas de `20260716_0001_ugc_moderation.sql` quedan
ligadas a metadatos estructurados completos: dos tablas con RLS, 14 columnas,
13 restricciones, cinco índices, dos políticas, cinco funciones
canonicalizadas y sus ACL fuente. `BEGIN` y `COMMIT` son control transaccional.
Los grants directos adicionales a `anon` y `service_role` quedan registrados
sin atribuirlos a la fuente. Evidencia:
[`ugc-moderation-semantics-20260922.json`](evidence/ugc-moderation-semantics-20260922.json).

## Método aislado

Se restauró el backup lógico de aplicación del 22 de septiembre en
`public.ecr.aws/supabase/postgres:17.6.1.063`: 87 tablas y 52.211 filas. El
ensayo no abrió una conexión de escritura a Supabase.

[`db-historical-migration-replay-digest.sql`](../../../scripts/db-historical-migration-replay-digest.sql)
calcula dos hashes sin emitir valores de negocio:

1. catálogo de `public`, `storage` y `security_citizen`: relaciones, columnas,
   restricciones, índices, triggers, políticas, funciones, tipos y ACL;
2. contenido de todas las tablas y secuencias de `public` y
   `security_citizen`, agregando únicamente hashes por fila.

Cada archivo transaccional se ejecutó entre un digest previo y otro posterior y
se revirtió. `20260716_0001_ugc_moderation.sql`, que contiene su propio
`COMMIT`, se ejecutó al final en una restauración desechable. El actor guard de
Official se ensayó en otra restauración limpia. El informe durable, con hashes
del restore, del helper y el resultado por archivo, es
[`migration-ledger-replay-20260922.json`](evidence/migration-ledger-replay-20260922.json).

## Hallazgos conservadores

- Dieciocho archivos pendientes modificarían catálogo, datos o ambos si se repitieran
  sobre el snapshot actual. Son evidencia directa contra un `db push` completo.
- `20260628_0004_chat_android_runtime_support.sql` no puede completarse porque
  el backup de aplicación excluye `storage.objects`.
- `20260702_0003_official_accounts.sql` depende de filas de Auth que el backup
  de aplicación excluye.
- Otros tres archivos no cambiaron los digests, pero usan `IF NOT EXISTS`,
  DDL condicional o DML histórico. Un no-op no demuestra que la definición
  existente sea la misma que la definición omitida; permanecen sin verificar.
- El replay directo de `20260808_0001_official_posts_actor_guard.sql` conserva
  su conflicto no idempotente como evidencia; la reconciliación usa además el
  replay controlado, el recibo de PR #195 y el audit remoto completo.

Por tanto, `selectivePackageEligible` continúa en `false`: faltan 13 decisiones
y la superficie Community PUBLIC DELETE continúa documentada como divergencia
separada. Esta auditoría no amplía ninguna excepción de gobernanza, no
autoriza RLS-003/RLS-004 y no sustituye backup administrado o PITR.
