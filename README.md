# ProgressEngine

ProgressEngine is HERA Network's centralized points economy for Paper. It provides a consistent, low-latency foundation for gameplay rewards, purchases, transfers, administration, and integrations across the entire network.

The repository is public, but the project is developed primarily for HERA's internal infrastructure and gameplay systems.

## Features

- A single global points balance per player across every HERA server.
- Atomic awards, credits, debits, transfers, balance adjustments, and resets.
- Idempotent operations that prevent duplicate effects when requests are retried.
- An append-only ledger with complete source, actor, reason, metadata, and balance traceability.
- NetworkBoosters integration for calculating boosted gameplay awards with exact rounding.
- Immutable Caffeine snapshots for synchronous, thread-safe, I/O-free balance reads.
- Cross-server cache invalidation through Redis with MySQL reconciliation during degraded operation.
- Typed asynchronous API for gameplay plugins, stores, rewards, and administrative systems.
- Player and administration commands with safe large-transfer confirmation.
- Localized feedback through chat, action bars, titles, sounds, and boss bars.
- PlaceholderAPI support and consistent player identity rendering through LuckPerms and NetworkPlayerSettings.

## Architecture

| Module | Responsibility |
| --- | --- |
| `progressengine-api` | Stable integration contract containing `PointsService`, plugin-bound clients, requests, results, receipts, snapshots, and Paper events. |
| `progressengine-paper` | Paper implementation responsible for persistence, transactions, caching, synchronization, commands, feedback, localization, and lifecycle. |

MySQL is the only durable source of truth. Every mutation is validated and committed inside a database transaction before snapshots, Redis invalidations, events, or feedback are published. Row locks and exact `long` arithmetic protect balances from concurrent spending, partial transfers, overflow, and negative values.

Caffeine accelerates local reads without authorizing economic mutations. Redis distributes revisions and non-critical transfer notifications between servers; if it becomes unavailable, MySQL operations continue and more frequent reconciliation restores cache convergence.

Consumer plugins must integrate through `progressengine-api`. They must not depend on Paper implementation classes or access ProgressEngine tables and Redis channels directly.

## Technical Specifications

| Component | Specification |
| --- | --- |
| Project version | `1.0.0` |
| Java | `25` |
| Build system | Gradle `9.6.1` with Kotlin DSL |
| Server platform | Paper `26.1` |
| Numeric model | Signed `long`, constrained to `0..maximumBalance` |
| Database | MySQL with Flyway migrations and HikariCP |
| Local cache | Caffeine with revision-aware immutable snapshots |
| Synchronization | Redis invalidation with MySQL reconciliation |
| Required plugins | NetworkPlayerSettings `2.0.0`, LuckPerms `5.5` |
| Optional plugins | NetworkBoosters, PlaceholderAPI |
| Public API artifact | `com.stephanofer:progressengine-api:1.0.0` |

ProgressEngine manages points only. Levels, rewards, stores, leaderboards, missions, ranks, multiple currencies, and gameplay-specific rules belong to their respective systems.

## Build

Use a Java 25 JDK and the included Gradle wrapper:

```bash
./gradlew clean build
```

On Windows:

```powershell
.\gradlew.bat clean build
```

The deployable plugin is generated under `target/`. API, sources, and Javadoc artifacts are generated under `target-api/`.

Run unit tests independently with `./gradlew test`. MySQL integration tests use externally managed infrastructure and run with `./gradlew integrationTest` after configuring the `PROGRESSENGINE_TEST_DB_*` environment variables. Testcontainers is intentionally not used.

## Deployment

1. Install NetworkPlayerSettings and LuckPerms on the Paper server. Install NetworkBoosters and PlaceholderAPI when their integrations are required.
2. Place the ProgressEngine JAR in the server's `plugins/` directory.
3. Start the server once to generate configuration and localization resources.
4. Configure the unique server identifier, MySQL connection, Redis connection, cache limits, and integration settings in `plugins/ProgressEngine/`.
5. Restart the server. Database migrations and infrastructure checks run automatically during startup.

The plugin fails closed when MySQL or required dependencies are unavailable. Redis failures place the runtime in degraded mode without compromising confirmed balances or transactions.

## Integration

Other plugins obtain `PointsService` from Paper's `ServicesManager` and request a `PointsClient` bound to their plugin. Binding the client records the source plugin automatically for every economic operation.

Use the API as `compileOnly`, reuse a stable `OperationId` when retrying the same request, and treat the typed asynchronous result as the only authority for delivering rewards or purchases.

```kotlin
dependencies {
    compileOnly("com.stephanofer:progressengine-api:1.0.0")
}
```

Cached reads are synchronous and never perform I/O. Loads and mutations return `CompletableFuture`; consumers must return to the appropriate Paper scheduler before using thread-confined server APIs.

## Documentation

- [Final product design](docs/producto/diseno-final-progressengine.md)
- [Implementation plan](docs/producto/plan-implementacion-progressengine.md)
