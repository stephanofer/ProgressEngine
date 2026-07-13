# Reload seguro y tracking

`craftkit-zmenu` implementa reload seguro solo para las partes de zMenu que exponen APIs públicas de cleanup. No promete limpiar features donde zMenu no permite unregister público.

## Qué significa tracking

Tracking significa que CraftKit recuerda qué recursos cargó o registró mediante bootstrap.

Ejemplo:

```java
Pattern pattern = patternManager.loadPattern(file);
tracker.trackPattern(pattern);
```

zMenu sigue haciendo la carga real. CraftKit solo guarda referencias para saber qué limpiar después.

## Qué se trackea actualmente

`ZMenuRegistrationTracker` guarda:

- `ButtonLoader`
- `Pattern`
- `ActionPattern`
- `Inventory`
- `DialogInventory`
- `BedrockInventory`
- `ButtonOption`
- `InventoryOption`

No trackea inventory listeners ni fast events porque la API pública actual de `ZMenuBootstrap` no los registra. Esto evita código anticipado.

## Reload desde un plugin consumidor

Ejemplo típico:

```java
public void reloadPlugin() {
    reloadConfig();
    this.zmenu.reload();
}
```

`zmenu.reload()` requiere que antes se haya cargado un bootstrap. Si no existe plan cargado, se lanza una excepción indicando que no hay bootstrap para ese plugin.

## Orden de cleanup

`DefaultZMenuReloadPlan` limpia antes de volver a cargar:

```java
buttonManager.unregisters(plugin);
inventoryManager.deleteInventories(plugin);
inventoryManager.unregisterOptions(plugin);
inventoryManager.unregisterInventoryOptions(plugin);
inventoryManager.unregisterListener(plugin);
patternManager.unregisterPattern(pattern);
patternManager.unregisterActionPattern(actionPattern);
dialogManager.deleteDialog(plugin);
bedrockManager.deleteBedrockInventory(plugin);
```

Después:

```java
tracker.clear();
loadFresh();
```

## Reload-safe

Estas features tienen cleanup público en zMenu y son compatibles con reload controlado por CraftKit:

| Feature | Cleanup usado |
| --- | --- |
| Button loaders | `ButtonManager.unregisters(plugin)` |
| Inventories | `InventoryManager.deleteInventories(plugin)` |
| Button options | `InventoryManager.unregisterOptions(plugin)` |
| Inventory options | `InventoryManager.unregisterInventoryOptions(plugin)` |
| Fast event por plugin | `InventoryManager.unregisterListener(plugin)` |
| Patterns | `PatternManager.unregisterPattern(pattern)` |
| Action patterns | `PatternManager.unregisterActionPattern(pattern)` |
| Dialog inventories | `DialogManager.deleteDialog(plugin)` |
| Bedrock inventories | `BedrockManager.deleteBedrockInventory(plugin)` |

Aunque `unregisterListener(plugin)` se llama durante cleanup, la API pública actual no registra fast events desde bootstrap. La llamada limpia cualquier fast event asociado al plugin si existiera por integración externa.

## Enable-only

Estas features se pueden registrar con zMenu directamente, pero CraftKit no las mete en bootstrap reload-safe porque zMenu no expone unregister público granular:

| Feature | Motivo |
| --- | --- |
| Action loaders | No hay `unregisterAction` público. |
| Permissible loaders | No hay `unregisterPermissible` público. |
| Material loaders | No hay unregister público. |
| ItemStack similar verifiers | No hay unregister público. |
| Placeholders | No hay unregister público. |
| Item components | No hay unregister público. |
| Title animation loaders | No hay unregister público. |
| Custom item mechanic factories | No hay unregister público. |
| Custom item mechanic listeners | No hay cleanup granular seguro por plugin externo. |
| Config dialogs | No hay unregister público. |

Si un plugin consumidor necesita esas features, debe registrarlas una sola vez durante `onEnable`, no en cada reload.

## Por qué no se limpia todo manualmente

No se debe manipular estado interno de zMenu por reflexión ni acceder a estructuras privadas. Eso sería frágil y rompería con nuevas versiones de zMenu.

CraftKit solo usa APIs públicas estables. Esa decisión mantiene el módulo simple, auditable y fácil de mantener.
