# ACCOUNT-RECOVERY-SECRET: estado operativo

## Alcance y referencia

Unidad `ACCOUNT-RECOVERY-SECRET`, requisito `VERIFIED_ANDROID`, aceptación E2E
pendiente. Cuenta debe configurar pregunta/respuesta; recuperación debe consumir
ese secreto; el ensayo debe restaurar contraseña/secreto y limpiar sus sesiones.
ACCOUNT-DETAILS y SCR-AUTH-RECOVERY conservan sus cierres. No se amplía contraseña
legacy, avatar, SOS ni otros subflujos.

La referencia publicada es el AAB v32 confirmado por el propietario; véase
[identidad y contrato Android](../ANDROID_PUBLISHED_REFERENCE_V32.md). El cliente
publicado compara `secret_answer`: no se puede anular ese campo durante la migración.
La rama vieja `codex/account-recovery-secret` se examinó como salvage y fue retirada;
`c86c324` no se promovió. Se conservaron las anclas semánticas de pregunta y se
sustituyó el runner antiguo por un propietario focal independiente de ACCOUNT-DETAILS.

## Backend pendiente

[Plan compatible revisado](deployment-compatible-v21.md): v21 más productor
autenticado sobre `secret_question`/`secret_answer`, sin migración de esquema, RLS,
pepper ni sustitución de consumidores. El despliegue y su activación siguen sin
autorización. La sonda real sin bearer devuelve `400/password_required`, por lo que
el coordinador se detiene antes de abrir navegador o crear journal.

La propuesta hashed retirada se conserva únicamente en el historial Git, por ejemplo
en el documento de dependencia del commit `8b2da0ff`. No forma parte del plan operativo.
El contrato hash/pepper de main tampoco acredita el paquete compatible propuesto.

## Implementación disponible

- `account-recovery-secret-evidence.mjs`: preparación persistente, flujo focal y
  restitución tras interrupción. La reanudación devuelve `restored`, nunca GO E2E.
- Snapshot por actor exacto y formato explícito `legacy-v32`; restauración de los
  dos campos condicionada atómicamente a los valores temporales esperados.
- Journal privado DPAPI del coordinador Windows; tickets antes de sesiones,
  posibles mutaciones antes de Save/reset y eliminación sólo tras verificar cleanup.
  Si una preparación falla después de intentarse, la existencia del journal queda
  desconocida; no se elimina automáticamente.
- Adaptador Web mediante bridges Auth y Recovery propios, con opt-ins localhost.
  Espera recomposición, ejecuta un Save y sólo expone pregunta/booleanos. No captura
  respuestas escritas. Los tokens capturados de la sesión quedan en memoria; sus
  IDs se acreditan con Auth y consultas exactas antes de persistir recibos privados.
- Backend con preflight, auditoría de sesiones, lectura pública, login de verificación,
  reset de restitución y limpieza por recibos. Los tickets HTTP no se reutilizan.
  Una petición incierta conserva el journal; un timeout no prueba cancelación remota.
- `account-recovery-secret-web.mjs`: entrada invocable que une esos módulos.
  El caller aporta DB dedicada con timeout, acceso serializado, comprobación de
  candidata/despliegue y navegador/servidor propios. No hay CLI ni aceptación real.

Antes de login, reset o restitución se comprueba que no haya sesiones Auth ajenas;
los IDs excluidos deben estar acreditados, nunca deducidos por fechas o diferencias.
La auditoría readonly del 8 de septiembre observó A sin sesiones Auth y B con 13;
no es una garantía vigente ni una autorización para revocar sus sesiones históricas.
La comprobación puntual no proporciona exclusión atómica frente a otros clientes.

## Aislamiento del fixture

El Save de `KmpProfileRepository` también escribe campos generales y contactos de
emergencia. El preflight y la preparación exigen campos que no cambien al aplicar
esa proyección y cero contactos. El digest privado se comprueba antes y después
de Save y al cerrar; una discrepancia impide certificar y conserva el journal.
No se normalizan ni restauran campos ajenos al secreto. El caller debe aportar
un contexto de navegador nuevo y desechable, sin selecciones SOS almacenadas.
Estas lecturas no excluyen cambios concurrentes de terceros.

La auditoría readonly del 8 de septiembre rechazó ambas cuentas A/B porque Save
normalizaría otros campos. Sus datos no se modificaron: falta un actor compatible.
La prueba ensamblada sigue simulando servicios; no acredita esos efectos en SQL real.

## Validación y trabajo restante

Se han probado módulos y ensamblado con servicios simulados y DPAPI real de Windows.
El ensamblado verifica orden, recibos, restitución sintética y cleanup; no acredita
SQL real, firmas JWT, Compose, cancelación remota ni exclusión concurrente.
La distribución Web de `8b2da0ff` compiló y pasó el smoke sin sesión. Sus artefactos
y recibos permanecen en `build-reports/account-recovery-secret-salvage`; son
preparación, no certificación final.

Queda disponer de un fixture compatible, validar el despliegue
compatible tras autorización, conectar Android/iOS a superficies reales y ejecutar
la aceptación focal sobre el candidato integrado exacto. El test Android de Auth
con repositorio simulado no sustituye esa aceptación; el runner iOS de Auth puede
aportar pasos reutilizables, pero no será el propietario del productor de Cuenta.
No extender el runner de ACCOUNT-DETAILS ni construir otro framework de XCTest.

Antes de candidate-final: revisión independiente, correcciones, evidencia local
proporcional y head congelado. Después: certificación real, merge y cierre inmediato
del inventario maestro con límites explícitos, sin promover padres ni vecinos.
