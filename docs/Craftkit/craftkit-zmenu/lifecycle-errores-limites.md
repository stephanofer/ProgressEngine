# Lifecycle, errores y límites

`craftkit-zmenu` está diseñado para ser explícito. Si algo necesario falta, falla claro. Si algo es opcional, se expone como `Optional`. Si zMenu no permite limpiar una feature, CraftKit no promete reload seguro para esa feature.

## Lifecycle recomendado

### `onEnable`

1. Resolver zMenu:

```java
this.zmenu = ZMenus.require(this);
```

2. Registrar features enable-only directamente con zMenu si hacen falta.

3. Ejecutar bootstrap reload-safe:

```java
this.zmenu.bootstrap()
    .buttons(...)
    .patterns("patterns")
    .inventories("inventories")
    .load();
```

### Comando reload

```java
public void reloadPlugin() {
    reloadConfig();
    this.zmenu.reload();
}
```

No volver a registrar enable-only features en reload.

### `onDisable`

La versión actual no expone un método `close()` o `shutdown()` porque CraftKit no es dueño del lifecycle de zMenu. Si el plugin consumidor necesita cleanup propio, debe hacerlo en su código.

## Errores principales

### `ZMenuMissingDependencyException`

Ocurre cuando:

- el plugin `zMenu` no está instalado;
- zMenu no está habilitado;
- el plugin llamado `zMenu` no implementa `MenuPlugin`.

Acción recomendada:

- revisar dependencias del plugin consumidor;
- revisar metadata `plugin.yml` o `paper-plugin.yml`;
- confirmar que zMenu arranca correctamente antes del plugin consumidor.

### `ZMenuMissingServiceException`

Ocurre cuando falta un service obligatorio:

- `InventoryManager`
- `ButtonManager`
- `PatternManager`

Acción recomendada:

- revisar versión de zMenu;
- revisar si zMenu falló parcialmente en `onEnable`;
- confirmar compatibilidad con `zmenu-api:1.1.1.4`.

### `ZMenuException`

Ocurre para errores generales del módulo, especialmente carga de archivos.

Los mensajes de carga incluyen:

- categoría (`inventory`, `pattern`, `action pattern`, `dialog`, `bedrock`);
- path del archivo;
- nombre del plugin consumidor;
- causa original.

## Inventario faltante al abrir

Si se llama:

```java
zmenu.open(player, "main");
```

y `main` no está cargado para ese plugin, CraftKit loggea un error con:

- nombre del inventario solicitado;
- nombre del plugin consumidor;
- inventarios conocidos para ese plugin;
- si el bootstrap ya fue cargado.

No hay fallback global. Esto evita abrir accidentalmente un inventario de otro plugin.

## Threading

`craftkit-zmenu` no introduce un scheduler propio. Los helpers de apertura llaman al `InventoryManager` real de zMenu.

Regla para plugins consumidores:

- abrir inventarios y tocar APIs Paper/Bukkit desde el contexto correcto del servidor;
- si se viene desde una tarea async, volver al scheduler adecuado antes de abrir.

CraftKit no intenta ocultar esa responsabilidad porque no es un runtime ni un lifecycle manager.

## Límites conocidos

### No hay tests unitarios específicos todavía

La implementación compila y fue revisada, pero actualmente no hay tests unitarios dedicados para:

- resolución de services obligatorios/opcionales;
- orden de bootstrap;
- filtrado recursivo de `.yml`;
- llamadas de cleanup en reload;
- tracking.

Esto es un buen candidato a mejora futura.

### Dialogs y Bedrock dependen de zMenu

CraftKit no fuerza `DialogManager` ni `BedrockManager`. Si zMenu no los registra, CraftKit no carga esas carpetas.

### Custom classes de Dialog/Bedrock

La documentación revisada de zMenu indica que la API expone overloads con custom classes para dialogs y bedrock, pero la implementación actual no las usa realmente como se esperaría.

Por eso `craftkit-zmenu` no promete soporte especial para custom dialog/bedrock classes.

### Commands no están en v1

zMenu tiene APIs reload-safe para commands, pero `craftkit-zmenu` no las implementa todavía. Si un plugin necesita commands de zMenu, debe usar el `CommandManager` real desde `MenuPlugin` o zMenu directamente.

### No hay auto-discovery

CraftKit no busca recursos automáticamente en el classpath del plugin consumidor. Todo default debe declararse explícitamente.

Esto es intencional: reduce magia, hace el review más claro y evita cargar archivos por accidente.
