# Diseño final de ProgressEngine

> Estado: diseño formal cerrado para implementación  
> Alcance: ProgressEngine 1.0  
> Fecha: 2026-07-13  
> Fuente de verdad: este documento sustituye las propuestas y decisiones parciales anteriores

ProgressEngine será la economía global de points de HERA Network. Su única responsabilidad de producto será administrar balances, awards, créditos, débitos, transferencias y su trazabilidad, con una API segura para modalidades y tiendas. El diseño prioriza consistencia económica, baja latencia, claridad operativa y la menor complejidad necesaria.

## 1. Resumen ejecutivo

La arquitectura se rige por una decisión central:

> MySQL es la única fuente durable de verdad. Caffeine acelera lecturas locales y Redis propaga invalidaciones y avisos no críticos. Ninguna mutación se considera exitosa antes del commit de MySQL.

| Área | Decisión final |
| --- | --- |
| Recurso | Una única economía global de points |
| Modelo numérico | `long`, rango `0..maximumBalance` |
| Persistencia | MySQL mediante `craftkit-database` |
| Consistencia | Transacciones SQL y bloqueos de fila |
| Escrituras | Async y write-through; nunca write-behind |
| Lecturas rápidas | Snapshots inmutables en Caffeine |
| Sincronización | Invalidaciones Redis best-effort y reconciliación MySQL |
| Auditoría | Operaciones idempotentes y ledger append-only |
| API | Lecturas cacheadas síncronas; cargas y mutaciones asíncronas |
| Boosters | NetworkBoosters calcula awards de gameplay |
| Texto | Adventure `Component` y MiniMessage |
| Idioma | NetworkPlayerSettings |
| Identidad | LuckPerms + Nick Style + bandera de NetworkPlayerSettings |
| Comandos | cloud-paper y cloud-minecraft-extras |
| Placeholders | Solo snapshots locales; nunca I/O |
| Leaderboards | Fuera del producto |
| Pruebas de DB | MySQL real externo; Testcontainers prohibido |

### 1.1 Ruta de revisión

| Si se necesita validar... | Revisar primero |
| --- | --- |
| Alcance y límites | Secciones 2 y 3 |
| Consistencia económica | Secciones 8 a 12 |
| Caché y network | Secciones 13 a 15 |
| Developer experience | Secciones 16 a 18 y 24 |
| User experience | Secciones 19 a 23 |
| Operación y configuración | Secciones 25 a 30 |
| Riesgos y cierre | Secciones 31 a 37 |

### 1.2 Garantías principales

- Un balance confirmado nunca depende de Caffeine o Redis.
- Perder todo Redis y toda caché local no pierde balances ni transacciones.
- Dos débitos concurrentes no pueden gastar el mismo saldo.
- Una transferencia nunca queda aplicada parcialmente.
- Reintentar una solicitud con el mismo `OperationId` no duplica su efecto.
- Un balance no cargado nunca se representa engañosamente como cero.
- Ningún acceso JDBC, Redis, archivo o API potencialmente bloqueante corre en el main thread.
- Los eventos de éxito y el feedback económico ocurren después del commit.

## 2. Alcance del producto

### 2.1 Responsabilidades incluidas

ProgressEngine incluye:

- balance global de points por UUID;
- creación de cuentas con balance inicial cero;
- awards de gameplay con integración de NetworkBoosters;
- créditos directos que no aplican boosters;
- débitos atómicos para compras y consumos;
- transferencias atómicas entre jugadores;
- set y reset administrativos auditados;
- historial de operaciones y movimientos;
- caché local para lecturas frecuentes;
- sincronización rápida entre servidores;
- comandos de jugadores y administración;
- API pública para plugins consumidores;
- eventos Paper relevantes;
- PlaceholderAPI;
- feedback configurable y localizado;
- formato uniforme de identidad de jugadores;
- estado operativo y degradación segura.

### 2.2 Responsabilidades excluidas

ProgressEngine no incluye:

- niveles o experiencia;
- rewards, comandos o items entregados como premio;
- tiendas o catálogos de productos;
- leaderboards, posiciones, rankings o tiers;
- misiones, logros o temporadas;
- boosters propios;
- bancos, cuentas compartidas o múltiples monedas;
- conversión entre monedas;
- perfiles generales de jugadores;
- lógica de kills, wins o cualquier modalidad;
- entrega durable de notificaciones offline;
- event sourcing como arquitectura general;
- locks económicos distribuidos en Redis.

Las modalidades determinan cuándo corresponde otorgar points. Las tiendas determinan qué se entrega después de un débito exitoso. ProgressEngine solo administra la economía.

## 3. Principios no negociables

### 3.1 Simplicidad con garantías

El proyecto no introducirá abstracciones, módulos o infraestructura especulativa. La simplicidad no puede eliminar estas garantías:

- atomicidad;
- idempotencia;
- no negatividad;
- control de overflow;
- trazabilidad;
- threading correcto;
- degradación fail-closed para mutaciones.

### 3.2 Fuente de verdad única

MySQL contiene:

- balances definitivos;
- revisión de cada balance;
- resultados idempotentes;
- ledger;
- índice de nombres conocidos.

Caffeine y Redis son reconstruibles. No existe un flujo de recuperación que dependa de que conserven datos.

### 3.3 Confirmación verdadera

Una operación solo devuelve éxito cuando MySQL confirmó el commit. No se actualiza primero la caché, no se encola una escritura y no se comunica éxito anticipado.

### 3.4 API explícita

Las operaciones de negocio usan requests y resultados tipados. No se usan booleanos ambiguos para expresar condiciones como saldo insuficiente, replay idempotente o límite máximo.

## 4. Fundamentos verificados

El diseño se apoya en las capacidades reales revisadas de las dependencias.

### 4.1 PlayerPoints

PlayerPoints demostró el valor de:

- balances cacheados para API síncrona;
- carga anticipada de cuentas;
- offsets SQL en lugar de sobrescrituras completas;
- cooldown de pagos;
- historial de transacciones;
- invalidación entre servidores;
- autocompletado basado en nombres conocidos.

No se replica su write-behind de 10 ticks porque deja ventanas de pérdida, confirmación prematura y validación de fondos sobre estados locales potencialmente obsoletos. ProgressEngine conserva la UX rápida de lectura, pero MySQL arbitra todas las mutaciones.

### 4.2 CraftKit Database

`craftkit-database` ya proporciona:

- HikariCP;
- executor JDBC dedicado;
- operaciones asíncronas;
- transacciones con commit y rollback;
- isolation configurable;
- Flyway;
- retry opt-in de deadlocks y lock wait timeouts;
- restauración y cierre correcto de conexiones.

No se requiere modificar CraftKit para el alcance inicial.

### 4.3 CraftKit Redis

`craftkit-redis` ya proporciona:

- cliente Lettuce asíncrono;
- Pub/Sub;
- reconexión;
- `RedisStartupMode.RECOVER`;
- estado operativo observable;
- separación entre conexión de comandos y Pub/Sub.

Pub/Sub no es durable. Solo se utiliza para información que puede reconstruirse desde MySQL.

### 4.4 NetworkPlayerSettings

Se consumen exclusivamente sus servicios públicos:

- `PlayerSettingsService` para idioma y readiness;
- `PlayerStyleService` para Nick Style;
- `CountryFlagService` para bandera como `Component`;
- `PlayerSettingsReadyEvent` y `PlayerSettingChangeEvent` para lifecycle e invalidación.

ProgressEngine no duplica idioma, país, assets ni preferencias.

### 4.5 LuckPerms

- Jugadores online: `PlayerAdapter#getMetaData(player)` y datos cacheados.
- Jugadores offline: `UserManager#loadUser(UUID)` fuera del main thread.
- Invalidación de identidad: `UserDataRecalculateEvent`.
- Prefix y permisos se resuelven con las query options apropiadas.

### 4.6 Paper y Adventure

Paper expone `CommandSender` y `Player` como Adventure audiences. El feedback usa `Component`, action bar, title, sound y boss bar mediante la API nativa. El acceso a entidades después de callbacks async vuelve al scheduler correcto.

### 4.7 Cloud

`cloud-paper` y `cloud-minecraft-extras` proporcionan parsers, Brigadier, sender types, permisos, captions, help y manejo de excepciones. Los handlers usan el coordinador simple e inician trabajo asíncrono explícitamente.

## 5. Modelo numérico

### 5.1 Tipo y rango

- El tipo de balance es `long`.
- El balance mínimo es `0`.
- El máximo es `maximumBalance`, configurable.
- `maximumBalance` debe estar entre `1` y `Long.MAX_VALUE`.
- No se usa `double` ni `int` para cantidades persistidas o públicas.

### 5.2 Aritmética exacta

Antes de persistir se usa `Math.addExact`, `Math.subtractExact` o una validación equivalente sobre el valor bloqueado en MySQL. El SQL también impide superar el rango configurado.

Un overflow nunca se recorta silenciosamente. Produce un resultado de negocio `BALANCE_LIMIT_EXCEEDED` o un error de validación, según el punto donde sea detectado.

### 5.3 Boosters y redondeo

NetworkBoosters calcula con `BigDecimal`. ProgressEngine convierte el resultado final a `long` usando una política explícita.

Política inicial:

```text
FLOOR
```

La política es configurable como decisión del recurso, no por consumidor individual. No puede cambiar a mitad de una operación.

### 5.4 Cantidades válidas

- Awards, créditos, débitos y transferencias requieren cantidades positivas.
- Cero y negativos se rechazan antes de abrir una transacción.
- Penalizaciones se expresan como débitos, no como awards negativos.
- Set acepta `0..maximumBalance`.
- Reset equivale a set de cero con un tipo y una razón específicos.

## 6. Modelo de dominio

### 6.1 Cuenta

Una cuenta pertenece a un UUID y contiene:

- balance;
- revisión monotónica;
- fechas de creación y actualización.

Las cuentas nuevas se crean con balance cero. Si HERA desea points iniciales, se conceden mediante un award idempotente con una razón estable, por ejemplo `progressengine:first_join`. Esto conserva su origen en el ledger.

### 6.2 Snapshot

Un `BalanceSnapshot` es inmutable y contiene como mínimo:

```text
playerId
balance
revision
loadedAt
```

La revisión permite descartar cargas, invalidaciones y mensajes fuera de orden.

### 6.3 Operación

Una operación representa una solicitud idempotente y su resultado. Puede terminar sin mover balance, por ejemplo por fondos insuficientes.

### 6.4 Entrada de ledger

Una entrada representa un movimiento confirmado sobre una cuenta. El ledger es append-only.

### 6.5 Razón

Cada mutación requiere una razón namespaced estable:

```text
skywars:kill
bedwars:win
hera_shop:special_item
progressengine:player_transfer
progressengine:admin_add
```

La razón sirve para auditoría e integración. No es un mensaje localizado.

### 6.6 Origen y actor

Cada operación identifica:

- plugin consumidor;
- servidor de origen;
- actor: sistema, plugin, jugador o consola;
- jugador relacionado cuando corresponda;
- metadata limitada y validada.

## 7. Arquitectura del proyecto

Los dos módulos existentes son suficientes.

```text
ProgressEngine/
├── progressengine-api/
└── progressengine-paper/
```

### 7.1 API pública

```text
progressengine-api/src/main/java/com/stephanofer/progressengine/api/
├── PointsService.java
├── account/
├── operation/
├── request/
├── result/
├── source/
├── transaction/
└── event/
```

El módulo API:

- expone contratos inmutables;
- puede compilar contra Paper para eventos;
- no expone CraftKit, JDBC, Redis, Caffeine, BoostedYAML o implementaciones;
- se publica como `progressengine-api` para consumidores.

### 7.2 Runtime Paper

```text
progressengine-paper/src/main/java/com/stephanofer/progressengine/
├── ProgressEngine.java
├── ProgressEngineBootstrap.java
├── lifecycle/
├── config/
├── account/
├── transaction/
├── persistence/
├── synchronization/
├── player/
├── booster/
├── command/
├── feedback/
├── localization/
├── identity/
├── placeholder/
└── integration/
```

La organización es por capacidad. No se crearán capas ceremoniales de `usecase`, `port` y `adapter` para cada clase. Repositorios e implementaciones permanecen internos y, cuando sea posible, package-private.

### 7.3 Plataforma y empaquetado

- Java 25.
- Paper API 26.1, validada contra `26.1.2.build.74-stable`.
- Gradle multi-project.
- `progressengine-api` publicado como artefacto Java con sources y Javadocs.
- `progressengine-paper` genera el plugin sombreado.
- NetworkPlayerSettings y LuckPerms son dependencias requeridas del servidor.
- PlaceholderAPI es opcional.
- NetworkBoosters permanece opcional durante rollout.
- CraftKit Database, CraftKit Redis, BoostedYAML, Caffeine, Cloud y sus dependencias runtime se sombrean y relocalizan para evitar conflictos de classloader.

ProgressEngine no depende de zMenu y no empaqueta las APIs de plugins consumidores dentro de su API pública.

## 8. Persistencia

### 8.1 Tabla `progress_accounts`

| Columna | Propósito |
| --- | --- |
| `player_uuid` | UUID en formato binario, primary key |
| `balance` | Balance `BIGINT` no negativo |
| `revision` | Revisión monotónica |
| `created_at` | Creación de la cuenta |
| `updated_at` | Última mutación confirmada |

La tabla aplica constraints compatibles con MySQL para no negatividad y máximo representable. La validación de `maximumBalance` permanece en la transacción porque es configurable.

### 8.2 Tabla `progress_operations`

| Columna | Propósito |
| --- | --- |
| `operation_id` | Clave idempotente única |
| `request_fingerprint` | Detecta reutilización incorrecta del ID |
| `correlation_id` | Agrupa movimientos relacionados |
| `type` | Tipo de operación |
| `status` | Resultado durable |
| `actor_type` | Sistema, plugin, jugador o consola |
| `actor_uuid` | Actor cuando existe |
| `source_plugin` | Consumidor que originó la solicitud |
| `reason_key` | Razón namespaced |
| `server_id` | Servidor de origen |
| `metadata_json` | Contexto acotado |
| `created_at` | Inicio |
| `completed_at` | Resolución |

Los rechazos económicos resueltos dentro de MySQL pueden persistirse para que un retry con el mismo ID devuelva exactamente el mismo resultado. Los errores sintácticos previos y los fallos de infraestructura que causan rollback no generan una operación confirmada.

### 8.3 Tabla `progress_ledger`

| Columna | Propósito |
| --- | --- |
| `ledger_id` | Identificador único de entrada |
| `operation_id` | Operación causante |
| `player_uuid` | Cuenta afectada |
| `related_player_uuid` | Contraparte opcional |
| `delta` | Cambio firmado |
| `balance_before` | Balance bloqueado anterior |
| `balance_after` | Balance confirmado posterior |
| `revision` | Revisión resultante |
| `created_at` | Momento del movimiento |

Reglas:

- Solo contiene movimientos confirmados.
- No se actualiza ni elimina desde la API o comandos.
- Una transferencia genera dos entradas y una operación.
- Set y reset registran el delta real.

### 8.4 Tabla `progress_player_names`

| Columna | Propósito |
| --- | --- |
| `normalized_username` | Nombre actual normalizado y clave única |
| `player_uuid` | Identidad canónica y unique |
| `username` | Nombre actual con capitalización original |
| `last_seen_at` | Última actualización |

Esta tabla mantiene una relación actual uno-a-uno entre username normalizado y UUID. En cada join se eliminan dentro de una misma transacción el mapping anterior del UUID y cualquier mapping antiguo del username reutilizado, y luego se inserta el mapping actual. Así se cubren renames y reutilización legítima de nombres sin resolver dos UUIDs para el mismo target.

La tabla sirve para targets offline, historial y suggestions. No pretende conservar historial de nombres y un nombre nunca reemplaza al UUID como identidad económica. Si no existe mapping para una cuenta administrativa creada directamente por UUID, la UI muestra el UUID hasta conocer un username válido.

### 8.5 Índices

Como mínimo:

- unique de `progress_operations.operation_id`;
- índice de ledger por `(player_uuid, created_at)`;
- índice de ledger por `operation_id`;
- unique de `progress_player_names.normalized_username`;
- unique de `progress_player_names.player_uuid`.

Los índices finales se validan con planes de ejecución sobre MySQL real antes del release.

### 8.6 Migraciones

- Flyway mediante `craftkit-database`.
- Scripts en `src/main/resources/db/migration/`.
- Migraciones ejecutadas antes de registrar API y comandos.
- Un fallo de migración impide habilitar el plugin.
- No se editan migraciones ya publicadas; se agrega una nueva versión.

## 9. Idempotencia

### 9.1 `OperationId`

Cada mutación usa un identificador estable. Las factories pueden generar uno, pero un consumidor que necesite retry debe conservar el mismo request y el mismo ID.

### 9.2 Fingerprint

El fingerprint cubre los campos económicos relevantes:

- tipo;
- cuenta o cuentas;
- cantidad;
- razón;
- plugin origen;
- campos que cambien el efecto económico.

Metadata puramente diagnóstica solo forma parte si su cambio altera el significado de la operación.

### 9.3 Resolución transaccional

La idempotencia durable se resuelve dentro de la transacción:

1. Intentar reservar `operation_id` con su fingerprint.
2. Si ya existe, esperar la resolución impuesta por la unique key.
3. Leer el resultado durable.
4. Comparar fingerprint.
5. Devolver replay o `IDEMPOTENCY_CONFLICT`.

Nunca se hace un check idempotente solo en Caffeine o antes de abrir la transacción.

### 9.4 Resultados rechazados

Un request resuelto como `INSUFFICIENT_FUNDS` queda asociado a su `OperationId`. Reutilizar ese ID después de recibir fondos devuelve el rechazo original. Una nueva intención requiere un nuevo ID.

### 9.5 Commit ambiguo

CraftKit no reintenta fallos ambiguos de commit. El consumidor reenvía el mismo request. Si MySQL confirmó la primera ejecución, se devuelve el recibo existente; si no la confirmó, la operación puede ejecutarse una vez.

## 10. Flujo general de mutación

Toda mutación sigue esta secuencia normativa:

1. Validar request, UUIDs, cantidad, razón y metadata.
2. Abrir transacción MySQL en el executor de CraftKit.
3. Reservar o consultar idempotencia.
4. Crear las cuentas ausentes con balance cero.
5. Bloquear las filas requeridas con `SELECT ... FOR UPDATE`.
6. Revalidar fondos, máximo y reglas con el estado bloqueado.
7. Calcular balances con aritmética exacta.
8. Actualizar balance y revisión.
9. Insertar las entradas de ledger.
10. Persistir el resultado de la operación.
11. Hacer commit.
12. Publicar el snapshot local solo si es más nuevo.
13. Publicar invalidación Redis best-effort.
14. Emitir eventos Paper post-commit.
15. Completar el `CompletableFuture`.

Redis y el feedback no forman parte de la atomicidad económica.

## 11. Flujos económicos

### 11.1 Award

`award` representa points obtenidos por gameplay.

```text
validar base positiva
  -> evento prepare
  -> asegurar snapshot de NetworkBoosters
  -> calcular BoostCalculation
  -> aplicar FLOOR
  -> validar long y máximo
  -> crédito transaccional
  -> resultado con desglose
```

El resultado incluye:

- cantidad base;
- multiplicador;
- cantidad final;
- boosters aplicados;
- indicador de cap;
- recibo económico.

### 11.2 Crédito directo

`credit` aumenta el balance sin boosters. Se usa para administración, migraciones controladas, refunds y otras operaciones que no sean rewards de gameplay.

No se permite usar un crédito genérico para ocultar un award. La razón y el tipo permanecen auditables.

### 11.3 Débito

El débito bloquea la cuenta y solo actualiza si el saldo bloqueado cubre la cantidad. No existe un flujo público `has` seguido de `take` como garantía económica.

Un consumidor puede mostrar el balance cacheado, pero debe usar el resultado de `debit` para decidir si entrega una compra.

### 11.4 Transferencia

Una transferencia:

- rechaza auto-transferencias;
- acepta un target offline conocido;
- permite UUID explícito en administración;
- ordena ambos UUIDs canónicamente;
- crea las cuentas ausentes con cero en ese orden;
- bloquea ambas cuentas en el mismo orden;
- valida fondos del emisor;
- valida capacidad máxima del receptor;
- debita y acredita en una transacción;
- genera una operación y dos entradas de ledger;
- usa retry transaccional para deadlock y lock wait timeout;
- nunca compensa manualmente un débito confirmado.

Los `SELECT ... FOR UPDATE` se ejecutan respetando el orden canónico; no se depende del orden de resultados de un `IN (...)`.

### 11.5 Set y reset

Son operaciones administrativas:

- requieren permiso específico;
- requieren una razón estable, con default explícito del comando;
- bloquean la cuenta;
- registran el delta;
- incrementan revisión;
- producen ledger y evento como cualquier mutación.

Reset no elimina la cuenta ni su historial.

## 12. Concurrencia y transacciones

### 12.1 Autoridad de fila

El balance usado para decidir una mutación es el leído bajo lock en MySQL. El snapshot local nunca autoriza por sí solo una compra o transferencia.

Las mutaciones usan `TransactionIsolation.READ_COMMITTED` con locks explícitos y `TransactionRetryPolicy.mysqlTransient()`. La consistencia no depende del isolation default del servidor MySQL.

### 12.2 Orden local

Las operaciones originadas en el mismo servidor pueden serializarse por UUID para reducir contención y conservar un orden intuitivo. Esta serialización es una optimización; MySQL sigue siendo la protección entre servidores.

La estructura de serialización debe limpiar cadenas completadas para no retener UUIDs indefinidamente.

### 12.3 Retry

Se usa `TransactionRetryPolicy.mysqlTransient()` en operaciones con locks concurrentes.

Puede reintentar:

- deadlock MySQL `1213`;
- lock wait timeout `1205`.

No reintenta automáticamente:

- commit ambiguo;
- rollback fallido;
- error configurando o cerrando la conexión;
- errores SQL no clasificados;
- excepciones runtime del consumidor.

El callback transaccional no ejecuta efectos externos. Puede repetirse.

### 12.4 Side effects

Dentro de la transacción solo se realizan cambios SQL de ProgressEngine. No se envían mensajes, eventos, Redis ni rewards. Todo efecto externo ocurre después del commit.

## 13. Caché local con Caffeine

### 13.1 Responsabilidad

Caffeine contiene snapshots para:

- API síncrona de lectura;
- comandos y menús;
- PlaceholderAPI;
- feedback;
- integraciones que solo necesitan mostrar estado.

### 13.2 Configuración

- `maximumSize` configurable y validado contra la concurrencia esperada.
- Expiración por acceso para cuentas no activas.
- Estadísticas opcionales para diagnóstico.
- Cargas simultáneas del mismo UUID coalescidas.
- Invalidación explícita durante lifecycle.

Los jugadores online se mantienen activos mediante carga, mutaciones y reconciliación. El sizing debe permitir contener, como mínimo, el pico de jugadores online del servidor con margen.

### 13.3 Publicación por revisión

Una carga solo reemplaza el snapshot actual si su revisión es mayor. Esto evita que una consulta vieja que termina tarde sobrescriba una mutación nueva.

No se usa `refreshAfterWrite` automático para balances. Los refreshes son explícitos y revision-aware.

### 13.4 Readiness

- Ausencia de snapshot no significa balance cero.
- `isReady(UUID)` diferencia el estado.
- `cached(UUID)` devuelve `Optional.empty()` si no existe snapshot.
- No se ofrece `getCachedOrZero`.

## 14. Redis y sincronización de network

### 14.1 Responsabilidades permitidas

Redis se usa para:

- invalidaciones de balances;
- avisos no críticos de transferencias recibidas;
- propagación rápida de revisión;
- estado operativo de esta integración.

### 14.2 Responsabilidades prohibidas

Redis no se usa para:

- guardar balances definitivos;
- ejecutar créditos o débitos;
- garantizar rewards;
- sustituir el ledger;
- leaderboards;
- sincronizar perfiles de NetworkPlayerSettings o LuckPerms;
- locks económicos distribuidos.

### 14.3 Invalidación

Payload mínimo:

```text
playerId
revision
transactionId
sourceServerId
```

Al recibirlo:

1. Validar payload y versión.
2. Ignorar mensajes propios cuando corresponda.
3. Ignorar revisiones iguales o anteriores.
4. Si la cuenta está cacheada, refrescar desde MySQL.
5. Publicar solo si el snapshot leído sigue siendo más nuevo.
6. Volver al scheduler Paper antes de emitir eventos o feedback.

### 14.4 Aviso de transferencia

El aviso puede incluir:

```text
transactionId
senderId
receiverId
amount
receiverRevision
sourceServerId
```

Se deduplica por `transactionId`. Si el receptor está online en otro servidor, recibe feedback. Si está completamente offline, Pub/Sub no garantiza entrega y no se crea una bandeja durable en la versión inicial.

El éxito del emisor nunca depende de este aviso.

### 14.5 Modo degradado

Redis inicia con `RedisStartupMode.RECOVER`.

Cuando Redis no está operativo:

- MySQL sigue procesando mutaciones;
- los débitos permanecen protegidos;
- el runtime reporta `DEGRADED_REDIS`;
- las cachés remotas pueden quedar temporalmente desactualizadas;
- se reduce el intervalo de reconciliación para cuentas online;
- un cambio de servidor fuerza carga desde MySQL.

Cuando Redis se recupera:

- se espera el ACK real de las suscripciones;
- se fuerzan refreshes relevantes de cuentas online;
- se restaura el intervalo normal de reconciliación.

### 14.6 Reconciliación

La reconciliación consulta en lotes las revisiones de jugadores online. Solo carga balances completos para revisiones que cambiaron.

Los intervalos son configurables dentro de rangos seguros. Redis operativo usa un intervalo relajado; Redis degradado usa uno más frecuente.

## 15. Lifecycle del jugador

### 15.1 Precarga

1. `AsyncPlayerPreLoginEvent` inicia la carga asíncrona sin bloquear.
2. Cargas simultáneas comparten un future.
3. Si el login es rechazado, el estado temporal puede descartarse.
4. `PlayerSettingsReadyEvent` confirma que idioma y ajustes están disponibles.
5. Se espera o reutiliza la carga de balance.
6. Si NetworkBoosters está disponible, se garantiza su snapshot antes de awards.
7. Se publica el snapshot.
8. Se marca al jugador ready.
9. Se dispara `PlayerPointsReadyEvent`.

### 15.2 Antes de ready

- Las lecturas cacheadas devuelven ausencia.
- Los placeholders muestran un fallback de carga, no cero.
- Comandos económicos informan que el estado está cargando.
- Integraciones de compra fallan cerradas.
- Awards asíncronos pueden esperar la carga requerida en vez de calcular sin boosters.

### 15.3 Quit y cambio de servidor

- Se incrementa un epoch de lifecycle.
- Una carga que finalice con un epoch anterior no se publica como ready.
- Operaciones ya enviadas a MySQL pueden completar; su resultado durable no se cancela por logout.
- No se intenta enviar feedback a una entidad inválida.
- El siguiente servidor fuerza una carga autoritativa durante conexión.

## 16. API pública

### 16.1 Servicio principal

El contrato conceptual es:

```java
public interface PointsService {

    Optional<BalanceSnapshot> cached(UUID playerId);

    CompletableFuture<BalanceSnapshot> load(UUID playerId);

    CompletableFuture<BalanceSnapshot> refresh(UUID playerId);

    boolean isReady(UUID playerId);

    CompletableFuture<AwardResult> award(AwardRequest request);

    CompletableFuture<CreditResult> credit(CreditRequest request);

    CompletableFuture<DebitResult> debit(DebitRequest request);

    CompletableFuture<TransferResult> transfer(TransferRequest request);

    CompletableFuture<SetBalanceResult> setBalance(SetBalanceRequest request);
}
```

Los nombres exactos podrán ajustarse durante la implementación si mejora la coherencia Java, sin cambiar los contratos establecidos aquí.

### 16.2 Threading

- `cached` e `isReady` son síncronos, thread-safe y sin I/O.
- Cargas y mutaciones son asíncronas.
- No se usa `.join()` o `.get()` desde gameplay, comandos o eventos Paper.
- Los callbacks que toquen Paper vuelven al scheduler correcto.
- Las colecciones y modelos públicos son inmutables.

### 16.3 Resultados de negocio

Estados mínimos según operación:

```text
SUCCESS
REPLAYED
INSUFFICIENT_FUNDS
BALANCE_LIMIT_EXCEEDED
CANCELLED
SELF_TRANSFER_REJECTED
UNKNOWN_TARGET
NOT_READY
IDEMPOTENCY_CONFLICT
```

Las excepciones se reservan para:

- MySQL no disponible;
- runtime cerrado;
- configuración o infraestructura inválida;
- fallos inesperados.

### 16.4 Recibo

Un recibo exitoso incluye como mínimo:

```text
operationId
transactionId o correlationId
playerId
type
delta
balanceBefore
balanceAfter
revision
reason
createdAt
```

Una transferencia incluye ambos estados resultantes.

### 16.5 Metadata

La metadata pública tendrá límites de:

- cantidad de entradas;
- longitud de key;
- longitud de value;
- tamaño serializado total;
- caracteres permitidos en keys.

No se aceptan objetos arbitrarios ni datos sensibles.

### 16.6 Registro

`PointsService` se registra en Bukkit `ServicesManager` después de migraciones e inicialización. Los consumidores declaran dependencia fuerte cuando no pueden operar sin la economía.

## 17. Integración con NetworkBoosters

### 17.1 Dependencia

La integración se obtiene mediante el `NetworkBoostersService` público. Durante rollout puede ser opcional; cuando NetworkBoosters esté desplegado en toda la network podrá convertirse en dependencia requerida.

### 17.2 Target

Se conserva el target ya implementado y probado:

```text
network_progression:points
```

Es un identificador técnico estable, no un nombre visible. No se renombra únicamente porque el plugin consumidor se llame ProgressEngine.

### 17.3 Operaciones que aplican boosters

| Operación | Aplica boosters |
| --- | --- |
| `award` | Sí |
| `credit` | No |
| `debit` | No |
| `transfer` | No |
| `set` | No |
| `reset` | No |
| `refund` | No |

### 17.4 Snapshot no listo

Si NetworkBoosters está instalado pero el jugador no está ready, ProgressEngine carga o espera su snapshot antes de calcular. No concede silenciosamente el award base por una carrera de lifecycle.

Si la carga requerida falla, el award falla y el consumidor recibe un future excepcional. No se persiste una recompensa incorrecta.

### 17.5 Separación de responsabilidades

NetworkBoosters decide:

- scopes;
- stacking;
- expiración;
- multiplicador;
- cap;
- boosters aplicados.

ProgressEngine decide:

- redondeo a points;
- validación del `long`;
- persistencia;
- idempotencia;
- ledger;
- feedback económico.

## 18. Eventos Paper

### 18.1 Eventos iniciales

- `PlayerPointsReadyEvent`;
- `PointsAwardPrepareEvent`;
- `PointsTransactionCommittedEvent`;
- `PointsBalanceChangedEvent`.

### 18.2 Prepare de award

`PointsAwardPrepareEvent`:

- ocurre antes del cálculo de boosters;
- es cancelable;
- puede modificar la cantidad base;
- se valida nuevamente después de listeners;
- no representa una transacción confirmada.

No se ofrece un evento modificable para débitos o transferencias. Alterar precios desde un evento económico genérico dañaría sus invariantes.

### 18.3 Eventos post-commit

- Son observacionales.
- No pueden revertir una operación.
- Se entregan después de actualizar el snapshot local.
- Se ejecutan en el scheduler Paper apropiado.
- Una excepción de un listener no revierte MySQL ni evita el cierre operativo del flujo.

### 18.4 Origen remoto

`PointsTransactionCommittedEvent` representa commits originados localmente. Un servidor remoto no inventa ese evento al recibir Pub/Sub.

Después de refrescar un balance remoto puede emitir `PointsBalanceChangedEvent` con `origin = REMOTE`, revisión y transaction ID conocidos.

### 18.5 Orden del future

El future de mutación completa después de:

1. commit;
2. publicación segura de caché;
3. dispatch del evento post-commit local.

Redis y feedback no determinan el éxito económico. El cambio al scheduler Paper puede añadir hasta un tick a la finalización observable. Si el scheduler rechaza el dispatch durante shutdown, el intento se registra y el future conserva el resultado económico confirmado; un fallo observacional no transforma un commit exitoso en un fallo de negocio.

## 19. Feedback configurable

### 19.1 Modelo

Cada outcome localizado contiene una lista ordenada de acciones.

Tipos iniciales:

- `chat`;
- `action_bar`;
- `title`;
- `sound`;
- `boss_bar`.

No se agregan partículas, toasts o diálogos hasta que exista un caso de producto concreto.

### 19.2 Ejemplo

```yaml
award-received:
  - type: action_bar
    message: "<green>+<amount> points"
  - type: sound
    sound: "minecraft:entity.experience_orb.pickup"
    volume: 0.8
    pitch: 1.2
```

### 19.3 Chat y action bar

```yaml
- type: chat
  message: "<green>Transferiste <amount> points a <target>."

- type: action_bar
  message: "<yellow>Balance: <balance> points"
```

### 19.4 Title

```yaml
- type: title
  title: "<green>Compra completada"
  subtitle: "<gray>Balance: <balance> points"
  fade-in: 10
  stay: 40
  fade-out: 10
```

### 19.5 Sound

- El sonido usa namespaced key.
- Se valida contra la API/registry disponible.
- Volume y pitch tienen rangos validados.
- Un sonido inválido rechaza el snapshot de configuración durante reload.

### 19.6 Boss bar

```yaml
- type: boss_bar
  message: "<green>+<amount> points"
  color: green
  overlay: progress
  progress: 1.0
  duration: 40
```

Las bossbars usan un canal lógico por feedback. Una nueva instancia del mismo canal reemplaza y limpia la anterior. Quit y shutdown también las limpian.

### 19.7 Seguridad MiniMessage

- Plantillas administradas se interpretan como MiniMessage.
- Nombres, razones visibles y valores externos usan resolvers `component` o `unparsed`.
- Nunca se concatena input de jugador dentro de una plantilla interpretable.
- `Component` es la salida canónica.
- Si PlaceholderAPI está habilitado para feedback, se aplica mediante su soporte de Adventure Component después de construir el componente propio; no se serializa de vuelta a un string MiniMessage interpretable.

### 19.8 Consola

Chat funciona para cualquier `Audience`. Action bar, title, sound y boss bar se omiten cuando el receptor no es un jugador.

### 19.9 Coalescing de awards

Awards confirmados en una ventana corta configurable pueden agruparse para UX:

- suma visual de points;
- un action bar;
- un único sonido;
- sin alterar operaciones, ledger o eventos.

El coalescing solo agrupa presentación. Nunca agrupa ni demora persistencia.

## 20. Localización

### 20.1 Fuente del idioma

Para jugadores se usa:

```java
PlayerSettingsService.resolvedLanguage(player)
```

ProgressEngine no mantiene otra preferencia ni caché de idioma.

### 20.2 Catálogos

```text
plugins/ProgressEngine/messages/
├── es.yml
└── en.yml
```

Cada idioma contiene las acciones completas para permitir diferencias reales de UX. Una key faltante usa fallback explícito y registra warning durante la carga.

### 20.3 Idioma no ready

No se envía feedback localizado dependiente del jugador antes de `PlayerSettingsReadyEvent`, salvo mensajes críticos de conexión con un fallback global definido.

### 20.4 Cambio de idioma

Los mensajes siguientes resuelven inmediatamente el idioma nuevo. No se cachean componentes finales por jugador de forma que congelen el idioma.

## 21. Formato de identidad

### 21.1 Regla global

Todo nombre de jugador presentado por ProgressEngine pasa por un único `PlayerIdentityRenderer`. Comandos, historial, transferencias y feedback no construyen nombres por su cuenta.

### 21.2 Formato configurable

```yaml
player-identity:
  parts:
    - prefix
    - nick
    - country_flag
  separator: " "
```

Las partes vacías se filtran antes de insertar separadores. Esto evita espacios dobles y permite reordenar o desactivar partes sin condicionales MiniMessage complejos.

### 21.3 Resolución online

- Prefix: LuckPerms cached metadata.
- Nick: `PlayerStyleService#formattedNick(Player)`.
- Bandera: `CountryFlagService#flag(UUID)`.

Los prefixes administrados por HERA se definen en MiniMessage. Se deserializan como contenido administrativo confiable y se componen con los otros `Component`; no se mezclan con input de jugadores. Un prefix inválido cae a texto plano y registra un warning limitado para no romper toda la identidad.

### 21.4 Resolución offline

- LuckPerms `loadUser(UUID)`.
- Query options apropiadas para el contexto disponible.
- `PlayerStyleService#formattedNick(NickStyleRenderRequest)`.
- `CountryFlagService#flagAsync(UUID)`.
- Resultado cacheado temporalmente en Caffeine.

La pérdida del permiso del Nick Style produce el nick plano según el contrato de NetworkPlayerSettings.

### 21.5 Invalidación

- `UserDataRecalculateEvent` invalida cambios de prefix o permisos.
- `PlayerSettingChangeEvent` invalida Nick Style, país o visibilidad de bandera.
- Quit aplica la política de expiración normal.

### 21.6 País desconocido y bandera deshabilitada

- País `XX` usa el asset fallback de NetworkPlayerSettings.
- Si el jugador deshabilitó su bandera, la parte queda vacía y no deja un separador sobrante.

## 22. Comandos

### 22.1 Stack

- `cloud-paper`;
- Brigadier;
- `ExecutionCoordinator.simpleCoordinator()`;
- sender types;
- parsers tipados;
- permissions;
- `MinecraftExceptionHandler`;
- `MinecraftHelp`;
- captions localizados;
- tooltips de suggestions cuando aporten contexto.

El coordinador simple conserva parsing y entrada en el hilo esperado. Cada handler inicia la operación async y agenda únicamente el feedback final.

### 22.2 Jugadores

```text
/points
/points balance [player]
/points pay <player> <amount>
/points pay confirm <token>
/points history [page]
/points help
```

### 22.3 Administración

```text
/points admin add <player|uuid> <amount> [reason]
/points admin remove <player|uuid> <amount> [reason]
/points admin set <player|uuid> <amount> [reason]
/points admin reset <player|uuid> [reason]
/points admin history <player|uuid> [page]
/points admin reload
/points admin status
```

### 22.4 Parsing de cantidades

- Parser `long`.
- Solo enteros.
- Rechazo de cero, negativos, decimales y overflow.
- Mensajes específicos para sintaxis y límites.

### 22.5 Targets

- Jugador online por nombre exacto.
- Jugador offline mediante perfil conocido.
- UUID explícito solo donde el comando administrativo lo permita.
- Suggestions nunca consultan MySQL en el main thread; usan un índice cacheado preparado async.

### 22.6 Pay

- Auto-transferencia rechazada.
- Mínimo y máximo configurables.
- Cooldown por emisor.
- Permiso configurable.
- Target offline conocido permitido.
- Resultado comunicado solo después del commit.

### 22.7 Confirmación de cantidades grandes

Sobre un umbral configurable, `/points pay` no ejecuta inmediatamente. Crea un token:

- aleatorio e impredecible;
- de un solo uso;
- con expiración corta;
- ligado a emisor, receptor y cantidad;
- ligado a la revisión observada del emisor.

Si cambia la revisión, la confirmación expira lógicamente y debe generarse otra. La transacción final revalida todas las reglas; el token no sustituye MySQL.

Un retry por resultado ambiguo reutiliza el `OperationId` ya confirmado por el token y no necesita autorizar una intención económica distinta.

### 22.8 Configuración de comandos

Root, aliases, permisos, límites, cooldowns y disponibilidad son configurables. Cambios de root o aliases pueden requerir reinicio por el lifecycle de registro de comandos. El reload informa claramente qué cambios no fueron aplicados en caliente.

## 23. PlaceholderAPI

### 23.1 Expansión

La expansión es interna, se registra explícitamente y usa `persist() = true`.

Placeholders iniciales:

```text
%progressengine_points%
%progressengine_points_formatted%
%progressengine_points_compact%
%progressengine_ready%
```

### 23.2 Reglas

- Solo consulta snapshots locales.
- Nunca consulta MySQL, Redis, LuckPerms o NetworkPlayerSettings de forma asíncrona desde el request.
- No contiene posiciones ni leaderboards.
- No implementa búsquedas arbitrarias `_for_<username>`.
- Si el jugador no está ready, `%progressengine_points%` devuelve vacío y `%progressengine_ready%` devuelve `false`; nunca devuelve `0` para ocultar el estado.
- Los placeholders formatted y compact pueden usar un marcador localizado de carga porque no prometen una salida numérica parseable.
- Si el contexto no tiene jugador, devuelve el fallback documentado o `null` según el placeholder.

### 23.3 Formato

- Raw: representación decimal completa.
- Formatted: separadores según el idioma efectivo ya disponible.
- Compact: notación abreviada localizada y determinista.

## 24. Integraciones de economía síncrona

### 24.1 Limitación verificada

zShop y ShopGUI+ exponen providers síncronos con retiros `void`. zShop comprueba saldo, retira y entrega el item sin poder esperar o conocer un fallo durable. Vault también está diseñado alrededor de operaciones síncronas.

No existe un adapter que sea simultáneamente:

- no bloqueante;
- durable;
- atómico;
- fiel a esos contratos.

### 24.2 Decisión

ProgressEngine 1.0 no registra providers mutables para zShop, ShopGUI+ o Vault.

Sí puede exponer lecturas cacheadas para presentación, pero ninguna fachada debe fingir que un débito async fue confirmado.

### 24.3 Integración correcta de tiendas

```text
crear DebitRequest estable
  -> PointsService.debit(request)
  -> esperar SUCCESS
  -> volver al scheduler del jugador
  -> entregar producto
```

Ante timeout, la tienda reintenta el mismo request. Ante fallo, no entrega el producto.

Si HERA adopta un fork propio de zShop, la solución correcta es agregar un contrato async con resultado al fork. ShopGUI+ no se usa para compras con points mientras mantenga su contrato actual.

## 25. Configuración

### 25.1 Archivos

```text
plugins/ProgressEngine/
├── config.yml
├── commands.yml
├── identity.yml
└── messages/
    ├── es.yml
    └── en.yml
```

No se crea un archivo de rewards porque ProgressEngine no define rewards.

### 25.2 BoostedYAML

Se usa para:

- copiar defaults;
- preservar comentarios;
- versionar documentos;
- auto-update;
- migrar o relocalizar keys;
- reload y save.

### 25.3 Snapshot inmutable

El runtime consume un snapshot validado. Un reload:

1. carga todos los documentos candidatos;
2. valida estructura y referencias;
3. valida MiniMessage y acciones de feedback;
4. construye el snapshot completo;
5. calcula cambios;
6. publica atómicamente si no hay errores.

Un reload parcialmente inválido conserva toda la configuración anterior. No mezcla archivos nuevos y viejos.

### 25.4 Qué es configurable

- conexión, pool y timeouts de MySQL;
- Redis y prefijos de channels;
- máximo de balance;
- redondeo permitido;
- sizing y expiración de caché;
- intervalos de reconciliación;
- server ID;
- comandos y permisos;
- pay mínimo, máximo, cooldown y confirmación;
- formato de identidad;
- textos y acciones de feedback;
- formatos numéricos;
- toggles de integraciones opcionales.

### 25.5 Qué no es configurable

- MySQL como autoridad;
- atomicidad de transferencias;
- no negatividad;
- resolución transaccional de idempotencia;
- ledger post-commit;
- prohibición de I/O en main thread;
- actualización de caché después del commit;
- ausencia de leaderboards.

## 26. Lifecycle del plugin

### 26.1 Startup

1. Cargar y validar configuración.
2. Crear `Database` de CraftKit.
3. Ejecutar migraciones Flyway.
4. Crear Redis en modo recuperable.
5. Obtener servicios requeridos de NetworkPlayerSettings y LuckPerms.
6. Obtener NetworkBoosters si la integración está disponible.
7. Construir repositorios, cachés y servicios.
8. Registrar `PointsService`.
9. Registrar listeners, comandos y PlaceholderAPI.
10. Iniciar reconciliación.
11. Marcar runtime `READY` o `DEGRADED_REDIS`.

Un fallo de configuración, MySQL o migraciones impide habilitar el runtime. Redis no impide startup porque opera en `RECOVER`.

### 26.2 Shutdown

1. Cambiar a `SHUTTING_DOWN`.
2. Dejar de aceptar nuevas mutaciones.
3. Cancelar precargas y reconciliación.
4. Esperar operaciones en vuelo con timeout acotado.
5. Desregistrar expansión y servicios.
6. Limpiar feedback temporal.
7. Cerrar suscripciones y Redis.
8. Cerrar MySQL.
9. Limpiar cachés y executors propios.

No existe una cola write-behind que deba vaciarse.

### 26.3 Disable durante una mutación

Una operación ya enviada a CraftKit puede terminar dentro del periodo de cierre. Si el timeout expira, el caller puede resolver el resultado posteriormente con el mismo `OperationId`. No se comunica éxito sin conocer el commit.

## 27. Estado operativo y fallos

Estados públicos mínimos:

```text
STARTING
READY
DEGRADED_REDIS
UNAVAILABLE_DATABASE
SHUTTING_DOWN
CLOSED
```

### 27.1 MySQL no disponible

- Las nuevas mutaciones fallan cerradas.
- No se modifica la caché.
- No se emiten eventos de éxito.
- No se entrega una compra como confirmada.
- Los snapshots existentes pueden mostrarse como última lectura conocida.
- Jugadores no cargados permanecen `NOT_READY`.
- Nunca se sustituye el fallo por balance cero.

### 27.2 Redis no disponible

- Las mutaciones siguen correctas.
- El estado pasa a degradado.
- Se usa reconciliación más frecuente.
- Se registran transiciones sin spam repetitivo.

### 27.3 Dependencia requerida ausente

Si NetworkPlayerSettings o LuckPerms no registra su servicio requerido, ProgressEngine no habilita su runtime público. No accede a implementaciones internas ni crea fallbacks inconsistentes.

## 28. Observabilidad y auditoría

### 28.1 Logs

Los errores de mutación incluyen:

- `operationId`;
- tipo;
- jugador o jugadores;
- source plugin;
- server ID;
- SQLState y error code cuando corresponda.

No se registran passwords, metadata sensible ni payloads completos sin sanitizar.

### 28.2 Status administrativo

`/points admin status` muestra:

- estado del runtime;
- conectividad operativa de Redis;
- estado de suscripción;
- salud de una consulta MySQL controlada;
- tamaño y estadísticas de caché;
- cargas y mutaciones en vuelo;
- intervalo de reconciliación;
- última reconciliación exitosa;
- versión de schema.

### 28.3 Historial

El historial pagina por cuenta y fecha. No carga el ledger completo en memoria. Los nombres de actores se renderizan mediante el servicio de identidad y la consulta permanece asíncrona.

## 29. Seguridad y validación

- Prepared statements para todos los valores.
- Nombres de tabla únicamente desde validación de CraftKit/Flyway.
- Razones namespaced con longitud máxima.
- Metadata acotada.
- UUIDs canónicos.
- Permisos separados por comando administrativo.
- Confirmaciones de un solo uso para pagos grandes.
- MiniMessage dinámico insertado como `unparsed` o `component`.
- Suggestions no revelan información que el sender no puede consultar.
- Los mensajes Redis se validan, versionan y no autorizan mutaciones.

## 30. Rendimiento

### 30.1 Main thread

Está prohibido ejecutar en el main thread:

- JDBC;
- Redis;
- carga o guardado YAML;
- LuckPerms `loadUser`;
- resolución offline de NetworkPlayerSettings;
- consultas de historial.

### 30.2 Coste esperado

- Lecturas cacheadas: O(1).
- Placeholder: O(1), sin I/O.
- Mutación: una transacción MySQL corta.
- Transferencia: una transacción y dos filas bloqueadas.
- Reconciliación: consulta batch de revisiones y refresh selectivo.

Una latencia MySQL típica de pocos milisegundos no bloquea gameplay. La confirmación puede incluir el regreso al scheduler Paper, pero sigue representando un resultado verdadero.

### 30.3 No optimizar antes de medir

No se agregan batch write-behind, Redis como balance, sharding, event sourcing o colas durables sin métricas que demuestren una necesidad. Si la carga futura exige batching, debe conservar idempotencia y confirmación durable.

## 31. Edge cases obligatorios

La implementación y pruebas deben cubrir:

### 31.1 Concurrencia económica

- Dos compras simultáneas con saldo para una sola.
- Dos servidores mutando la misma cuenta.
- Pago concurrente con compra.
- Dos transferencias cruzadas sin deadlock permanente.
- Set administrativo concurrente con award.
- Receptor alcanzando el máximo durante una transferencia.

### 31.2 Idempotencia y fallos

- Duplicación de eventos de modalidad.
- Reintento de una transferencia ya confirmada.
- Mismo `OperationId` con payload diferente.
- Timeout después de commit con respuesta desconocida.
- Deadlock y lock wait timeout.
- MySQL caído antes de comenzar.
- Conexión perdida durante la transacción.
- Plugin deshabilitándose con operaciones activas.

### 31.3 Lifecycle distribuido

- Jugador cambiando de servidor durante una mutación.
- Redis caído o perdiendo Pub/Sub.
- Mensajes Redis duplicados y fuera de orden.
- Reconciliación después de recuperar Redis.
- Carga completada después de quit.
- Operación administrativa contra un jugador conectado en otro servidor.

### 31.4 Awards y boosters

- Booster todavía no cargado.
- Award cero o negativo.
- Multiplicador excesivo o capped.
- Resultado decimal con `FLOOR`.
- Overflow de `long`.
- NetworkBoosters ausente durante rollout.
- NetworkBoosters presente pero su carga falla.

### 31.5 Identidad y localización

- Jugador offline o nunca conocido.
- Cambio de username.
- Cambio de prefix con identidad cacheada.
- Pérdida de permiso del Nick Style.
- Nick Style eliminado del catálogo.
- Bandera deshabilitada.
- País `XX`.
- Idioma todavía no ready.
- Cambio de idioma en runtime.

### 31.6 Configuración y feedback

- MiniMessage inválido.
- Sonido inválido.
- Bossbar inválida o superpuesta.
- Jugador desconectado antes del feedback.
- Placeholder antes de ready.
- Reload parcialmente inválido.
- Cambio de root command que requiere reinicio.

No existen edge cases de leaderboard porque esa capacidad está fuera del producto.

## 32. Estrategia de pruebas

### 32.1 Restricción

No se usa Testcontainers en ningún módulo, tarea o entorno de CI.

### 32.2 Unitarias

Cubren sin infraestructura:

- validación de requests;
- aritmética exacta;
- redondeo;
- límites;
- fingerprint;
- resultados tipados;
- orden canónico de UUID;
- confirmaciones;
- codecs Redis;
- publicación por revisión;
- coalescing de cargas;
- lifecycle epochs;
- parsing de configuración;
- feedback;
- formato numérico e identidad.

### 32.3 Integración MySQL

Se usa una instancia MySQL real externa y administrada fuera de la suite.

Variables esperadas:

```text
PROGRESSENGINE_TEST_DB_HOST
PROGRESSENGINE_TEST_DB_PORT
PROGRESSENGINE_TEST_DB_NAME
PROGRESSENGINE_TEST_DB_USER
PROGRESSENGINE_TEST_DB_PASSWORD
```

Reglas:

- misma versión mayor que producción;
- base exclusiva para pruebas;
- schema o prefijo único por ejecución;
- limpieza controlada;
- la suite nunca inicia contenedores;
- sin credenciales, el grupo de integración se omite explícitamente;
- H2 no sustituye MySQL para locks, isolation o sintaxis.

Casos mínimos:

- créditos concurrentes sin lost updates;
- solo un débito exitoso cuando el saldo alcanza para uno;
- conservación de suma en transferencia;
- rollback completo;
- idempotencia concurrente;
- fingerprint conflict;
- deadlock y retry;
- lock timeout;
- overflow;
- límite del receptor;
- historial y revisiones correctas.

### 32.4 Redis

Las pruebas de unidad usan un fake del contrato interno. Las pruebas de integración, cuando se ejecuten, apuntan a un Redis externo configurado; tampoco se inicia con Testcontainers.

Cubren:

- invalidación;
- deduplicación;
- mensajes fuera de orden;
- caída y recuperación;
- reconciliación compensatoria.

### 32.5 Paper

Se prueban adaptadores con dobles donde sea útil y se mantiene un smoke test en un servidor Paper real para:

- startup;
- servicios;
- comandos Brigadier;
- eventos;
- feedback Adventure;
- PlaceholderAPI;
- NetworkPlayerSettings;
- LuckPerms;
- NetworkBoosters.

## 33. Criterios de aceptación

ProgressEngine 1.0 está listo cuando:

- [ ] Las migraciones funcionan sobre una base vacía y una ya migrada.
- [ ] Ninguna mutación realiza I/O en main thread.
- [ ] Débitos concurrentes no producen saldo negativo ni doble gasto.
- [ ] Transferencias son atómicas y conservan la suma.
- [ ] Replays idempotentes devuelven el mismo resultado.
- [ ] Fingerprints distintos con el mismo ID son rechazados.
- [ ] Caché solo cambia después del commit y por revisión más nueva.
- [ ] Redis puede desaparecer sin pérdida económica.
- [ ] Reconciliación corrige cachés tras mensajes perdidos.
- [ ] Awards esperan NetworkBoosters cuando está presente.
- [ ] Toda identidad visible usa el renderer central.
- [ ] Todo feedback respeta el idioma de NetworkPlayerSettings.
- [ ] Placeholders nunca hacen I/O ni ocultan loading como cero.
- [ ] Reload inválido conserva el snapshot anterior.
- [ ] Historial pagina sin cargar todo en memoria.
- [ ] Status operativo diferencia MySQL y Redis.
- [ ] No existe código de leaderboards, rewards o múltiples monedas.
- [ ] La suite no usa Testcontainers.

## 34. Orden recomendado de implementación

1. Contratos y modelos de `progressengine-api`.
2. Configuración y lifecycle base.
3. Migraciones y repositorios.
4. Idempotencia, operaciones y ledger.
5. Créditos, débitos, set y transferencias.
6. Snapshots Caffeine y precarga.
7. Redis, invalidación y reconciliación.
8. Integración NetworkBoosters y awards.
9. Eventos Paper.
10. Localización, identidad y feedback.
11. Comandos.
12. PlaceholderAPI.
13. Observabilidad, hardening y pruebas de integración.

Cada bloque debe cerrar sus pruebas antes de construir el siguiente. No se implementan bridges síncronos inseguros para acelerar una demo.

## 35. Decisiones deliberadamente pospuestas

No forman parte de 1.0:

- notificaciones offline durables;
- API batch pública;
- providers Vault, zShop o ShopGUI+;
- métricas con una plataforma externa;
- herramientas de export/import masivo;
- particionado o archivado del ledger;
- integración web;
- soporte de múltiples recursos;
- leaderboards.

Agregar una capacidad pospuesta requiere un caso de producto, revisar invariantes y actualizar este documento antes de implementarla.

## 36. Registro de validación final

Durante el cierre del diseño se descartaron explícitamente estas alternativas:

| Alternativa | Decisión | Motivo |
| --- | --- | --- |
| Write-behind cada pocos ticks | Rechazada | Confirma antes de persistir y puede perder operaciones |
| Balance autoritativo en Redis | Rechazada | Convierte Redis en punto de fallo económico |
| Idempotencia comprobada antes de la transacción | Rechazada | Mantiene una carrera entre check y escritura |
| Débito `has` seguido de `take` | Rechazada | Permite TOCTOU y doble gasto |
| Transferencia con débito y crédito separados | Rechazada | Requiere compensaciones y puede quedar parcial |
| Starting balance implícito | Rechazada | Oculta el origen y complica carreras; se usa award explícito |
| Providers síncronos mutables | Rechazada en 1.0 | zShop, ShopGUI+ y Vault no pueden confirmar una operación async durable |
| Leaderboards | Rechazada | Está fuera de la responsabilidad del producto |
| Sincronizar perfiles mediante Redis | Rechazada | Pertenece a NetworkPlayerSettings y LuckPerms |
| Testcontainers | Prohibida | Requisito de jefatura; se usa infraestructura externa |
| Más módulos o arquitectura genérica | Rechazada | Los dos módulos actuales cubren el producto sin ceremonia |

La revisión final no detectó contradicciones conocidas entre el modelo numérico, la transacción, la caché, Redis, la API, NetworkBoosters y el lifecycle. La validez del comportamiento implementado deberá demostrarse mediante los criterios de aceptación y las pruebas descritas, no asumirse por el documento.

## 37. Regla final de evolución

Toda nueva feature debe responder afirmativamente:

1. ¿Pertenece a una economía de points?
2. ¿Conserva MySQL como autoridad?
3. ¿Mantiene atomicidad e idempotencia?
4. ¿Evita I/O bloqueante en el main thread?
5. ¿Puede explicarse sin introducir una abstracción innecesaria?

Si una propuesta falla estas preguntas, debe vivir en otro plugin, mejorar primero la infraestructura compartida correspondiente o descartarse.
