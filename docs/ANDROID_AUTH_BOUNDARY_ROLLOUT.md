# Android auth boundary rollout

Android no debe leer `pass_plain`, `pass_hash` ni `secret_answer`, ni hacer
`PATCH` anónimo de contraseñas. Login, registro y recuperación usan
`quata-auth-bridge`.

## Secuencia segura

1. Desplegar primero esta versión backward-compatible de
   `quata-auth-bridge --no-verify-jwt`. Las acciones login/web existentes no
   cambian.
2. Ejecutar las pruebas E2E del bridge con una cuenta temporal: registro,
   logout/login, consulta de pregunta y reset. Eliminar la cuenta temporal.
3. Publicar Android con esta revisión. Las sesiones ya guardadas, incluida
   Gabrielo, no se modifican; la navegación Feed anónima tampoco depende del
   bridge.
4. Confirmar en telemetría sólo códigos/resultados (nunca payloads) que ya no
   hay clientes Android soportados usando REST directo para credenciales.
5. Aplicar `profiles 003`.
6. Aplicar `RLS-004` y verificar que anon no puede seleccionar las columnas
   secretas ni actualizar credenciales.

No debe aplicarse 003/RLS-004 antes de desplegar la función y distribuir el
cliente Android actualizado. Esta rama modifica fuentes de la función pero no
la despliega.
