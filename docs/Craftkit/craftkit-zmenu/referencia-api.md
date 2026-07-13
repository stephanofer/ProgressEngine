# Referencia de API pública — `craftkit-zmenu`

Esta referencia describe la API pública real del módulo.

## `ZMenus`

```java
public final class ZMenus {
    public static ZMenuIntegration require(Plugin plugin);
}
```

### `require(Plugin plugin)`

Resuelve zMenu para un plugin consumidor.

Valida:

- `plugin` no es `null`;
- existe un plugin Bukkit llamado `zMenu`;
- ese plugin expone `MenuPlugin`;
- existen los services obligatorios de zMenu.

Devuelve:

- `ZMenuIntegration`

Puede lanzar:

- `NullPointerException` si `plugin` es `null`;
- `ZMenuMissingDependencyException` si falta `zMenu`/`MenuPlugin`;
- `ZMenuMissingServiceException` si falta un manager obligatorio.

## `ZMenuIntegration`

```java
public interface ZMenuIntegration {
    MenuPlugin menuPlugin();
    InventoryManager inventories();
    ButtonManager buttons();
    PatternManager patterns();
    Optional<DialogManager> dialogs();
    Optional<BedrockManager> bedrock();
    void open(Player player, String inventoryName);
    void open(Player player, String inventoryName, int page);
    void openWithHistory(Player player, String inventoryName, int page);
    ZMenuBootstrap bootstrap();
    ZMenuReloadPlan reloadPlan();
    void reload();
}
```

### `menuPlugin()`

Devuelve el `MenuPlugin` real de zMenu.

Usarlo cuando se necesite una API que vive en `MenuPlugin`, por ejemplo placeholders, components manager o item manager.

### `inventories()`

Devuelve el `InventoryManager` real de zMenu.

### `buttons()`

Devuelve el `ButtonManager` real de zMenu.

### `patterns()`

Devuelve el `PatternManager` real de zMenu.

### `dialogs()`

Devuelve `Optional<DialogManager>`.

Está vacío cuando zMenu no registró soporte de dialogs.

### `bedrock()`

Devuelve `Optional<BedrockManager>`.

Está vacío cuando zMenu no registró soporte Bedrock.

### `open(Player player, String inventoryName)`

Abre la página `1` de un inventario cargado para el plugin consumidor.

Internamente equivale a buscar con:

```java
inventoryManager.getInventory(plugin, inventoryName)
```

Si no existe, loggea error y no abre nada.

### `open(Player player, String inventoryName, int page)`

Abre una página específica.

### `openWithHistory(Player player, String inventoryName, int page)`

Abre una página específica preservando historial de inventarios mediante zMenu:

```java
inventoryManager.openInventoryWithOldInventories(player, inventory, page);
```

### `bootstrap()`

Crea un nuevo bootstrap mutable.

Al llamar `load()`, ese bootstrap se convierte en snapshot para reload.

### `reloadPlan()`

Devuelve el último plan cargado.

Si nunca se llamó `bootstrap().load()`, lanza `IllegalStateException`.

### `reload()`

Shortcut para:

```java
reloadPlan().reload();
```

## `ZMenuBootstrap`

```java
public interface ZMenuBootstrap {
    ZMenuBootstrap buttons(Consumer<ButtonRegistry> consumer);
    ZMenuBootstrap buttonOptions(Class<? extends ButtonOption>... options);
    ZMenuBootstrap inventoryOptions(Class<? extends InventoryOption>... options);
    ZMenuBootstrap defaultInventories(String... paths);
    ZMenuBootstrap defaultPatterns(String... paths);
    ZMenuBootstrap defaultActionPatterns(String... paths);
    ZMenuBootstrap defaultDialogs(String... paths);
    ZMenuBootstrap defaultBedrock(String... paths);
    ZMenuBootstrap inventories(String folder);
    ZMenuBootstrap patterns(String folder);
    ZMenuBootstrap actionPatterns(String folder);
    ZMenuBootstrap dialogs(String folder);
    ZMenuBootstrap bedrock(String folder);
    ZMenuReloadPlan load();

    interface ButtonRegistry {
        void button(ButtonLoader loader);
    }
}
```

### `buttons(...)`

Registra button loaders usando el `ButtonManager` real de zMenu durante `load()`.

```java
.buttons(registry -> registry.button(new MyButtonLoader(this)))
```

### `buttonOptions(...)`

Registra clases `ButtonOption` usando:

```java
inventoryManager.registerOption(plugin, optionClass);
```

### `inventoryOptions(...)`

Registra clases `InventoryOption` usando:

```java
inventoryManager.registerInventoryOption(plugin, optionClass);
```

### `defaultInventories(...)`

Copia y carga inventarios default explícitos usando:

```java
inventoryManager.loadInventoryOrSaveResource(plugin, path);
```

Si el path está cubierto por `inventories(folder)`, CraftKit solo copia el recurso si falta y deja que el escaneo de carpeta lo cargue una única vez.

### `defaultPatterns(...)`

Copia el recurso si no existe y carga cada pattern.

Si el path está cubierto por `patterns(folder)`, solo se copia; el folder scan lo carga una vez.

### `defaultActionPatterns(...)`

Copia el recurso si no existe y carga cada action pattern.

Si el path está cubierto por `actionPatterns(folder)`, solo se copia; el folder scan lo carga una vez.

### `defaultDialogs(...)`

Copia y carga dialogs solo si existe `DialogManager`.

Si el path está cubierto por `dialogs(folder)`, solo se copia; el folder scan lo carga una vez.

### `defaultBedrock(...)`

Copia y carga bedrock inventories solo si existe `BedrockManager`.

Si el path está cubierto por `bedrock(folder)`, solo se copia; el folder scan lo carga una vez.

### `inventories(folder)`

Carga recursivamente `.yml` desde la carpeta del plugin consumidor.

### `patterns(folder)`

Carga recursivamente `.yml` como zMenu patterns.

### `actionPatterns(folder)`

Carga recursivamente `.yml` como action patterns.

### `dialogs(folder)`

Carga recursivamente `.yml` como dialogs si existe `DialogManager`.

### `bedrock(folder)`

Carga recursivamente `.yml` como bedrock inventories si existe `BedrockManager`.

### `load()`

Ejecuta el bootstrap, guarda el plan para reload y devuelve `ZMenuReloadPlan`.

## `ZMenuReloadPlan`

```java
public interface ZMenuReloadPlan {
    void reload();
}
```

Recarga el último snapshot del bootstrap:

1. limpia recursos reload-safe;
2. limpia tracker;
3. ejecuta nuevamente la carga.

## Excepciones

### `ZMenuException`

Base runtime exception del módulo.

### `ZMenuMissingDependencyException`

Indica que zMenu no está cargado o no expone `MenuPlugin`.

### `ZMenuMissingServiceException`

Indica que falta un service obligatorio de zMenu.
