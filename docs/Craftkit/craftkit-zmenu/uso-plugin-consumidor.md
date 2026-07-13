# Uso desde plugins consumidores

Esta guía muestra cómo un plugin de HERA debería consumir `craftkit-zmenu` en un flujo real.

## Estructura recomendada de recursos

Ejemplo dentro del plugin consumidor:

```text
src/main/resources/
  inventories/
    main.yml
    profile.yml
  patterns/
    decoration.yml
  actions_patterns/
    default-actions.yml
  dialogs/
    confirm.yml
  bedrock/
    profile.yml
```

En runtime, esos recursos se copian/cargan hacia:

```text
plugins/MyPlugin/
  inventories/
  patterns/
  actions_patterns/
  dialogs/
  bedrock/
```

Solo se copian defaults declarados explícitamente.

## Inicialización completa

```java
public final class MyPlugin extends JavaPlugin {

    private ZMenuIntegration zmenu;

    @Override
    public void onEnable() {
        this.zmenu = ZMenus.require(this);

        this.zmenu.bootstrap()
            .buttons(registry -> {
                registry.button(new NoneLoader(this, ProfileButton.class, "HERA_PROFILE"));
                registry.button(new ShopCategoryButtonLoader(this));
            })
            .buttonOptions(GlowButtonOption.class)
            .inventoryOptions(ProfileInventoryOption.class)
            .defaultInventories(
                "inventories/main.yml",
                "inventories/profile.yml"
            )
            .defaultPatterns("patterns/decoration.yml")
            .defaultActionPatterns("actions_patterns/default-actions.yml")
            .actionPatterns("actions_patterns")
            .patterns("patterns")
            .inventories("inventories")
            .dialogs("dialogs")
            .bedrock("bedrock")
            .load();
    }

    public void reloadPlugin() {
        reloadConfig();
        this.zmenu.reload();
    }
}
```

## Acceso directo a zMenu

El consumidor puede seguir usando zMenu directamente:

```java
InventoryManager inventoryManager = this.zmenu.inventories();
ButtonManager buttonManager = this.zmenu.buttons();
PatternManager patternManager = this.zmenu.patterns();
```

CraftKit no impide usar métodos reales de zMenu. La idea es que el plugin use el poder completo de zMenu cuando lo necesite.

## Apertura de inventarios

Preferir helpers de CraftKit cuando se abra por nombre:

```java
this.zmenu.open(player, "main");
this.zmenu.open(player, "profile", 1);
this.zmenu.openWithHistory(player, "settings", 1);
```

Ventajas:

- lookup scoped al plugin consumidor;
- error claro si falta el inventario;
- no hay colisiones por inventarios de otros plugins;
- opción de preservar historial con `openWithHistory`.

Si ya se tiene la instancia real de `Inventory`, también se puede usar zMenu directamente:

```java
this.zmenu.inventories().openInventory(player, inventory);
```

## Dialogs

Dialogs son opcionales.

```java
this.zmenu.dialogs().ifPresent(dialogs -> {
    // usar DialogManager real
});
```

Si el plugin usa `.dialogs("dialogs")` en bootstrap pero zMenu no expuso `DialogManager`, CraftKit simplemente no carga esa carpeta.

## Bedrock inventories

Bedrock también es opcional.

```java
this.zmenu.bedrock().ifPresent(bedrock -> {
    // usar BedrockManager real
});
```

Si no hay Geyser/Floodgate o zMenu no registró `BedrockManager`, el `Optional` estará vacío.

## Custom inventory classes

zMenu soporta custom inventory classes, pero el constructor debe ser compatible con zMenu:

```java
(Plugin plugin, String name, String fileName, int size, List<Button> buttons)
```

La API actual de `craftkit-zmenu` no agrega overloads específicos para custom inventory classes en bootstrap. Si un plugin necesita ese caso, puede usar directamente el `InventoryManager` real:

```java
this.zmenu.inventories().loadInventory(this, "inventories/custom.yml", MyInventory.class);
```

## Features enable-only

Si el plugin necesita registrar action loaders, permissibles, material loaders, placeholders, item components o title loaders, hacerlo fuera del bootstrap reload-safe y una sola vez en `onEnable`.

Ejemplo conceptual:

```java
@Override
public void onEnable() {
    this.zmenu = ZMenus.require(this);

    // Enable-only: zMenu no expone unregister público para esto.
    this.zmenu.buttons().registerAction(new MyActionLoader());

    this.zmenu.bootstrap()
        .inventories("inventories")
        .load();
}
```

No registrar esas features otra vez en `reloadPlugin()`.

## Buenas prácticas

- Usar IDs de button loaders únicos y estables.
- Evitar nombres globales genéricos como `SHOP` si pueden colisionar.
- Preferir prefijos claros, por ejemplo `HERA_PROFILE`, `HERA_SHOP_CATEGORY`.
- Mantener los YAML del plugin consumidor bajo carpetas declaradas en bootstrap.
- Declarar defaults uno por uno para que el equipo sepa exactamente qué archivos se copian.
- No usar reload para registrar features que zMenu no puede desregistrar públicamente.
