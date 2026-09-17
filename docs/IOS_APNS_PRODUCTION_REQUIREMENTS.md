# Requisitos de producción para notificaciones iOS/APNs

Este documento define la preparación necesaria para que Qüata entregue notificaciones
remotas en iOS de forma verificable, sin alterar la aplicación Android publicada, el
cliente Web existente ni las políticas RLS actuales. Es un plan de requisitos: no
autoriza despliegues de Supabase, cambios de RLS ni la publicación de una app.

## Checklist de preparación para el propietario

La [identidad local Personal Team validada](IOS_LOCAL_DEVELOPMENT_SIGNING.md) permite
comprobaciones de firma local sin capacidades restringidas. No satisface los requisitos
de APNs, App Groups ni distribución de esta lista.

Esta lista conserva los requisitos pendientes para certificar la entrega real.
Los valores identificativos pueden anotarse en el ticket privado de lanzamiento; los
secretos se cargan exclusivamente en el almacén indicado. **No pegar ninguno en este
documento, Git, chats, capturas, `.xcconfig`, artefactos ni logs de CI.**

| Hecho | Activo o decisión | Dónde se obtiene | Dónde se carga o registra | Observación exigida |
| --- | --- | --- | --- | --- |
| [ ] | Apple Developer Team ID | Apple Developer → Membership | Gestor seguro de firma / variable de CI `QUATA_DEVELOPMENT_TEAM` | Debe ser el equipo propietario de la distribución. |
| [ ] | App ID principal `com.quata.ios` | Certificates, Identifiers & Profiles → Identifiers | Apple Developer | Explícito, no wildcard; habilitar Push Notifications y App Groups. |
| [ ] | App ID extensión `com.quata.ios.shareextension` | Apple Developer → Identifiers | Apple Developer | Habilitar el App Group; Push no es necesario para la extensión actual. |
| [ ] | App Group `group.com.quata.ios.share` | Apple Developer → Identifiers → App Groups | Asociarlo a los dos App IDs | Debe coincidir exactamente con los entitlements versionados. |
| [ ] | Certificado Apple Distribution y perfiles de distribución | Apple Developer → Profiles | Keychain temporal y almacén seguro del runner | Un perfil por bundle ID; nunca versionar `.p12` ni `.mobileprovision`. |
| [ ] | Perfiles de desarrollo para dispositivo físico | Apple Developer → Profiles | Sólo Mac/keychain autorizado | Necesarios para comprobar sandbox y entitlement efectivo. |
| [ ] | APNs Auth Key `.p8`, Key ID y Team ID | Apple Developer → Keys | **Sólo** Supabase Edge Function secrets | La `.p8` se descarga una vez; crear una dedicada al producto. |
| [ ] | Topic APNs | Derivado del App ID principal | Secreto/configuración de Edge Function | Inicialmente `com.quata.ios`; no usar el bundle de la extensión. |
| [ ] | Entorno APNs por release | Perfil/entitlement de cada build | Configuración de firma y Edge Function | Desarrollo → sandbox; TestFlight/App Store → producción. |
| [ ] | Credenciales runtime públicas | Configuración existente de Qüata/Supabase | Secretos/variables de CI ya establecidos | `QUATA_SUPABASE_URL` y `QUATA_SUPABASE_PUBLISHABLE_KEY` no sustituyen secretos APNs. |
| [ ] | Dos cuentas de prueba y permiso de enviar chat | Operación de Qüata | Gestor de secretos efímeros del E2E | Un emisor y un receptor; limpiar tokens/cuentas al terminar. |
| [ ] | Privacidad, responsable on-call y rollback | Propietario de producto/operaciones | Ticket y runbook privado | Debe poder desactivar sólo APNs sin afectar Android ni Web. |

### Variables y secretos: destino correcto

Los siguientes nombres describen la configuración de la implementación actual; su
carga y activación deben cumplir las autorizaciones y los gates aplicables. Se prefija APNs
para impedir reutilización accidental de VAPID, FCM o credenciales de sesión.

| Ámbito | Nombre | Valor | Custodia |
| --- | --- | --- | --- |
| Firma/CI | `QUATA_DEVELOPMENT_TEAM` | Team ID (identificador, no clave) | Secretos/variables protegidos del runner. |
| Firma/CI | `QUATA_IOS_APP_PROVISIONING_PROFILE` | Nombre o UUID del perfil principal | Secretos/variables protegidos del runner. |
| Firma/CI | `QUATA_IOS_SHARE_EXTENSION_PROVISIONING_PROFILE` | Nombre o UUID del perfil de extensión | Secretos/variables protegidos del runner. |
| Edge Function | `QUATA_APNS_AUTH_KEY_P8` | Clave privada PKCS#8 en formato PEM | Supabase project secrets para el proveedor; nunca el cliente. |
| Edge Function | `QUATA_APNS_KEY_ID` | Key ID de Apple | Supabase project secrets. |
| Edge Function | `QUATA_APNS_TEAM_ID` | Team ID emisor del JWT | Supabase project secrets. |
| Edge Function | `QUATA_APNS_TOPIC` | `com.quata.ios` inicial | Supabase project secrets/configuración protegida. |
| Cliente/firma | `QUATA_APNS_ENVIRONMENT` | `development` o `production` | Se traduce a `sandbox`/`production` y se conserva en cada registro APNs. |
| Cliente | `QUATA_IOS_APNS_ENABLED` | `true` para nuevos registros; predeterminado `false` | Configuración de build. |
| Edge Function | `QUATA_APNS_ENABLED` | `true` habilita el proveedor; cualquier otro valor lo deshabilita | Supabase project secrets/configuración protegida. |

La clave de firma de la app (certificado y clave privada), los perfiles y la Auth Key
APNs son activos distintos. La Auth Key `.p8` **no** sirve para firmar una IPA y el
certificado de distribución **no** sirve para autenticar el proveedor APNs.

### Correspondencia estricta de entornos

| Build que se prueba | Entitlement `aps-environment` | Host proveedor | Topic | Evidencia mínima |
| --- | --- | --- | --- | --- |
| Desarrollo firmado en iPhone | `development` | `api.sandbox.push.apple.com` | `com.quata.ios` | Token obtenido, chat de prueba y tap; sin exponer token. |
| TestFlight/App Store | `production` | `api.push.apple.com` | `com.quata.ios` | Archivo firmado, entrega y deep link en dispositivo real. |
| CI/simulador sin firma | No acredita entitlement | No envía a APNs real | No aplica | Compilación/XCTest/payload de simulador solamente. |

No cruzar filas: un token sandbox no se prueba contra producción y un build de
distribución no se anuncia como sandbox. La configuración `Release` actual fija
`QUATA_APNS_ENVIRONMENT=production`; el perfil y el archive firmados deben confirmar
que esa expansión es la efectiva. Para desarrollo se requerirá una configuración de
firma explícita, no editar entitlements manualmente.

## Alcance y estado de partida

La rama incorpora el runtime autenticado, journal Keychain, transporte RPC y proveedor
APNs directo. [Registro APNs](IOS_APNS_REGISTRATION.md) describe el ciclo de sesión y
la correspondencia de entornos. Esto todavía no acredita entrega real.

- `IosApnsLifecycleBridge` conecta tokens y estado del permiso al runtime compartido.
- `QuataIos.entitlements` mantiene `$(QUATA_APNS_ENVIRONMENT)`; los bundle IDs y el App
  Group siguen siendo `com.quata.ios`, `com.quata.ios.shareextension` y
  `group.com.quata.ios.share`.
- El paquete SQL aditivo prepara `apns_environment`, `quata_register_apns_token` y
  `quata_reserve_apns_delivery`; su despliegue sigue pendiente del gate de historial.
  El registro Android existente y su RLS permanecen intactos.
- El dispatcher distingue tokens iOS y no los envía a FCM, incluso con APNs apagado.
  El proveedor requiere `QUATA_APNS_ENABLED=true` y configuración válida. El entorno
  procede de cada registro, no se infiere del token.

El inbox de Notifications no depende del proveedor: se deriva de conversaciones de
Chat. APNs debe despertar y llevar al deep link común `conversation_id`/`thread_id`/
`message_id`; no debe crear un segundo inbox ni cambiar el significado de los payloads
Android o Web.

## Arquitectura implementada

La implementación utiliza **APNs directo con autenticación por token `.p8`**.
La app incorpora el registro y los callbacks de token APNs; la obtención real depende
del entorno y no está acreditada en el Simulator actual. El dispatcher es el emisor central de chat; el canal
APNs en ese backend conserva FCM para Android y Web Push para navegador.

No se deben mezclar tokens APNs con FCM ni enviar una clave de Apple a la aplicación.
La alternativa Firebase Cloud Messaging para iOS exigiría configurar Firebase iOS,
GoogleService-Info y cambiar el ciclo de token. No es el camino propuesto ni debe
introducirse parcialmente.

## Material de firma y operación pendiente

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
renovaciones más frecuentes y no simplifica el servidor. La Auth Key `.p8` no se incorpora a la IPA ni al repositorio. El propietario ha
autorizado crear los activos, conservar copia local y cargar los secretos donde sean
necesarios. La ruta local prevista es `C:\Users\PC\Desktop\QÜATA\Apple-signing`; el
proveedor consume la clave desde Supabase. La copia privada no es un artefacto de CI.

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

Los nombres de configuración actuales figuran en la tabla anterior. No deben
reutilizar VAPID, credenciales FCM ni secretos de login. El repositorio contiene
integraciones Web y Android que deben permanecer independientes:

- Android continúa con FCM y los RPC `quata_register_push_token` /
  `quata_unregister_push_token`.
- Web continúa con `quata-web-push`, VAPID y sesiones Web aisladas.
- APNs usa `quata_register_apns_token` con entorno explícito y el RPC de retirada
  existente; el cliente no accede directamente a `push_tokens` ni requiere nueva RLS.

Las operaciones remotas se rigen por la [autorización permanente](MIGRATION_REMOTE_OPERATIONS_AUTHORIZATION.md).
Debe mantenerse una recuperación revisada y la capacidad de deshabilitar sólo APNs;
este documento no exige repetir permisos ya concedidos ni elimina gates pendientes.

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

## Integración y validación pendientes

La implementación se integra en una PR separada y revisable. Se debe validar:

1. **Cliente iOS.** Conectar un `IosApnsTokenHost` autenticado al bridge después de
   restaurar/iniciar sesión, registrar el token mediante `quata_register_apns_token`
   con entorno explícito, reintentar de forma acotada y eliminar/deshabilitar el token en
   logout. No guardar el token en texto ni registrar su valor. Si falta sesión, dejar el
   token pendiente sólo en almacenamiento seguro y sin asociarlo a otro perfil.
2. **Servidor emisor.** Separar los destinos por plataforma en
   `quata-push-dispatch`: FCM sólo para Android, Web Push sólo para suscripciones Web,
   y APNs sólo para `platform = 'ios'`. Firmar JWT ES256 de corta vida con la `.p8`,
   enviar los headers APNs obligatorios `apns-topic` y `apns-push-type`, y construir
   `aps.alert` más los campos de deep link comunes. La prioridad y expiración se
   definen y prueban como política de producto/entrega; no son requisitos universales
   del protocolo para todos los mensajes.
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
degrade el canal Android/Web. La evolución aditiva `20260914135400` se aplicó
selectivamente el 17 de septiembre de 2026, tras compatibilidad hacia atrás, revisión
independiente, preflight, ledger único y plan de recuperación. No cambió RLS ni el RPC
Android, y el despliegue no alteró tokens ni logs existentes. El rollback versionado
rechaza datos iOS, deriva de funciones y dependencias posteriores antes de retirar la
columna o los RPC.

## Seguridad y ciclo de vida de secretos

- El `.p8` se custodia en el gestor de secretos y en la copia privada local autorizada.
  La service-role, `QUATA_PUSH_DISPATCH_SECRET` y los tokens de prueba se custodian en
  los almacenes seguros correspondientes. No van a `xcconfig`, `Info.plist`,
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

Para `FLOW-PUSH-LIFECYCLE`, la decisión del propietario del 15/09/2026 sustituye
la exigencia de dispositivo físico como bloqueo del flujo por la validación
separada de [Simulator y proveedor](IOS_PUSH_SIMULATOR_VALIDATION.md). La matriz
siguiente conserva los requisitos de entrega y distribución de producción;
no impide completar el alcance verificable del ciclo de vida en Simulator.

Los simuladores iOS modernos pueden participar en pruebas de Remote Push y son evidencia
útil del payload, presentación y deep link. Complementan los contratos Swift/Kotlin y la
UI, pero no sustituyen un dispositivo físico/TestFlight firmado: sólo éste acredita el
entitlement efectivo, los perfiles, el entorno APNs final y la distribución real.

| Caso | Entorno / dispositivo | Resultado exigido |
| --- | --- | --- |
| Build sin firma y XCTest | CI/simulador | Sigue verde; no requiere secreto APNs ni afirma entrega. |
| Remote Push de simulador | Simulador iOS moderno | El payload aprobado se presenta y el tap resuelve el deep link; evidencia complementaria, no sustituto de firma/entorno APNs real. |
| Build firmado desarrollo | iPhone físico, perfil development | `aps-environment=development`, obtiene token sin exponerlo. |
| Permiso denegado | iPhone físico | No registra ni sube token; la app y Chat siguen funcionando. |
| Permiso concedido + login | iPhone físico | Registra exactamente el token del perfil autenticado como `ios`; reintento idempotente. |
| Chat de prueba en foreground | Dos perfiles aislados | Política de presentación equivalente a Android y continuidad de Chat; no exigir banner ni tap cuando esa política suprima el aviso. |
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
4. Entrega real de un chat de prueba en foreground, background y terminada; verificar
   la política foreground y el tap al deep link común desde los avisos presentados en
   background/terminada; limpieza de cuentas/tokens de prueba.
5. Pruebas Android, Web, Kotlin/Native, XCTest y CI continúan verdes y no hay cambios RLS
   ni despliegues de base de datos no aprobados.
6. Secretos, privacidad, rotación, observabilidad y rollback están aprobados por el
   propietario y documentados fuera del repositorio cuando contengan datos sensibles.

Hasta que se cumplan todos, el estado correcto es **runtime y proveedor APNs implementados;
entrega APNs no verificada**, no “push iOS listo”.
