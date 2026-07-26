# SB-09: integridad de likes Official

`scripts/run-supabase-e2e-sb09.ps1` valida la única mutación Official candidata
sin usar service-role, SQL, RPC ni secretos de administración: crea un like con
A, intenta que A use el perfil de B, intenta que B borre el like de A y confirma
el borrado posterior por A.

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
