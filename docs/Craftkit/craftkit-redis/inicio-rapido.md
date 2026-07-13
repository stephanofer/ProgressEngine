# Inicio rápido — `craftkit-redis`

Esta guía muestra el flujo mínimo para crear, usar y cerrar un `RedisClient` desde un plugin consumidor.

## Crear configuración

```java
RedisConfig config = RedisConfig.builder()
    .host("127.0.0.1")
    .port(6379)
    .database(0)
    .password("secret")
    .keyPrefix("hera")
    .environment("prod")
    .serverId("lobby-1")
    .build();
```

Campos recomendados por plugin:

- `host`
- `port`
- `database`
- `username` si Redis usa ACL
- `password`
- `ssl` / `verifyPeer` si aplica
- `keyPrefix`
- `environment`
- `serverId`

## Crear cliente

```java
RedisClient redis = RedisClients.lettuce(config);
```

`RedisClients.lettuce(config)` usa modo fail-fast y crea internamente:

- `DefaultClientResources` de Lettuce;
- `io.lettuce.core.RedisClient`;
- conexión principal de comandos;
- `RedisCoordinator` para leases.

La conexión Pub/Sub se crea de forma lazy en el primer `subscribe(...)` o `subscribePattern(...)`.

Si Redis es opcional, usar el modo recuperable:

```java
RedisClient redis = RedisClients.lettuce(config, RedisStartupMode.RECOVER);
```

Este modo devuelve el cliente de inmediato. Mientras Redis no esté disponible, las operaciones que requieren Redis devuelven futures fallidos y `operationalStatus()` permite reaccionar sin polling. Ver [Estado operativo y recuperación](./estado-operativo.md).

## Usar keys convencionales

```java
String key = redis.key("player", "session", playerUuid.toString());
```

Con `keyPrefix = hera` y `environment = prod`, produce:

```text
hera:prod:player:session:<uuid>
```

## Guardar caché temporal

```java
redis.cache()
    .set(key, "online", Duration.ofMinutes(5))
    .thenAccept(stored -> {
        if (!stored) {
            // Redis no respondió con OK.
        }
    });
```

`set(...)` requiere TTL. No existe `set` persistente en v1.

## Leer datos

```java
redis.cache().get(key).thenAccept(value -> {
    if (value == null) {
        // La key no existe.
        return;
    }

    // Usar valor.
});
```

La API actual devuelve `CompletableFuture<String>`. Redis puede devolver `null` cuando la key no existe.

## Mantener un índice distribuido

```java
String indexKey = redis.key("gamekit", "server-index", "bedwars", "arena");

redis.set().add(indexKey, "bedwars-arena-01");

redis.set().members(indexKey).thenCompose(serverIds -> {
    List<String> serverKeys = serverIds.stream()
        .map(serverId -> redis.key("gamekit", "server", serverId))
        .toList();

    return redis.cache().getMany(serverKeys);
});
```

`RedisSet` usa sets reales de Redis para membership concurrente. No usar strings separados por coma para índices compartidos entre servidores.

## Publicar evento

```java
String channel = redis.channel("party", "member-joined");

redis.publisher().publish(channel, "{\"player\":\"...\",\"sourceServerId\":\"lobby-1\"}");
```

## Suscribirse a eventos

```java
RedisSubscription subscription = redis.subscriber().subscribe(channel, message -> {
    String payload = message.payload();

    // Callback async: no tocar Paper API directamente aquí.
});

subscription.initialRegistration().thenRun(() -> {
    // Redis confirmó el SUBSCRIBE.
});
```

Para dejar de recibir mensajes:

```java
subscription.close();
```

## Usar un lease seguro

```java
redis.coordinator().withLease(
    redis.key("matchmaking", "processor", "solo"),
    Duration.ofSeconds(10),
    () -> processQueueAsync()
).thenAccept(result -> {
    if (result.isEmpty()) {
        // Otro servidor obtuvo el lease.
        return;
    }

    // Esta instancia ejecutó la tarea.
});
```

`withLease(...)` libera el lease al terminar la tarea, incluso si la tarea falla.

## Cerrar en shutdown

```java
redis.close();
```

El cierre es idempotente. Después de `close()`, las operaciones fallan con `RedisException` o `CompletableFuture` fallido según el caso.
