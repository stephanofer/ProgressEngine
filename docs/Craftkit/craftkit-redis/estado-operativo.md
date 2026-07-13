# Estado operativo y recuperación — `craftkit-redis`

`craftkit-redis` expone un estado operativo público para plugins que necesitan degradar y recuperar comportamiento sin inspeccionar Lettuce.

## Crear un cliente recuperable

Redis obligatorio:

```java
RedisClient redis = RedisClients.lettuce(config);
```

Redis opcional y recuperable:

```java
RedisClient redis = RedisClients.lettuce(config, RedisStartupMode.RECOVER);
```

`RECOVER` devuelve un cliente aunque Redis no esté disponible durante startup. En ese modo `autoReconnect` debe estar habilitado.

## Consultar estado actual

```java
RedisOperationalStatus status = redis.operationalStatus();

if (status.isOperational()) {
    // Redis command connection and requested subscriptions are active.
}
```

`operationalStatus()` no bloquea.

## Observar cambios

```java
RedisStatusRegistration registration = redis.observeOperationalStatus(status -> {
    if (status.isOperational()) {
        reconciliation.useInterval(Duration.ofSeconds(10));
    } else if (status.state() != RedisOperationalState.CLOSED) {
        reconciliation.useInterval(Duration.ofSeconds(2));
    }
});
```

Cerrar el registro durante shutdown:

```java
registration.close();
```

## Estados

`RedisOperationalState`:

| Estado | Significado |
| --- | --- |
| `STARTING` | Todavía no terminó el arranque inicial. |
| `OPERATIONAL` | La conexión de comandos está activa y todas las suscripciones solicitadas están confirmadas. |
| `DEGRADED` | Falta una capacidad requerida. El plugin debe usar su modo degradado. |
| `RECOVERING` | CraftKit/Lettuce está intentando recuperar conexión o suscripciones. |
| `CLOSED` | Cierre intencional y terminal. No hay recuperación posterior. |

`RedisConnectionState`:

| Estado | Significado |
| --- | --- |
| `NOT_STARTED` | La conexión todavía no se solicitó. |
| `CONNECTING` | CraftKit está abriendo la conexión. |
| `CONNECTED` | La conexión está activada y lista. |
| `DISCONNECTED` | La conexión no está disponible. |
| `RECONNECTING` | Hay una recuperación en curso. |
| `CLOSED` | La conexión fue cerrada intencionalmente. |

## Cuándo Redis es operativo

Redis se considera `OPERATIONAL` solo si:

```text
commandConnection == CONNECTED
AND
(
    requestedSubscriptions == 0
    OR (
        pubSubConnection == CONNECTED
        AND activeSubscriptions == requestedSubscriptions
    )
)
```

Esto distingue correctamente:

- cliente creado;
- conexión de comandos disponible;
- conexión Pub/Sub disponible;
- suscripciones realmente confirmadas por Redis.

## Suscripciones

`RedisSubscription` expone:

```java
CompletableFuture<Void> initialRegistration();
boolean isActive();
boolean isClosed();
```

`initialRegistration()` completa cuando Redis confirma el primer `SUBSCRIBE`/`PSUBSCRIBE`.

Si el registro inicial falla, el future completa excepcionalmente. La suscripción puede seguir existiendo como deseada para recuperación posterior mientras no se cierre.

`isActive()` depende del ACK real de Redis. Una conexión Pub/Sub activa sin ACK no vuelve operativa la integración.

Si un `SUBSCRIBE`/`PSUBSCRIBE` falla, o si la restauración no recibe todos los ACK dentro de `commandTimeout`, CraftKit reinicia la conexión Pub/Sub y conserva los topics como deseados para reintentar. El future de registro inicial sigue exponiendo su primer fallo; la recuperación posterior se observa mediante `isActive()` y `RedisOperationalStatus`.

## Threading

Los observers de estado no corren en el hilo principal de Paper y no deben tocar APIs Paper/Bukkit directamente. El consumidor debe volver al scheduler correcto.

CraftKit no expone Lettuce ni requiere Paper.

## Errores

El estado observable no convierte errores en éxitos:

- `publish(...)` sigue completando excepcionalmente si Redis falla;
- `subscribe(...).initialRegistration()` expone fallos de registro inicial;
- operaciones que requieren Redis y se solicitan sin conexión principal operativa devuelven `CompletableFuture` fallido;
- operaciones después de `close()` conservan el comportamiento de cierre documentado.
