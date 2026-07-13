# Arquitectura — `craftkit-database`

`craftkit-database` expone una API pequeña y deja la implementación concreta en clases internas.

## Mapa de componentes

| Componente | Tipo | Responsabilidad |
| --- | --- | --- |
| `Databases` | Público | Entry point para crear `Database`. |
| `Database` | Público | Contrato principal: migraciones, operaciones async, transacciones, `DataSource`, tablas y cierre. |
| `DatabaseConfig` | Público | Configuración raíz de MySQL, pool, executor, migraciones y propiedades JDBC. |
| `PoolConfig` | Público | Parámetros HikariCP. |
| `ExecutorConfig` | Público | Parámetros del executor interno de DB. |
| `MigrationConfig` | Público | Parámetros de Flyway y estrategia para schemas existentes. |
| `TransactionOptions` | Público | Opciones por transacción: isolation, read-only y política de retry. |
| `TransactionIsolation` | Público | Mapeo a niveles JDBC de isolation. |
| `TransactionRetryPolicy` | Público | Política opt-in de retry transaccional: intentos, backoff, jitter, classifier y listener. |
| `SqlRetryClassifier` | Público | Clasificador de `SQLException` reintentables. Incluye classifier MySQL para deadlock/lock timeout. |
| `TransactionRetryListener`, `TransactionRetryEvent` | Público | Observabilidad de reintentos sin acoplar el módulo a logging/métricas. |
| `ExistingSchemaStrategy` | Público | Estrategia de Flyway para bases no vacías/schemas existentes. |
| `SqlQuery`, `SqlUpdate`, `SqlOperation`, `SqlTransaction` | Público | Functional interfaces usadas por el consumidor. |
| `DatabaseException` | Público | Runtime exception del módulo. |
| `HikariDatabase` | Interno | Implementación de `Database`. |
| `HikariDataSources` | Interno | Construcción de `HikariConfig` y `HikariDataSource`. |
| `DatabaseExecutors` | Interno | Creación y cierre del executor interno. |
| `FlywayMigrator` | Interno | Configuración y ejecución de Flyway. |
| `DatabaseMigrator` | Interno | Abstracción interna para migraciones. |
| `TablePrefixes` | Interno | Validación y composición de prefijos/nombres de tabla. |

## Flujo de creación

```java
Database database = Databases.mysql(config);
```

Internamente:

1. Valida `DatabaseConfig`.
2. Crea un `ExecutorService` con `DatabaseExecutors.createExecutor(config.executor())`.
3. Crea un `HikariDataSource` con `HikariDataSources.create(config)`.
4. Crea `FlywayMigrator` usando el datasource, `MigrationConfig`, `tablePrefix` y el `ClassLoader` configurado para resolver migraciones `classpath:`.
5. Devuelve `HikariDatabase`.

Si falla la creación del datasource o de la instancia, el código intenta cerrar los recursos ya creados y agrega fallos de cierre como `suppressed`.

## Variante con executor externo

```java
Database database = Databases.mysql(config, customExecutor);
```

En esta variante CraftKit crea y cierra el `HikariDataSource`, pero **no cierra** el executor externo. El consumidor es dueño de ese executor.

## Modelo async

Todas las operaciones principales se envían a un executor explícito:

- `migrate()`
- `query(...)`
- `update(...)`
- `execute(...)`
- `transaction(...)`

`HikariDatabase` usa `executor.execute(...)` y completa manualmente un `CompletableFuture`. El código actual no usa `ForkJoinPool.commonPool()` ni `CompletableFuture.supplyAsync(...)` para ejecutar JDBC.

Cuando una transacción tiene retry opt-in, cada intento vuelve a entrar al executor DB. El backoff se programa fuera del executor DB para no ocupar threads mientras espera; el JDBC del siguiente intento vuelve siempre al executor configurado.

## Retry transaccional

El retry vive en `craftkit-database` porque deadlocks y lock timeouts son una preocupación transversal de los plugins HERA con escrituras concurrentes.

Principios de diseño:

- Es opt-in por transacción mediante `TransactionOptions.retryPolicy(...)`.
- Reintenta la transacción completa, no una sentencia aislada.
- Usa una conexión nueva por intento.
- Cierra la conexión antes de aplicar backoff.
- Solo reintenta fallos clasificados durante el callback del consumidor.
- No reintenta fallos de `commit`, `rollback`, restauración de estado, cierre de conexión, setup, runtime exceptions ni errores no transitorios.
- No completa el `CompletableFuture` hasta que el callback, el `commit` y el cierre del intento exitoso hayan terminado correctamente.

## Relación con Paper

El módulo no importa ni usa Paper/Bukkit. Esto mantiene `craftkit-database` como infraestructura Java/JDBC reusable.

Consecuencia: cualquier callback que toque jugadores, mundos, inventarios, scoreboards o cualquier API Paper debe volver al main thread desde el plugin consumidor.
