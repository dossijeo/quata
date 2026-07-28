# Evidencia de ciclo de vida DocMentis en staging

Esta plantilla describe la evidencia mínima para los cuatro formatos autorizados
en staging: `PDF`, `DOCX`, `PPTX` y `XLSX`. Es un control de eventos de ciclo de
vida; **no afirma que se hayan renderizado píxeles ni que se haya inspeccionado la
UI**.

## Límites de seguridad

- La ejecución debe usar únicamente una fixture aprobada y el entorno de staging
  autorizado. No se permite una URL, token, cabecera, ruta de Storage, correo u
  otro dato personal en el informe o el artefacto de CI.
- No se habilita una licencia, permiso remoto ni origen de producción mediante
  este control. Tampoco se cambia el runtime Web, Android, Supabase, RLS o Edge
  Functions.
- Una captura o una inspección visual verificada es evidencia distinta. Debe
  conservarse y revisarse por separado antes de declarar renderizado real.

## Transcript autorizado

Se debe generar un transcript independiente por cada par autorizado:

| Fixture ID | Formato |
| --- | --- |
| `staging-pdf` | `PDF` |
| `staging-docx` | `DOCX` |
| `staging-pptx` | `PPTX` |
| `staging-xlsx` | `XLSX` |

El parser acepta exactamente esta forma, orden y valores:

```json
{
  "fixtureId": "staging-pdf",
  "format": "PDF",
  "events": [
    { "type": "document:load", "sequence": 1 },
    { "type": "customPageOverlay", "sequence": 2 },
    { "type": "isLoaded", "sequence": 3, "value": true },
    { "type": "pageCount", "sequence": 4, "value": 1 },
    { "type": "cleanup", "sequence": 5 }
  ]
}
```

El resultado correcto indica `evidenceKind: docmentis_lifecycle` y
`visualEvidence: not_evaluated`. Cualquier campo adicional o transcript
incompleto falla de forma cerrada; el reporte contiene sólo códigos fijos y no
repite contenido proporcionado por la ejecución.

## Registro de ejecución de staging

Para cada formato, registrar únicamente: el identificador fijo de fixture, el
formato, el resultado del gate y un identificador no sensible de build. No copiar
logs de red, permisos, URLs ni datos de usuario. Si se necesita evidencia visual,
abrir un registro separado, con revisión humana y la política de retención
correspondiente.
