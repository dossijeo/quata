# Android auth boundary rollout

Android no debe leer `pass_plain`, `pass_hash` ni `secret_answer`, ni hacer
`PATCH` anónimo de credenciales. Login y recuperación usan el bridge versionado.
Registro usa exclusivamente `quata-register` con Turnstile; la publishable
`apikey` sólo enruta y no es atestación.

## Secuencia segura

1. Configurar la sitekey Turnstile Android y el origen permitido. Sin ambos,
   el cliente falla cerrado antes de enviar credenciales.
2. Desplegar primero `quata-register` v1 y el `quata-auth-bridge` compatible con
   hashes legacy/PBKDF2, `secret_answer_hash` y `update_recovery_secret`.
3. Ejecutar E2E con cuentas temporales: accepted anti-enumeración, login,
   recuperación, reset, activa/desactivada/baneada, país erróneo, password
   erróneo, concurrencia/idempotencia y rollback. Eliminar las cuentas.
4. Publicar Android con esta revisión. Las sesiones ya guardadas, incluida
   Gabrielo, no se modifican; la navegación Feed anónima tampoco depende del
   bridge.
5. Observar adopción y fijar una versión mínima soportada. Mantener lectura
   legacy en servidor durante la ventana; el rollback consiste en desactivar
   el canal Android de `quata-register`, nunca reabrir columnas anon.
6. Confirmar en telemetría sólo códigos/resultados (nunca payloads) que ya no
   hay clientes Android soportados usando REST directo para credenciales.
7. Aplicar `profiles 003`.
8. Aplicar `RLS-004` y verificar que anon no puede seleccionar las columnas
   secretas ni actualizar credenciales.

No debe aplicarse 003/RLS-004 antes de completar la ventana de adopción. Esta
rama no despliega funciones ni modifica producción.
