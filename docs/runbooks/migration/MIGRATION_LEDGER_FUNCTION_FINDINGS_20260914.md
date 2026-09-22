# Comparación focal de funciones — 14 de septiembre de 2026

Estado: auditoría parcial; no acredita ejecución histórica ni autoriza despliegues.
El gate de reconciliación permanece sin cambios. Se comparó SQL versionado con
catálogo leído en una transacción de solo lectura, sin consultar filas de negocio.

## Diferencias con impacto semántico

| Función | Catálogo observado frente al SQL versionado | Destino de la revisión |
| --- | --- | --- |
| `quata_chat_get_thread(uuid,bigint,bigint[],integer)` | Cuerpo de conversación por usuario con selección `ASC` antes de `LIMIT`, frente a `DESC` en `20260714_0001`. La prueba focal de tres mensajes y límite dos confirmó que el remoto devuelve la página más antigua. | `20260922173500_chat_get_thread_latest_page.sql` restaura como candidata la definición versionada que selecciona la página más reciente y la devuelve en orden cronológico. La candidata y su rollback no están desplegados; la decisión histórica sigue abierta. Evidencia: `evidence/chat-get-thread-pagination-20260922.json`. |
| `quata_chat_community_key(text)` | El literal de caracteres acentuados contiene 50 signos `?` en el remoto; la evaluación focal de un literal sintético acentuado devuelve `null`. El audit también observa 1 incumplimiento del backfill de propietarios y 3 del de miembros bajo la función actual. | `20260922174500_chat_community_members_repair.sql` restaura como candidata la transliteración versionada y repite los dos backfills idempotentes originales. No está desplegada y la decisión histórica sigue abierta. Evidencia: `evidence/chat-community-members-repair-20260922.json`. |
| `quata_account_deactivate(uuid,uuid)` | El remoto conserva `deactivated_auth_user_id = p_auth_user_id`, ausente del cuerpo de `20260721_0001`; la columna y la ACL exclusiva de `service_role` están presentes. | `20260922175500_account_deactivation_auth_link.sql` versiona como sucesora exacta la definición remota y su ACL sin cambiar el comportamiento desplegado. La auditoría exhaustiva de las 18 sentencias fuente confirma que esta es la única divergencia; la sucesora no está desplegada y la decisión histórica sigue abierta. Evidencia: `evidence/account-lifecycle-semantics-20260922.json` y `evidence/account-deactivation-auth-link-20260922.json`. |

Las tres diferencias tienen ahora una sucesora versionada candidata, pero ninguna
está desplegada. El paquete completo `20260714_0001_chat_conversation_user_state.sql`
también quedó auditado: los otros 14 cuerpos de función coinciden exactamente, el
catálogo de tabla, columnas, constraints, índices, política y triggers está presente,
y no faltan estados para participantes. El remoto vivo conserva 75 estados con límite
de visibilidad inicial ausente; el replay aislado sobre el snapshot restaurado encontró
76 y demostró que repetir toda la migración reescribiría `updated_at` en las 618 filas.
La candidata `20260922180500_conversation_state_visibility_backfill.sql` limita el
cambio a esas 76 filas del snapshot y deja cero límites ausentes, sin modificar otras
columnas. Las cifras viva y restaurada se conservan separadas porque se midieron en
momentos distintos. Evidencia: `evidence/conversation-user-state-semantics-20260922.json`.

No se han cambiado funciones ni ejecutado RPC mutantes para investigar estas
diferencias en el remoto.

El paquete base `20260628_0002_chat_rpc.sql` contiene 31 funciones y 31 grants.
El replay aislado confirma 19 cuerpos fuente exactos y 12 sustituidos por cuerpos
versionados posteriores. La cadena versionada más reciente coincide con 30 de las
31 funciones remotas; la única diferencia restante es `quata_chat_get_thread`, ya
vinculada a la candidata de paginación anterior. Los 31 permisos efectivos para
`anon` y `authenticated` están presentes. La decisión histórica continúa abierta
hasta desplegar y verificar esa sucesora. Evidencia:
`evidence/chat-rpc-semantics-20260922.json`.

`20260714_0003_chat_private_thread_membership.sql` tiene función y constraint
trigger exactos, 152 mappings actuales y cero pares inválidos tanto en el remoto
vivo como en el snapshot restaurado. Su replay cambia cero threads y elimina cero
mappings. Esto acredita el estado actual, pero no permite reconstruir mappings que
pudieran haberse borrado históricamente. La candidata
`20260922185000_chat_private_thread_membership_reconciliation.sql` repite de forma
explícita las cinco sentencias originales; su rollback exige restore point si el
saneamiento llegara a eliminar filas. Evidencia:
`evidence/private-thread-membership-reconciliation-20260922.json`.

`20260721_0001_account_lifecycle.sql` quedó cubierto sentencia por sentencia: dos
`ALTER TABLE`, el bloque de constraint, el índice, la tabla de solicitudes, ocho
grants y cinco funciones. El catálogo remoto conserva columnas, constraints, índice,
RLS y ACL esperados; cuatro de las cinco funciones coinciden exactamente con el
replay aislado de la fuente. La única diferencia es
`quata_account_deactivate(uuid,uuid)`, enlazada a la sucesora Auth-link anterior.
La fuente no contiene DML y el replay transaccional no cambió los 178 perfiles ni
las cero solicitudes observadas en el snapshot. Esto completa la cobertura
semántica, pero no declara aplicada la sucesora ni habilita por sí solo el paquete
selectivo. Evidencia: `evidence/account-lifecycle-semantics-20260922.json`.

Las cinco decisiones abiertas pasan a `approved_ledger_reconciliation` sólo para
preparar el paquete selectivo. Cada decisión enlaza su evidencia exhaustiva y
enumera `requiredPackageMigrations`; la preparación falla si falta cualquiera de
esas sucesoras. Esta clasificación no significa que el SQL histórico o las
reparaciones se hayan aplicado, y las evidencias anteriores conservan ese límite.
El manifiesto del paquete mantiene `deploymentAuthorized=false` hasta completar
backup, dry-run, revisión y ejecución del release.

Actualización del 22 de septiembre: el paquete selectivo exacto fue aplicado en
una transacción y el postflight independiente confirmó sus cinco filas de ledger,
los hashes de las funciones de desactivación y membresía privada, el ACL exclusivo
de `service_role`, el trigger de membresía, ambos órdenes de paginación, la
transliteración acentuada y cero incumplimientos en los cuatro conteos de datos.
Las cinco decisiones pasan por ello a `verified_applied_semantics`, enlazadas a la
evidencia histórica original y a
`evidence/selective-db-release-postflight-20260922.json`. La limitación sobre
mappings privados borrados históricamente se conserva: el cierre acredita el
estado actual y la reconciliación desplegada, no reconstruye historia eliminada.

## Paquetes pendientes, no equivalencias acreditadas

- Profiles conserva el cuerpo histórico de `quata_guard_profile_roles` y
  `SECURITY DEFINER`; no refleja el endurecimiento `20260726171003`.
  [RLS_FINDINGS](../../RLS_FINDINGS.md) documenta la preparación y dependencia de
  compatibilidad con registro/recuperación.
- Likes no tiene `quata_official_like_delete_allowed`; las cuatro funciones de
  registro Web (`quata_claim_web_registration`, `quata_web_registration_auth_user`,
  `quata_claim_web_registration_cleanup`, `quata_finish_web_registration_cleanup`)
  tampoco aparecen. [DB_RELEASE_001_002_RUNBOOK](../../DB_RELEASE_001_002_RUNBOOK.md)
  y [el snapshot de julio](../../DB_RELEASE_SNAPSHOT_2026-07-26.md) describen el
  corte pendiente y la exclusión de Profiles/Web.
- Comentarios conserva el guard histórico con `SECURITY DEFINER` y carece de
  `quata_official_comment_mutation_allowed`; los objetos no reflejan el paquete
  `20260727120001`. [SEC_MUTATIONS_001_AUDIT](../../SEC_MUTATIONS_001_AUDIT.md)
  documenta el problema original.

Estas ausencias son coherentes con paquetes pendientes; no prueban que jamás se
aplicaran o que no fueran revertidos. Su revisión debe incluir políticas, triggers,
grants, tablas, compatibilidad y rollback antes de cualquier despliegue.

## Evidencia y límites

El catálogo local ignorado `build-reports/migration-ledger/function-catalog.json`
se obtuvo a las `2026-09-14T13:10:19.315Z`. Los informes de preparación
`function-body-triage.json` y `function-identity-triage-v2.json` conservan resultados
por sentencia y firma; el segundo incluye el hash del catálogo. Una coincidencia
de cuerpo o declaración no cubre `ALTER`, `DROP`, ACL, propietarios, dependencias
ni cambios históricos de datos. Las diferencias sintácticas incluyen formato,
defaults explícitos y aliases de tipos; no todas son diferencias operativas.

La revisión independiente confirmó las tres divergencias y la separación de los
paquetes pendientes mediante los archivos versionados y el catálogo guardado.
No se modificó el ledger, RLS, datos de usuarios ni la clasificación de migraciones.
