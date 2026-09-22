# Resultado de la auditoría del historial — 14 de septiembre de 2026

**La reconciliación no está cerrada.** El catálogo permite comparar efectos
actuales, pero no acredita la ejecución de los cambios históricos de datos.
La auditoría original no cambió el gate, las clasificaciones, el ledger ni las
autorizaciones.

Actualización del 22 de septiembre: un replay sobre restore completo de
aplicación acreditó equivalencia semántica para cuatro archivos compuestos sólo
por funciones y grants; audits read-only posteriores acreditaron las cinco ramas
condicionales exactas de `20260628_0003_auth_bridge_support.sql` y la política
normalizada completa de `20260709_0003_official_post_soft_delete_policy.sql`.
Auditorías focales posteriores acreditaron actor guard, read-more label, las
políticas admin, una función push, el opener Community, el listado de adjuntos,
la desactivación de tokens obsoletos y la idempotencia de mensajes con sus
sustituciones versionadas. Quedan 16 decisiones sin cerrar y el gate continúa
bloqueado. La evidencia y sus límites están en
[la auditoría de replay](MIGRATION_LEDGER_REPLAY_AUDIT_20260922.md); esta
reducción no reinterpreta los hallazgos originales sobre DML, UGC, DDL
condicional o dependencias Auth/Storage.

La decisión adicional `20260808_0001_official_posts_actor_guard.sql` ya no está
ausente: PR #195 documenta su aplicación manual exacta y la auditoría del 22 de
septiembre liga ese recibo a un replay controlado y a la semántica remota
completa. No se crea una fila retroactiva del ledger.

La migración focal `20260702_0004_official_read_more_label.sql` queda asimismo
acreditada: el replay no cambió catálogo ni datos, la columna y su comentario
coinciden exactamente y el único cambio posterior del default está ligado a la
sentencia 3 versionada de `20260709_0002_official_post_languages.sql`. Las 22
decisiones que quedaban en ese punto se reducen después a 21 mediante la
decisión focal de políticas admin descrita en la auditoría de replay.

`20260703_0001_admin_delete_posts.sql` queda acreditada por la política
Community exacta y la sustitución versionada exacta de sus dos políticas
Official. Esta decisión conserva, sin absorberla, la divergencia separada de
dos políticas Community DELETE para PUBLIC y el privilegio DELETE de `anon`;
no afirma autorización efectiva owner-only.

La sentencia única de `20260629_0009_chat_push_pg_net_body.sql` queda ligada a
dos sucesores versionados y a la definición remota canonicalizada exacta. Los
demás efectos de esos archivos posteriores permanecen abiertos.

La función y el grant de `20260628_0005_chat_open_community_thread.sql` quedan
ligados a sus sucesores exactos; el helper y el backfill DML posteriores no se
incluyen en esa decisión.

La función y el grant de `20260628_0006_chat_shared_attachment_sender.sql`
quedan ligados al cuerpo sucesor exacto y al ACL remoto preservado; los demás
efectos de la migración de estado de conversación no se incluyen.

Las columnas, el índice, la función y el ACL fuente de
`20260629_0010_push_token_disable_invalid.sql` quedan ligados al catálogo actual
y a las tres sentencias sucesoras exactas; el resto del sucesor multidevice no
se incluye.

Los ocho efectos de `20260630_0012_chat_message_idempotency.sql` quedan ligados
al catálogo actual y a las definiciones sucesoras exactas; los demás efectos del
sucesor de estado de conversación no se incluyen.

## Qué aporta la comparación

Se inventariaron 38 archivos y 617 sentencias. Las comparaciones focales de
funciones, columnas, restricciones, índices, triggers, políticas y grants fueron
revisadas independientemente. Sus informes conservan límites explícitos: una
coincidencia parcial no se convierte en equivalencia de toda la migración.

Los informes locales están en `build-reports/migration-ledger/`; los archivos
`*-audit-conclusion.json` enlazan evidencia mediante hashes cuando corresponde.
No se ejecutaron escrituras sobre datos de negocio ni se reparó el ledger.

## Diferencias que impiden acreditar equivalencia

- [Funciones](MIGRATION_LEDGER_FUNCTION_FINDINGS_20260914.md): orden de selección
  de mensajes, normalización de acentos y conservación de identidad durante la
  desactivación tienen diferencias sin procedencia versionada acreditada.
- UGC: `ugc_reports_status_check` admite `removed` en el remoto y `actioned` en
  `20260716_0001`. Una prueba aislada confirma aceptación/rechazo distintos.
  No restaurar el CHECK antiguo como reparación automática: podría rechazar
  estados existentes. Evidencia: `check-constraint-audit-conclusion.json`.
- Los paquetes pendientes de Likes, Profiles, registro Web y comentarios
  oficiales siguen sin reflejarse íntegramente en el catálogo. Profiles conserva
  inserción con `WITH CHECK true`, grants amplios y un trigger sólo de UPDATE.
  Likes/Comments conservan guards SECURITY DEFINER y RLS deshabilitado.
- Hay políticas adicionales en tablas revisadas. La comparación de las políticas
  versionadas no acredita por sí sola el acceso efectivo ni la seguridad del
  conjunto. No se ensayaron operaciones destructivas ni explotación HTTP.

## Límite de los cambios históricos de datos

Se identificaron 13 sentencias DML, un ANALYZE y un refresco de datos dentro de
DO. Incluyen roles, membresías, estados de conversación, tokens y directorio de
contactos. El estado presente no prueba que se ejecutaran en el pasado.

Repetirlos puede revertir decisiones posteriores: el upsert del creador reactiva
membresías y puede promover `member` a `owner`; el siguiente upsert depende de la
normalización divergente. La restauración posterior de tokens sólo selecciona
un motivo de retirada concreto y no es la inversa exacta del cambio anterior.
Evidencia corregida: `historical-data-effects-triage-v2.json`.

## Consecuencia para APNs

No usar estos resultados para marcar el backlog como aplicado ni habilitar
`supabase db push`. La excepción del [lote RLS histórico](../../DB_RELEASE_001_002_RUNBOOK.md)
tiene alcance propio y no se extiende a APNs.

La preparación de APNs debe conservar su paquete aditivo exacto, compatibilidad,
preflight y recuperación revisados. Su vía de despliegue debe resolver el gate
de historial conforme a las autorizaciones aplicables, sin presentar como prueba
de ejecución lo que sólo es evidencia de catálogo. Esta auditoría no autoriza
una excepción nueva ni modifica el comportamiento del gate.
