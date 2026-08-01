# Estado del proyecto

> Corte documental inicial: 1 de agosto de 2026. Para decisiones de merge prevalecen GitHub, el inventario de `main` y la evidencia del head exacto.

## Cómo leer este estado

| Estado | Definición |
|---|---|
| Integrado | Está en `main` y posee evidencia aceptada. |
| Candidato | Vive en una PR draft; no forma parte del producto integrado. |
| HOLD | Falta una lane, entorno o evidencia. |
| NO-GO | Existe un incumplimiento demostrado. |
| Deuda | Funciona con el contrato vigente, pero necesita corrección posterior. |
| Histórico | Ya no representa el sistema actual. |

## Situación consolidada

- Feed y Oficial son las superficies con integración multiplataforma más acreditada.
- Login, Registro y Recuperación poseen raíces Compose comunes, pero los recorridos finales dependen de configuración, sesión y evidencia por plataforma.
- El shell público y el retorno de autenticación han avanzado en Web e iOS.
- Communities, perfiles, Conversations, Chat, compositor y editor Oficial continúan en migración y no deben declararse integrados por la existencia de una rama.
- Firma/distribución iOS y APNs siguen siendo capacidades externas pendientes de validación de release.
- La deuda RLS permanece documentada y se resuelve mediante releases compatibles independientes.

## Fuentes dinámicas

- [PR abiertas](https://github.com/dossijeo/quata/pulls)
- [Inventario de pantallas](https://github.com/dossijeo/quata/blob/main/docs/SCREEN_MIGRATION_INVENTORY_V2.md)
- [Matriz de capacidades](https://github.com/dossijeo/quata/blob/main/capabilities/platform-capability-matrix.json)
- [Tablero de migración](https://github.com/dossijeo/quata/blob/main/docs/MULTIPLATFORM_MIGRATION_BOARD.md)
- [GitHub Actions](https://github.com/dossijeo/quata/actions)

## Regla de actualización

Esta página sólo debe afirmar como integrado lo que ya está en `main`. Las PR pueden mencionarse como candidatas enlazando su estado actual, pero nunca trasladar un “GO” de un SHA anterior.

Después de cada merge de pantalla:

1. Actualizar inventario y matriz de capacidades.
2. Enlazar evidencia del SHA fusionado.
3. Actualizar esta página si cambia la situación consolidada.
4. Sincronizar la Wiki.
5. Eliminar rama y worktree integrados.
