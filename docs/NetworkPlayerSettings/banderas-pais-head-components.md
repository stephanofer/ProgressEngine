# Banderas de País con Player Head Components

Esta guía documenta toda la funcionalidad actual para renderizar banderas de país usando player head components en `NetworkPlayerSettings` y en su addon `networkplayersettings-zmenu`.

El objetivo es que un plugin consumidor pueda integrar esta feature correctamente, sin depender de clases internas, sin duplicar lógica y sin asumir comportamientos que el código no garantiza.

## Qué resuelve esta feature

La feature permite mostrar una bandera de país asociada al jugador en distintos contextos:

- nametags;
- TAB;
- leaderboards;
- menús;
- texto MiniMessage que termine renderizando un `ObjectComponent` de player head;
- código Java que necesite `Component` o `TagResolver` ya listos.

La bandera no se resuelve por nombre de jugador ni por UUID de Mojang. Se resuelve por el `Value` base64 de la textura del asset del país.

## Arquitectura real

La implementación actual está separada en tres capas.

### 1. Estado del jugador: `PlayerSettingsService`

Archivo principal:

- `src/main/java/com/stephanofer/networkplayersettings/settings/api/PlayerSettingsService.java`

Responsabilidades relevantes para esta feature:

- resolver el país efectivo del jugador con `countryCode(UUID)`;
- exponer si el jugador quiere mostrar su bandera con `showCountryFlag(UUID)`;
- persistir el toggle con `setShowCountryFlag(UUID, boolean)`.

### 2. Catálogo de assets: `NetworkAssetService`

Archivos principales:

- `src/main/java/com/stephanofer/networkplayersettings/assets/api/NetworkAssetService.java`
- `src/main/java/com/stephanofer/networkplayersettings/assets/api/CountryAsset.java`

Responsabilidades:

- resolver un asset por código o alias;
- exponer `code`, `displayName`, `headTextureBase64` y `aliases`;
- devolver el fallback `XX` cuando no hay coincidencia.

Esta capa no sabe nada de jugadores, settings ni renderizado.

### 3. Helpers de renderizado: `CountryFlagService`

Archivos principales:

- `src/main/java/com/stephanofer/networkplayersettings/assets/api/CountryFlagService.java`
- `src/main/java/com/stephanofer/networkplayersettings/assets/country/DefaultCountryFlagService.java`

Responsabilidades:

- componer `PlayerSettingsService` + `NetworkAssetService`;
- entregar el `Value` base64 correcto para la bandera del jugador;
- entregar un tag MiniMessage listo para CraftKit;
- entregar un `Component` Adventure listo para enviar/renderizar;
- cargar y renderizar de forma asíncrona una bandera de un jugador offline;
- entregar un `TagResolver` MiniMessage listo para código Java.

## Registro de servicios públicos

Durante `onEnable`, el core registra estos servicios en `ServicesManager`:

- `PlayerSettingsService`
- `NetworkAssetService`
- `CountryFlagService`

Archivo:

- `src/main/java/com/stephanofer/networkplayersettings/NetworkPlayerSettingsPlugin.java`

Lookup recomendado:

```java
PlayerSettingsService settings = Bukkit.getServicesManager().load(PlayerSettingsService.class);
NetworkAssetService assets = Bukkit.getServicesManager().load(NetworkAssetService.class);
CountryFlagService flags = Bukkit.getServicesManager().load(CountryFlagService.class);

if (settings == null || assets == null || flags == null) {
    throw new IllegalStateException("NetworkPlayerSettings services are not available");
}
```

## Modelo de datos relevante

### `SettingKey.SHOW_COUNTRY_FLAG`

Archivo:

- `src/main/java/com/stephanofer/networkplayersettings/settings/api/SettingKey.java`

Contrato real:

- storage key: `show_country_flag`
- persisted: `true`
- playerWritable: `true`
- default: `"true"`

### `PlayerSettingsSnapshot#showCountryFlag()`

Archivo:

- `src/main/java/com/stephanofer/networkplayersettings/settings/api/PlayerSettingsSnapshot.java`

Contrato real:

- si no existe fila persistida, el snapshot cae al default `true`;
- el valor se parsea como boolean;
- el core persiste defaults faltantes en DB al cargar/crear al jugador.

### Persistencia en DB

Archivo:

- `src/main/java/com/stephanofer/networkplayersettings/settings/storage/SqlPlayerSettingsRepository.java`

Comportamiento observable:

- al cargar/crear al jugador, si falta `show_country_flag`, se inserta la fila default con valor `true`;
- no se creó columna nueva ni migración nueva;
- sigue usando la misma tabla key-value `player_settings`.

## Flujo de resolución de bandera

Para un jugador concreto, el flujo real es este:

1. `PlayerSettingsService#countryCode(UUID)` resuelve el país efectivo.
2. `PlayerSettingsService#showCountryFlag(UUID)` dice si la bandera debe mostrarse.
3. `NetworkAssetService#countryAsset(code)` obtiene el asset.
4. `CountryFlagService` transforma eso en alguno de estos formatos:
   - `String` base64;
   - `String` con tag MiniMessage para CraftKit;
   - `Component` Adventure;
   - `TagResolver` MiniMessage.

## Comportamientos importantes

### Si el jugador desactiva la bandera

Para métodos player-aware de `CountryFlagService`:

- `headTextureValue(UUID)` devuelve `""`
- `miniMessageTag(UUID)` devuelve `""`
- `flag(UUID)` devuelve `Component.empty()`
- `resolver(UUID)` resuelve `<country_flag>` a `Component.empty()`

### Render offline

`flag(UUID)` es síncrono y consulta el estado ya disponible en caché. Para una identidad de jugador offline, usá `flagAsync(UUID)`:

```java
flags.flagAsync(playerId).thenAccept(flag -> {
    // flag es la bandera persistida o Component.empty() si está desactivada.
});
```

- carga el snapshot desde caché o almacenamiento de manera asíncrona;
- respeta `country_override`, `detected_country` y `show_country_flag`;
- no crea filas default al consultar un UUID que no tiene settings;
- completa excepcionalmente si falla la lectura de settings.

### Si el país efectivo es `XX`

`XX` se trata como país normal.

No significa vacío.

Si la bandera está activa:

- se usa el asset `XX` del catálogo;
- se devuelve su `headTextureBase64`;
- se puede renderizar una cabeza válida para `XX`.

### Si pedís un país directo con `ForCountry(...)`

Los métodos:

- `assetForCountry(String)`
- `headTextureValueForCountry(String)`
- `miniMessageTagForCountry(String)`
- `flagForCountry(String)`
- `resolverForCountry(String)`

no leen settings del jugador.

Son helpers directos sobre el catálogo.

Esto es útil para:

- menús de administración;
- previews;
- leaderboards por país;
- renderizado de assets estáticos.

## API pública disponible

### `PlayerSettingsService`

Métodos nuevos relevantes:

```java
boolean showCountryFlag(UUID playerId);
CompletableFuture<Void> setShowCountryFlag(UUID playerId, boolean enabled);
```

Contrato observable de `setShowCountryFlag`:

- persiste async;
- actualiza caché recién después de persistir correctamente;
- dispara `PlayerSettingChangeEvent` en main thread;
- si la persistencia falla, el caché no cambia;
- si el valor no cambió, devuelve future completado.

### `CountryFlagService`

Interfaz real:

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
    Component flagForCountry(String countryCodeOrAlias);

    TagResolver resolver(UUID playerId);
    TagResolver resolverForCountry(String countryCodeOrAlias);
}
```

### `CountryAsset`

Información que entrega:

```java
String code();
String displayName();
String headTextureBase64();
Set<String> aliases();
```

## Cómo integrar desde Java

### Caso 1: quiero el `Value` base64

```java
String textureValue = flags.headTextureValue(player.getUniqueId());
if (textureValue.isEmpty()) {
    return;
}
```

Úsalo cuando otro sistema tuyo ya sabe cómo renderizar la textura.

### Caso 2: quiero un `Component` listo

```java
Component flag = flags.flag(player.getUniqueId());
Component line = Component.text()
    .append(flag)
    .append(Component.space())
    .append(Component.text(player.getName()))
    .build();
```

Internamente el componente usa:

```java
Component.object(ObjectContents.playerHead()
    .profileProperty(PlayerHeadObjectContents.property("textures", value))
    .build())
```

### Caso 3: quiero integrar con MiniMessage sin PlaceholderAPI

```java
MiniMessage miniMessage = MiniMessage.builder()
    .editTags(tags -> tags.resolver(flags.resolver(player.getUniqueId())))
    .build();

Component component = miniMessage.deserialize("<country_flag> <green>Vendimia");
```

`resolver(UUID)` registra el tag:

```text
<country_flag>
```

### Caso 4: quiero usar un país explícito

```java
Component peru = flags.flagForCountry("PE");
Component peruByAlias = flags.flagForCountry("peru");
```

## Cómo integrar con CraftKit + MiniMessage

Referencia del contrato base:

- `docs/Craftkit/craftkit-paper/README.md`

CraftKit registra el tag:

```text
<craftkit_head:texture[:hat]>
```

`NetworkPlayerSettings` te entrega dos formatos listos para ese flujo:

- `%playersettings_country_head_value%`
- `%playersettings_country_head_tag%`

### Opción recomendada para configs externas

```text
<craftkit_head:%playersettings_country_head_value%>
```

### Opción más corta

```text
%playersettings_country_head_tag%
```

### Orden correcto si usás PlaceholderAPI

1. Resolver placeholders en el string.
2. Parsear el resultado con MiniMessage y el resolver de CraftKit.

Ejemplo:

```java
String raw = "%playersettings_country_head_tag% <green>Vendimia";
String withPlaceholders = PlaceholderAPI.setPlaceholders(player, raw);
Component component = miniMessage.deserialize(withPlaceholders);
```

## PlaceholderAPI: placeholders disponibles

Archivo real:

- `src/main/java/com/stephanofer/networkplayersettings/platform/bukkit/PlayerSettingsPlaceholderExpansion.java`

Identificador de expansión:

```text
playersettings
```

### Requisito para que estos placeholders existan

La expansión solo se registra si se cumplen las dos condiciones del core:

1. `placeholderapi.enabled: true` en `config.yml`.
2. El plugin `PlaceholderAPI` está instalado y habilitado.

Si una de esas condiciones no se cumple:

- `%playersettings_country_head_value%` no se va a resolver;
- `%playersettings_country_head_tag%` no se va a resolver;
- cualquier integración basada en `%playersettings_*%` va a quedar en texto literal.

### Placeholders de país/bandera

#### `%playersettings_country%`

Devuelve el país efectivo del jugador.

Reglas:

- para jugador válido: `countryCode(UUID)`;
- sin jugador/UUID: `XX`.

#### `%playersettings_country_display_name%`

Devuelve `CountryAsset.displayName()` del país efectivo.

Reglas:

- para jugador válido: asset del país efectivo;
- sin jugador/UUID: asset `XX`.

#### `%playersettings_country_head_value%`

Devuelve el `Value` base64 del asset del país efectivo.

Reglas:

- si el jugador desactivó bandera: `""`;
- sin jugador/UUID: devuelve el `Value` del asset `XX`.

#### `%playersettings_country_head_tag%`

Devuelve el tag MiniMessage completo:

```text
<craftkit_head:VALUE>
```

Reglas:

- si el jugador desactivó bandera: `""`;
- sin jugador/UUID: devuelve tag para el asset `XX`.

#### `%playersettings_show_country_flag%`

Devuelve `true` o `false`.

Reglas:

- para jugador válido: preferencia persistida/cacheada;
- sin jugador/UUID: `true`.

### Caché interna de placeholders

La expansión cachea por `playerId:param` cuando `placeholderapi.cache-ttl-millis` es positivo.

Configuración actual del core:

```yaml
placeholderapi:
  enabled: true
  cache-ttl-millis: 250
  cache-maximum-size: 50000
```

Invalidación real:

- al recibir `PlayerSettingChangeEvent`;
- en `PlayerQuitEvent`.

Esto importa porque `show_country_flag` y los placeholders derivados no quedan stale después de un cambio persistido correcto.

## Ejemplos correctos de integración

### Nametag o TAB con PlaceholderAPI + CraftKit

```text
%playersettings_country_head_tag% <gray>[VIP]</gray> %player_name%
```

Si el jugador ocultó su bandera, el resultado del placeholder será vacío y el resto del texto seguirá funcionando.

### Texto con CraftKit explícito

```text
<craftkit_head:%playersettings_country_head_value%> <green>%player_name%
```

### Leaderboard por país fijo

```java
Component line = Component.text()
    .append(Component.text("TOP 1 "))
    .append(flags.flagForCountry("PE"))
    .append(Component.space())
    .append(Component.text("Vendimia"))
    .build();
```

### Menú propio con país del jugador

```java
CountryAsset asset = flags.asset(player.getUniqueId());
String displayName = asset.displayName();
String code = asset.code();
String texture = asset.headTextureBase64();
```

## Validaciones y errores importantes

### `setShowCountryFlag(UUID, boolean)`

No recibe string, así que no tiene problema de parseo.

### `setSetting(UUID, SettingKey.SHOW_COUNTRY_FLAG, String)`

Sí valida estrictamente el string.

Valores válidos:

- `true`
- `false`

Reglas:

- hace `trim()`;
- compara case-insensitive;
- si recibe cualquier otro valor, falla con `IllegalArgumentException`;
- no convierte silenciosamente valores inválidos a `false`.

### `setCountryOverride(UUID, String)`

Para esta feature también es relevante:

- rechaza `XX`;
- rechaza códigos inválidos;
- si hay override válido, la bandera usará ese país efectivo.

## Catálogo de países

Archivo:

- `src/main/resources/assets/countries.yml`

Formato actual:

```yaml
countries:
  XX:
    name: Unknown
    head-texture-base64: "eyJ0ZXh0dXJlcyI6e319"
    aliases: [unknown]
  AR:
    name: Argentina
    head-texture-base64: "eyJ0ZXh0dXJlcyI6e319"
    aliases: [argentina, south-america]
```

Reglas relevantes:

- debe existir `XX`;
- `head-texture-base64` no puede estar vacío;
- `displayName` no puede estar vacío;
- aliases se normalizan a minúsculas;
- búsquedas desconocidas caen a `XX`.

### Importante para producción

El repo actualmente trae solo un catálogo mínimo de ejemplo.

Si querés que la feature sea útil en producción, tenés que completar `assets/countries.yml` con los países y texturas reales que vaya a usar tu red.

## Requisitos de renderizado

### Para `Component`/`ObjectComponent`

La representación visual de player head components requiere entorno compatible con Minecraft `1.21.9+`.

### Para el flujo `%playersettings_country_head_tag%`

Necesitás que el consumidor registre el resolver de CraftKit:

```java
MiniMessage miniMessage = MiniMessage.builder()
    .editTags(tags -> tags.resolver(CraftKitMiniMessageTags.playerHead()))
    .build();
```

Si no registrás ese resolver, el texto `<craftkit_head:...>` no se va a interpretar.

## Integración actual del addon `networkplayersettings-zmenu`

El addon ya integra esta feature.

Archivos relevantes:

- `networkplayersettings-zmenu/src/main/java/com/stephanofer/networkplayersettingszmenu/settings/country/CountryFlagButton.java`
- `networkplayersettings-zmenu/src/main/java/com/stephanofer/networkplayersettingszmenu/settings/country/CountryFlagButtonLoader.java`
- `networkplayersettings-zmenu/src/main/java/com/stephanofer/networkplayersettingszmenu/settings/view/SettingsMenuBootstrap.java`
- `networkplayersettings-zmenu/src/main/resources/inventories/language.yml`

### Tipo de botón registrado

```text
NPS_COUNTRY_FLAG
```

### Config del addon

Archivo:

- `networkplayersettings-zmenu/src/main/resources/config.yml`

Valor nuevo:

```yaml
settings:
  country-flag-toggle-cooldown-millis: 500
```

Reglas:

- si el valor es menor a `0`, el config loader lo normaliza a `0`;
- el botón aplica cooldown por jugador para evitar spam de escrituras;
- el cooldown se limpia al salir el jugador y al deshabilitar el addon.

### Comportamiento del botón zMenu

Archivo real:

- `networkplayersettings-zmenu/src/main/java/com/stephanofer/networkplayersettingszmenu/settings/country/CountryFlagButton.java`

Flujo:

1. Verifica `isReady(UUID)`.
2. Si hay cooldown, avisa al jugador.
3. Calcula el siguiente estado con `!showCountryFlag(UUID)`.
4. Llama `setShowCountryFlag(UUID, nextState)`.
5. Si falla, invalida cooldown y muestra error.
6. Si funciona, muestra mensaje de enabled/disabled y re-renderiza el botón.

### Placeholders internos del botón zMenu

El botón registra estos placeholders locales para el item:

- `%country_flag_enabled%`
- `%country_flag_marker%`
- `%country_flag_state%`
- `%country_flag_action%`
- `%country_code%`

### Config por defecto del item

Archivo:

- `networkplayersettings-zmenu/src/main/resources/inventories/language.yml`

Snippet actual:

```yaml
country_flag:
  type: NPS_COUNTRY_FLAG
  is-permanent: true
  slot: 22
  item:
    material: NAME_TAG
    name: "%playersettings_country_head_tag%#ffd166%country_flag_marker%Country Flag"
    lore:
      - "#8c8c8cCountry#8c8c8c: #ffd166%country_code%"
      - "#8c8c8cState#8c8c8c: #92ffff%country_flag_state%"
      - ""
      - "#8c8c8c• #2CCED2Click #92ffff%country_flag_action%"
```

Observaciones importantes:

- el addon usa `%playersettings_country_head_tag%` directamente en el nombre del item;
- para que ese placeholder se resuelva, el core debe haber registrado su expansión de PlaceholderAPI;
- si el jugador ocultó la bandera, el placeholder del core devuelve vacío y el nombre queda solo con el texto;
- si la bandera está activa, el botón agrega glow;
- el archivo se sigue llamando `language.yml`, aunque hoy actúa como menú global de settings.

## Buenas prácticas para plugins consumidores

### Hacé esto

- obtené servicios desde `ServicesManager`;
- usá `CountryFlagService` si querés una API Java cómoda;
- usá `%playersettings_country_head_value%` o `%playersettings_country_head_tag%` en configs externas;
- respetá `show_country_flag` cuando el caso sea player-aware;
- usá `ForCountry(...)` solo cuando tu intención sea ignorar la preferencia del jugador;
- completá el catálogo de países antes de depender visualmente de la feature en producción.

### No hagas esto

- no construyas perfiles Mojang por nombre/UUID para esta feature;
- no leas ni escribas directo `setting_key = 'show_country_flag'` en DB;
- no asumas que `XX` significa vacío;
- no mezcles la capa de catálogo (`NetworkAssetService`) con lógica de settings de jugador;
- no parsees `<craftkit_head:...>` sin registrar el resolver de CraftKit en MiniMessage.

## Casos límite visibles en código

- Si no hay jugador o UUID en PlaceholderAPI, los placeholders de bandera usan el asset `XX` o `true` según el caso.
- `CountryFlagService#asset(UUID)` y `headTextureValue(UUID)` requieren `UUID` no nulo.
- `country_head_tag` no incluye espacio automático; si querés separación visual, agregala vos en la plantilla.
- `CountryFlagService` no tiene caché propia; se apoya en la caché de settings, el catálogo en memoria y el caché corto de PlaceholderAPI.
- El addon zMenu no usa `CountryFlagService` directamente; consume `PlayerSettingsService` y placeholders del core.

## Archivos clave para extender o auditar esta feature

- `src/main/java/com/stephanofer/networkplayersettings/assets/api/CountryFlagService.java`
- `src/main/java/com/stephanofer/networkplayersettings/assets/country/DefaultCountryFlagService.java`
- `src/main/java/com/stephanofer/networkplayersettings/platform/bukkit/PlayerSettingsPlaceholderExpansion.java`
- `src/main/java/com/stephanofer/networkplayersettings/settings/application/DefaultPlayerSettingsService.java`
- `src/main/java/com/stephanofer/networkplayersettings/settings/api/SettingKey.java`
- `src/main/java/com/stephanofer/networkplayersettings/settings/api/PlayerSettingsSnapshot.java`
- `src/main/resources/assets/countries.yml`
- `networkplayersettings-zmenu/src/main/java/com/stephanofer/networkplayersettingszmenu/settings/country/CountryFlagButton.java`
- `networkplayersettings-zmenu/src/main/resources/inventories/language.yml`

## Resumen corto de integración recomendada

Si tu plugin usa configs/MiniMessage:

```text
%playersettings_country_head_tag% <green>%player_name%
```

Si tu plugin usa Java y Adventure:

```java
Component flag = flags.flag(player.getUniqueId());
```

Si querés controlar visibilidad:

```java
settings.setShowCountryFlag(player.getUniqueId(), false);
```

Si querés renderizar un país fijo:

```java
Component peru = flags.flagForCountry("PE");
```

Si necesitás que todo funcione bien:

1. registrá dependencias correctas;
2. cargá servicios desde Bukkit;
3. completá `assets/countries.yml`;
4. si usás `<craftkit_head:...>`, resolvé PlaceholderAPI primero y MiniMessage con CraftKit después;
5. no ignores `show_country_flag` en flujos player-aware.
