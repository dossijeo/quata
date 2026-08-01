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

Toda evidencia debe estar asociada al head actual. Si se añade un commit después de una revisión, esa revisión no concede GO al nuevo head aunque el cambio parezca pequeño.

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
- Registrar si hubo inyección manual de configuración pública.
- Detener servidores y limpiar fixtures/sesiones al terminar.

Referencias:

- [CI Web/Android](https://github.com/dossijeo/quata/blob/main/docs/CI_WEB_ANDROID.md)
- [Validación Web](https://github.com/dossijeo/quata/blob/main/docs/WASM_WEB_VALIDATION.md)
- [Auditoría de evidencia](https://github.com/dossijeo/quata/blob/main/docs/MULTIPLATFORM_EVIDENCE_AUDIT.md)
