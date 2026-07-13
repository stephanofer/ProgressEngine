# Inicio rápido — `craftkit-zmenu`

Esta guía muestra el flujo mínimo para usar `craftkit-zmenu` desde un plugin consumidor.

## 1. Agregar la dependencia

El plugin consumidor debe depender del módulo CraftKit y también tener zMenu disponible en el servidor. zMenu es un plugin externo, no algo que CraftKit deba empaquetar.

Ejemplo conceptual en Gradle del plugin consumidor:

```kotlin
dependencies {
    implementation("com.hera.craftkit:craftkit-zmenu:$craftkitVersion")
    compileOnly("fr.maxlego08.menu:zmenu-api:1.1.1.4")
}
```

El servidor debe tener el plugin `zMenu` instalado y habilitado.

## 2. Resolver la integración

En `onEnable`, resolver zMenu con:

```java
private ZMenuIntegration zmenu;

@Override
public void onEnable() {
    this.zmenu = ZMenus.require(this);
}
```

Si zMenu no está instalado, no expone `MenuPlugin` o faltan servicios obligatorios, CraftKit lanza una excepción clara.

## 3. Declarar el bootstrap

El bootstrap dice qué recursos del plugin consumidor debe cargar CraftKit.

```java
this.zmenu.bootstrap()
    .buttons(registry -> {
        registry.button(new NoneLoader(this, ProfileButton.class, "HERA_PROFILE"));
        registry.button(new ShopCategoryButtonLoader(this));
    })
    .defaultInventories("inventories/main.yml")
    .defaultPatterns("patterns/decoration.yml")
    .actionPatterns("actions_patterns")
    .patterns("patterns")
    .inventories("inventories")
    .load();
```

El orden interno de carga queda controlado por CraftKit. Los button loaders y patterns se registran antes de cargar inventories, porque los YAML de inventarios pueden referenciarlos.

## 4. Abrir un inventario

Usar el helper de CraftKit cuando se quiere apertura scoped al plugin consumidor:

```java
this.zmenu.open(player, "main");
this.zmenu.open(player, "main", 2);
this.zmenu.openWithHistory(player, "main", 1);
```

Internamente se usa:

```java
inventoryManager.getInventory(plugin, inventoryName)
```

No hay fallback global. Si falta el inventario, CraftKit loggea el plugin, el nombre solicitado y los inventarios conocidos de ese plugin.

## 5. Recargar desde un comando del plugin

En el comando reload del plugin consumidor:

```java
public void reloadPlugin() {
    reloadConfig();
    this.zmenu.reload();
}
```

`zmenu.reload()` limpia lo reload-safe y vuelve a ejecutar el último bootstrap cargado.

## Checklist rápido

- [ ] zMenu está instalado en el servidor.
- [ ] El plugin consumidor llama `ZMenus.require(this)` en `onEnable`.
- [ ] Los button loaders custom se registran antes de cargar inventories.
- [ ] Los defaults se declaran explícitamente.
- [ ] Las carpetas configuradas existen o pueden ser creadas por CraftKit.
- [ ] El reload del plugin llama `zmenu.reload()` después de recargar su propia config.
