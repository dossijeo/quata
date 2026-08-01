# Mantenimiento de la Wiki

Las páginas de la GitHub Wiki se mantienen desde `docs/wiki` para que los cambios puedan revisarse junto al código.

## Autoridad

La Wiki es una presentación navegable. En caso de conflicto prevalecen:

1. Código y contratos de `main`.
2. Documentos normativos de `docs`.
3. Evidencia del SHA exacto.
4. Páginas publicadas de la Wiki.

## Publicación

La primera página debe crearse desde la interfaz de GitHub para inicializar `quata.wiki.git`. Después:

```powershell
.\scripts\sync-github-wiki.ps1
```

El script clona la Wiki en un directorio temporal, copia exclusivamente las páginas declaradas y muestra el diff. Para publicar:

```powershell
.\scripts\sync-github-wiki.ps1 -Push
```

El script no borra páginas desconocidas. Si una página deja de existir, su eliminación debe revisarse y ejecutarse expresamente en el repositorio Wiki.

## Actualización de estado

Después de fusionar una pantalla:

1. Actualizar matriz e inventario.
2. Enlazar evidencia exacta.
3. Actualizar `10-Estado-del-proyecto.md` si cambia la situación general.
4. Sincronizar la Wiki.

No se copia el cuerpo de una PR como estado consolidado.
