# Seguridad y releases

## Principios

- Ningún secreto entra en clientes o documentación publicada.
- Los cambios de base de datos se separan de la migración visual/funcional.
- La compatibilidad con Android publicado se conserva hasta una transición de release explícita.
- Los gates reales usan cuentas y fixtures aislados con limpieza.
- Un fallo de seguridad no se oculta, pero tampoco se usa como excusa para crear un port falso.

## Secretos

Pertenecen a gestores protegidos, no a Git:

- Claves `service_role`.
- URLs de conexión de PostgreSQL.
- Certificados y CA privadas.
- Credenciales de usuarios de prueba.
- Claves APNs `.p8`.
- Credenciales Firebase Admin.
- Claves privadas VAPID.
- Claves de proveedores de traducción.
- Certificados y perfiles de firma.

Las URL públicas y publishable keys pueden formar parte del artefacto cliente cuando el modelo de seguridad/RLS está diseñado para ello.

## Base de datos

No se ejecuta `supabase db push` sobre producción sin reconciliación completa del historial. Los releases de RLS/DDL requieren:

1. Snapshot y backup recuperable.
2. Paquete forward acotado.
3. Rollback probado.
4. Validación de catálogo.
5. E2E aislado.
6. Postflight y documentación.

## Web

Un release Web debe incluir bundle identificable, revisión de configuración pública, service worker, smoke de rutas y comprobación de que no aparecen claves privadas. Los metadatos requeridos, como versión de release, deben inyectarse determinísticamente.

## iOS

Compilar el framework y el host no equivale a publicar. Un release necesita Team/App IDs, perfiles, firma, entitlements, archive/IPA, prueba en dispositivo y canal APNs verificado.

## Android

Android sigue siendo el cliente publicado. Los cambios comunes deben mantener sus contratos, permisos, comportamiento offline y servicios del sistema. El pipeline no puede sacrificar Android para hacer pasar un port.

Referencias:

- [Database release safety](https://github.com/dossijeo/quata/blob/main/docs/DATABASE_RELEASE_SAFETY.md)
- [Requisitos APNs](https://github.com/dossijeo/quata/blob/main/docs/IOS_APNS_PRODUCTION_REQUIREMENTS.md)
- [Firma iOS](https://github.com/dossijeo/quata/blob/main/docs/IOS_SIGNING_RELEASE.md)
