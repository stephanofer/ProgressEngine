# Transacciones

Las transacciones permiten ejecutar varias operaciones SQL como una sola unidad atómica.

```text
éxito -> commit
fallo -> rollback
```

Esto es crítico para economía, compras, rewards, transferencias, inventarios persistentes y cualquier flujo donde varias escrituras deben quedar consistentes.

## API

```java
<T> CompletableFuture<T> transaction(SqlTransaction<T> transaction);

<T> CompletableFuture<T> transaction(
    TransactionOptions options,
    SqlTransaction<T> transaction
);
```

`SqlTransaction<T>` recibe una `Connection` y devuelve un resultado.

```java
@FunctionalInterface
public interface SqlTransaction<T> {
    T execute(Connection connection) throws SQLException;
}
```

## Ejemplo: transferencia de coins

```java
CompletableFuture<Void> future = database.transaction(connection -> {
    withdrawCoins(connection, fromUuid, 100);
    depositCoins(connection, toUuid, 100);
    return null;
});
```

Las dos operaciones usan la misma conexión. Si una falla, CraftKit intenta `rollback()`.

## Ejemplo con resultado

```java
CompletableFuture<Boolean> purchase = database.transaction(connection -> {
    int coins = loadCoins(connection, uuid);

    if (coins < price) {
        return false;
    }

    updateCoins(connection, uuid, coins - price);
    insertPurchase(connection, uuid, itemId);
    return true;
});
```

## Isolation y read-only

`TransactionOptions` permite configurar isolation/read-only y retry por transacción.

```java
database.transaction(
    TransactionOptions.builder()
        .isolation(TransactionIsolation.READ_COMMITTED)
        .readOnly(false)
        .retryPolicy(TransactionRetryPolicy.none())
        .build(),
    connection -> {
        // SQL transaccional
        return result;
    }
);
```

Factories disponibles:

```java
TransactionOptions.defaults();
TransactionOptions.readUncommitted();
TransactionOptions.readCommitted();
TransactionOptions.repeatableRead();
TransactionOptions.serializable();
TransactionOptions.readOnly(TransactionIsolation.READ_COMMITTED);
```

## `TransactionIsolation`

| Valor | Comportamiento |
| --- | --- |
| `DEFAULT` | No cambia el isolation de la conexión. |
| `READ_UNCOMMITTED` | Mapea a `Connection.TRANSACTION_READ_UNCOMMITTED`. |
| `READ_COMMITTED` | Mapea a `Connection.TRANSACTION_READ_COMMITTED`. |
| `REPEATABLE_READ` | Mapea a `Connection.TRANSACTION_REPEATABLE_READ`. |
| `SERIALIZABLE` | Mapea a `Connection.TRANSACTION_SERIALIZABLE`. |

Default actual:

```text
TransactionIsolation.DEFAULT
readOnly = false
retryPolicy = TransactionRetryPolicy.none()
```

## Qué hace CraftKit internamente

`HikariDatabase.transaction(...)`:

1. Agenda la transacción en el executor DB.
2. Obtiene una conexión de Hikari.
3. Guarda `autoCommit`, `readOnly` e isolation anteriores.
4. Aplica isolation si no es `DEFAULT`.
5. Aplica `readOnly` si difiere del estado actual.
6. Ejecuta `connection.setAutoCommit(false)`.
7. Ejecuta el callback del consumidor.
8. Hace `commit()` si todo sale bien.
9. Hace `rollback()` si falla el callback o el commit.
10. Restaura `autoCommit`, `readOnly` e isolation.
11. Cierra la conexión, devolviéndola al pool.
12. Si hay retry opt-in y el fallo es seguro de reintentar, programa un nuevo intento con conexión nueva.

Si rollback o restauración fallan, esos errores se agregan como `suppressed` al error principal cuando corresponde.

## Retry transaccional opt-in

Los plugins con transacciones concurrentes pueden habilitar retry para fallos transitorios conocidos:

```java
TransactionOptions options = TransactionOptions.builder()
    .isolation(TransactionIsolation.READ_COMMITTED)
    .retryPolicy(TransactionRetryPolicy.mysqlTransient())
    .build();

CompletableFuture<Boolean> result = database.transaction(options, connection -> {
    // This callback may run more than once.
    return activateBooster(connection, playerId, boosterId);
});
```

`TransactionRetryPolicy.mysqlTransient()` cubre MySQL/InnoDB:

| Condición | Error code | SQLState habitual |
| --- | ---: | --- |
| Deadlock | `1213` | `40001` |
| Lock wait timeout | `1205` | `HY000` |

El classifier revisa la `SQLException` principal y la cadena `SQLException#getNextException()`.

Defaults:

```text
maxAttempts = 3
initialDelayMillis = 25 ms
maxDelayMillis = 250 ms
multiplier = 2.0
jitterFactor = 0.25
```

`maxAttempts` incluye el primer intento.

### Garantías

- Se reintenta la transacción completa.
- Cada intento obtiene una conexión nueva.
- La conexión del intento fallido se restaura y cierra antes del backoff.
- El backoff no ocupa threads del executor DB.
- El `CompletableFuture` solo se completa con éxito después de callback exitoso, `commit()` exitoso y cierre correcto del intento.
- Si se agotan los intentos, se propaga el último fallo; fallos previos pueden aparecer como `suppressed`.

### Qué no se reintenta

CraftKit no reintenta:

- fallos de `commit()`;
- fallos de `rollback()`;
- fallos restaurando `autoCommit`, `readOnly` o isolation;
- fallos cerrando la conexión;
- fallos obteniendo/configurando la conexión;
- errores runtime del consumidor;
- errores SQL no clasificados como transitorios.

La regla de `commit()` es crítica: si el cliente pierde la respuesta del commit, no siempre puede saber si MySQL aplicó o no la transacción. Reintentar en ese estado puede duplicar una operación económica.

### Observabilidad

```java
TransactionRetryPolicy policy = TransactionRetryPolicy.builder()
    .maxAttempts(3)
    .initialDelayMillis(25)
    .maxDelayMillis(250)
    .multiplier(2.0)
    .jitterFactor(0.25)
    .classifier(SqlRetryClassifier.mysqlTransient())
    .listener(event -> logger.warn(
        "Retrying transaction after attempt {}/{} in {} ms. SQLState={}, code={}",
        event.failedAttempt(),
        event.maxAttempts(),
        event.nextDelayMillis(),
        event.failure().getSQLState(),
        event.failure().getErrorCode()
    ))
    .build();
```

El listener es observacional. Si lanza una excepción, el retry continúa.

### Side effects

El callback puede ejecutarse más de una vez. Por eso no debe hacer side effects externos irreversibles.

Incorrecto:

```java
database.transaction(options, connection -> {
    economyApi.withdraw(player, amount);
    sendRedisMessage(player);
    updateDatabase(connection);
    return null;
});
```

Correcto:

```java
database.transaction(options, connection -> {
    updateBalance(connection, playerId, amount);
    insertOutboxEvent(connection, playerId, "booster-activated");
    return null;
});
```

Los efectos externos deben ocurrir después del éxito confirmado o modelarse con transactional outbox.

## Regla más importante

Dentro de una transacción, usar siempre la `Connection` recibida.

Correcto:

```java
database.transaction(connection -> {
    updateA(connection);
    updateB(connection);
    return null;
});
```

Incorrecto:

```java
database.transaction(connection -> {
    database.update(otherConnection -> updateA(otherConnection)); // fuera de la transacción
    return null;
});
```

`database.update(...)`, `database.query(...)` y `database.execute(...)` obtienen otra conexión del pool. Si se llaman dentro de `transaction`, quedan fuera de la transacción actual.

## Qué no incluye todavía

La implementación actual no agrega helpers de:

- nested transactions;
- savepoints;
- timeout propio de transacción;
- transaction manager;
- ORM o query builder.

Si se necesitan savepoints, el consumidor puede usar JDBC directo dentro de la misma conexión recibida.
