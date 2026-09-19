# Comparación focal de funciones — 14 de septiembre de 2026

Estado: auditoría parcial; no acredita ejecución histórica ni autoriza despliegues.
El gate de reconciliación permanece sin cambios. Se comparó SQL versionado con
catálogo leído en una transacción de solo lectura, sin consultar filas de negocio.

## Diferencias con impacto semántico

| Función | Catálogo observado frente al SQL versionado | Destino de la revisión |
| --- | --- | --- |
| `quata_chat_get_thread(uuid,bigint,bigint[],integer)` | Cuerpo de conversación por usuario con selección `ASC` antes de `LIMIT`, frente a `DESC` en `20260714_0001`. Cambia qué mensajes se seleccionan. | Procedencia pendiente; comparar paginación con fixtures sintéticos antes de elegir una corrección. |
| `quata_chat_community_key(text)` | El literal de caracteres acentuados contiene 50 signos `?` en el remoto. | Procedencia pendiente; afecta la normalización usada para asociar comunidades. |
| `quata_account_deactivate(uuid,uuid)` | El remoto conserva `deactivated_auth_user_id = p_auth_user_id`, ausente del cuerpo de `20260721_0001`. | Procedencia pendiente; los consumidores actuales conocen esa columna. Restaurar el cuerpo antiguo perdería este efecto. |

No se ha encontrado una supersesión versionada que acredite esas tres definiciones
remotas exactas. No se han cambiado funciones ni ejecutado RPC mutantes para
investigar sus diferencias.

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
