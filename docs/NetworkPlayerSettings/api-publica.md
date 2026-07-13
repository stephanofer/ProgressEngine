# Referencia de API pública

La API pública para consumidores está separada por dominio. Las demás clases del proyecto deben tratarse como internas, aunque sean `public` por necesidades de implementación.

| Dominio | Paquete público |
|---|---|
| Settings | `com.stephanofer.networkplayersettings.settings.api` |
| Idioma | `com.stephanofer.networkplayersettings.settings.language` |
| País | `com.stephanofer.networkplayersettings.settings.country.CountryFlag` |
| Eventos de settings | `com.stephanofer.networkplayersettings.settings.event` |
| Assets | `com.stephanofer.networkplayersettings.assets.api` |

## `PlayerSettingsService`

Paquete: `com.stephanofer.networkplayersettings.settings.api`.

Interfaz principal para leer y modificar ajustes.

```java
public interface PlayerSettingsService {
    CompletableFuture<PlayerSettingsSnapshot> load(UUID playerId);
    Optional<PlayerSettingsSnapshot> cached(UUID playerId);
    PlayerSettingsSnapshot getCachedOrDefault(UUID playerId);
    Language resolvedLanguage(Player player);
    LanguagePreference languagePreference(UUID playerId);
    String countryCode(UUID playerId);
    boolean showCountryFlag(UUID playerId);
    CompletableFuture<Void> setLanguage(UUID playerId, LanguagePreference preference);
    CompletableFuture<Void> setCountryOverride(UUID playerId, String countryCode);
    CompletableFuture<Void> clearCountryOverride(UUID playerId);
    CompletableFuture<Void> setShowCountryFlag(UUID playerId, boolean enabled);
    CompletableFuture<Void> setSetting(UUID playerId, SettingKey key, String value);
    Optional<String> getSetting(UUID playerId, SettingKey key);
    boolean isReady(UUID playerId);
}
```

| Método | Contrato observable |
|---|---|
| `load(UUID)` | Si hay caché, devuelve `CompletableFuture` ya completado. Si no, lee desde repositorio async y actualiza caché sin crear defaults persistidos. Las cargas simultáneas del mismo UUID comparten una sola consulta. |
| `cached(UUID)` | Devuelve `Optional` con snapshot en caché o vacío. No fuerza carga. |
| `getCachedOrDefault(UUID)` | Devuelve caché o `PlayerSettingsSnapshot.defaults(playerId)`. No persiste defaults por sí mismo. |
| `resolvedLanguage(Player)` | Resuelve idioma efectivo usando snapshot cache/default y locale actual del jugador si `settings.detect-client-locale` está activo. |
| `languagePreference(UUID)` | Devuelve preferencia guardada/cacheada o `AUTO` por default. |
| `countryCode(UUID)` | Devuelve país efectivo: `country_override` válido si existe; si no `detected_country`; fallback `XX`. |
| `showCountryFlag(UUID)` | Devuelve si el jugador permite renderizar su bandera de país. Default `true`. |
| `setLanguage(UUID, LanguagePreference)` | Persiste async; después agenda actualización de caché y evento en main thread. Si no cambia, devuelve future completado. |
| `setCountryOverride(UUID, String)` | Normaliza ISO alpha-2. Rechaza `XX`/inválidos con future fallido. Persiste async; luego actualiza caché/evento en main thread. |
| `clearCountryOverride(UUID)` | Persiste `country_override` vacío; luego actualiza caché/evento en main thread si cambia el país efectivo. |
| `setShowCountryFlag(UUID, boolean)` | Persiste async la visibilidad de bandera; luego actualiza caché/evento en main thread si cambia. |
| `setSetting(UUID, SettingKey, String)` | Solo acepta claves `playerWritable`. En el código actual soporta `LANGUAGE`, `SHOW_COUNTRY_FLAG`, `NICK_STYLE` y `CHAT_STYLE`. Para styles es una escritura raw con trim, sin validar existencia de catálogo; para consumidores conviene usar `PlayerStyleService`. |
| `getSetting(UUID, SettingKey)` | Lee del snapshot cache/default y devuelve `Optional.empty()` para valores blancos. |
| `isReady(UUID)` | `true` después de que `handleJoin` marca al jugador listo y dispara `PlayerSettingsReadyEvent`; `false` tras quit o antes de ready. |

### Async y errores

Las mutaciones devuelven `CompletableFuture<Void>`. Si falla la persistencia, el future falla y el caché no se actualiza. No bloquees el main thread con `join()`/`get()`.

Las mutaciones persistentes se serializan por jugador para evitar que dos cambios concurrentes completen fuera de orden y dejen el caché con un snapshot viejo.

## `PlayerSettingsSnapshot`

Paquete: `com.stephanofer.networkplayersettings.settings.api`.

Snapshot inmutable de ajustes por jugador.

```java
public final class PlayerSettingsSnapshot {
    public PlayerSettingsSnapshot(UUID playerId, Map<SettingKey, String> values);
    public static PlayerSettingsSnapshot defaults(UUID playerId);
    public UUID playerId();
    public Map<SettingKey, String> values();
    public Optional<String> setting(SettingKey key);
    public String valueOrDefault(SettingKey key);
    public LanguagePreference languagePreference();
    public String detectedCountryCode();
    public Optional<String> countryOverride();
    public String countryCode();
    public boolean showCountryFlag();
    public PlayerSettingsSnapshot withSetting(SettingKey key, String value);
}
```

Defaults aplicados por constructor/snapshot:

| `SettingKey` | Default |
|---|---|
| `LANGUAGE` | `auto` |
| `DETECTED_COUNTRY` | `XX` |
| `COUNTRY_OVERRIDE` | `""` |
| `SHOW_COUNTRY_FLAG` | `true` |
| `NICK_STYLE` | `""` |
| `CHAT_STYLE` | `""` |

Detalles importantes:

- `values()` es un mapa no modificable.
- Los valores `null` se convierten en `""` y se trimmean.
- `setting(key)` filtra valores en blanco.
- `countryOverride()` solo devuelve códigos ISO alpha-2 reales distintos de `XX`.
- `countryCode()` prioriza override; si no existe, usa país detectado.
- `showCountryFlag()` parsea el valor persistido como boolean y cae en `true` por default.

## `SettingKey`

Paquete: `com.stephanofer.networkplayersettings.settings.api`.

```java
public enum SettingKey {
    LANGUAGE("language", true, true, "auto"),
    DETECTED_COUNTRY("detected_country", true, false, "XX"),
    COUNTRY_OVERRIDE("country_override", true, false, ""),
    SHOW_COUNTRY_FLAG("show_country_flag", true, true, "true"),
    NICK_STYLE("nick_style", true, true, ""),
    CHAT_STYLE("chat_style", true, true, "");
}
```

| Método | Resultado |
|---|---|
| `storageKey()` | Nombre persistido en DB. |
| `persisted()` | `true` para las claves actuales. |
| `playerWritable()` | `true` para `LANGUAGE`, `SHOW_COUNTRY_FLAG`, `NICK_STYLE` y `CHAT_STYLE`. |
| `defaultValue()` | Default por clave. |
| `fromStorageKey(String)` | Busca case-insensitive; devuelve `null` si no reconoce la key. |

### Convención para nuevas settings

Antes de agregar una preferencia nueva al core, definí explícitamente:

- `SettingKey`: nombre persistido estable, default y si es escribible por jugador.
- Validación: valores permitidos, normalización y comportamiento ante valores inválidos.
- Persistencia: si requiere fila default en DB o si alcanza con default en `PlayerSettingsSnapshot`.
- Mutación pública: método específico en `PlayerSettingsService` si necesita reglas propias; `setSetting` solo debe usarse para claves genéricas realmente seguras.
- Evento: cuándo debe disparar `PlayerSettingChangeEvent` y qué representan `oldResolvedValue`/`newResolvedValue`.

Contrato actual importante: `country_override = ""` significa “sin override manual”. Si no hay override válido, `countryCode()` usa `detected_country`.

## `PlayerStyleService`

Paquete: `com.stephanofer.networkplayersettings.settings.api`.

Servicio público para catálogo, render y persistencia de nick/chat styles.

```java
public interface PlayerStyleService {
    List<StylePatternInfo> nickPatterns();
    List<StylePatternInfo> chatPatterns();
    Optional<StylePatternInfo> nickPattern(String patternId);
    Optional<StylePatternInfo> chatPattern(String patternId);
    Optional<String> nickStyleId(UUID playerId);
    Optional<String> chatStyleId(UUID playerId);
    boolean canUseNickStyle(Player player, String patternId);
    boolean canUseChatStyle(Player player, String patternId);
    boolean hasActiveNickStyle(Player player);
    boolean hasActiveChatStyle(Player player);
    Component formattedNick(Player player);
    CompletableFuture<Component> formattedNick(NickStyleRenderRequest request);
    String formattedNickMiniMessage(Player player);
    String nickPreviewMiniMessage(Player player, String patternId);
    Optional<Component> formatChatMessage(Player player, Component message);
    String chatPreviewMiniMessage(Player player);
    String chatPreviewMiniMessage(String patternId);
    CompletableFuture<Void> setNickStyle(UUID playerId, String patternId);
    CompletableFuture<Void> clearNickStyle(UUID playerId);
    CompletableFuture<Void> setChatStyle(UUID playerId, String patternId);
    CompletableFuture<Void> clearChatStyle(UUID playerId);
}
```

Reglas observables:

- `nickPatterns()` y `chatPatterns()` exponen el catálogo público ya validado y cargado desde YAML.
- `nickStyleId()` y `chatStyleId()` leen la selección persistida, no necesariamente un estilo activo por permisos.
- `formattedNick()` y `formatChatMessage()` solo aplican el style si el jugador todavía puede usarlo.
- `formattedNick(NickStyleRenderRequest)` carga los settings necesarios para renderizar un nick offline y respeta el permiso provisto por el consumidor.
- `setNickStyle()` y `setChatStyle()` validan existencia de catálogo, pero no validan permisos.
- `clearNickStyle()` y `clearChatStyle()` persisten `""`.

### Render offline de nick

`NickStyleRenderRequest` permite renderizar una identidad sin una instancia Bukkit `Player`:

```java
public record NickStyleRenderRequest(
    UUID playerId,
    String username,
    StylePermissionChecker permissionChecker
) {}

@FunctionalInterface
public interface StylePermissionChecker {
    boolean hasPermission(String permission);
}
```

- `playerId`, `username` y `permissionChecker` son obligatorios; el username no puede ser blanco.
- El core consulta el nick style persistido, busca el patrón vigente y solo invoca `permissionChecker` cuando el patrón requiere permiso.
- Si no hay style, el ID ya no existe o el permiso es denegado, devuelve `Component.text(username)`.
- Si falla la lectura de settings o el resolvedor de permisos lanza una excepción, el `CompletableFuture` falla.
- El consumidor resuelve permisos offline, incluido su contexto de LuckPerms si corresponde. `NetworkPlayerSettings` no integra LuckPerms.
- La salida canónica es `Component`. Si una integración necesita MiniMessage, debe serializar el componente en su propio borde de integración.

## `StylePatternInfo`

Paquete: `com.stephanofer.networkplayersettings.settings.api`.

```java
public record StylePatternInfo(
    String id,
    String displayName,
    String category,
    String permission,
    String previewText
) {}
```

Reglas observables:

- `id`, `displayName`, `category` y `previewText` no pueden ser blancos.
- `permission` se normaliza a `""` cuando viene `null`.
- `displayName` puede contener MiniMessage; no asumir texto plano.

## `LanguagePreference`

Paquete: `com.stephanofer.networkplayersettings.settings.language`.

```java
public enum LanguagePreference {
    AUTO("auto"), SPANISH("es"), ENGLISH("en")
}
```

- `storageValue()` devuelve el valor persistido.
- `isSupported(String)` valida `auto`, `es`, `en` case-insensitive y con trim.
- `fromStorage(String)` devuelve `AUTO` si el valor es `null`, blanco o desconocido.

## `Language`

Paquete: `com.stephanofer.networkplayersettings.settings.language`.

```java
public enum Language {
    SPANISH("es", "Español", "Spanish"),
    ENGLISH("en", "Inglés", "English")
}
```

- `code()` devuelve `es` o `en`.
- `displayName(Language viewerLanguage)` devuelve el nombre del idioma visto desde español o inglés.
- `fromCode(String)` devuelve `SPANISH` solo para `es`; cualquier otro valor cae en `ENGLISH`.

## `CountryFlag`

Paquete: `com.stephanofer.networkplayersettings.settings.country`.

Utilidad pública de país.

```java
public final class CountryFlag {
    public static final String UNKNOWN_CODE = "XX";
    public static String normalizeCode(String raw);
    public static boolean isIsoAlpha2(String code);
}
```

- `normalizeCode(null)` o valores inválidos devuelven `XX`.
- `isIsoAlpha2` exige exactamente dos letras mayúsculas `A-Z`.

## `CountryAsset`

Paquete: `com.stephanofer.networkplayersettings.assets.api`.

```java
public final class CountryAsset {
    public CountryAsset(String code, String displayName, String headTextureBase64, Set<String> aliases);
    public String code();
    public String displayName();
    public String headTextureBase64();
    public Set<String> aliases();
}
```

Validaciones del constructor:

- `code` se normaliza a mayúsculas y debe ser ISO alpha-2.
- `displayName` y `headTextureBase64` no pueden ser blancos.
- aliases se normalizan a minúsculas, no pueden ser blancos y se exponen como set inmutable.

## `NetworkAssetService`

Paquete: `com.stephanofer.networkplayersettings.assets.api`.

```java
public interface NetworkAssetService {
    CountryAsset countryAsset(String codeOrAlias);
    CountryAsset unknownCountryAsset();
    Map<String, CountryAsset> countryAssets();
}
```

- `countryAsset` acepta código o alias; ante `null`, blanco o desconocido devuelve asset fallback `XX`.
- `countryAssets()` expone solo el mapa canónico por códigos, no los aliases, y es inmutable.

## `CountryFlagService`

Paquete: `com.stephanofer.networkplayersettings.assets.api`.

Servicio público para obtener helpers de bandera de país. Compone `PlayerSettingsService`, `NetworkAssetService` y Adventure.

```java
public interface CountryFlagService {
    String COUNTRY_FLAG_TAG = "country_flag";

    CountryAsset asset(UUID playerId);
    CountryAsset assetForCountry(String countryCodeOrAlias);
    String headTextureValue(UUID playerId);
    String headTextureValueForCountry(String countryCodeOrAlias);
    String miniMessageTag(UUID playerId);
    String miniMessageTagForCountry(String countryCodeOrAlias);
    Component flag(UUID playerId);
    CompletableFuture<Component> flagAsync(UUID playerId);
    Component flagForCountry(String countryCodeOrAlias);
    TagResolver resolver(UUID playerId);
    TagResolver resolverForCountry(String countryCodeOrAlias);
}
```

- Métodos con `UUID playerId` respetan `show_country_flag`.
- Métodos `ForCountry` son helpers directos del catálogo y no leen settings de jugador.
- `flag(UUID)` devuelve `Component.empty()` cuando el jugador desactiva la bandera.
- `flagAsync(UUID)` carga settings si no están en caché y devuelve la bandera efectiva respetando `show_country_flag`; no crea defaults en persistencia.
- `resolver(UUID)` registra `<country_flag>` para MiniMessage.
- `miniMessageTag(...)` devuelve `<craftkit_head:VALUE>` para pipelines que primero resuelven PlaceholderAPI y luego MiniMessage con CraftKit.

## Eventos públicos

### `PlayerSettingsReadyEvent`

Paquete: `com.stephanofer.networkplayersettings.settings.event`.

```java
public final class PlayerSettingsReadyEvent extends Event {
    public PlayerSettingsReadyEvent(Player player, PlayerSettingsSnapshot snapshot, Language resolvedLanguage);
    public Player player();
    public PlayerSettingsSnapshot snapshot();
    public Language resolvedLanguage();
    public String countryCode();
}
```

Se dispara durante `PlayerJoinEvent` después de marcar al jugador como listo. El evento incluye el snapshot disponible y el idioma resuelto en ese momento.

### `PlayerSettingChangeEvent`

Paquete: `com.stephanofer.networkplayersettings.settings.event`.

```java
public final class PlayerSettingChangeEvent extends Event {
    public PlayerSettingChangeEvent(
        UUID playerId,
        SettingKey settingKey,
        String oldValue,
        String newValue,
        @Nullable String oldResolvedValue,
        @Nullable String newResolvedValue
    );
    public UUID playerId();
    public SettingKey settingKey();
    public String oldValue();
    public String newValue();
    public @Nullable String oldResolvedValue();
    public @Nullable String newResolvedValue();
}
```

Se dispara para cambios manuales de idioma/país efectivo y para cambios de locale cuando la preferencia está en `AUTO` y el idioma resuelto cambia. `oldValue`/`newValue` nunca son `null`; valores resueltos pueden ser `null` según el constructor, aunque los caminos actuales pasan códigos de idioma o país cuando corresponde.
