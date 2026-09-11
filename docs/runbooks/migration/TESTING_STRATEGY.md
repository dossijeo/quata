# Estrategia de testing

Detalle del [modelo operativo](../../MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md); forma parte de la misma fuente de verdad, con el mismo alcance y autorizaciones.

### Testing por capas: preferencia para nuevas unidades y suites con churn real

**Directiva permanente (confirmada el 9 de septiembre de 2026): Compose UI primero;
Maestro sólo tras un piloto fiable del recorrido; XCTest para bordes nativos o casos todavía
sin sustituto probado. No ampliar XCTest por defecto ni migrar suites que funcionan por rutina.**

- Antes de crear o ampliar un XCTest complejo, separar la aceptación común, la E2E de plataforma
  y los bordes del sistema. Evaluar primero Compose UI tests y un piloto Maestro pequeño, local y
  reversible cuando pueda sustituir la interacción problemática.
- **Compose UI tests**: preferencia para UI común, eventos, estados, validaciones y navegación.
  Usar el formulario/host de producto y dispatchers controlados donde ya exista inyección.
  Un fake local nunca demuestra persistencia, autorización o recuperación real; ejecutar un target
  no certifica los restantes.
- **Maestro**: candidato para E2E de usuario Android/iOS y Web cuando la superficie sea viable.
  Adoptarlo por recorrido sólo tras demostrar repetibilidad con identificadores/inputText, sin
  coordenadas, menús o gestos ad hoc, y conservar verificaciones backend, restauración, revisión
  visual y tratamiento privado de secretos. Si requiere hacks equivalentes, detener el piloto.
- **XCTest/XCUI**: reservar trabajo nuevo para APIs/flujos Apple o casos que las otras capas no
  cubran razonablemente. Las suites certificadas siguen vigentes hasta demostrar un reemplazo
  equivalente; no reescribirlas por rutina ni invalidar evidencia existente.
- Antes de cambiar la estrategia, registrar alcance, líneas/fixtures/workarounds, estabilidad,
  limitaciones, bordes nativos pendientes y decisión ADOPTAR / ADOPTAR PARCIALMENTE / DESCARTAR.
- Resultado inicial de ACCOUNT-RECOVERY-SECRET: **ADOPTAR PARCIALMENTE**, Compose para la capa común;
  Maestro permanece como diagnóstico sintético y no sustituye el runner E2E real. La entrada por
  tags mejora, pero el teclado impide completar la navegación y falta acreditar privacidad de
  artefactos con secretos. Véase [piloto y resultados](../../recovery-secret/ui-testing-pilot.md).
