# Referencia de API pública — `craftkit-redis`

Referencia de las interfaces públicas actuales del módulo.

## `RedisClients`

```java
public final class RedisClients {
    public static RedisClient lettuce(RedisConfig config);
    public static RedisClient lettuce(RedisConfig config, RedisStartupMode startupMode);
}
```

Factory pública para crear `RedisClient` con implementación Lettuce.

`RedisStartupMode.FAIL_FAST` es el comportamiento default. `RedisStartupMode.RECOVER` permite iniciar degradado y recuperarse sin reiniciar el consumidor.

`RECOVER` requiere que `RedisConfig.autoReconnect()` sea `true`.

## `RedisClient`

```java
public interface RedisClient extends AutoCloseable {
    RedisOperationalStatus operationalStatus();
    RedisStatusRegistration observeOperationalStatus(RedisOperationalStatusListener listener);
    CompletableFuture<Boolean> ping();
    RedisCache cache();
    RedisState state();
    RedisSet set();
    RedisPublisher publisher();
    RedisSubscriber subscriber();
    RedisCoordinator coordinator();
    String key(String domain, String... parts);
    String channel(String domain, String event);
    boolean isClosed();
    void close();
}
```

### `ping()`

Ejecuta `PING`. Devuelve `true` si Redis responde `PONG`.

### `operationalStatus()`

Devuelve el snapshot operativo actual sin bloquear.

### `observeOperationalStatus(...)`

Registra un observer y devuelve un `RedisStatusRegistration` idempotente para desregistrarlo. Recibe primero el snapshot actual y luego cambios en secuencia monotónica. Los callbacks se serializan fuera de callbacks directos de Lettuce y no tienen afinidad con Paper.

## Estado operativo

```java
public enum RedisStartupMode {
    FAIL_FAST,
    RECOVER
}

public enum RedisOperationalState {
    STARTING,
    OPERATIONAL,
    DEGRADED,
    RECOVERING,
    CLOSED
}

public enum RedisConnectionState {
    NOT_STARTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
    CLOSED
}
```

```java
public record RedisOperationalStatus(
    long sequence,
    RedisOperationalState state,
    RedisConnectionState commandConnection,
    RedisConnectionState pubSubConnection,
    int requestedSubscriptions,
    int activeSubscriptions,
    RedisException lastFailure
) {
    boolean isOperational();
}
```

`isOperational()` solo es `true` cuando la conexión de comandos está activa y, si existen topics solicitados, Pub/Sub está activo con todos sus ACK confirmados.

`lastFailure()` contiene el último fallo observable mientras el cliente no está operativo y vuelve a `null` al recuperar `OPERATIONAL`.

```java
@FunctionalInterface
public interface RedisOperationalStatusListener {
    void onStatusChanged(RedisOperationalStatus status);
}

public interface RedisStatusRegistration extends AutoCloseable {
    boolean isClosed();
    void close();
}
```

`RedisStatusRegistration.close()` es idempotente. Un callback de estado que ya comenzó puede finalizar, pero no comienza uno nuevo después de cerrarlo.

### `key(String domain, String... parts)`

Construye una key con:

```text
<keyPrefix>:<environment>:<domain>[:parts...]
```

### `channel(String domain, String event)`

Construye un channel con:

```text
<keyPrefix>:<environment>:events:<domain>:<event>
```

## `RedisConfig`

```java
RedisConfig config = RedisConfig.builder()
    .host("localhost")
    .port(6379)
    .database(0)
    .username("")
    .password("")
    .ssl(false)
    .verifyPeer(true)
    .commandTimeout(Duration.ofSeconds(3))
    .connectTimeout(Duration.ofSeconds(3))
    .shutdownTimeout(Duration.ofSeconds(10))
    .autoReconnect(true)
    .pingBeforeActivate(true)
    .requestQueueSize(10_000)
    .reconnectMinDelay(Duration.ofMillis(100))
    .reconnectMaxDelay(Duration.ofSeconds(10))
    .ioThreads(2)
    .computationThreads(2)
    .keyPrefix("hera")
    .environment("default")
    .serverId("unknown")
    .build();
```

Constantes públicas relevantes:

```java
RedisConfig.DEFAULT_PORT
RedisConfig.DEFAULT_DATABASE
RedisConfig.DEFAULT_KEY_PREFIX
RedisConfig.DEFAULT_ENVIRONMENT
RedisConfig.DEFAULT_SERVER_ID
RedisConfig.DEFAULT_COMMAND_TIMEOUT
RedisConfig.DEFAULT_CONNECT_TIMEOUT
RedisConfig.DEFAULT_SHUTDOWN_TIMEOUT
RedisConfig.DEFAULT_REQUEST_QUEUE_SIZE
RedisConfig.MAX_REQUEST_QUEUE_SIZE
RedisConfig.DEFAULT_RECONNECT_MIN_DELAY
RedisConfig.DEFAULT_RECONNECT_MAX_DELAY
RedisConfig.DEFAULT_IO_THREADS
RedisConfig.DEFAULT_COMPUTATION_THREADS
```

## `RedisCache`

```java
public interface RedisCache {
    CompletableFuture<String> get(String key);
    CompletableFuture<Map<String, String>> getMany(Collection<String> keys);
    CompletableFuture<Boolean> set(String key, String value, Duration ttl);
    CompletableFuture<Boolean> setIfAbsent(String key, String value, Duration ttl);
    CompletableFuture<Boolean> expire(String key, Duration ttl);
    CompletableFuture<Boolean> delete(String key);
    CompletableFuture<Long> unlink(String... keys);
    CompletableFuture<Duration> ttl(String key);
}
```

Notas:

- `get(...)` puede completar con `null` si Redis no tiene la key.
- `getMany(...)` usa `MGET`, deduplica keys, devuelve un `Map` no modificable y omite keys faltantes.
- `set(...)` y `setIfAbsent(...)` requieren TTL positivo.
- `ttl(...)` devuelve `Duration.ZERO` para key faltante o sin expiración.

## `RedisState`

```java
public interface RedisState {
    CompletableFuture<Long> increment(String key);
    CompletableFuture<Long> incrementBy(String key, long amount);
    CompletableFuture<Boolean> putIfAbsent(String key, String value, Duration ttl);
    CompletableFuture<String> getAndDelete(String key);
}
```

`getAndDelete(...)` usa `GETDEL` y puede completar con `null`.

## `RedisSet`

```java
public interface RedisSet {
    CompletableFuture<Long> add(String key, String... members);
    CompletableFuture<Long> remove(String key, String... members);
    CompletableFuture<Set<String>> members(String key);
    CompletableFuture<Long> size(String key);
    CompletableFuture<Boolean> contains(String key, String member);
    CompletableFuture<Boolean> expire(String key, Duration ttl);
}
```

Notas:

- `add(...)` usa `SADD` y requiere al menos un member.
- `remove(...)` usa `SREM` y requiere al menos un member.
- `members(...)` usa `SMEMBERS` y devuelve un `Set` no modificable.
- `size(...)` usa `SCARD`.
- `contains(...)` usa `SISMEMBER`.
- `expire(...)` usa `PEXPIRE` sobre la key del set y requiere TTL positivo.

## `RedisPublisher`

```java
public interface RedisPublisher {
    CompletableFuture<Long> publish(String channel, String payload);
}
```

Devuelve la cantidad de subscribers reportada por Redis.

## `RedisSubscriber`

```java
public interface RedisSubscriber {
    RedisSubscription subscribe(String channel, RedisMessageHandler handler);
    RedisSubscription subscribePattern(String pattern, RedisMessageHandler handler);
}
```

## `RedisSubscription`

```java
public interface RedisSubscription extends AutoCloseable {
    CompletableFuture<Void> initialRegistration();
    boolean isActive();
    boolean isClosed();
    void close();
}
```

`initialRegistration()` completa con el primer ACK de Redis y completa excepcionalmente si el primer registro falla. `isActive()` representa si la suscripción está actualmente confirmada; puede volver a `false` durante una reconexión.

`close()` es idempotente.

## `RedisMessage`

```java
public record RedisMessage(String channel, String pattern, String payload) {}
```

`pattern` es `null` para suscripciones exactas.

## `RedisMessageHandler`

```java
@FunctionalInterface
public interface RedisMessageHandler {
    void handle(RedisMessage message);
}
```

Las excepciones runtime del handler se capturan internamente para no romper el listener global.

## `RedisCoordinator`

```java
public interface RedisCoordinator {
    CompletableFuture<Optional<RedisLease>> tryAcquireLease(String key, Duration ttl);

    <T> CompletableFuture<Optional<T>> withLease(
        String key,
        Duration ttl,
        Supplier<CompletableFuture<T>> task
    );
}
```

## `RedisLease`

```java
public interface RedisLease {
    String key();
    String token();
    Duration ttl();
    CompletableFuture<Boolean> release();
}
```

`RedisLease` no extiende `AutoCloseable`; el release es async y explícito.

## `RedisException`

```java
public class RedisException extends RuntimeException {
    public RedisException(String message);
    public RedisException(String message, Throwable cause);
}
```

Excepción base del módulo.
