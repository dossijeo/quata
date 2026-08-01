# Backend y datos

## Supabase

Supabase es el backend principal para autenticación federada con el modelo legacy, perfiles, feeds, comunidades, chat, estado social, RPC, Realtime, Storage y coordinación de push.

Los clientes utilizan URL y clave publicable. Las operaciones privilegiadas viven en políticas, RPC o Edge Functions; una clave `service_role` nunca debe distribuirse en Android, Web o iOS.

## WordPress

WordPress conserva servicios de media y compatibilidad heredada, especialmente para publicación/subida de vídeo y limpieza de recursos asociados. Los ports deben reutilizar el contrato real vigente en Android o un proxy/backend equivalente; no deben presentar publicación disponible si el despliegue no proporciona ese contrato.

## Notificaciones

- Android: Firebase Cloud Messaging.
- Web: Web Push y service worker.
- iOS: APNs es el canal objetivo.
- Backend: Edge Functions y RPC coordinan registro, revocación y envío.

La disponibilidad externa de push no convierte en incompleta la UI común, pero sí impide declarar entrega de producción mientras falten firma, credenciales o prueba en dispositivo.

## Traducción

- Fang: servicio NLLB/FastAPI, con detección local y cache.
- Oficial ES/EN/FR: servicio de traducción protegido detrás de configuración/backend revisado.

Las claves privadas de proveedores no pertenecen al cliente Web ni al repositorio.

## Cache y offline

El producto Android posee caches para lecturas Supabase, conversaciones, perfiles, media y traducciones. Durante la migración:

- Una lectura cacheada puede mostrarse mientras se refresca la red.
- Las mutaciones son remotas y deben informar su fallo.
- Un valor local no puede presentarse como persistido remotamente.
- El logout debe limpiar o desvincular datos asociados a la identidad anterior.

## Compatibilidad RLS

Durante la migración prevalece la compatibilidad con los clientes publicados. Una política RLS temporalmente amplia se documenta como deuda, pero no justifica dejar Web o iOS sin funcionalidad.

Los cambios de RLS, DDL, grants, RPC o datos siguen un release independiente, con backup, forward, rollback y E2E. No se usa `supabase db push` indiscriminadamente en este repositorio.

Referencias canónicas:

- [Seguridad de releases de base de datos](https://github.com/dossijeo/quata/blob/main/docs/DATABASE_RELEASE_SAFETY.md)
- [Hallazgos RLS](https://github.com/dossijeo/quata/blob/main/docs/RLS_FINDINGS.md)
- [Gates de compatibilidad](https://github.com/dossijeo/quata/blob/main/docs/BACKEND_COMPATIBILITY_GATES.md)
