# Comparación focal de funciones — 14 de septiembre de 2026

Estado: auditoría parcial; no acredita ejecución histórica ni autoriza despliegues.
El gate de reconciliación permanece sin cambios. Se comparó SQL versionado con
catálogo leído en una transacción de solo lectura, sin consultar filas de negocio.

## Diferencias con impacto semántico

| Función | Catálogo observado frente al SQL versionado | Destino de la revisión |
| --- | --- | --- |
| `quata_chat_get_thread(uuid,bigint,bigint[],integer)` | Cuerpo de conversación por usuario con selección `ASC` antes de `LIMIT`, frente a `DESC` en `20260714_0001`. La prueba focal de tres mensajes y límite dos confirmó que el remoto devuelve la página más antigua. | `20260922173500_chat_get_thread_latest_page.sql` restaura como candidata la definición versionada que selecciona la página más reciente y la devuelve en orden cronológico. La candidata y su rollback no están desplegados; la decisión histórica sigue abierta. Evidencia: `evidence/chat-get-thread-pagination-20260922.json`. |
| `quata_chat_community_key(text)` | El literal de caracteres acentuados contiene 50 signos `?` en el remoto; la evaluación focal de un literal sintético acentuado devuelve `null`. El audit también observa 1 incumplimiento del backfill de propietarios y 3 del de miembros bajo la función actual. | `20260922174500_chat_community_members_repair.sql` restaura como candidata la transliteración versionada y repite los dos backfills idempotentes originales. No está desplegada y la decisión histórica sigue abierta. Evidencia: `evidence/chat-community-members-repair-20260922.json`. |
| `quata_account_deactivate(uuid,uuid)` | El remoto conserva `deactivated_auth_user_id = p_auth_user_id`, ausente del cuerpo de `20260721_0001`; la columna y la ACL exclusiva de `service_role` están presentes. | `20260922175500_account_deactivation_auth_link.sql` versiona como sucesora exacta la definición remota y su ACL sin cambiar el comportamiento desplegado. No está desplegada y la decisión histórica sigue abierta. Evidencia: `evidence/account-deactivation-auth-link-20260922.json`. |

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
