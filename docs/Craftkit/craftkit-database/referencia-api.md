# Referencia de API pública

Esta referencia lista la API pública actual del paquete `com.hera.craftkit.database`.

## `Databases`

```java
public static Database mysql(DatabaseConfig config);
public static Database mysql(DatabaseConfig config, Executor executor);
```

- `mysql(config)`: crea datasource y executor interno. CraftKit cierra ambos.
- `mysql(config, executor)`: crea datasource y usa executor externo. CraftKit no cierra el executor externo.

## `Database`

```java
CompletableFuture<Void> migrate();
<T> CompletableFuture<T> query(SqlQuery<T> query);
CompletableFuture<Integer> update(SqlUpdate update);
CompletableFuture<Void> execute(SqlOperation operation);
<T> CompletableFuture<T> transaction(SqlTransaction<T> transaction);
<T> CompletableFuture<T> transaction(TransactionOptions options, SqlTransaction<T> transaction);
DataSource dataSource();
String tablePrefix();
String table(String name);
boolean isClosed();
void close();
```

## Functional interfaces

```java
public interface SqlQuery<T> {
    T execute(Connection connection) throws SQLException;
}
```

```java
public interface SqlUpdate {
    int execute(Connection connection) throws SQLException;
}
```

```java
public interface SqlOperation {
    void execute(Connection connection) throws SQLException;
}
```

```java
public interface SqlTransaction<T> {
    T execute(Connection connection) throws SQLException;
}
```

## `DatabaseConfig`

Builder methods:

```java
host(String)
port(int)
database(String)
username(String)
password(String)
tablePrefix(String)
pool(PoolConfig)
executor(ExecutorConfig)
executor(ExecutorConfig.Builder)
migration(MigrationConfig)
jdbcProperties(Map<String, String>)
putJdbcProperty(String, String)
build()
```

Getters:

```java
host(); port(); database(); username(); password(); tablePrefix();
pool(); executor(); migration(); jdbcProperties();
```

## `PoolConfig`

Builder methods:

```java
poolName(String)
maximumPoolSize(int)
minimumIdle(Integer)
connectionTimeoutMillis(long)
validationTimeoutMillis(long)
idleTimeoutMillis(long)
maxLifetimeMillis(long)
autoCommit(boolean)
leakDetectionThresholdMillis(long)
build()
```

Constantes:

```java
DEFAULT_POOL_NAME = "craftkit-mysql"
DEFAULT_MAXIMUM_POOL_SIZE = 10
DEFAULT_CONNECTION_TIMEOUT_MILLIS = 10_000L
DEFAULT_VALIDATION_TIMEOUT_MILLIS = 5_000L
DEFAULT_IDLE_TIMEOUT_MILLIS = 600_000L
DEFAULT_MAX_LIFETIME_MILLIS = 1_800_000L
DEFAULT_LEAK_DETECTION_THRESHOLD_MILLIS = 0L
```

## `ExecutorConfig`

Builder methods:

```java
threadCount(int)
threadNamePrefix(String)
daemon(boolean)
shutdownTimeoutMillis(long)
build()
build(int defaultThreadCount)
```

Constantes:

```java
DEFAULT_THREAD_NAME_PREFIX = "craftkit-database"
DEFAULT_DAEMON = true
DEFAULT_SHUTDOWN_TIMEOUT_MILLIS = 10_000L
```

## `MigrationConfig`

Factories:

```java
builder()
sharedDatabaseDefaults()
```

Builder methods:

```java
enabled(boolean)
clearLocations()
locations(List<String>)
addLocation(String)
baselineOnMigrate(boolean)
validateOnMigrate(boolean)
cleanDisabled(boolean)
existingSchemaStrategy(ExistingSchemaStrategy)
baselineVersion(String)
baselineDescription(String)
placeholders(Map<String, String>)
putPlaceholder(String, String)
classLoader(ClassLoader)
build()
```

Constante:

```java
DEFAULT_LOCATION = "classpath:db/migration"
```

## `ExistingSchemaStrategy`

```java
FAIL
BASELINE_AT_ZERO
BASELINE_AT_VERSION
```

## `TransactionOptions`

Factories:

```java
defaults()
readUncommitted()
readCommitted()
repeatableRead()
serializable()
readOnly(TransactionIsolation)
builder()
```

Builder methods:

```java
isolation(TransactionIsolation)
readOnly(boolean)
retryPolicy(TransactionRetryPolicy)
build()
```

Getters:

```java
isolation()
readOnly()
retryPolicy()
```

## `TransactionIsolation`

```java
DEFAULT
READ_UNCOMMITTED
READ_COMMITTED
REPEATABLE_READ
SERIALIZABLE
```

Métodos:

```java
jdbcLevel()
shouldApply()
```

`DEFAULT` tiene `jdbcLevel() == null` y `shouldApply() == false`.

## `TransactionRetryPolicy`

Factories:

```java
none()
mysqlTransient()
builder()
```

Builder methods:

```java
maxAttempts(int)
initialDelayMillis(long)
maxDelayMillis(long)
multiplier(double)
jitterFactor(double)
classifier(SqlRetryClassifier)
listener(TransactionRetryListener)
build()
```

Getters:

```java
maxAttempts()
initialDelayMillis()
maxDelayMillis()
multiplier()
jitterFactor()
classifier()
listener()
nextDelayMillis(int failedAttempt)
```

`maxAttempts` incluye el primer intento.

`none()` es el default de `TransactionOptions` y no reintenta.

`mysqlTransient()` usa:

```text
maxAttempts = 3
initialDelayMillis = 25
maxDelayMillis = 250
multiplier = 2.0
jitterFactor = 0.25
classifier = SqlRetryClassifier.mysqlTransient()
listener = TransactionRetryListener.noop()
```

## `SqlRetryClassifier`

```java
boolean isRetryable(SQLException exception)
```

Factories:

```java
never()
mysqlTransient()
```

`mysqlTransient()` cubre:

| Condición | Error code | SQLState habitual |
| --- | ---: | --- |
| Deadlock | `1213` | `40001` |
| Lock wait timeout | `1205` | `HY000` |

También revisa la cadena `SQLException#getNextException()`.

## `TransactionRetryListener`

```java
void onRetry(TransactionRetryEvent event)
```

Factory:

```java
noop()
```

El listener es observacional. Si falla, el retry continúa y el fallo del listener se agrega como `suppressed` al fallo del intento.

## `TransactionRetryEvent`

```java
int failedAttempt()
int maxAttempts()
long nextDelayMillis()
SQLException failure()
```

## `DatabaseException`

```java
DatabaseException(String message)
DatabaseException(String message, Throwable cause)
```
