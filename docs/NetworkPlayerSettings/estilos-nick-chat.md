# Nick styles y chat styles

Esta guía documenta la feature real de estilos de nick y estilos de chat implementada hoy en `NetworkPlayerSettings` y en su addon `networkplayersettings-zmenu`. Está pensada para plugins consumidores que necesitan integrarse SIN asumir detalles internos y sin romperse cuando cambie la implementación.

## Ruta rápida

1. Obtené `PlayerStyleService` desde `ServicesManager`.
2. Usá `nickPatterns()` y `chatPatterns()` para listar el catálogo público.
3. Usá `setNickStyle(...)` y `setChatStyle(...)` para mutar estilos. No uses `PlayerSettingsService#setSetting(...)` para esto salvo que realmente quieras escribir un valor raw.
4. Antes de permitir que un jugador seleccione un estilo, validá permisos con `canUseNickStyle(...)` o `canUseChatStyle(...)`.
5. Para renderizar, usá `formattedNick(...)`, `formattedNickMiniMessage(...)` y `formatChatMessage(...)`.
6. Para un jugador offline, usá `formattedNick(NickStyleRenderRequest)` y resolvé permisos externamente.
7. Si tu plugin consume PlaceholderAPI, usá los placeholders `%playersettings_*%` documentados abajo.

## Qué expone el sistema

### API pública para consumidores

- `com.stephanofer.networkplayersettings.settings.api.PlayerStyleService`
- `com.stephanofer.networkplayersettings.settings.api.StylePatternInfo`
- `com.stephanofer.networkplayersettings.settings.api.SettingKey` con `NICK_STYLE` y `CHAT_STYLE`
- `com.stephanofer.networkplayersettings.settings.event.PlayerSettingChangeEvent`

### Implementación interna que NO deberías consumir directamente

- `settings/style/StylePattern`
- `settings/style/StylePatternCatalog`
- `settings/style/StylePatternCatalogLoader`
- `settings/style/StylePatternRenderer`
- `settings/style/DefaultPlayerStyleService`
- `platform/bukkit/PlayerChatStyleListener`
- clases del addon zMenu bajo `networkplayersettings-zmenu/.../settings/style`

## Archivos fuente reales

### Core

- `src/main/resources/styles/nick-patterns.yml`
- `src/main/resources/styles/chat-patterns.yml`
- `src/main/java/com/stephanofer/networkplayersettings/settings/api/PlayerStyleService.java`
- `src/main/java/com/stephanofer/networkplayersettings/settings/api/StylePatternInfo.java`
- `src/main/java/com/stephanofer/networkplayersettings/settings/style/DefaultPlayerStyleService.java`
- `src/main/java/com/stephanofer/networkplayersettings/platform/bukkit/PlayerChatStyleListener.java`
- `src/main/java/com/stephanofer/networkplayersettings/platform/bukkit/PlayerSettingsPlaceholderExpansion.java`

### zMenu addon

- `networkplayersettings-zmenu/src/main/resources/inventories/nick-styles.yml`
- `networkplayersettings-zmenu/src/main/resources/inventories/chat-styles.yml`
- `networkplayersettings-zmenu/src/main/java/com/stephanofer/networkplayersettingszmenu/settings/style/*`

## Catálogo actual bundled

El core carga ambos catálogos durante el startup. Si uno es inválido, el plugin falla al habilitarse.

| Catálogo | Archivo | Cantidad actual | Placeholder obligatorio |
|---|---|---:|---|
| Nick styles | `styles/nick-patterns.yml` | 84 | `<name>` |
| Chat styles | `styles/chat-patterns.yml` | 70 | `<message>` |

Las categorías actuales son convencionales, no un enum duro. Hoy aparecen valores como `basic`, `clean`, `professional`, `competitive`, `dark`, `soft`, `luxury`, `premium`, `special` y `pride`.

## Contrato público principal: `PlayerStyleService`

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

### Lectura de catálogo

- `nickPatterns()` y `chatPatterns()` devuelven una lista inmutable de `StylePatternInfo` en el mismo orden en que aparecen en el YAML.
- `nickPattern(id)` y `chatPattern(id)` buscan por ID normalizado case-insensitive.
- Si el ID no existe o viene blanco, devuelven `Optional.empty()`.

### Lectura de selección guardada

- `nickStyleId(playerId)` y `chatStyleId(playerId)` leen el valor persistido/cacheado del setting.
- Si el valor está vacío, devuelven `Optional.empty()`.
- OJO: estos métodos no verifican permisos. Si un jugador tiene guardado un estilo que ya no puede usar, el ID sigue apareciendo como seleccionado.

### Permisos y estilo activo

- `canUseNickStyle(...)` y `canUseChatStyle(...)` devuelven `true` solo si el patrón existe y el jugador tiene el permiso requerido, o si el patrón no define permiso.
- `hasActiveNickStyle(...)` y `hasActiveChatStyle(...)` requieren dos cosas: que haya un ID guardado válido y que el jugador siga teniendo permiso para usarlo.
- Si el patrón existe pero el jugador perdió el permiso, el estilo queda guardado pero NO se considera activo.

### Render de nick

- `formattedNick(player)` devuelve `Component` con el estilo activo aplicado.
- Si no hay estilo activo, devuelve `Component.text(player.getName())`.
- `formattedNickMiniMessage(player)` devuelve un string MiniMessage listo para otra pipeline que consuma MiniMessage.
- `nickPreviewMiniMessage(player, patternId)` renderiza la preview del patrón pedido usando el nombre actual del jugador, aunque ese patrón no sea su selección activa.

### Render de nick offline

```java
styleService.formattedNick(new NickStyleRenderRequest(
    playerId,
    knownUsername,
    permission -> resolvedOfflinePermissions.has(permission)
)).thenAccept(nick -> {
    // Componer y enviar el mensaje desde la estrategia de scheduling del consumidor.
});
```

- La operación carga settings desde caché o base de datos de manera asíncrona.
- Una lectura offline no crea filas default para UUIDs sin settings.
- Consultas concurrentes para el mismo UUID comparten una única carga.
- Si no hay un estilo activo, el patrón persistido ya no existe o el permiso offline es denegado, devuelve el username como `Component` sin estilo.
- El `permissionChecker` solo se invoca cuando el patrón activo define un permiso. Si falla, el future falla para no confundir un error de autorización con una denegación real.
- `NetworkPlayerSettings` no resuelve LuckPerms. El consumidor debe entregar un resolvedor de permisos con el contexto correcto.

### Render de chat

- `formatChatMessage(player, message)` devuelve `Optional.empty()` si no hay estilo de chat activo.
- Si hay estilo activo, devuelve el `Component` formateado con el patrón.
- `chatPreviewMiniMessage(player)` devuelve la preview de la selección activa del jugador o `""` si no hay ninguna activa.
- `chatPreviewMiniMessage(patternId)` devuelve la preview del patrón pedido o `""` si no existe.

### Mutaciones

- `setNickStyle(...)` y `setChatStyle(...)` normalizan el ID a minúsculas con trim.
- Si el patrón no existe en el catálogo correspondiente, fallan con `IllegalArgumentException` dentro del `CompletableFuture`.
- `clearNickStyle(...)` y `clearChatStyle(...)` persisten `""`.
- Las mutaciones no chequean permisos. Esa validación es responsabilidad del plugin consumidor o de la UI.

Esto último es MUY importante: el core valida existencia, no autorización. La UI zMenu sí hace chequeo de permiso antes de llamar al servicio.

## `StylePatternInfo`

```java
public record StylePatternInfo(
    String id,
    String displayName,
    String category,
    String permission,
    String previewText
) {}
```

### Significado de cada campo

- `id`: ID técnico estable del patrón. Se normaliza a minúsculas.
- `displayName`: nombre visible definido en YAML. Puede contener MiniMessage.
- `category`: categoría funcional para agrupar en UI o lógica externa.
- `permission`: permiso requerido. Si está vacío, el patrón es público.
- `previewText`: texto base de preview almacenado en el catálogo.

### Cosas que un consumidor debe saber

- `displayName` NO está serializado a texto plano. Si lo mostrás en una UI propia, tratá el valor como MiniMessage.
- `category` no está limitado a una lista cerrada por API.
- `previewText` es solo el texto base del catálogo. La preview final visible se obtiene con `PlayerStyleService`.

## Persistencia y settings involucrados

Los estilos viven en los settings públicos:

- `SettingKey.NICK_STYLE` con `storageKey = "nick_style"`
- `SettingKey.CHAT_STYLE` con `storageKey = "chat_style"`

Ambos tienen estas propiedades hoy:

- `persisted = true`
- `playerWritable = true`
- `defaultValue = ""`

Se persisten en la tabla `${tablePrefix}player_settings` usando la misma infraestructura del resto de settings.

### Recomendación fuerte

Aunque `PlayerSettingsService#setSetting(playerId, SettingKey.NICK_STYLE, value)` y `setSetting(..., CHAT_STYLE, ...)` hoy funcionan, NO es la API recomendada para plugins consumidores porque:

1. solo hace trim del valor raw;
2. no valida que el ID exista en el catálogo;
3. no aplica ninguna semántica específica de styles.

Para estilos, usá `PlayerStyleService`.

## Comportamiento runtime real

## Bootstrap

Durante `NetworkPlayerSettingsPlugin#onEnable()`:

1. se cargan `styles/nick-patterns.yml` y `styles/chat-patterns.yml`;
2. se construyen dos `StylePatternCatalog` independientes;
3. se registra `PlayerStyleService` en `ServicesManager`;
4. se registra `PlayerChatStyleListener`.

Si cualquiera de los catálogos es inválido, el startup falla y el plugin se deshabilita.

## Cómo se decide si un estilo está activo

Para nick y chat la lógica es la misma:

1. leer el valor del setting desde `PlayerSettingsService`;
2. buscar el patrón en el catálogo;
3. verificar permiso del jugador;
4. solo entonces tratarlo como estilo activo.

Consecuencia práctica:

- un estilo guardado puede existir pero no estar activo;
- `nickStyleId()` puede devolver un valor mientras `hasActiveNickStyle()` devuelve `false`.

## Pipeline real del chat

El listener `PlayerChatStyleListener` escucha `AsyncChatEvent` con prioridad `HIGH`.

Flujo exacto:

1. si el jugador no tiene estilo de chat activo, no toca el mensaje;
2. si tiene estilo activo, serializa `event.message()` a texto plano;
3. crea `Component.text(safeText)`;
4. aplica el patrón encima de ese texto;
5. reemplaza `event.message(...)`.

### Implicación importante

El sistema elimina formato previo del mensaje antes de aplicar el style. O sea:

- si otro plugin ya había metido colores, hover o click events dentro del `message`, este listener los aplana a texto;
- el style de chat está pensado como dueño visual final del contenido del mensaje, no como una capa que preserve formatting arbitrario previo.

Si tu plugin reemplaza la pipeline de chat o usa otro evento/motor de chat, no asumas que el listener core te cubre. En ese caso integrá vos mismo con `PlayerStyleService`.

## Render con MiniMessage

`StylePatternRenderer` usa `MiniMessage.miniMessage()` por compatibilidad runtime con Adventure 4.x en Paper.

Reglas visibles:

- nick usa `Placeholder.component("name", Component.text(playerName))`;
- chat usa `Placeholder.component("message", message)`;
- las previews MiniMessage escapan tags del nombre del jugador y del `previewText` antes de inyectarlos.

## Placeholders de styles

Todos pertenecen a la expansión `%playersettings_*%`.

| Placeholder | Valor real |
|---|---|
| `%playersettings_nick_style_id%` | ID guardado del nick style o `""`. |
| `%playersettings_nick_style_name%` | `displayName` del patrón guardado o `""`. |
| `%playersettings_nick_style_category%` | `category` del patrón guardado o `""`. |
| `%playersettings_nick_style_permission%` | permiso del patrón guardado o `""`. |
| `%playersettings_nick_formatted%` | nick formateado como MiniMessage si el estilo está activo; si no, nombre raw del jugador. |
| `%playersettings_nick_formatted_raw%` | hoy devuelve exactamente lo mismo que `%playersettings_nick_formatted%`. |
| `%playersettings_chat_style_id%` | ID guardado del chat style o `""`. |
| `%playersettings_chat_style_name%` | `displayName` del patrón guardado o `""`. |
| `%playersettings_chat_style_category%` | `category` del patrón guardado o `""`. |
| `%playersettings_chat_style_permission%` | permiso del patrón guardado o `""`. |
| `%playersettings_chat_preview%` | preview MiniMessage del chat style guardado o `""`. |

### Matices importantes

- `*_style_id`, `*_style_name`, `*_style_category` y `*_style_permission` reflejan el valor guardado si el patrón existe, aunque el jugador ya no tenga permiso.
- `%playersettings_nick_formatted%` sí respeta permisos porque usa la noción de estilo activo.
- `%playersettings_chat_preview%` se basa en el ID guardado, no en `hasActiveChatStyle(...)`.
- `%playersettings_nick_style_name%` y `%playersettings_chat_style_name%` devuelven MiniMessage, no texto plano.

## Eventos relacionados

Los cambios de styles disparan `PlayerSettingChangeEvent` desde `DefaultPlayerSettingsService`.

Para styles, el evento lleva:

- `settingKey = SettingKey.NICK_STYLE` o `SettingKey.CHAT_STYLE`
- `oldValue` y `newValue` con el ID raw persistido
- `oldResolvedValue` y `newResolvedValue` con el mismo valor raw persistido

No existe hoy un evento específico tipo `PlayerNickStyleChangeEvent`.

## Integración correcta para plugins consumidores

## Obtener el servicio

```java
PlayerStyleService styleService = Bukkit.getServicesManager().load(PlayerStyleService.class);
if (styleService == null) {
    throw new IllegalStateException("Missing PlayerStyleService");
}
```

## Mostrar catálogo en una UI propia

```java
for (StylePatternInfo info : styleService.nickPatterns()) {
    boolean unlocked = info.permission().isBlank() || player.hasPermission(info.permission());
    String preview = styleService.nickPreviewMiniMessage(player, info.id());
}
```

## Guardar selección respetando permisos

```java
if (!styleService.canUseNickStyle(player, patternId)) {
    player.sendMessage("No tenes permiso para usar ese estilo");
    return;
}

styleService.setNickStyle(player.getUniqueId(), patternId)
    .exceptionally(error -> {
        plugin.getLogger().warning("Could not save nick style: " + error.getMessage());
        return null;
    });
```

## Renderizar tu propio chat

```java
String safeText = PlainTextComponentSerializer.plainText().serialize(originalMessage);
Component rendered = styleService
    .formatChatMessage(player, Component.text(safeText))
    .orElse(Component.text(safeText));
```

Si tu plugin necesita preservar componentes avanzados, entonces tenés que diseñar tu propia estrategia. El core actual no preserva formatting arbitrario previo al aplicar styles.

## Cómo crear un nuevo nick style o chat style

## Paso 1: agregarlo al catálogo core

### Nick style

Archivo: `src/main/resources/styles/nick-patterns.yml`

```yaml
patterns:
  my-style:
    display-name: "<gradient:#80ffdb:#5390d9>My Style</gradient>"
    category: "premium"
    permission: "networkplayersettings.nick.my-style"
    mini-message: "<gradient:#80ffdb:#5390d9><name></gradient>"
    preview: "Vendimia"
```

### Chat style

Archivo: `src/main/resources/styles/chat-patterns.yml`

```yaml
patterns:
  my-chat-style:
    display-name: "<gradient:#80ffdb:#5390d9>My Chat Style</gradient>"
    category: "premium"
    permission: "networkplayersettings.chat.my-chat-style"
    mini-message: "<gradient:#80ffdb:#5390d9><message></gradient>"
    preview: "This is my message"
```

## Reglas obligatorias del catálogo

- El ID debe cumplir regex `[a-z0-9_-]{2,64}`.
- `display-name`, `category`, `mini-message` y `preview` no pueden estar vacíos.
- `category` se normaliza a minúsculas.
- Nick styles deben incluir `<name>`.
- Chat styles deben incluir `<message>`.
- `permission` puede estar vacío.

Si incumplís estas reglas, el loader lanza una `IllegalStateException` de catálogo inválido y el plugin no habilita.

## Paso 2: UI zMenu

La UI zMenu de nick/chat styles está paginada y lee el catálogo desde `PlayerStyleService`. Para un pattern normal nuevo, alcanza con agregarlo al YAML core correspondiente:

- `src/main/resources/styles/nick-patterns.yml`
- `src/main/resources/styles/chat-patterns.yml`

No agregues un botón por cada style en el inventario. `nick-styles.yml` y `chat-styles.yml` usan un único botón paginado (`NPS_NICK_STYLE` o `NPS_CHAT_STYLE`) con `slots`, y el addon renderiza cada entrada dinámicamente.

## Paso 3: no tocar loaders salvo que estés creando un nuevo tipo de botón

Para agregar patterns nuevos normales:

- no hace falta crear nuevas clases Java;
- no hace falta crear un nuevo loader;
- no hace falta editar el inventario zMenu;
- solo agregás la entrada nueva al catálogo core.

## Integración del addon zMenu

El addon registra estos tipos de botón:

- `NPS_NICK_STYLE`
- `NPS_CHAT_STYLE`
- `NPS_CLEAR_NICK_STYLE`
- `NPS_CLEAR_CHAT_STYLE`

### Placeholders internos del pattern de botón

Los botones de styles rellenan estos placeholders:

- `%style_id%`
- `%style_name%`
- `%style_category%`
- `%style_permission%`
- `%style_selected_marker%`
- `%style_lock_marker%`
- `%style_state%`
- `%style_action%`
- `%style_preview%`

### Comportamiento de la UI zMenu

- no deja seleccionar un pattern inexistente;
- no deja seleccionar un pattern sin permiso;
- no deja re-seleccionar el mismo estilo;
- aplica cooldown de mutación compartido para settings desde `settings.mutation-cooldown-millis`;
- al fallar la persistencia limpia el cooldown del jugador;
- al salir el jugador limpia su cooldown.

### Matiz visual importante

Un estilo puede figurar como `selected` a nivel raw, pero si el jugador perdió permiso el botón lo muestra como bloqueado. Eso refleja exactamente la lógica del core.

## Qué NO hacer

- No instancies `DefaultPlayerStyleService` manualmente.
- No escribas directo en `nps_player_settings` para estilos.
- No uses `PlayerSettingsService#setSetting(...)` para styles si necesitás validación de existencia.
- No asumas que un ID guardado implica que el estilo está activo.
- No asumas que `%playersettings_nick_style_name%` devuelve texto plano.
- No asumas que el chat style preserva colores o componentes previos.

## Checklist para una integración sana

- [ ] El plugin obtiene `PlayerStyleService` desde `ServicesManager`.
- [ ] La UI valida permisos antes de mutar.
- [ ] Las mutaciones usan `setNickStyle` o `setChatStyle`.
- [ ] El consumidor maneja `CompletableFuture` sin bloquear el main thread.
- [ ] Si renderiza chat propio, decide explícitamente si quiere texto plano o preservar componentes.
- [ ] Si agrega un nuevo pattern, actualiza catálogo core y también inventario zMenu.
- [ ] Si usa placeholders de nombre, trata el valor como MiniMessage.
