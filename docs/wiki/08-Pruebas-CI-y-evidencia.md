# Pruebas, CI y evidencia

## Qué demuestra cada capa

| Capa | Demuestra | No demuestra |
|---|---|---|
| Unit tests | Reglas y contratos aislados. | Integración visual o backend real. |
| Compilación | Compatibilidad del source set/host. | Que la ruta se monte o funcione. |
| Browser smoke | Arranque, bundle y rutas básicas. | Paridad funcional autenticada completa. |
| E2E backend | Contrato remoto real. | Paridad visual. |
| Captura visual | Composición observada. | Que todas las mutaciones persistan. |
| CI verde | Gates automatizados del SHA. | GO total por sí sola. |

## Workflows principales

- Auditoría de imports Android en `commonMain`.
- Tests, bundle y smoke Web/Wasm.
- Tests y lint/assembly Android.
- Compilación Kotlin/Native y host Swift.
- CodeQL.
- E2E Supabase separados por alcance.

## Revisión exacta

Todo gate integrado Web o iOS debe usar el commit exacto de `refs/pull/<N>/merge`. Antes de
ejecutarlo se registran la `origin/main` exacta, `refs/pull/<N>/head` y el merge sintético. El merge
debe tener exactamente dos padres: primero la base `main` registrada y segundo el head de PR
registrado. El número de PR y los tres SHA completos quedan en logs, informe y capturas. Si cambia
un padre, falta el ref o no coincide el orden, la evidencia se invalida y el gate se repite.

Una compilación del head aislado solo sirve para diagnosticar la rama. No concede GO ni sustenta
una decisión de merge. Sus informes y capturas deben identificarse como
**DESCARTADOS: HEAD-ONLY**. La regla normativa completa permanece en el
[modelo operativo](https://github.com/dossijeo/quata/blob/main/docs/MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md).

La descripción de una PR debe indicar:

- SHA revisado.
- Comandos y targets.
- Tests ejecutados y no ejecutados.
- Backend consultado o mutado.
- Evidencia visual disponible.
- Resultado GO, HOLD o NO-GO.

## Comparación visual

La comparación utiliza Android como referencia y debe reproducir:

- La misma ruta y estado.
- Datos equivalentes.
- Sesión equivalente.
- Orientaciones o tamaños relevantes.
- Loading, vacío, error y reintento.
- Gates de autenticación y retorno.
- Acciones de producto importantes.

No se acepta un renderer alternativo o una captura fabricada para sustituir la app real.

## Evidencia segura

- No incluir tokens ni cabeceras de autorización.
- Redactar teléfonos, IDs sensibles y contenido privado.
- No almacenar claves de backend en artefactos.
- La configuración pública de despliegue solo puede inyectarse en una copia temporal servida o
  instalada; se conserva intacto el artefacto original y se registra la inyección.
- Ni la copia temporal ni el artefacto original pueden contener service-role keys o claves VAPID
  privadas.
- Detener servidores y limpiar fixtures/sesiones al terminar.

Referencias:

- [CI Web/Android](https://github.com/dossijeo/quata/blob/main/docs/CI_WEB_ANDROID.md)
- [Validación Web](https://github.com/dossijeo/quata/blob/main/docs/WASM_WEB_VALIDATION.md)
- [Auditoría de evidencia](https://github.com/dossijeo/quata/blob/main/docs/MULTIPLATFORM_EVIDENCE_AUDIT.md)
