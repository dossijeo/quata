# Entrega dinámica de modelos de subtítulos

## Objetivo

Evitar que todos los usuarios descarguen los tres modelos de subtítulos automáticos si solo necesitan el idioma de su dispositivo.

## Implementación preparada

Los modelos Vosk se entregan como módulos dinámicos:

- Inglés: `vosk_model_en`
- Español: `vosk_model_es`
- Francés: `vosk_model_fr`

La app debe solicitar el módulo correspondiente al idioma del sistema:

- Español: descarga `vosk_model_es`.
- Francés: descarga `vosk_model_fr`.
- Cualquier otro idioma: descarga `vosk_model_en`.

Si el usuario cambia el idioma del sistema y la app pasa a otro idioma soportado, puede pedir la descarga del paquete que falta.

## Validación realizada

El AAB inspeccionado no incluye los modelos Vosk en el módulo base. Los modelos aparecen dentro de sus módulos dinámicos respectivos.

## Prueba pendiente obligatoria

La descarga real bajo demanda debe verificarse instalando desde Google Play Internal testing o Closed testing. Instalar un AAB localmente no reproduce completamente la entrega dinámica de Play.

## Flujo de QA recomendado

1. Dispositivo en español.
2. Instalar desde Internal testing.
3. Abrir función de subtítulos.
4. Confirmar solicitud/descarga del modelo español.
5. Cambiar idioma del sistema a francés.
6. Abrir de nuevo la función de subtítulos.
7. Confirmar solicitud/descarga del modelo francés.
8. Cambiar a idioma no soportado, por ejemplo alemán.
9. Confirmar fallback a inglés.
10. Confirmar que modelos no usados pueden limpiarse si la app lo implementa.
