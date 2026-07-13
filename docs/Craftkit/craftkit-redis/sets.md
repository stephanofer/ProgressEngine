# Sets distribuidos — `craftkit-redis`

`RedisSet` expone operaciones Redis Set para índices distribuidos y membership checks concurrentes sin usar strings manuales ni descubrir keys con `KEYS`.

## Acceso

```java
RedisSet sets = redis.set();
```

## Caso principal: índices explícitos

GameKit puede mantener un índice de servidores por modalidad y rol:

```java
String indexKey = redis.key("gamekit", "server-index", "bedwars", "arena");

sets.add(indexKey, "bedwars-arena-01");
sets.add(indexKey, "bedwars-arena-02");

sets.members(indexKey).thenCompose(serverIds -> {
    List<String> serverKeys = serverIds.stream()
        .map(serverId -> redis.key("gamekit", "server", serverId))
        .toList();

    return redis.cache().getMany(serverKeys);
});
```

`SADD` y `SREM` son operaciones atómicas en Redis. Varios servidores pueden actualizar el mismo índice sin pisarse entre sí.

## Operaciones disponibles

### `add(String key, String... members)`

Agrega uno o más miembros usando `SADD`.

```java
sets.add(indexKey, "bedwars-arena-01", "bedwars-arena-02");
```

Devuelve la cantidad de miembros nuevos agregados según Redis.

### `remove(String key, String... members)`

Remueve uno o más miembros usando `SREM`.

```java
sets.remove(indexKey, "bedwars-arena-01");
```

Devuelve la cantidad de miembros removidos según Redis.

### `members(String key)`

Lee todos los miembros usando `SMEMBERS`.

```java
sets.members(indexKey).thenAccept(serverIds -> {
    // serverIds es un Set no modificable.
});
```

Usar esta operación para sets acotados y controlados por el consumidor. Para colecciones enormes o sin límite claro, primero hay que diseñar una estrategia específica.

### `size(String key)`

Lee la cantidad de miembros usando `SCARD`.

```java
sets.size(indexKey);
```

### `contains(String key, String member)`

Verifica membership usando `SISMEMBER`.

```java
sets.contains(indexKey, "bedwars-arena-01");
```

### `expire(String key, Duration ttl)`

Actualiza el TTL de la key del set usando `PEXPIRE`.

```java
sets.expire(indexKey, Duration.ofMinutes(2));
```

## Validaciones

Todas las operaciones validan:

- key no `null`;
- key no vacía;
- key sin espacios/caracteres inválidos;
- key máximo 256 caracteres;
- member no `null` cuando aplica;
- `add(...)` y `remove(...)` requieren al menos un member;
- TTL positivo en `expire(...)`.

## Lo que no hace

`RedisSet` no descubre keys. El consumidor debe mantener índices explícitos.

No usar:

```text
KEYS gamekit:server-index:*
```

Tampoco usar strings separados por coma para membership concurrente:

```text
gamekit:server-index:bedwars:arena = bedwars-arena-01,bedwars-arena-02
```

Ese enfoque pierde escrituras cuando dos procesos leen y guardan al mismo tiempo. Para índices distribuidos, la primitiva correcta es Redis Set.

## Threading

Todas las operaciones son async. No bloquear el hilo principal con `.join()` o `.get()` dentro de eventos/comandos de Paper.
