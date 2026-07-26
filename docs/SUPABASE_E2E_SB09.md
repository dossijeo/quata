# SB-09: integridad de likes Official

`scripts/run-supabase-e2e-sb09.ps1` valida la única mutación Official candidata
sin usar service-role, SQL, RPC ni secretos de administración: crea likes propios
con A y B, intenta que A use el perfil de B, intenta que A borre el like de B y
confirma el borrado posterior de cada like por su propietario. También comprueba
que ambos likes siguen siendo visibles mediante SELECT anónimo, igual que antes
de activar RLS.

El runner requiere dos cuentas y un post Official creados exclusivamente como
fixtures aislados. La cuenta autora y el post no son datos de producto. Antes de
ejecutarlo se debe disponer de una purga externa autorizada de perfiles, Auth y
datos dependientes; el runner rechaza la mutación si no se declara ese contrato.

## Evidencia del 2026-07-26

SB-09 creó correctamente el like de A, pero el intento de A de escribir
`profile_id` de B no devolvió `42501`. El runner registra el ID de cualquier
like de suplantación aceptado y, durante el rollback, intenta borrarlo con la
sesión de B además del like propio de A; si alguna limpieza falla, lo declara
pendiente antes de la purga externa. Después se purgaron por el flujo de ciclo
de vida los perfiles y usuarios Auth temporales, y se verificó su ausencia junto
con el post temporal.

Por tanto, la capacidad Web Official de mutar permanece en `Unsupported`. No se
debe cambiar RLS ni publicar like/unlike hasta que una corrección coordinada de
backend haga pasar íntegramente SB-09.

## Prueba posterior a la migración candidata

La migración `20260726171002_official_post_likes_actor_guard.sql` no se prueba
contra producción desde esta rama. Tras aplicarla en staging, SB-09 debe demostrar:

1. A y B pueden crear su propio like.
2. A no puede insertar con `profile_id` de B y recibe `42501`.
3. A no puede borrar el like de B y recibe `42501`; la fila sigue presente.
4. A y B pueden borrar sus likes propios.
5. SELECT anónimo ve los likes creados y su desaparición posterior.
6. El rollback del runner elimina cualquier fila aceptada antes de la purga dura.
7. El operador purga cada cuenta en una transacción separada, elimina Auth y
   comprueba ausencia de perfiles, usuarios, post y likes.
