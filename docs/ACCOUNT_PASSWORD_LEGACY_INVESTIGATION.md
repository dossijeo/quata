# ACCOUNT-PASSWORD-LEGACY: trazabilidad del campo histórico

Estado: investigación estática completada y revisada independientemente; integración documental pendiente.
No introduce una función de cambio de contraseña ni reabre ACCOUNT-DETAILS.

## Referencia fijada

El propietario confirmó el AAB publicado `quata-release-v32.aab`, código 32 / versión 1.0.4.
SHA-256 revalidado: `bf6aadc60e18b05d4f4203c8356a9e9a8b4c917262cc0a1d28518b8c6baf70ff`.
Se consultó primero el commit «Versión 1.0.4», `1b8f3b70c21c361e7566b69d0bcc4fbeb9f96c55`
(código 31), y después `f0fd50ca65ca35372093c127fb09e379743274ff` (código 32).
El diff de `app/src/main/java/com/quata/feature/profile` entre ambos está vacío.
Esto no identifica por sí solo el commit exacto del build; por eso se contrastó el binario.

## Recorrido de fuente y binario

En `f0fd50ca`, `ProfileScreen.kt:421` muestra `state.newPassword`; el cambio emite
`NewPasswordChanged`. `ProfileViewModel.kt:44` conserva el texto y `:112` lo pasa a
`ProfileUpdate` al guardar. Tras éxito, `:125` vacía el campo y muestra el éxito general.

`ProfileRepositoryImpl.saveProfile` llama a `remote.saveProfile(..., toRemotePatch())`.
`toRemotePatch` sólo envía nombre, barrio, prefijo/teléfono, avatar y pregunta/respuesta
secreta. No utiliza `newPassword`. `ProfileRemoteDataSource.saveProfile` entrega ese mapa
a `SupabaseCommunityApi.updateProfile`; no añade una llamada Auth de cambio de contraseña.
La rama mock tampoco consume `newPassword`.

El mapping R8 y el DEX extraídos coinciden por hash con las entradas del AAB:

| Entrada | SHA-256 |
|---|---|
| `base/dex/classes.dex` | `51aa7c0c8e987847d2bcd80bd1d24380c1f5afc66bf1891cf94e67a375c0bcc3` |
| `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map` | `670f59516a727c84537fdd12e9ae6da26b8d84a70f72ac6047c3763313ec2320` |

R8 fusionó clases: el encabezado contenedor de `zf3` lleva otro nombre; se siguieron las
entradas cualificadas de `ProfileRepositoryImpl`, no se dedujo identidad del encabezado.
`ProfileUpdate.newPassword` se corresponde con `ug4.f`.

- `toRemotePatch` → `zf3.a0`, offset DEX `0x3b5090`: el método completo no lee `ug4.f`.
  Construye las claves de perfil citadas, sin `pass_hash`, `pass_plain` ni `password`.
- `saveProfile` → `zf3.O`, offset `0x3b8814`: invoca el patch en `0x3b895c` y
  `SupabaseCommunityApi.updateProfile` (`mi4.O0`) en `0x3b8980`. El resto guarda contactos,
  preferencias del mensaje de emergencia y nombre de sesión; no hay otra escritura de contraseña.

## Conclusión y decisión de alcance

El control publicado aceptaba texto y podía mostrar el éxito general de Guardar, pero esa
contraseña no se transmitía al backend. Es un campo histórico sin escritura funcional, no una
capacidad de cambio autenticado de contraseña pendiente de portar. Un trigger del servidor no
puede aplicar el valor introducido si el cliente no lo envía.

Mantener el comportamiento migrado vigente que explica el uso de «Olvidé mi contraseña».
No recuperar un control que promete una escritura ausente ni inventar un endpoint de cambio
autenticado dentro de esta investigación. Una función nueva requeriría su propio requisito y
contrato de producto; no queda autorizada ni implementada por este documento.

## Límites de la evidencia

Es trazabilidad estática del AAB confirmado y de la fuente histórica, sin login, cambios de
credenciales ni mutaciones Supabase. No es un nuevo E2E del Android publicado. No cierra la
recuperación autorizada (unidad separada ACCOUNT-RECOVERY-SECRET), ni prueba otras versiones
publicadas. La identidad del AAB no equivale a identificar el APK firmado/distribuido por Play.
Los extractos completos de los métodos y del mapping se conservan en el directorio local
`build-reports/account-password-legacy/`; no contienen datos de cuentas.
