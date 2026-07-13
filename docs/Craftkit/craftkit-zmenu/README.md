# `craftkit-zmenu`

`craftkit-zmenu` es el módulo de CraftKit que estandariza la integración de plugins consumidores con zMenu sin reemplazar la API real de zMenu. El módulo resuelve dependencia, servicios Bukkit, bootstrap de recursos, carga de YAML, tracking y reload seguro para las partes que zMenu permite limpiar públicamente.

La regla más importante es simple: **zMenu sigue siendo quien maneja los menús**. CraftKit solo reduce boilerplate repetido y evita errores típicos de integración en plugins de HERA.

## Qué resuelve

- Valida que el plugin `zMenu` exista y exponga `MenuPlugin`.
- Resuelve managers reales de zMenu desde Bukkit `ServicesManager`.
- Expone tipos reales de zMenu: `InventoryManager`, `ButtonManager`, `PatternManager`, `DialogManager`, `BedrockManager` y `MenuPlugin`.
- Carga defaults explícitos sin sobrescribir archivos existentes.
- Recorre carpetas del plugin consumidor y carga archivos `.yml` recursivamente.
- Registra button loaders, button options e inventory options desde un bootstrap explícito.
- Trackea únicamente lo que CraftKit carga o registra mediante bootstrap.
- Ejecuta reload limpiando primero las partes que zMenu permite desregistrar.
- Abre inventarios con lookup scoped al plugin consumidor, evitando búsquedas globales ambiguas.

## Qué no resuelve

- No reemplaza `InventoryManager`, `ButtonManager`, `PatternManager` ni otros managers reales de zMenu.
- No crea una DSL propia de menús.
- No envuelve toda la API de zMenu.
- No autodetecta recursos dentro del JAR del plugin consumidor.
- No promete reload limpio para features donde zMenu no expone unregister público.
- No registra actions, permissibles, material loaders, placeholders, item components, title loaders, mechanics ni config dialogs en esta primera versión.
- No implementa soporte de comandos zMenu en esta primera versión.

## Dependencias del módulo

`craftkit-zmenu/build.gradle.kts` declara:

```kotlin
repositories {
    maven {
        name = "groupez"
        url = uri("https://repo.groupez.dev/releases")
    }
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnlyApi(libs.zmenu.api)
}
```

Versiones actuales en `gradle/libs.versions.toml`:

| Librería | Versión |
| --- | --- |
| zMenu API | `1.1.1.4` |
| Paper API | `26.1.2.build.69-stable` |

`zmenu-api` usa `compileOnlyApi` porque la API pública de `craftkit-zmenu` devuelve tipos de zMenu. El consumidor debe poder compilar contra esos tipos, pero zMenu sigue siendo una dependencia provista por el servidor.

## Documentos de esta sección

1. [Inicio rápido](./inicio-rapido.md)
2. [Arquitectura y componentes](./arquitectura.md)
3. [Resolución de zMenu y servicios Bukkit](./servicios-bukkit.md)
4. [Bootstrap, defaults y carga de YAML](./bootstrap-carga.md)
5. [Reload seguro y tracking](./reload-tracking.md)
6. [Uso desde plugins consumidores](./uso-plugin-consumidor.md)
7. [Lifecycle, errores y límites](./lifecycle-errores-limites.md)
8. [Referencia de API pública](./referencia-api.md)

## Regla mental rápida

El plugin consumidor define:

- sus archivos zMenu reales;
- sus button loaders custom;
- sus clases de botón;
- sus options si las necesita;
- sus nombres de inventarios;
- sus comandos propios de reload;
- cuándo abrir menús;
- qué features de zMenu usa directamente.

CraftKit proporciona:

- resolución segura de zMenu;
- servicios reales ya listos;
- bootstrap ordenado;
- carga recursiva de `.yml`;
- copia explícita de defaults;
- tracking interno;
- reload seguro para lo soportado por zMenu;
- helpers mínimos de apertura.

## Ejemplo completo mínimo

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
            .defaultInventories("inventories/main.yml")
            .defaultPatterns("patterns/decoration.yml")
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

Este ejemplo no reemplaza zMenu. Solo usa CraftKit para hacer siempre igual la conexión, carga y recarga.
