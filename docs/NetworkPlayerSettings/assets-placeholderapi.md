# Assets de países y PlaceholderAPI

Este documento cubre puntos de integración complementarios del core `NetworkPlayerSettings`. El core no registra comandos, inventarios ni menús.

## `NetworkAssetService`

NetworkPlayerSettings registra `NetworkAssetService` durante startup después de cargar `assets/countries.yml`.

```java
import com.stephanofer.networkplayersettings.assets.api.CountryAsset;
import com.stephanofer.networkplayersettings.assets.api.NetworkAssetService;

NetworkAssetService assets = Bukkit.getServicesManager().load(NetworkAssetService.class);
CountryAsset argentina = assets.countryAsset("AR");
CountryAsset byAlias = assets.countryAsset("argentina");
CountryAsset fallback = assets.countryAsset("no-existe"); // XX
```

Comportamiento:

- `countryAsset(String)` acepta código canónico o alias.
- Códigos se buscan en mayúsculas; aliases en minúsculas.
- `null`, strings blancos o desconocidos devuelven `unknownCountryAsset()`.
- `countryAssets()` expone un mapa inmutable con códigos canónicos, no aliases.
- Las búsquedas de gameplay son en memoria; el catálogo se carga una vez al bootstrap.

## `CountryFlagService`

NetworkPlayerSettings también registra `CountryFlagService` como capa de ayuda para consumidores Java que necesitan renderizar banderas sin pasar por PlaceholderAPI.

```java
import com.stephanofer.networkplayersettings.assets.api.CountryFlagService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

CountryFlagService flags = Bukkit.getServicesManager().load(CountryFlagService.class);

Component playerFlag = flags.flag(player.getUniqueId());
Component peruFlag = flags.flagForCountry("PE");
TagResolver resolver = flags.resolver(player.getUniqueId());
```

Comportamiento:

- Los métodos con `UUID playerId` respetan `show_country_flag`.
- Los métodos `ForCountry(String)` no dependen de settings de jugador; son helpers directos del catálogo.
- Si el jugador desactiva la bandera, `headTextureValue(UUID)` y `miniMessageTag(UUID)` devuelven `""`, y `flag(UUID)` devuelve `Component.empty()`.
- `XX` se trata como país normal: si el jugador tiene país efectivo `XX` y la bandera activa, se usa el asset `XX`.
- `resolver(UUID)` registra el tag MiniMessage `<country_flag>` para insertar el componente directamente desde código Java.
- `miniMessageTag(...)` devuelve texto compatible con CraftKit: `<craftkit_head:VALUE>`.

## Normalización de códigos de país

`CountryFlag` no depende del catálogo YAML. Sirve para normalizar y validar códigos de país:

```java
import com.stephanofer.networkplayersettings.settings.country.CountryFlag;

String code = CountryFlag.normalizeCode(settings.countryCode(playerId));
boolean validIso = CountryFlag.isIsoAlpha2(code);
```

Fallbacks:

- Código inválido o `null` -> `XX`.

## PlaceholderAPI

La expansión se registra solo si:

1. `placeholderapi.enabled: true`;
2. el plugin PlaceholderAPI está instalado y habilitado.

Metadata real: `softdepend: [PlaceholderAPI]`.

Identificador de expansión: `playersettings`.

| Placeholder | Valor |
|---|---|
| `%playersettings_language%` | Idioma efectivo (`es`/`en`) para jugador online. Offline usa preferencia manual cacheada si existe; si está en `auto` o no hay UUID, usa `settings.default-language`. |
| `%playersettings_language_preference%` | Preferencia guardada/cacheada (`auto`/`es`/`en`). Si no hay jugador/UUID, fallback `auto`. |
| `%playersettings_language_name%` | Nombre del idioma efectivo visto desde ese idioma. Offline sigue la misma regla de fallback que `%playersettings_language%`. |
| `%playersettings_country%` | País efectivo (`AR`, `XX`, etc.). Si no hay jugador/UUID, fallback `XX`. |
| `%playersettings_country_display_name%` | Nombre del asset del país efectivo. Si no hay jugador/UUID, fallback al asset `XX`. |
| `%playersettings_country_head_value%` | `Value` base64 de la textura del país efectivo, listo para `<craftkit_head:%playersettings_country_head_value%>`. Si el jugador desactivó la bandera, devuelve vacío. |
| `%playersettings_country_head_tag%` | Tag MiniMessage completo `<craftkit_head:VALUE>`. Si el jugador desactivó la bandera, devuelve vacío. |
| `%playersettings_show_country_flag%` | `true`/`false` según la preferencia guardada/cacheada del jugador. Si no hay jugador/UUID, fallback `true`. |
| `%playersettings_nick_style_id%` | ID guardado del nick style o `""`. |
| `%playersettings_nick_style_name%` | `displayName` del nick style guardado o `""`. Puede contener MiniMessage. |
| `%playersettings_nick_style_category%` | Categoría del nick style guardado o `""`. |
| `%playersettings_nick_style_permission%` | Permiso del nick style guardado o `""`. |
| `%playersettings_nick_formatted%` | Nick renderizado como MiniMessage si el estilo está activo; si no, nombre del jugador. |
| `%playersettings_nick_formatted_raw%` | Actualmente idéntico a `%playersettings_nick_formatted%`. |
| `%playersettings_chat_style_id%` | ID guardado del chat style o `""`. |
| `%playersettings_chat_style_name%` | `displayName` del chat style guardado o `""`. Puede contener MiniMessage. |
| `%playersettings_chat_style_category%` | Categoría del chat style guardado o `""`. |
| `%playersettings_chat_style_permission%` | Permiso del chat style guardado o `""`. |
| `%playersettings_chat_preview%` | Preview MiniMessage del chat style guardado o `""`. |

Matices de styles:

- Los placeholders `*_style_id`, `*_style_name`, `*_style_category` y `*_style_permission` reflejan el valor guardado si el patrón existe, aunque el jugador ya no tenga permiso.
- `%playersettings_nick_formatted%` sí respeta permisos porque usa la noción de estilo activo.
- `%playersettings_chat_preview%` se basa en el ID guardado, no en `hasActiveChatStyle(...)`.

Los placeholders se cachean por `playerId:param` durante `placeholderapi.cache-ttl-millis` si el TTL es positivo. Con TTL `0`, no se cachean. La expansión invalida el caché del jugador cuando recibe `PlayerSettingChangeEvent` y también al salir el jugador.

## Consumo desde interfaces externas

NetworkPlayerSettings no expone comandos ni menús propios. Un plugin consumidor puede construir cualquier interfaz usando únicamente:

- `PlayerSettingsService` para leer/mutar idioma y país;
- `PlayerStyleService` para catálogo, render y persistencia de styles;
- `NetworkAssetService` para renderizar assets de países;
- `PlayerSettingsReadyEvent` para esperar datos listos;
- `PlayerSettingChangeEvent` para reaccionar a cambios.

Regla importante: la UI externa no debe instanciar servicios internos ni escribir directo en la base de datos. Debe usar `ServicesManager` y los contratos públicos documentados en [`api-publica.md`](api-publica.md).
