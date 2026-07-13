# Arquitectura y componentes — `craftkit-zmenu`

`craftkit-zmenu` está dividido en una API pública pequeña y una implementación interna. La API pública está diseñada para plugins consumidores; la implementación interna existe solo para resolver servicios, ejecutar bootstrap, trackear recursos y recargar.

## Mapa de componentes

```text
com.hera.craftkit.zmenu
  ZMenus
  ZMenuIntegration
  ZMenuBootstrap
  ZMenuReloadPlan
  ZMenuException
  ZMenuMissingDependencyException
  ZMenuMissingServiceException

com.hera.craftkit.zmenu.internal
  BukkitZMenuServices
  DefaultZMenuIntegration
  DefaultZMenuBootstrap
  DefaultZMenuReloadPlan
  ZMenuFileLoader
  ZMenuRegistrationTracker
```

## API pública

### `ZMenus`

Es el entrypoint del módulo.

```java
ZMenuIntegration zmenu = ZMenus.require(plugin);
```

Responsabilidades:

- validar que el plugin no sea `null`;
- delegar la resolución real a `BukkitZMenuServices`;
- devolver una `ZMenuIntegration` lista para usar.

No guarda estado global y no crea un lifecycle centralizado.

### `ZMenuIntegration`

Representa la integración de un plugin consumidor con zMenu.

Expone tipos reales de zMenu:

```java
MenuPlugin menuPlugin();
InventoryManager inventories();
ButtonManager buttons();
PatternManager patterns();
Optional<DialogManager> dialogs();
Optional<BedrockManager> bedrock();
```

También expone helpers mínimos:

```java
void open(Player player, String inventoryName);
void open(Player player, String inventoryName, int page);
void openWithHistory(Player player, String inventoryName, int page);
ZMenuBootstrap bootstrap();
ZMenuReloadPlan reloadPlan();
void reload();
```

El helper de apertura existe porque agrega validación, logging y lookup scoped al plugin. No reemplaza la API real de zMenu.

### `ZMenuBootstrap`

Define qué debe cargar CraftKit para el plugin consumidor.

Soporta:

- button loaders;
- button options;
- inventory options;
- defaults explícitos;
- carpetas de inventories, patterns, action patterns, dialogs y bedrock.

El bootstrap es explícito. No busca recursos automáticamente en el JAR.

### `ZMenuReloadPlan`

Representa el último plan de carga que puede repetirse durante reload.

```java
ZMenuReloadPlan plan = zmenu.bootstrap().inventories("inventories").load();
plan.reload();
```

Normalmente el consumidor usa directamente:

```java
zmenu.reload();
```

## Implementación interna

### `BukkitZMenuServices`

Resuelve zMenu y sus servicios:

- `MenuPlugin` desde `PluginManager` usando el nombre `zMenu`;
- `InventoryManager`, `ButtonManager` y `PatternManager` desde `ServicesManager` como obligatorios;
- `DialogManager` y `BedrockManager` desde `ServicesManager` como opcionales.

### `DefaultZMenuIntegration`

Guarda el plugin consumidor, los managers reales de zMenu, los managers opcionales y el tracker.

También implementa los helpers de apertura:

- busca inventarios con `inventoryManager.getInventory(plugin, inventoryName)`;
- si faltan, loggea un error claro;
- no usa búsqueda global.

### `DefaultZMenuBootstrap`

Acumula la configuración declarada por el consumidor y crea un snapshot inmutable al llamar `load()`.

Ese snapshot permite que `reload()` repita exactamente el mismo plan.

### `ZMenuFileLoader`

Ejecuta la carga real:

1. registra extensiones reload-safe declaradas;
2. carga defaults explícitos;
3. carga action patterns;
4. carga patterns;
5. carga inventories;
6. carga dialogs si existe `DialogManager`;
7. carga bedrock si existe `BedrockManager`.

Siempre llama métodos reales de zMenu.

### `DefaultZMenuReloadPlan`

Ejecuta cleanup y luego vuelve a cargar:

1. unregister buttons del plugin;
2. delete inventories del plugin;
3. unregister button options;
4. unregister inventory options;
5. unregister fast event por plugin;
6. unregister patterns trackeados;
7. unregister action patterns trackeados;
8. delete dialogs del plugin si existe manager;
9. delete bedrock inventories del plugin si existe manager;
10. limpia tracker;
11. ejecuta nuevamente el loader.

### `ZMenuRegistrationTracker`

Guarda solo recursos cargados o registrados por CraftKit:

- `ButtonLoader`
- `Pattern`
- `ActionPattern`
- `Inventory`
- `DialogInventory`
- `BedrockInventory`
- `ButtonOption`
- `InventoryOption`

El tracker no intenta manejar features que no están expuestas en el bootstrap actual.

## Flujo completo

```text
Plugin consumidor
  -> ZMenus.require(this)
  -> BukkitZMenuServices.require(plugin)
  -> DefaultZMenuIntegration
  -> zmenu.bootstrap()
  -> DefaultZMenuBootstrap
  -> snapshot del plan
  -> DefaultZMenuReloadPlan.loadFresh()
  -> ZMenuFileLoader.load()
  -> managers reales de zMenu
```

Reload:

```text
zmenu.reload()
  -> DefaultZMenuReloadPlan.reload()
  -> cleanup reload-safe
  -> tracker.clear()
  -> ZMenuFileLoader.load()
```

## Principio arquitectónico

CraftKit no es el dueño de los menús. zMenu es el dueño de los menús. CraftKit solo estandariza cómo los plugins de HERA se conectan, cargan y recargan zMenu.
