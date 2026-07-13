# Bootstrap, defaults y carga de YAML

El bootstrap de `craftkit-zmenu` define qué recursos zMenu pertenecen al plugin consumidor y deben cargarse de forma consistente.

## Por qué existe el bootstrap

zMenu carga automáticamente sus propias carpetas bajo `plugins/zMenu`, pero no carga automáticamente las carpetas de cada plugin consumidor.

Si un plugin consumidor tiene:

```text
plugins/MyPlugin/inventories
plugins/MyPlugin/patterns
plugins/MyPlugin/actions_patterns
```

ese plugin debe llamar métodos de zMenu para cargar esos archivos. `craftkit-zmenu` centraliza ese boilerplate.

## API de bootstrap

```java
zmenu.bootstrap()
    .buttons(registry -> {
        registry.button(new NoneLoader(this, MyButton.class, "MY_BUTTON"));
        registry.button(new MyButtonLoader(this));
    })
    .buttonOptions(MyButtonOption.class)
    .inventoryOptions(MyInventoryOption.class)
    .defaultInventories("inventories/main.yml")
    .defaultPatterns("patterns/decoration.yml")
    .defaultActionPatterns("actions_patterns/default.yml")
    .defaultDialogs("dialogs/confirm.yml")
    .defaultBedrock("bedrock/profile.yml")
    .actionPatterns("actions_patterns")
    .patterns("patterns")
    .inventories("inventories")
    .dialogs("dialogs")
    .bedrock("bedrock")
    .load();
```

Cada método es explícito. Si no se declara una carpeta o default, CraftKit no lo carga.

## Orden de carga

CraftKit carga en este orden:

1. Button loaders, button options e inventory options declaradas.
2. Defaults explícitos.
3. Action patterns.
4. Patterns.
5. Inventories.
6. Dialogs, solo si existe `DialogManager`.
7. Bedrock inventories, solo si existe `BedrockManager`.

Este orden es importante porque los inventarios pueden referenciar button loaders, action patterns y patterns. Si se cargan al revés, zMenu puede no encontrar tipos usados en YAML.

## Button loaders

Los button loaders se registran usando el `ButtonManager` real de zMenu.

```java
.buttons(registry -> {
    registry.button(new NoneLoader(this, ProfileButton.class, "HERA_PROFILE"));
    registry.button(new ShopCategoryButtonLoader(this));
})
```

El nombre del loader es el `type` en YAML:

```yaml
profile:
  type: HERA_PROFILE
  slot: 13
```

El nombre debe ser único. CraftKit no inventa nombres ni cambia cómo zMenu resuelve buttons.

## Button options e inventory options

CraftKit soporta registro reload-safe de:

```java
.buttonOptions(MyButtonOption.class)
.inventoryOptions(MyInventoryOption.class)
```

Internamente usa:

```java
inventoryManager.registerOption(plugin, optionClass);
inventoryManager.registerInventoryOption(plugin, optionClass);
```

En reload usa:

```java
inventoryManager.unregisterOptions(plugin);
inventoryManager.unregisterInventoryOptions(plugin);
```

## Defaults explícitos

Los defaults son archivos del JAR del plugin consumidor que deben copiarse al `dataFolder` si todavía no existen.

Ejemplo:

```java
.defaultInventories("inventories/main.yml")
```

Cuando un default no está cubierto por una carpeta declarada para escaneo, CraftKit lo copia y lo carga en la etapa de defaults.

Para inventories, CraftKit usa el método real de zMenu:

```java
inventoryManager.loadInventoryOrSaveResource(plugin, "inventories/main.yml");
```

Ese método copia el recurso si falta y luego carga el inventario.

Para otros defaults, CraftKit hace:

```java
if (!file.exists()) {
    plugin.saveResource(path, false);
}
```

y después carga el archivo con el manager real de zMenu.

Los defaults no sobrescriben archivos existentes.

### Defaults cubiertos por carpetas escaneadas

Si un default explícito está dentro de una carpeta que también se escanea, CraftKit evita cargarlo dos veces.

Ejemplo:

```java
.defaultInventories("inventories/language.yml")
.inventories("inventories")
```

En este caso CraftKit:

1. copia `inventories/language.yml` si falta en el `dataFolder`;
2. no lo carga durante la etapa de defaults;
3. lo carga una sola vez durante el escaneo de `inventories`.

La misma regla aplica a `defaultPatterns`, `defaultActionPatterns`, `defaultDialogs` y `defaultBedrock` cuando sus paths están cubiertos por `patterns`, `actionPatterns`, `dialogs` o `bedrock`.

Los paths repetidos dentro de la misma categoría de defaults se normalizan y se procesan una sola vez.

## Carga de carpetas

Para cada carpeta declarada:

```java
.inventories("inventories")
.patterns("patterns")
.actionPatterns("actions_patterns")
```

CraftKit:

1. crea la carpeta si no existe;
2. recorre recursivamente;
3. filtra solo archivos `.yml`;
4. ordena por path para tener carga estable;
5. llama métodos reales de zMenu.

Métodos usados:

```java
inventoryManager.loadInventory(plugin, file);
patternManager.loadPattern(file);
patternManager.loadActionPattern(file);
dialogManager.loadInventory(plugin, file);
bedrockManager.loadInventory(plugin, file);
```

## Dialogs y Bedrock

Dialogs y Bedrock son opcionales.

```java
.dialogs("dialogs")
.bedrock("bedrock")
```

Si el manager correspondiente no existe, CraftKit no carga esa carpeta.

Esto evita fallos en servidores donde:

- zMenu no cargó Dialog support;
- no existe Geyser/Floodgate para Bedrock.

## Errores de carga

Si un archivo falla, CraftKit lanza `ZMenuException` con:

- categoría del recurso;
- path del archivo;
- nombre del plugin consumidor;
- excepción original como causa.

Esto hace que los errores sean auditables y no queden como fallos silenciosos.
