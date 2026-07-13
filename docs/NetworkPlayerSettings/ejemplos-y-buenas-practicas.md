# Ejemplos y buenas prácticas para consumidores

Estos ejemplos usan únicamente API pública de NetworkPlayerSettings.

## Obtener servicios de forma segura

```java
import com.stephanofer.networkplayersettings.settings.api.PlayerSettingsService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConsumerPlugin extends JavaPlugin {
    private PlayerSettingsService settings;

    @Override
    public void onEnable() {
        this.settings = Bukkit.getServicesManager().load(PlayerSettingsService.class);
        if (this.settings == null) {
            getLogger().severe("Missing NetworkPlayerSettings PlayerSettingsService");
            getServer().getPluginManager().disablePlugin(this);
        }
    }
}
```

## Esperar datos listos

```java
import com.stephanofer.networkplayersettings.settings.event.PlayerSettingsReadyEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class SettingsReadyListener implements Listener {
    @EventHandler
    public void onSettingsReady(PlayerSettingsReadyEvent event) {
        String language = event.resolvedLanguage().code();
        String country = event.countryCode();
        // Safe point: NetworkPlayerSettings already marked this player as ready.
    }
}
```

## Leer idioma efectivo en un comando propio

```java
if (!settings.isReady(player.getUniqueId())) {
    player.sendMessage("Your settings are still loading.");
    return;
}

Language language = settings.resolvedLanguage(player);
player.sendMessage("Language: " + language.code());
```

## Leer país normalizado

```java
import com.stephanofer.networkplayersettings.settings.country.CountryFlag;

UUID playerId = player.getUniqueId();
String countryCode = CountryFlag.normalizeCode(settings.countryCode(playerId));
player.sendMessage("Country: " + countryCode);
```

## Cambiar idioma sin bloquear el main thread

```java
import com.stephanofer.networkplayersettings.settings.language.LanguagePreference;

settings.setLanguage(player.getUniqueId(), LanguagePreference.SPANISH)
    .thenRun(() -> player.getScheduler().run(plugin, task -> {
        player.sendMessage("Language saved.");
    }, null))
    .exceptionally(error -> {
        plugin.getLogger().warning("Could not save language for " + player.getUniqueId() + ": " + error.getMessage());
        player.getScheduler().run(plugin, task -> {
            player.sendMessage("Could not save your language. Try again later.");
        }, null);
        return null;
    });
```

Nota: el `CompletableFuture` de NetworkPlayerSettings completa después de persistir y después de aplicar caché/evento en main thread para las mutaciones públicas actuales.

## Cambiar país manual

```java
settings.setCountryOverride(player.getUniqueId(), "AR")
    .thenRun(() -> plugin.getLogger().info("Country override saved"))
    .exceptionally(error -> {
        plugin.getLogger().warning("Invalid or unsaved country override: " + error.getMessage());
        return null;
    });
```

`setCountryOverride` rechaza códigos inválidos y `XX`. Para volver al país detectado:

```java
settings.clearCountryOverride(player.getUniqueId());
```

## Escuchar cambios

```java
import com.stephanofer.networkplayersettings.settings.api.SettingKey;
import com.stephanofer.networkplayersettings.settings.event.PlayerSettingChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class SettingChangeListener implements Listener {
    @EventHandler
    public void onSettingChange(PlayerSettingChangeEvent event) {
        if (event.settingKey() == SettingKey.LANGUAGE) {
            String newResolvedLanguage = event.newResolvedValue();
            // Refresh localized UI, scoreboard, cached messages, etc.
        }
    }
}
```

## Listar nick styles disponibles

```java
import com.stephanofer.networkplayersettings.settings.api.PlayerStyleService;
import com.stephanofer.networkplayersettings.settings.api.StylePatternInfo;

PlayerStyleService styles = Bukkit.getServicesManager().load(PlayerStyleService.class);
if (styles != null) {
    for (StylePatternInfo info : styles.nickPatterns()) {
        boolean unlocked = info.permission().isBlank() || player.hasPermission(info.permission());
        String preview = styles.nickPreviewMiniMessage(player, info.id());
    }
}
```

## Guardar un nick style correctamente

```java
if (!styles.canUseNickStyle(player, patternId)) {
    player.sendMessage("You cannot use that style");
    return;
}

styles.setNickStyle(player.getUniqueId(), patternId)
    .exceptionally(error -> {
        plugin.getLogger().warning("Could not save nick style: " + error.getMessage());
        return null;
    });
```

## Renderizar chat propio con el style activo

```java
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

String safeText = PlainTextComponentSerializer.plainText().serialize(originalMessage);
Component finalMessage = styles.formatChatMessage(player, Component.text(safeText))
    .orElse(Component.text(safeText));
```

Este ejemplo replica la semántica real del listener del core: primero texto plano, después aplicar style.

## Acceder a assets de país

```java
import com.stephanofer.networkplayersettings.assets.api.CountryAsset;
import com.stephanofer.networkplayersettings.assets.api.NetworkAssetService;

NetworkAssetService assets = Bukkit.getServicesManager().load(NetworkAssetService.class);
if (assets != null) {
    CountryAsset asset = assets.countryAsset(settings.countryCode(player.getUniqueId()));
    String displayName = asset.displayName();
    String texture = asset.headTextureBase64();
}
```

## Do / Don't

| Do | Don't |
|---|---|
| Usá `ServicesManager` para `PlayerSettingsService` y `NetworkAssetService`. | No instancies `DefaultPlayerSettingsService`, repositorios ni loaders internos. |
| Esperá `PlayerSettingsReadyEvent` o `isReady(UUID)`. | No asumas datos listos en cualquier evento temprano de conexión. |
| Encadená `CompletableFuture` para mutaciones. | No hagas `join()`/`get()` en main thread. |
| Usá `setLanguage`, `setCountryOverride`, `clearCountryOverride`. | No escribas directo en MySQL ni uses `setSetting` para claves no escribibles. |
| Usá `PlayerStyleService#setNickStyle` y `setChatStyle` para estilos. | No uses `PlayerSettingsService#setSetting` para styles si necesitás validación de existencia o previews coherentes. |
| Tratá `cached(UUID)` como lectura best-effort. | No confundas snapshot default con datos persistidos. |
| Manejás `null` al usar servicios con `softdepend`. | No crashees si NetworkPlayerSettings no está instalado cuando tu integración es opcional. |
| Usá `CountryFlag.normalizeCode` y fallback `XX`. | No confíes en input externo de país sin normalizar. |
| Tratá `%playersettings_nick_style_name%` y `%playersettings_chat_style_name%` como MiniMessage. | No asumas que esos placeholders son texto plano. |

## Fallos que debe manejar un consumidor

- Servicio ausente: plugin deshabilitado, startup fallido o integración opcional.
- Future fallido al mutar: DB caída, error de pool o excepción de persistencia.
- Jugador no listo: join todavía no procesado o jugador desconectado.
- Placeholder con fallback/cache: para jugadores no cacheados usa defaults; para jugadores cacheados se invalida al cambiar settings o al salir.
- País desconocido `XX`: GeoIP apagado, IP no pública, DB faltante o lookup fallido.
