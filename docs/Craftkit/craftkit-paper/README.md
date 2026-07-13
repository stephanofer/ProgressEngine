# craftkit-paper

`craftkit-paper` contiene utilidades Paper/Adventure reutilizables por plugins consumidores.

## Player Head MiniMessage Tag

`CraftKitMiniMessageTags.playerHead()` registra el tag MiniMessage:

```text
<craftkit_head:texture[:hat]>
```

El tag inserta un `ObjectComponent` de player head usando `PlayerHeadObjectContents.property("textures", value)`.

## Contrato recomendado

El valor oficial para placeholders internos es el `Value` base64 de la textura:

```text
<craftkit_head:%country_head_value%>
```

Después de PlaceholderAPI debe quedar así:

```text
<craftkit_head:eyJ0ZXh0dXJlcyI6...> VIP Vendimia PRO
```

Este formato evita resolver perfiles por nombre o UUID con Mojang.

## Formatos aceptados

`texture` puede ser:

- `Value` base64 completo.
- Hash de `textures.minecraft.net`, por ejemplo `c1a6...0035b`.
- URL completa de `textures.minecraft.net/texture/<hash>`; si contiene `https://`, usala entre comillas por la sintaxis de MiniMessage.

Ejemplos:

```text
<craftkit_head:eyJ0ZXh0dXJlcyI6...>
<craftkit_head:c1a6dff7ef4f96f8be24ee808f8e9fb201155101b2567e64f80812df9660035b>
<craftkit_head:"https://textures.minecraft.net/texture/c1a6dff7ef4f96f8be24ee808f8e9fb201155101b2567e64f80812df9660035b">
```

`hat` controla si se renderiza la capa externa de la cabeza. Default: `true`.

```text
<craftkit_head:%country_head_value%:false>
```

## Uso en consumidores

Dependencia:

```kotlin
dependencies {
    compileOnly("com.hera.craftkit:craftkit-paper:1.1.0")
}
```

Registro en MiniMessage:

```java
MiniMessage miniMessage = MiniMessage.builder()
    .editTags(tags -> tags.resolver(CraftKitMiniMessageTags.playerHead()))
    .build();
```

Si el consumidor usa PlaceholderAPI, el orden correcto es:

1. Resolver placeholders en el string.
2. Parsear el resultado con MiniMessage y el resolver de CraftKit.

## Límites

- La renderización visual de object/player-head components requiere clientes/servidores compatibles con Minecraft `1.21.9+`.
- El módulo no hace HTTP, I/O ni consultas a Mojang.
- La primera versión no usa cache interno. Los consumidores pueden cachear el componente final según su propio pipeline.
