# NetworkPlayerSettings: guía de integración para plugins consumidores

NetworkPlayerSettings centraliza ajustes globales de jugador para una network Minecraft/Paper: idioma preferido, idioma resuelto, país detectado, override manual de país, assets de países y estilos persistidos de nick/chat. Esta documentación está escrita para autores de plugins consumidores que quieren depender de este proyecto sin usar clases internas ni asumir estados que todavía no están listos.

## Ruta rápida

1. Declarar dependencia del plugin `NetworkPlayerSettings` en tu metadata de Paper/Bukkit.
2. Agregar la dependencia de compilación desde Maven Local: `com.stephanofer:networkplayersettings:2.0.0`.
3. Obtener `PlayerSettingsService` desde `ServicesManager` cuando tu plugin esté habilitado.
4. Esperar `PlayerSettingsReadyEvent` o comprobar `PlayerSettingsService#isReady(UUID)` antes de leer datos de un jugador conectado.
5. Usar solo los paquetes públicos por dominio: `settings/api`, `settings/event`, `settings/language`, `settings/country` y `assets/api`.
6. Tratar las mutaciones como operaciones persistentes asíncronas: usar el `CompletableFuture` y no bloquear el main thread.

```java
PlayerSettingsService settings = Bukkit.getServicesManager().load(PlayerSettingsService.class);
if (settings == null) {
    throw new IllegalStateException("NetworkPlayerSettings service is not available");
}
```

## Qué provee el proyecto

| Área | Qué expone para consumidores |
|---|---|
| Ajustes de jugador | `com.stephanofer.networkplayersettings.settings.api`: `PlayerSettingsService`, `PlayerSettingsSnapshot`, `PlayerStyleService`, `StylePatternInfo`, `SettingKey`. |
| Idioma | `com.stephanofer.networkplayersettings.settings.language`: `LanguagePreference`, `Language`. |
| Eventos | `com.stephanofer.networkplayersettings.settings.event`: `PlayerSettingsReadyEvent`, `PlayerSettingChangeEvent`. |
| Países | `com.stephanofer.networkplayersettings.settings.country.CountryFlag`, `com.stephanofer.networkplayersettings.assets.api.CountryAsset`, `NetworkAssetService`. |
| PlaceholderAPI | Expansión `%playersettings_*%` si PlaceholderAPI está instalada y la config lo permite. |
| UI/comandos | No pertenecen al core. Cualquier UI debe consumir la API pública como otro plugin. |

## Arquitectura de directorios

El código fuente usa una estructura feature-first. La idea es que una nueva preferencia global se ubique bajo `settings/<feature>` y no en carpetas técnicas genéricas.

| Área | Ruta principal | Qué contiene |
|---|---|---|
| Settings | `src/main/java/com/stephanofer/networkplayersettings/settings` | API, aplicación, eventos, storage y subdominios como idioma/país. |
| Assets | `src/main/java/com/stephanofer/networkplayersettings/assets` | API pública de assets y catálogo/loader de assets de países. |
| Plataforma | `src/main/java/com/stephanofer/networkplayersettings/platform/bukkit` | Adaptadores Bukkit/Paper: listeners, PlaceholderAPI y YAML loader. |
| Configuración | `src/main/java/com/stephanofer/networkplayersettings/config` | Modelo tipado de config. |
| Addon zMenu | `networkplayersettings-zmenu/src/main/java/com/stephanofer/networkplayersettingszmenu/settings` | UI zMenu relacionada con settings, separada del core. |

Regla de mantenimiento: si una clase existe por una feature, debe vivir cerca de esa feature. Solo usá `platform/bukkit` para glue de Paper/Bukkit compartido.

## Cuándo conviene depender de NetworkPlayerSettings

Usalo cuando tu plugin necesita:

- renderizar mensajes, menús o placeholders según el idioma global del jugador;
- consultar la preferencia guardada (`auto`, `es`, `en`);
- saber el país efectivo del jugador (`country_override` si existe, si no `detected_country`);
- acceder a assets de países por código ISO alpha-2 o alias;
- reaccionar cuando los ajustes quedan listos o cambian.

No lo uses para:

- escribir directamente en la base de datos `nps_player_settings`;
- llamar clases internas como `settings/application`, `settings/storage`, `assets/country`, `platform/bukkit` o `config`;
- asumir que el dato del jugador está listo durante `AsyncPlayerPreLoginEvent` de tu propio plugin sin coordinar el flujo.

## Documentos de esta carpeta

- [`instalacion-integracion.md`](instalacion-integracion.md): dependencias, plugin metadata, lookup del servicio y timing de lifecycle.
- [`api-publica.md`](api-publica.md): referencia completa de clases públicas de API y eventos.
- [`estilos-nick-chat.md`](estilos-nick-chat.md): guía exhaustiva de nick styles y chat styles, su API, placeholders, comportamiento runtime, zMenu y extensión segura.
- [`flujo-ajustes-eventos.md`](flujo-ajustes-eventos.md): carga, caché, persistencia, eventos y threading observado en el código.
- [`configuracion.md`](configuracion.md): `config.yml`, `assets/countries.yml`, recursos, defaults y consecuencias operativas.
- [`assets-placeholderapi.md`](assets-placeholderapi.md): assets de países, PlaceholderAPI y consumo desde interfaces externas.
- [`banderas-pais-head-components.md`](banderas-pais-head-components.md): guía completa de la feature de banderas de país con player head components, placeholders, API Java, CraftKit y addon zMenu.
- [`ejemplos-y-buenas-practicas.md`](ejemplos-y-buenas-practicas.md): snippets correctos y do/don't para plugins consumidores.
- [`troubleshooting-source-map.md`](troubleshooting-source-map.md): errores comunes, diagnóstico y mapa de archivos fuente.

## Límites documentados explícitamente

- El repositorio publica para desarrollo local con `maven-publish`: `com.stephanofer:networkplayersettings:2.0.0` mediante `publishToMavenLocal`.
- No hay repositorio Maven remoto configurado en el proyecto. Para builds compartidos fuera de la máquina local, publicá el artefacto en un Maven privado o usá otra estrategia controlada por tu pipeline.
- La metadata del core usa `softdepend: [PlaceholderAPI]`; no declara dependencia hacia zMenu ni registra comandos de UI.
- El proyecto registra servicios Bukkit con `ServicePriority.Normal`; no hay API propia de bootstrap fuera de `ServicesManager`.
