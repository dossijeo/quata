# Trazabilidad focal Official y Community — 15 de septiembre de 2026

Estado: **BLOCKED_HISTORY_RECONCILIATION**. Este mapa documenta efectos actuales y sustituciones; no acredita ejecución completa de migraciones, no cambia el manifiesto de reconciliación ni habilita un paquete APNs. No se consultaron datos de negocio ni se ejecutó SQL.

Los ordinales, líneas y hashes de sentencia proceden de `statement-inventory-versioned.json` (pglast). Las líneas pueden incluir comentarios previos. `actor_guard` abrevia `20260808_0001_official_posts_actor_guard.sql`. La evidencia de catálogo es del 14 de septiembre; no se presenta como nueva lectura remota.

## Mapa por sentencia

### 20260703_0001_admin_delete_posts.sql

SQL SHA256: `a41e2195c85c6ec667fa5d320465991165a3da674e750d722c3d6db2c7908fcb`.

| Sentencia / línea | Efecto y límite | SHA256 de sentencia |
|---|---|---|
| 1 / 4 | DROP Community admin DELETE: el catálogo no prueba la ejecución del DROP. | `5a0de3714b60186d68a199752aea28bd6246d56f0467e5c6e3129be186f27eb8` |
| 2 / 5 | CREATE Community admin DELETE: política authenticated observada; otras políticas PUBLIC DELETE y grants amplios impiden extrapolar autorización efectiva o cierre de Community. | `26fb476fcef1697b8df97940ebeb4ad9d9b8ff961de28ef3fb6427b65153f0f3` |
| 3 / 11 | DROP Official admin UPDATE: no acredita ejecución histórica. | `18a82c7ca8c65a059572fc9343431cdb99d88566fb379312003cd6db281ba59c` |
| 4 / 12 | CREATE Official admin UPDATE: sustituida por actor_guard #17 y #20; la política histórica ausente no es un efecto perdido. | `2fb0e433cc1f111939f07e49c8cbb57ac4ec8cb48bf32579ff76c6837521ce80` |
| 5 / 25 | DROP Official admin DELETE: no acredita ejecución histórica. | `eca874be71c36813432a449112e796f573f0e046c6c69fa1e66b3b4a0347fb7d` |
| 6 / 26 | CREATE Official admin DELETE: sustituida por actor_guard #18 y #21; el marcador histórico del manifiesto quedó obsoleto. | `60c8e6e09256724f8e922957f2f24f646523f1f86333a1f627ecd98970dbbae0` |

### 20260709_0003_official_post_soft_delete_policy.sql

SQL SHA256: `c17c577cd7a81dad51dec8915b1168a45d1cfb88d1035cb9a3fedca35e25e69c`.

| Sentencia / línea | Efecto y límite | SHA256 de sentencia |
|---|---|---|
| 1 / 5 | DROP SELECT por idioma: no acredita ejecución histórica. | `a59a05c2eaf82f70632a179d0ddfcc0e0b35ceacfadb30f423d134ca30b5bca7` |
| 2 / 6 | CREATE SELECT por idioma: definición reproducida por actor_guard #12–13 y observada en catálogo. | `87474e1567e7b529802dcffec7a8d2715d5ef1bfaf3188810465cbd0dccac14d` |

### 20260808_0001_official_posts_actor_guard.sql

SQL SHA256: `bd6357752f3d9f1403f90efa1e2cb26081b959fd64dfb250c32029e5934af0bc`.

| Sentencia / línea | Efecto y límite | SHA256 de sentencia |
|---|---|---|
| 1 / 8 | BEGIN: no hay recibo de esta transacción. | `e6f07d43b5c21db0fbb9a31feac2dc599787763393dd5acbfad80e247eb02ad5` |
| 2 / 10 | Guard SECURITY INVOKER observado; su cuerpo sigue procediendo de official_accounts. | `74de97802377962ebf113a041e20e9e7d4b931c5e7b4c92db499b298a9a14cf7` |
| 3 / 12 | REVOKE EXECUTE del guard: ACL y privilegios efectivos focales observados; no prueba histórica del REVOKE. | `50083a7370cb5aabacdc1a44cacc4914b2010e864c0b39f0b91dcbd60faf8754` |
| 4 / 14 | GRANT EXECUTE postgres del guard observado. | `9a69bdfc61d8dd5f23a558fd4f68f86919d4fdabbf931a40d07a27e9769a31b2` |
| 5 / 16 | RLS habilitado en official_posts observado; FORCE RLS no se solicita aquí. | `47965a88cc08178406d54dd9c02655d3cfda4c5a4a253a158172254d45bffe04` |
| 6 / 18 | Helper insert_allowed(uuid): cuerpo coincide y atributos/propietario constan en catálogo. | `7502887eed4f4fde950d76033df1ef65d7ca5a93dd6c1b0b44838f0a0e580dbd` |
| 7 / 56 | Helper owner_or_admin_allowed(uuid): cuerpo coincide y atributos/propietario constan en catálogo. | `273a02a42bac82e0bf78901c5b4df87f54f3b8d91a3c0173553209a253f6e81c` |
| 8 / 82 | REVOKE PUBLIC/anon insert_allowed: ACL y anon sin EXECUTE observados. | `bffbf4ae73663918ba2235dbe9893e2590626e88d783b75b3e920de01ee6f0d5` |
| 9 / 84 | REVOKE PUBLIC/anon owner_or_admin_allowed: ACL y anon sin EXECUTE observados. | `c750a9336162134306dac70d04fe1d1dca8b1f59fcec8da23b90640762b373dc` |
| 10 / 86 | GRANT authenticated insert_allowed observado. | `64c2fae50c7e60b223e0fa3681e9154bb305cd2acba52629f0cc3f1250fe7e1b` |
| 11 / 88 | GRANT authenticated owner_or_admin_allowed observado. | `b6537bccda7f4756fdfd5c883c43577980a211b0fcbe589e9857d2c6a99a5798` |
| 12 / 91 | DROP SELECT histórico: no prueba ejecución; ver #13. | `a59a05c2eaf82f70632a179d0ddfcc0e0b35ceacfadb30f423d134ca30b5bca7` |
| 13 / 92 | CREATE SELECT por idioma observado; conserva definición de 20260709_0003 #2. | `87474e1567e7b529802dcffec7a8d2715d5ef1bfaf3188810465cbd0dccac14d` |
| 14 / 114 | DROP authenticated_insert: ausencia actual observada, no prueba del DROP. | `6365c62d6a11143a472e1fe5114f12ad460122c1223b7bf8708611cd6942a17e` |
| 15 / 115 | DROP authenticated_update_guarded: ausencia actual observada, no prueba del DROP. | `54d72afc33eb2387b22ae4e9319b07a9d7267affb5de0e602a74e2439094f537` |
| 16 / 116 | DROP authenticated_delete_guarded: ausencia actual observada, no prueba del DROP. | `86cb56c7bfb7180a124708e791921f6a6c2d8b6f3c3f2a9f5512a96d7e15a311` |
| 17 / 117 | DROP admin_update: ausencia actual observada; sustitución #20. | `18a82c7ca8c65a059572fc9343431cdb99d88566fb379312003cd6db281ba59c` |
| 18 / 118 | DROP admin_delete: ausencia actual observada; sustitución #21. | `eca874be71c36813432a449112e796f573f0e046c6c69fa1e66b3b4a0347fb7d` |
| 19 / 120 | CREATE INSERT own official: política y helper observados. | `5de67125433ed68c4cf1ccee82c43488431484af8c7fc686cf62f1c88df3d56a` |
| 20 / 128 | CREATE UPDATE author/admin: USING y CHECK con helper observados. | `05fb8929e8b5d3652eb7bfe933ea60ba06782bff3cdc94f497d5beda41e69373` |
| 21 / 139 | CREATE DELETE author/admin: USING con helper observado. | `f0f193aa658572538227acaecc68457a03d63388bc4173c4e8c698d9aad6f1e7` |
| 22 / 147 | REVOKE ALL anon tabla: ACL actual sólo SELECT; no prueba histórica del REVOKE. | `8e8b6553b7a7ae7b596632b2490202d38dae2cc2c8f781cf27968aa4b651b211` |
| 23 / 148 | GRANT SELECT anon observado. | `3bd440329b832efdda7e79df4274799d4b8dedd4187d01edbeb41653348cc3fc` |
| 24 / 149 | GRANT SELECT/INSERT/UPDATE/DELETE authenticated observado; no afirma ausencia de otros privilegios. | `ef0acdfcbe447328e1aa99315f63e820b0fd18cb0ad86094094ab76281475fc1` |
| 25 / 151 | COMMIT: catálogo no demuestra ejecución atómica ni fecha. | `9505cacb7c710ed17125fcc6cb3669e8ddca6c8cd8af6a31f6b3cd64604c3098` |

## Siete cuerpos comparados

La coincidencia usa cuerpos normalizados por trim y LF, no equivalencia global de funciones. ACL, SECURITY, search_path, propietarios y triggers se contrastan separadamente en el catálogo focal.

| Firma | Última fuente versionada del cuerpo |
|---|---|
| `quata_current_profile_id()` | `20260702_0003_official_accounts.sql` |
| `quata_current_profile_is_admin()` | `20260702_0003_official_accounts.sql` |
| `quata_current_role_is_service()` | `20260702_0003_official_accounts.sql` |
| `quata_guard_official_posts()` | `20260702_0003_official_accounts.sql` |
| `quata_official_post_insert_allowed(uuid)` | `20260808_0001_official_posts_actor_guard.sql` |
| `quata_official_post_owner_or_admin_allowed(uuid)` | `20260808_0001_official_posts_actor_guard.sql` |
| `quata_requested_official_post_language()` | `20260709_0002_official_post_languages.sql` |

## Pendientes que conserva el mapa

La decisión de `20260808_0001_official_posts_actor_guard.sql` sigue ausente del manifiesto. Su correspondencia focal está documentada; no se añade `verified_applied_semantics` ni una fecha de aplicación. El archivo de julio mezcla Community y Official: la supersesión Official no cierra sus dos primeras sentencias ni la interacción con otras políticas Community. Los grants amplios y predicados PUBLIC DELETE son divergencias separadas; no se afirma explotación HTTP ni autorización efectiva por fila.

El recibo privado del worktree Push `build-reports/flow-push-lifecycle/db-preflight.json`, terminado `2026-09-14T12:54:08.795Z`, registra 35 archivos sin ledger y 29 decisiones parciales. Es anterior al renombrado APNs de `20260914_0001_apns_registration_environment.sql` a `20260914135400_apns_registration_environment.sql`; no es preflight del árbol actual. El renombrado no resuelve el historial ni autoriza despliegue. Se conserva ese recibo original.

El índice privado `audit-report-index.json` referencia ahora `historical-data-effects-triage-v2.json`, corrección ya respaldada por `historical-data-audit-conclusion.json`. Persisten 13 DML, un ANALYZE y el refresh dentro de DO sin prueba histórica. No se extiende la excepción del lote RLS a APNs.

La siguiente revisión puede usar este mapa para precisar decisiones documentales; aún faltan procedencias exactas de las divergencias funcionales/UGC y prueba o decisión de reconciliación sobre efectos históricos. Véase [decisión de auditoría](MIGRATION_LEDGER_AUDIT_DECISION_20260914.md).

## Evidencia local utilizada

Los archivos siguientes permanecen privados bajo `build-reports/migration-ledger/`; sus hashes permiten identificar exactamente la evidencia consultada.

| Archivo | SHA256 |
|---|---|
| `statement-inventory-versioned.json` | `116d6d44199f683b87ec65f8ad0291aab38f0dac7ad2a5a91f0566b99387fe4a` |
| `official-catalog.json` | `b7d22679b016584f8bbcb947eb7b45f48d61a90ce3c77e29edcb8261cc9c9b00` |
| `official-catalog-with-roles.json` | `60d96c72be489f69993120f7c2886cd95d4989bc5a2405796decf7d11926afda` |
| `official-body-comparison.json` | `a8e5e162377210876796247404a73508e473dd9e5468d2c1428bb767ffa3cf4a` |
| `official-audit-conclusion.json` | `e0389148859aac7a7943cd9888469f49463a20834b052ceabee64c6e23e530cf` |
| `historical-data-effects-triage-v2.json` | `b6d5b395178da3d99032afcf4e31a1e01000a0a5a4bcdb5b9e9e1ea047d803e0` |
| `historical-data-audit-conclusion.json` | `fc654fdf23f33390d7984a254d744823a76a29efa7b923addeba5cc6fa6fb343` |
