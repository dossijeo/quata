# Autorización permanente de operaciones remotas

Concedida por el propietario el 9 de septiembre de 2026. Texto íntegro de la directiva; vigente hasta revocación o limitación explícita.

AUTORIZACIÓN PERMANENTE PARA OPERACIONES REMOTAS DE LA MIGRACIÓN DE QÜATA

A partir de ahora tienes autorización explícita y continuada para ejecutar por tu cuenta las operaciones remotas necesarias para completar y certificar las unidades de la migración multiplataforma de Qüata.

Esta autorización pretende evitar que el trabajo quede bloqueado esperando una confirmación humana cada vez que sea necesario desplegar, activar temporalmente, probar, restaurar o desactivar una pieza ya revisada.

NO vuelvas a detenerte para pedirme autorización individual para estas operaciones cuando estén dentro de las condiciones siguientes.

======================================================================
1. SUPABASE: AUTORIZACIÓN GENERAL
======================================================================

Estás autorizado a:

- desplegar nuevas revisiones de Edge Functions necesarias para una unidad focal;
- desplegar paquetes versionados/revisados preparados específicamente para la migración;
- actualizar una Edge Function existente cuando el cambio:
  - pertenece al alcance focal actual;
  - conserva compatibilidad con producción;
  - ha pasado revisión/preflight;
  - tiene rollback conocido;
- activar y desactivar feature flags o secrets utilizados únicamente como interruptores operativos;
- activar temporalmente escritura para fixtures autorizados;
- desactivarla de nuevo tras el ensayo;
- ejecutar llamadas reales contra backend con cuentas/fixtures autorizados;
- crear y eliminar fixtures temporales;
- crear/revocar sesiones de prueba propias;
- restaurar snapshots de los fixtures;
- ejecutar sondas reales de compatibilidad;
- consultar estado remoto, versiones, configuración, funciones desplegadas y esquema;
- descargar nuevamente una función desplegada para comprobar hashes;
- volver a desplegar una revisión conocida y verificada para rollback;
- realizar las activaciones/desactivaciones necesarias para repetir una evidencia focal cuando una repetición esté técnicamente justificada.

Para estas operaciones NO necesitas volver a pedirme permiso.

En particular, para ACCOUNT-RECOVERY-SECRET y unidades equivalentes:
puedes desplegar, activar temporalmente el productor, ejecutar Android/Web/iOS, restaurar la cuenta y volver a desactivar la escritura sin solicitar confirmación entre etapas.

======================================================================
2. CONDICIONES OBLIGATORIAS ANTES DE UNA MUTACIÓN REMOTA
======================================================================

Antes de mutar producción/Supabase debes comprobar tú mismo:

1. que el cambio pertenece realmente a la unidad focal;
2. que el diff remoto exacto que vas a desplegar es el revisado;
3. que hashes/config/package coinciden con lo esperado;
4. que no existe una modificación concurrente que vaya a ser sobrescrita;
5. que la compatibilidad con el Android publicado y otros consumidores conocidos está preservada;
6. que el fixture está identificado y autorizado;
7. que existe snapshot/estado previo suficiente para restitución;
8. que existe una ruta concreta de rollback;
9. que las sesiones/mutaciones propias quedan registradas antes de realizarlas;
10. que los secretos o credenciales no van a quedar en logs, argv, commits, screenshots ni artefactos públicos.

Si todas estas condiciones están satisfechas:
ACTÚA.
No esperes otra autorización.

======================================================================
3. RESTITUCIÓN OBLIGATORIA
======================================================================

Toda operación temporal debe terminar intentando dejar:

- datos del fixture restaurados exactamente;
- contraseña original restaurada;
- secretos/preguntas restaurados;
- sesiones propias revocadas;
- Storage/filas temporales eliminadas;
- journals/locks temporales retirados sólo tras reconciliación;
- feature flags temporales devueltos al estado seguro;
- funciones/versión remota en el estado previsto;
- cero recursos propios residuales.

Si un test falla:
primero reconcilia/restaura el estado;
después decide si procede repetir.

No repitas una mutación incierta.

======================================================================
4. NO BLOQUEARSE ANTE DEPLOYS NORMALES
======================================================================

Frases como:

- “falta autorización para desplegar”
- “esperando permiso para activar”
- “no puedo continuar hasta que el propietario autorice”
- “necesito confirmación antes de cambiar temporalmente el flag”

NO deben volver a bloquear la migración cuando la operación entra dentro de esta autorización.

Si el cambio cumple las condiciones anteriores:
continúa autónomamente.

Puedes informar de lo que vas a hacer, pero la información NO es una solicitud de aprobación.

Ejemplo:

“Voy a desplegar la revisión compatible X, verificaré el hash remoto, activaré el flag sólo durante el fixture autorizado y lo devolveré a false tras la restitución.”

Después ejecuta inmediatamente.

======================================================================
5. QUÉ SIGUE REQUIRIENDO MI AUTORIZACIÓN
======================================================================

Esta autorización NO cubre automáticamente:

- cambios destructivos o irreversibles sobre datos reales ajenos al fixture;
- borrado masivo de datos;
- DROP/TRUNCATE;
- migraciones destructivas de esquema;
- cambios amplios de RLS/grants/policies que alteren seguridad de usuarios reales;
- rotación o eliminación de credenciales/secrets de producción no creados para el ensayo;
- despliegues de infraestructura ajena a Qüata;
- cambios de billing;
- cambios de DNS/dominio;
- publicación en Google Play/App Store;
- operaciones que puedan alertar/contactar a terceros reales, como SOS;
- cambios deliberadamente incompatibles con la versión publicada;
- cualquier operación para la que no exista rollback razonable.

En esos casos sí debes detenerte y explicar exactamente qué necesitas.

Pero no conviertas una duda genérica en motivo para parar:
primero determina si la operación cabe dentro de la autorización permanente.

======================================================================
6. COMPATIBILIDAD CON PRODUCCIÓN
======================================================================

La app Android publicada es una referencia de producto.

Cuando una operación backend pueda afectar a clientes publicados:

- inspecciona el AAB/APK publicado cuando sea necesario;
- verifica el contrato real consumido por esa versión;
- preserva compatibilidad mientras existan esos clientes;
- prefiere dual-read/dual-write o una transición compatible cuando proceda;
- no despliegues una mejora de seguridad que rompa el cliente publicado.

El descubrimiento realizado con el AAB v32 de ACCOUNT-RECOVERY-SECRET es el patrón correcto:
si el artefacto publicado contradice una propuesta, prevalece la compatibilidad real y se rediseña el cambio.

No necesitas mi permiso para realizar este tipo de investigación estática.

======================================================================
7. AUTORIZACIÓN PARA CERTIFICACIÓN
======================================================================

También tienes autorización para ejecutar de forma autónoma:

- Android Emulator;
- Web/Wasm local;
- macOS VM;
- iOS Simulator;
- Compose UI tests;
- Maestro;
- XCTest/XCUI cuando corresponda;
- builds locales;
- build-for-testing;
- capturas;
- xcresult;
- fixtures backend;
- coordinadores de evidencia;
- limpieza;
- restauración;
- reintentos técnicamente justificados.

No esperes autorización entre plataformas.

Cuando una plataforma termina:
pasa a la siguiente conforme al workflow.

======================================================================
8. REINTENTOS
======================================================================

La autorización no significa repetir indefinidamente.

Mantén las stop rules:

- un fallo conocido y corregido puede repetirse;
- una operación incierta no se repite hasta reconciliar estado;
- tercer workaround para la misma interacción UI → detener y replantear approach;
- fallo unrelated → no ampliar scope;
- no construir infraestructura ilimitada para conseguir un verde.

======================================================================
9. DEPLOY ≠ AUTORIZACIÓN PARA EXPANDIR SCOPE
======================================================================

Tener permiso para desplegar no autoriza a:

- arreglar otras features;
- cambiar Auth entero;
- modificar RLS “ya que estamos”;
- endurecer backend fuera del requisito focal;
- actualizar módulos ajenos;
- resolver deuda técnica no necesaria.

Sigue vigente:
UNA UNIDAD FOCAL, UN ALCANCE.

======================================================================
10. REGLA OPERATIVA FINAL
======================================================================

Si una operación:

- pertenece a la unidad focal,
- es reversible,
- usa únicamente fixtures/cuentas autorizadas,
- preserva compatibilidad conocida,
- tiene snapshot/cleanup/rollback,
- y ha pasado la revisión/preflight correspondiente,

ENTONCES ESTÁS AUTORIZADO A EJECUTARLA SIN PREGUNTARME.

No conviertas la necesidad de un deploy/activación temporal en un bloqueo humano.

Esta autorización permanece vigente para el resto de la migración salvo que yo la revoque o limite explícitamente.
