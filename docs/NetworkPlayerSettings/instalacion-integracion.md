# Instalación e integración de un plugin consumidor

Este documento explica cómo declarar y obtener NetworkPlayerSettings desde otro plugin Paper/Bukkit usando únicamente comportamiento visible en el repositorio.

## Dependencias reales del proyecto

El proyecto se compila como `java-library`, genera un JAR sombreado con Shadow y expone publicación local con `maven-publish`. El `build.gradle.kts` declara:

| Dependencia | Uso visible |
|---|---|
| `io.papermc.paper:paper-api:26.2.build.25-alpha` | API Paper de compilación del core. |
| `me.clip:placeholderapi:2.12.2` | Integración opcional PlaceholderAPI. |
| `com.hera.craftkit:craftkit-database:1.1.0` | Base de datos y migraciones internas. |
| `com.stephanofer.boostedyaml:boosted-yaml:1.3.7` | Carga y auto-update de YAML internos. |
| `com.maxmind.geoip2:geoip2:5.1.0` | Resolución GeoIP interna. |

El repositorio también puede contener consumidores de ejemplo o plugins complementarios, pero esta documentación se centra en el artefacto core `NetworkPlayerSettings`.

## Publicación local de NetworkPlayerSettings

El proyecto define estas coordenadas Maven locales:

```kotlin
group = "com.stephanofer"
version = "2.0.0"
artifactId = "networkplayersettings"
```

Para instalar el artefacto en Maven Local desde este proyecto:

```bash
./gradlew publishToMavenLocal
```

En Windows también podés usar:

```powershell
.\gradlew.bat publishToMavenLocal
```

Después, un plugin consumidor puede compilar contra la API pública así:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.stephanofer:networkplayersettings:2.0.0")
    compileOnly("io.papermc.paper:paper-api:26.2.build.25-alpha")
}
```

Usá `compileOnly`, no `implementation`, porque NetworkPlayerSettings debe estar instalado como plugin en el servidor. El consumidor necesita las clases para compilar, pero no debe sombrear ni empaquetar NetworkPlayerSettings dentro de su propio JAR.

`publishToMavenLocal` publica el JAR normal de compilación/API del core. Para instalar el plugin en un servidor, usá el JAR sombreado generado por `./gradlew build` en `target/`.

### Cuándo usar `libs/`

`libs/NetworkPlayerSettings.jar` sigue siendo posible para pruebas puntuales, pero no es la opción recomendada para desarrollo normal de varios plugins consumidores. Maven Local es más limpio porque versiona la dependencia, evita copias manuales y hace explícito qué versión consume cada plugin.

Ejemplo válido pero menos recomendable:

```kotlin
dependencies {
    compileOnly(files("libs/NetworkPlayerSettings.jar"))
}
```

### Límite actual

La publicación configurada es local. El proyecto no declara un repositorio Maven remoto para publicar releases compartidos. Si varios desarrolladores o un CI necesitan resolver la dependencia sin depender de la máquina local, publicá el mismo artefacto en un Maven privado y reemplazá `mavenLocal()` por ese repositorio.

## Salida de build para servidor

Al ejecutar el build completo desde la raíz:

```bash
./gradlew build
```

Shadow genera el JAR final del core en `target/`:

| Archivo | Plugin |
|---|---|
| `target/NetworkPlayerSettings-2.0.0.jar` | Core de settings, sin zMenu. |

Si el repo contiene plugins consumidores adicionales, pueden generar sus propios JARs en la misma carpeta, pero no forman parte del contrato público de `NetworkPlayerSettings`.

## Metadata del plugin consumidor

`NetworkPlayerSettings` se registra como plugin con este nombre exacto:

```yaml
name: NetworkPlayerSettings
main: com.stephanofer.networkplayersettings.NetworkPlayerSettingsPlugin
softdepend: [PlaceholderAPI]
```

Si tu plugin necesita NetworkPlayerSettings para funcionar, declaralo como dependencia fuerte:

```yaml
depend: [NetworkPlayerSettings]
```

Si tu plugin puede funcionar sin esta integración, usá dependencia blanda y tratá el servicio ausente:

```yaml
softdepend: [NetworkPlayerSettings]
```

## Lookup de servicios

NetworkPlayerSettings registra dos servicios en `ServicesManager`:

| Servicio | Registro real | Cuándo aparece |
|---|---|---|
| `PlayerSettingsService` | `register(PlayerSettingsService.class, settingsService, plugin, ServicePriority.Normal)` | Después de inicializar config, assets, DB y servicio de ajustes. |
| `NetworkAssetService` | `register(NetworkAssetService.class, service, plugin, ServicePriority.Normal)` | Durante `initializeAssets()`, antes del servicio de ajustes. |

Ejemplo recomendado en `onEnable()` de un consumidor con `depend`:

```java
import com.stephanofer.networkplayersettings.settings.api.PlayerSettingsService;

private PlayerSettingsService playerSettings;

@Override
public void onEnable() {
    this.playerSettings = getServer().getServicesManager().load(PlayerSettingsService.class);
    if (this.playerSettings == null) {
        getLogger().severe("NetworkPlayerSettings is enabled but PlayerSettingsService is not registered.");
        getServer().getPluginManager().disablePlugin(this);
        return;
    }
}
```

Ejemplo opcional con `softdepend`:

```java
import com.stephanofer.networkplayersettings.settings.api.PlayerSettingsService;

Optional<PlayerSettingsService> settings = Optional.ofNullable(
    Bukkit.getServicesManager().load(PlayerSettingsService.class)
);
```

## Lifecycle y orden correcto

NetworkPlayerSettings hace en `onEnable()`:

1. carga `config.yml` con `PluginYamlLoader`;
2. carga `assets/countries.yml` y registra `NetworkAssetService`;
3. abre base de datos MySQL y ejecuta migraciones;
4. crea `DefaultPlayerSettingsService`;
5. registra `PlayerSettingsService`;
6. registra PlaceholderAPI si aplica;
7. registra listeners de conexión.

En `onDisable()` desregistra la expansión PlaceholderAPI, desregistra todos sus servicios y cierra GeoIP/DB.

Para consumidores, esto significa:

- Con `depend: [NetworkPlayerSettings]`, buscá servicios en tu `onEnable()` o más tarde.
- Con `softdepend`, el servicio puede ser `null`; tu plugin debe degradar funcionalidad.
- No guardes referencias indefinidamente si soportás reloads externos. Si `NetworkPlayerSettings` se deshabilita, sus servicios se desregistran.

## Dependencias transitivas de runtime del servidor

NetworkPlayerSettings core no depende de zMenu. PlaceholderAPI es opcional por metadata y por config.

Si otro plugin quiere construir UI, comandos o menús, debe declarar dependencia hacia `NetworkPlayerSettings`, obtener `PlayerSettingsService`/`NetworkAssetService` desde `ServicesManager` y operar mediante la API pública.

Para usar placeholders `%playersettings_*%`, el servidor debe tener PlaceholderAPI y `placeholderapi.enabled: true`.
