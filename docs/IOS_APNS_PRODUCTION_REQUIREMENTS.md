# Requisitos de producción para notificaciones iOS/APNs

Este documento define la preparación necesaria para que Qüata entregue notificaciones
remotas en iOS de forma verificable, sin alterar la aplicación Android publicada, el
cliente Web existente ni las políticas RLS actuales. Es un plan de requisitos: no
autoriza despliegues de Supabase, cambios de RLS ni la publicación de una app.

## Alcance y estado de partida

El código ya contiene la frontera de cliente, pero no una integración de entrega:

- `iosApp/iosApp/IosApnsLifecycleBridge.swift` solicita el registro sólo tras una
  autorización de notificaciones válida, normaliza el token APNs y recibe los callbacks
  del `AppDelegate`.
- `core/src/iosMain/.../IosApnsRegistrationAdapter.kt` valida el token y falla de forma
  explícita mientras no se le conecte un receptor autenticado para subirlo. Por tanto,
  hoy ningún token iOS se persiste ni se envía desde la app.
- `iosApp/iosApp/QuataIos.entitlements` declara `aps-environment` mediante
  `$(QUATA_APNS_ENVIRONMENT)`. El identificador de bundle de la app es
  `com.quata.ios`; la Share Extension es `com.quata.ios.shareextension` y ambas usan
  el App Group `group.com.quata.ios.share`.
- La base existente admite `platform = 'ios'` en `push_tokens` y el RPC autenticado
  `quata_register_push_token(profile, token, platform)`. Esto es una capacidad ya
  catalogada, no una invitación a modificar su RLS.
- El desplegado candidato `supabase/functions/quata-push-dispatch` sólo conoce FCM y
  Web Push. Trata todos los registros de `push_tokens` como si fueran FCM. Registrar
  un token APNs antes de adaptar ese emisor provocaría intentos FCM erróneos y podría
  deshabilitarlo como inválido. Es un bloqueo duro.

El inbox de Notifications no depende del proveedor: se deriva de conversaciones de
Chat. APNs debe despertar y llevar al deep link común `conversation_id`/`thread_id`/
`message_id`; no debe crear un segundo inbox ni cambiar el significado de los payloads
Android o Web.

## Decisión de arquitectura que debe aprobar el propietario

Para este repositorio se recomienda **APNs directo con autenticación por token `.p8`**.
La app ya obtiene un token APNs y el dispatcher ya es el emisor central de chat; añadir
un canal APNs en ese backend conserva FCM para Android y Web Push para navegador.

No se deben mezclar tokens APNs con FCM ni enviar una clave de Apple a la aplicación.
La alternativa Firebase Cloud Messaging para iOS exigiría configurar Firebase iOS,
GoogleService-Info y cambiar el ciclo de token. No es el camino propuesto ni debe
introducirse parcialmente.

## Material que debe preparar el propietario

### Apple Developer y firma

1. Acceso administrativo al equipo Apple Developer que posea el App ID de producción.
   Confirmar que el Team ID corresponde al titular que distribuirá Qüata.
2. Registrar o comprobar el identificador explícito `com.quata.ios` y activar la
   capacidad **Push Notifications**. No usar wildcard App IDs.
3. Registrar/comprobar `com.quata.ios.shareextension` y el App Group
   `group.com.quata.ios.share`. El App Group debe estar habilitado para ambos targets;
   Push Notifications sólo es necesario en el target principal salvo que se apruebe
   una funcionalidad distinta para la extensión.
4. Crear perfiles separados que contengan exactamente esas capacidades:
   desarrollo para dispositivo físico y distribución (App Store/TestFlight) para
   `com.quata.ios`; perfil de distribución correspondiente para la extensión. Anotar
   sus nombres, sin subir los `.mobileprovision` al repositorio.
5. Proporcionar, por un gestor de secretos autorizado, estos identificadores no
   secretos: Team ID, nombres/UUID de los perfiles, App ID/bundle ID y entorno de cada
   build (`development` o `production`). La configuración Release actual espera
   `QUATA_DEVELOPMENT_TEAM` y
   `QUATA_IOS_APP_PROVISIONING_PROFILE`/
   `QUATA_IOS_SHARE_EXTENSION_PROVISIONING_PROFILE`.
6. Crear una APNs Auth Key con permiso **Apple Push Notifications service (APNs)**,
   registrar su Key ID y descargar el `.p8` una sola vez. Custodiar el fichero en un
   secreto de backend; no se puede descargar de nuevo desde Apple. Usar una clave
   dedicada a Qüata, no una llave personal compartida.

Un certificado APNs también es posible, pero no se recomienda: expira, obliga a
renovaciones más frecuentes y no simplifica el servidor. La Auth Key `.p8` nunca se
instala en el Mac de desarrollo, en CI, en la IPA ni en GitHub Actions.

### Supabase, secretos y operación

El propietario debe habilitar un canal seguro para cargar secretos **sólo** en el
entorno de Edge Functions (por ejemplo, Supabase project secrets):

- la clave privada APNs `.p8` codificada de forma apta para secreto;
- APNs Key ID y Apple Team ID;
- topic/bundle ID de producción `com.quata.ios`;
- una configuración de entorno que seleccione `api.sandbox.push.apple.com` para
  desarrollo y `api.push.apple.com` para producción;
- el secreto existente de invocación de `quata-push-dispatch` y la service-role, sin
  copiarlos a clientes, informes o logs.

Los nombres finales de secretos se decidirán junto con la implementación. No deben
reutilizar VAPID, credenciales FCM ni secretos de login. El repositorio contiene
integraciones Web y Android que deben permanecer independientes:

- Android continúa con FCM y los RPC `quata_register_push_token` /
  `quata_unregister_push_token`.
- Web continúa con `quata-web-push`, VAPID y sesiones Web aisladas.
- APNs reutilizará el mismo RPC autenticado únicamente con `platform = 'ios'`; no
  accederá a `push_tokens` desde el cliente ni requerirá una nueva política RLS.

Antes del despliegue, el propietario debe aprobar una ventana reversible, un responsable
on-call y un procedimiento para deshabilitar sólo el canal iOS si el proveedor falla.

### Datos, privacidad y publicación

1. Definir la finalidad y retención de tokens de dispositivo, identificador de perfil,
   estado de entrega y errores acotados en la política de privacidad y en App Store
   Connect. Declarar en App Privacy la recogida de identificadores vinculados al
   usuario si corresponde al flujo definitivo.
2. Confirmar el texto funcional de consentimiento: el permiso se pide desde una acción
   de Notifications, no al primer arranque. Debe explicar que sirve para avisos de
   conversaciones y que se puede revocar en Ajustes.
3. Acordar si el cuerpo de mensajes se muestra en pantalla bloqueada. Si no se aprueba,
   el payload debe ser genérico y no incluir contenido sensible. Nunca incluir token,
   teléfono, access token ni adjuntos en APNs.
4. Proporcionar dos perfiles de prueba controlados para el E2E (emisor/receptor), con
   autorización para enviar un chat real de prueba y un procedimiento de limpieza.
   Sus credenciales se pasan por variables efímeras, nunca al documento, Git, capturas
   ni logs.

## Trabajo implementable en el repositorio, una vez disponibles los requisitos

La implementación debe ir en una PR separada y revisable. El orden recomendado es:

1. **Cliente iOS.** Conectar un `IosApnsTokenHost` autenticado al bridge después de
   restaurar/iniciar sesión, registrar el token mediante el RPC actual con
   `platform = "ios"`, reintentar de forma acotada y eliminar/deshabilitar el token en
   logout. No guardar el token en texto ni registrar su valor. Si falta sesión, dejar el
   token pendiente sólo en almacenamiento seguro y sin asociarlo a otro perfil.
2. **Servidor emisor.** Separar los destinos por plataforma en
   `quata-push-dispatch`: FCM sólo para Android, Web Push sólo para suscripciones Web,
   y APNs sólo para `platform = 'ios'`. Firmar JWT ES256 de corta vida con la `.p8`,
   enviar los headers APNs obligatorios (`apns-topic`, `apns-push-type`, prioridad y
   expiración) y construir `aps.alert` más los campos de deep link comunes.
3. **Errores y revocación.** Marcar como inválido un token APNs sólo en respuestas
   permanentes del proveedor (por ejemplo, token ya no registrado); los timeouts,
   5xx y credenciales ausentes se registran como errores operativos sin borrar ni
   deshabilitar tokens válidos. Mantener idempotencia por mensaje y token como hace
   hoy el log de entregas.
4. **Recepción y UX.** Confirmar que `IosNotificationTapDelegate` procesa tanto app
   cerrada como en background y que la navegación espera a que el host autenticado esté
   instalado. Decidir explícitamente la presentación foreground (`willPresent`), badge,
   sonido y agrupación. No fabricar rutas ni datos cuando no exista sesión.
5. **Configuración de firma.** Añadir una plantilla ignorada o variables de CI para
   inyectar `QUATA_APNS_ENVIRONMENT`: `development` para perfiles de desarrollo y
   `production` para distribución. Un build firmado debe fallar si el valor, Team o
   perfil es vacío/no expandido; un build sin firma de CI debe seguir funcionando.
6. **Observabilidad.** Emitir métricas agregadas por plataforma y código APNs, sin
   incluir tokens, texto del chat, teléfono, ID de usuario ni cabeceras de autorización.
   Documentar alertas por aumento de tokens inválidos y por fallo de credenciales.

No se acepta una implementación que cambie RLS, aplique migraciones automáticamente o
degrade el canal Android/Web. Si hiciera falta una evolución de esquema, se prepara como
una propuesta independiente con compatibilidad hacia atrás, revisión de seguridad y plan
de rollback; no forma parte de esta fase.

## Seguridad y ciclo de vida de secretos

- El `.p8`, service-role, `QUATA_PUSH_DISPATCH_SECRET` y cualquier token de prueba sólo
  viven en el gestor de secretos correspondiente. No van a `xcconfig`, `Info.plist`,
  artefactos, capturas, consola de Xcode ni variables impresas por CI.
- Usar mínimo privilegio: una APNs Auth Key dedicada, acceso restringido a operadores de
  backend y registro de quién la crea/rota/revoca.
- Rotación: calendarizar revisión trimestral; probar una nueva Key ID en desarrollo,
  actualizar el secreto de backend, verificar entrega y revocar la anterior sólo tras
  estabilización. Tener un procedimiento inmediato de revocación ante sospecha de fuga.
- El JWT APNs debe renovarse antes de una hora, no persistirse y no loguearse. Validar
  issuer Team ID, Key ID, algoritmo ES256 y topic explícito.
- Tratar token APNs y endpoint de Push como datos sensibles de dispositivo. Cifrado en
  tránsito, acceso backend restringido, retención mínima y borrado/deshabilitado en
  logout, cuenta eliminada y respuesta permanente del proveedor.

## Matriz mínima de validación

Los simuladores iOS no reciben APNs: sirven para los contratos Swift/Kotlin, UI y deep
links, nunca para acreditar entrega. La entrega se valida en dispositivos físicos.

| Caso | Entorno / dispositivo | Resultado exigido |
| --- | --- | --- |
| Build sin firma y XCTest | CI/simulador | Sigue verde; no requiere secreto APNs ni afirma entrega. |
| Build firmado desarrollo | iPhone físico, perfil development | `aps-environment=development`, obtiene token sin exponerlo. |
| Permiso denegado | iPhone físico | No registra ni sube token; la app y Chat siguen funcionando. |
| Permiso concedido + login | iPhone físico | Registra exactamente el token del perfil autenticado como `ios`; reintento idempotente. |
| Chat de prueba en foreground | Dos perfiles aislados | Aviso/presentación aprobada; tap lleva a la conversación y mensaje correctos. |
| Chat en background / app terminada | Dispositivo físico | APNs llega, tap restaura sesión o muestra estado honesto y abre el deep link al estar listo. |
| Logout / cambio de cuenta | Dispositivo físico | Se revoca o deshabilita el token anterior; no recibe el siguiente chat del perfil previo. |
| Token inválido APNs | Entorno controlado | Sólo el token afectado queda deshabilitado; Android/Web y otros dispositivos siguen entregando. |
| Release/TestFlight | Dispositivo físico, perfil production | Usa `api.push.apple.com`, entitlement production y topic final; no se mezcla con sandbox. |
| Regresión multiplataforma | Android API 37 y Chrome | FCM y Web Push conservan sus pruebas y contratos actuales. |

Cada ejecución debe conservar únicamente evidencia no sensible: SHA de la app, fecha,
entorno, modelo/OS, resultado, hashes o IDs redaccionados y confirmación de limpieza. No
guardar payload completo, screenshots con contenido personal, tokens ni credenciales.

## Criterios de aceptación para declarar APNs listo

1. App firmada de desarrollo y distribución con App ID, perfiles, App Group y entitlement
   verificados en dispositivo físico.
2. Token APNs asociado de forma autenticada al perfil correcto, con logout, rotación y
   fallo controlado; sin acceso directo del cliente a tablas internas.
3. Dispatcher separa explícitamente Android/FCM, Web Push y APNs; una prueba de iOS no
   puede deshabilitar ni enviar por error un token de otra plataforma.
4. Entrega real de un chat de prueba en foreground, background y terminada, seguida de
   tap al deep link común y limpieza de cuentas/tokens de prueba.
5. Pruebas Android, Web, Kotlin/Native, XCTest y CI continúan verdes y no hay cambios RLS
   ni despliegues de base de datos no aprobados.
6. Secretos, privacidad, rotación, observabilidad y rollback están aprobados por el
   propietario y documentados fuera del repositorio cuando contengan datos sensibles.

Hasta que se cumplan todos, el estado correcto es **plumbing de APNs presente; entrega
APNs no verificada**, no “push iOS listo”.
