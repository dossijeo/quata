# Seguridad y operaciones remotas

Detalle del [modelo operativo](../../MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md); forma parte de la misma fuente de verdad, con el mismo alcance y autorizaciones.

## 3. Seguridad y compatibilidad con producción

- Android publicado, la Web antigua y el Feed anónimo no se pueden romper.
- La ausencia o amplitud temporal de RLS no bloquea la migración funcional: Web/iOS implementan el
  mismo contrato backend que Android utiliza hoy.
- No se endurecen, eliminan ni despliegan políticas RLS, esquema, tablas, funciones o datos que
  puedan romper clientes actuales.
- La deuda de seguridad se documenta con evidencia y se aplicará después de publicar los clientes
  migrados.
- Una Edge Function nueva puede desarrollarse de forma aditiva, pero su despliegue, secretos y
  activación se validan por separado. Nunca se incluye una service-role key ni un secreto privado en
  clientes, commits, logs o capturas.
- En una validación se pueden inyectar metadatos **públicos** de despliegue únicamente en una copia
  temporal del artefacto que se sirve o instala. El artefacto original y su hash permanecen
  inmutables. Ni la copia ni el original pueden contener una service-role key o una clave VAPID
  privada.
- Rige la [autorización permanente de operaciones remotas](../../MIGRATION_REMOTE_OPERATIONS_AUTHORIZATION.md),
  ampliada por el propietario el 9 de septiembre de 2026. Permite ejecutar autónomamente despliegues
  focales compatibles y revisados, activaciones/desactivaciones de interruptores, fixtures,
  sesiones propias, pruebas reales, restitución y rollback verificado. No solicitar permiso
  individual ni esperar confirmaciones entre Android, Web e iOS cuando se cumplan sus condiciones.
- Antes de mutar, verificar alcance focal, diff/hash/config/package revisados, ausencia de cambios
  concurrentes que se sobrescribirían, compatibilidad con clientes publicados, identidad autorizada,
  snapshot suficiente, rollback concreto, registro previo de sesiones/mutaciones y privacidad de
  credenciales. Antes de desplegar se informa del contenido; el resumen no es una solicitud de aprobación.
- Tras un fallo, reconciliar/restaurar antes de repetir. No repetir operaciones inciertas. Retirar
  journals/locks sólo después de verificar el cierre y devolver flags temporales al estado seguro.
  Tercer workaround de la misma interacción UI: detener y replantear el approach; un fallo ajeno no
  amplía el alcance ni justifica construir infraestructura indefinidamente.
- Siguen requiriendo autorización específica los cambios destructivos sobre datos reales ajenos a
  fixtures, borrados masivos, DROP/TRUNCATE, esquema destructivo, cambios amplios de RLS/grants/policies,
  rotación/eliminación de credenciales de producción no creadas para el ensayo, infraestructura ajena,
  billing, DNS/dominio, publicación en tiendas, contacto/alertas a terceros reales (incluido SOS),
  incompatibilidad deliberada con clientes publicados u operaciones sin rollback razonable.
  La autorización remota no amplía la unidad focal ni sustituye revisión, evidencia o certificación.
- Los datos y cuentas temporales de prueba se eliminan al terminar.
- Las credenciales locales, claves SSH, certificados y ficheros de sesión nunca se versionan ni se
  imprimen en logs.
