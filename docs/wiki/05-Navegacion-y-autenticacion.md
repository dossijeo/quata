# Navegación y autenticación

## Regla de producto

Feed, Oficial, Comunidades, notificaciones públicas y perfiles públicos deben poder consultarse sin autenticación cuando el backend los exponga públicamente.

Las acciones privadas no sustituyen el contenido visible por Login. Presentan el diálogo común de autenticación sobre la pantalla actual.

## Rutas de autenticación

Login, Registro y Recuperación son pantallas completas situadas fuera del shell principal. Deben utilizar las mismas raíces Compose comunes en Android, Web e iOS.

## Acción pendiente

Cuando una acción requiere sesión:

1. Se conserva la ruta y, cuando proceda, la acción solicitada.
2. Se muestra el gate común sobre el contenido.
3. Si el usuario acepta, se abre Login o Registro.
4. Tras autenticación correcta, se restaura el origen y se reanuda la intención permitida.
5. Si cancela, se descarta la intención pendiente sin cambiar de pantalla.

No se debe redirigir siempre a Feed ni perder el origen.

## Logout

El cierre de sesión debe:

- Revocar o limpiar la sesión de plataforma.
- Desregistrar o desvincular tokens push cuando corresponda.
- Limpiar estado privado y acciones pendientes.
- Reconstruir el shell público.
- Volver al Feed público.

## Deep links

Los enlaces a posts, perfiles, comunidades o conversaciones se resuelven mediante navegación de producto común. Si la ruta es privada, se conserva como intención pendiente hasta autenticar.

Web puede representar la ruta mediante fragmentos hash e iOS/Android mediante mecanismos del sistema, pero el resultado visible y el retorno deben coincidir.

## Antipatrones

- Login global antes de mostrar cualquier contenido.
- Pantalla “no disponible en esta plataforma”.
- Callback que guarda un ID pero no cambia la ruta.
- Perfil público que exige sesión por error del repositorio.
- Auth satisfactoria que siempre vuelve a una ruta fija.
- Logout que deja sesión, push o cache asociados al usuario anterior.
