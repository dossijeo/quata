# SB-02: Auth y sesión con clave publicable

`scripts/run-supabase-e2e-sb02.ps1` valida contra Supabase real el recorrido Web de login, forma de persistencia de sesión en memoria, refresh, logout Web y un login posterior. Sólo usa la URL y clave **publicable**; no acepta URL, contraseña, token ni clave como argumento, no usa service-role y no cambia DDL, funciones ni políticas.

## Precondición de cuenta

Web no implementa registro (`WebAuthRepository.register` devuelve `web_auth_registration_contract_unavailable`) y el alta Android actual crea perfiles mediante una capacidad de confianza que no existe en el navegador. No existe un alta Web pública y segura que SB-02 pueda automatizar. `-CreateUser` falla antes de hacer red. Un operador debe aprovisionar y autorizar previamente una cuenta **aislada y efímera** mediante el flujo permitido; no se permite usar una cuenta personal ni de producción. El contrato mínimo para habilitarlo está en [WEB_AUTH_REGISTRATION_CONTRACT.md](WEB_AUTH_REGISTRATION_CONTRACT.md).

La contraseña no se imprime ni se escribe en el informe. Cada logout Web ejerce el mismo endpoint que el cliente (`quata-web-push`, acción `logout`); al finalizar, el runner revoca globalmente las sesiones de esa cuenta como limpieza defensiva. No borra la cuenta, pues no la creó. El operador que la aprovisionó debe eliminarla por su flujo autorizado y anotar esa limpieza en el tablero.

## Ejecución

```powershell
$env:QUATA_SUPABASE_URL = 'https://<project-ref>.supabase.co'
$env:QUATA_SUPABASE_PUBLISHABLE_KEY = '<publishable-key>'
$env:QUATA_E2E_COUNTRY_CODE = '<country-code>'
$env:QUATA_E2E_PHONE = '<isolated-e2e-phone>'
$env:QUATA_E2E_PASSWORD = '<isolated-e2e-password>'
.\scripts\run-supabase-e2e-sb02.ps1 -AllowExistingTestUser
```

El informe local contiene sólo nombres de pasos, estado y estado de limpieza; nunca URL, número, ID de usuario, contraseña, refresh token ni access token. Un `rollback_pending` exige revocar las sesiones de la cuenta aislada antes de continuar SB-03/SB-04. Un resultado correcto no sustituye el smoke de navegador real: aquí la persistencia se comprueba mediante la misma serialización de campos en memoria para evitar escribir credenciales en disco.
