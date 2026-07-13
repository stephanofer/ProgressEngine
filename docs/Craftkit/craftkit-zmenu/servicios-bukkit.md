# Resolución de zMenu y servicios Bukkit

`craftkit-zmenu` resuelve zMenu en dos pasos distintos porque zMenu no expone todo por el mismo mecanismo.

## `MenuPlugin` se obtiene por `PluginManager`

`MenuPlugin` no se obtiene desde Bukkit `ServicesManager`. CraftKit busca el plugin real llamado `zMenu`:

```java
Plugin raw = plugin.getServer().getPluginManager().getPlugin("zMenu");
if (!(raw instanceof MenuPlugin menuPlugin)) {
    throw new ZMenuMissingDependencyException("zMenu is not loaded or does not expose MenuPlugin");
}
```

Si el plugin no existe, no está cargado o no implementa `MenuPlugin`, la integración falla inmediatamente.

## Managers obligatorios por `ServicesManager`

zMenu registra sus managers principales como servicios Bukkit. CraftKit resuelve estos servicios como obligatorios:

| Servicio | Uso |
| --- | --- |
| `InventoryManager` | Cargar, buscar, abrir, borrar y recargar inventarios. |
| `ButtonManager` | Registrar button loaders y trabajar con actions/requirements de zMenu. |
| `PatternManager` | Cargar, registrar y limpiar patterns/action patterns. |

Resolución conceptual:

```java
RegisteredServiceProvider<InventoryManager> provider = plugin.getServer()
    .getServicesManager()
    .getRegistration(InventoryManager.class);

if (provider == null || provider.getProvider() == null) {
    throw new ZMenuMissingServiceException("zMenu service missing: " + InventoryManager.class.getName());
}
```

CraftKit no devuelve `null` para estos servicios. Si faltan, hay un problema real de arranque o integración y debe fallar claro.

## Managers opcionales

Estos managers no siempre existen:

| Servicio | Cuándo existe |
| --- | --- |
| `DialogManager` | Cuando zMenu cargó soporte de dialogs. Depende de versión/entorno/configuración de zMenu. |
| `BedrockManager` | Cuando zMenu detectó Geyser o Floodgate. |

CraftKit los expone como:

```java
Optional<DialogManager> dialogs();
Optional<BedrockManager> bedrock();
```

Si no existen, no es necesariamente error. El plugin consumidor debe manejar el `Optional`.

## Qué obtiene el consumidor

Después de:

```java
ZMenuIntegration zmenu = ZMenus.require(this);
```

el consumidor puede acceder directamente a los managers reales:

```java
InventoryManager inventories = zmenu.inventories();
ButtonManager buttons = zmenu.buttons();
PatternManager patterns = zmenu.patterns();

zmenu.dialogs().ifPresent(dialogs -> {
    // usar DialogManager real
});
```

## Errores esperados

### Falta zMenu

Se lanza `ZMenuMissingDependencyException`.

Caso típico:

- zMenu no está instalado en `/plugins`;
- el plugin se llama distinto;
- zMenu falló durante su propio enable.

### Falta un servicio obligatorio

Se lanza `ZMenuMissingServiceException`.

Caso típico:

- zMenu está cargado parcialmente;
- zMenu cambió su API;
- el plugin consumidor arrancó demasiado pronto o sin dependencia declarada correctamente.

## Recomendación para plugin.yml / paper-plugin.yml

El plugin consumidor debe declarar dependencia hacia zMenu para que Paper cargue zMenu antes.

Ejemplo conceptual:

```yaml
dependencies:
  server:
    zMenu:
      load: BEFORE
      required: true
```

La forma exacta depende del formato de metadata del plugin consumidor. La regla importante es que zMenu debe estar disponible antes de llamar `ZMenus.require(this)`.
