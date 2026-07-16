# Plan secuencial de implementación de ProgressEngine 1.0

Este plan convierte el diseño completo del producto en una ruta de ejecución estrictamente ordenada. El objetivo es terminar cada capacidad junto con sus garantías y pruebas antes de comenzar otra que dependa de ella, evitando implementaciones parciales, regresos constantes y deuda de integración.

> Todo el detalle funcional y técnico de cada bloque se encuentra en `docs/producto/diseno-final-progressengine.md`. Este archivo define el orden de trabajo, las dependencias y las condiciones de cierre; no reemplaza ni duplica el diseño.

## Estado inicial verificado

El punto de partida ya contiene:

- proyecto Gradle con `progressengine-api` y `progressengine-paper`;
- Java 25 y Paper API 26.1;
- dependencias y relocalizaciones principales configuradas;
- publicación base del artefacto API;
- bootstrap de Paper y Cloud con coordinador simple;
- declaración de NetworkPlayerSettings, LuckPerms y PlaceholderAPI.

Todavía no contiene contratos públicos, configuración runtime, persistencia, economía, caché, sincronización, integraciones ni experiencia de usuario. Por ello, el plan comienza sobre la base existente y no presupone capacidades ya implementadas.

## Regla de ejecución

La ruta obligatoria es:

```text
Contratos públicos
  -> configuración y runtime base
  -> persistencia MySQL
  -> operaciones de una cuenta
  -> transferencias
  -> caché y eventos post-commit
  -> boosters y awards
  -> lifecycle del jugador
  -> localización, identidad y feedback
  -> Redis y reconciliación
  -> comandos
  -> PlaceholderAPI
  -> cierre operativo
  -> aceptación completa
```

Se aplican estas reglas durante todo el plan:

1. Los bloques se ejecutan en el orden indicado. No se inicia un bloque mientras el anterior tenga criterios de finalización pendientes.
2. Cada bloque se trabaja como una unidad completa, sin abrir el siguiente mientras quede trabajo pendiente en el actual.
3. Cada bloque termina con código compilable, pruebas de su alcance y sin stubs, rutas falsas de éxito ni `TODO` funcionales.
4. Cada bloque cierra sus edge cases relevantes; no se acumulan todos para el final.
5. Las pruebas de MySQL y Redis usan infraestructura externa. No se incorpora Testcontainers ni H2 como sustituto transaccional.
6. Ningún bloque introduce leaderboards, rewards, múltiples monedas, providers mutables síncronos o cualquier capacidad excluida.
7. Antes de implementar un bloque se validan sus decisiones contra el código y las APIs reales. Si aparece una contradicción, se resuelve antes de continuar en lugar de construir un workaround.
8. El plugin no registra públicamente un `PointsService` incompleto. El servicio se publica únicamente cuando todas sus garantías runtime están conectadas.

## Bloque 1: Contratos públicos y base de pruebas

**Objetivo:** fijar el lenguaje económico y las fronteras públicas sobre las que dependerá todo el runtime.

**Prerrequisito:** proyecto base compilable.

**Detalle en el diseño:** secciones 3, 5, 6, 7, 9, 16, 18, 29 y 32.

**Trabajo incluido:**

- Configurar JUnit y las tareas separadas para pruebas unitarias e integración externa.
- Definir el comportamiento explícito cuando no existan credenciales de integración.
- Verificar compilación, tests, sources JAR y Javadocs de ambos módulos.
- Mantener el build libre de Testcontainers.

- Implementar `OperationId` como value object UUID inmutable con `generate()`, `of(UUID)`, `parse(String)` y representación canónica.
- Documentar y probar que una intención nueva usa un ID nuevo, mientras todo retry reutiliza el mismo request y el mismo ID.
- Rechazar UUID nulo, UUID nil y representaciones inválidas sin introducir un servicio generador adicional.
- Implementar tipos de operación, `OperationReason`, actor, origen y metadata acotada.
- Implementar `BalanceSnapshot`, recibos y estados económicos inmutables.
- Centralizar validaciones puras de UUID, cantidades, rango, razón y metadata sin acoplarlas a Paper o infraestructura.
- Probar límites de `long`, cero, negativos, nombres inválidos y metadata excesiva.

- Implementar requests inmutables para award, credit, debit, transfer y set/reset.
- Implementar resultados tipados que diferencien éxito, replay y rechazos de negocio.
- Modelar `NO_POINTS_AWARDED` para el resultado cero derivado exclusivamente de `FLOOR`, sin receipt ni ledger y con replay durable.
- Definir `PointsService` como entrada de `ServicesManager` y `PointsClient` ligado al plugin consumidor, con lecturas síncronas sin I/O y operaciones asíncronas.
- Evitar booleanos ambiguos y excepciones para resultados económicos esperables.
- Mantener el fingerprint como detalle interno: ningún consumidor lo calcula o lo envía en un request.

- Definir los cuatro eventos Paper iniciales y sus datos inmutables.
- Limitar la mutabilidad al prepare cancelable de award.
- Representar origen local o remoto donde corresponda.
- Probar la semántica de cancelación y la inmutabilidad de eventos observacionales.

**El bloque se considera terminado cuando:**

- [x] El API compila sin depender de CraftKit, Redis, Caffeine, BoostedYAML o implementaciones runtime.
- [x] Los modelos públicos son inmutables y expresan todos los outcomes mínimos.
- [x] `OperationId` permite generación local segura, encapsulado y restauración canónica, con semántica de retry documentada y probada.
- [x] Validación, aritmética, metadata y contratos tienen pruebas unitarias.
- [x] El artefacto API produce JAR, sources y Javadocs correctamente.

**Constancia de cierre:** [x] Terminado | Fecha: 7/14/26 | 

## Bloque 2: Configuración y runtime base

**Objetivo:** crear un runtime que pueda iniciar, rechazar una configuración inválida y cerrar de forma controlada antes de conectar infraestructura económica.

**Prerrequisito:** bloque 1 cerrado.

**Detalle en el diseño:** secciones 7, 25, 26 y 27.

**Trabajo incluido:**

- Crear los modelos inmutables para MySQL, Redis, balance máximo, redondeo, caché, reconciliación, server ID e integraciones.
- Cargar defaults con BoostedYAML y validar rangos y referencias cruzadas.
- Separar lectura del archivo, validación y publicación atómica del snapshot.
- Probar configuraciones válidas, límites y combinaciones inválidas.

- Implementar estados `STARTING`, `READY`, `DEGRADED_REDIS`, `UNAVAILABLE_DATABASE`, `SHUTTING_DOWN` y `CLOSED`.
- Establecer propiedad y orden de cierre de los componentes.
- Rechazar nuevas operaciones cuando el estado no lo permita.
- Preparar seguimiento de tareas y operaciones en vuelo sin crear executors innecesarios.

- Implementar el proceso de construir un candidato completo y publicarlo solo si todo es válido.
- Conservar el snapshot anterior ante cualquier error.
- Preparar el registro de validadores de commands, identity y messages que se completará en sus bloques, sin exponer todavía el comando de reload.
- Probar concurrencia entre lecturas y publicación del snapshot.

**El bloque se considera terminado cuando:**

- [x] Una configuración inválida impide habilitar el runtime.
- [x] Una recarga inválida no publica estado parcial.
- [x] Las transiciones de estado están validadas y probadas.
- [x] Todavía no se registra una API económica incompleta.

**Constancia de cierre:** [x] Terminado | Fecha: 7/14/26 | 

## Bloque 3: Persistencia durable

**Objetivo:** establecer el schema y los accesos SQL que serán la única autoridad económica.

**Prerrequisito:** bloque 2 cerrado.

**Detalle en el diseño:** secciones 4.2, 8, 28.3, 29, 30 y 32.3.

**Trabajo incluido:**

- Crear y configurar `Database` de CraftKit con executor JDBC dedicado.
- Implementar migraciones Flyway para accounts, operations, ledger y player names.
- Crear constraints, uniques e índices definidos por el diseño.
- Implementar cobertura de migración para base vacía, base ya migrada y fallo de migración mediante suite de integración opcional.

- Implementar creación idempotente de cuenta, carga individual y carga batch de revisiones.
- Implementar reserva, lectura y resolución durable de operaciones.
- Usar UUID binario y prepared statements en todos los valores.
- Probar round trips, ausencia de cuenta, duplicados y rollback.

- Implementar inserción append-only del ledger y paginación estable por cuenta y fecha.
- Implementar actualización transaccional del mapping actual username/UUID.
- Implementar resolución de targets conocidos y carga asíncrona del índice de suggestions.
- Probar renames, reutilización de nombre, páginas vacías, límites y orden estable.

**El bloque se considera terminado cuando:**

- [x] Flyway queda configurado con migraciones versionadas para base vacía y base ya actualizada.
- [x] Las cuatro tablas e índices respetan sus invariantes de schema.
- [x] El ledger no posee rutas de update o delete en el runtime.
- [x] Historial y nombres operan sin cargar conjuntos completos en memoria.
- [x] Todo acceso SQL ocurre en el executor de CraftKit.

**Constancia de cierre:** [x] Terminado | Fecha: 7/14/26 | 

## Bloque 4: Núcleo económico de una cuenta

**Objetivo:** completar idempotencia, crédito, débito, set y reset con transacciones reales y trazabilidad.

**Prerrequisito:** bloque 3 cerrado.

**Detalle en el diseño:** secciones 5, 6, 9, 10, 11.2, 11.3, 11.5, 12 y 31.1-31.2.

**Trabajo incluido:**

- Implementar fingerprint SHA-256 determinista y versionado sobre una representación canónica de todos los campos relevantes.
- Confirmar que distinto `OperationId` permite operaciones legítimas con el mismo fingerprint y que el fingerprint nunca forma parte de la API consumidora.
- Resolver reserva, replay y conflicto dentro de la misma transacción.
- Implementar aritmética exacta sobre el balance bloqueado y validación de `maximumBalance`.
- Persistir rechazos económicos resueltos y no persistir fallos previos o rollbacks.

- Implementar creación de cuenta, lock de fila, validación, mutación, revisión, ledger y resultado durable.
- Garantizar que debit decide fondos exclusivamente sobre el estado bloqueado.
- Aplicar retry solo a deadlock y lock wait timeout mediante la política de CraftKit.
- Probar créditos concurrentes, doble gasto, overflow y replay concurrente.

- Implementar set dentro de `0..maximumBalance` y reset como operación explícita a cero.
- Registrar siempre el delta real, razón, actor y revisión.
- Cubrir concurrencia entre set y otras mutaciones.
- Probar no-op económico auditado según el contrato final de resultados.

- Serializar por UUID las mutaciones originadas en el mismo servidor.
- Limpiar cadenas completadas para no retener UUIDs.
- Confirmar que esta optimización no sustituye locks ni transacciones MySQL.
- Probar orden local, excepciones y limpieza posterior.

**El bloque se considera terminado cuando:**

- [x] Reutilizar un `OperationId` idéntico devuelve el resultado durable original.
- [x] Reutilizarlo con otro fingerprint produce conflicto sin mutación.
- [x] Dos débitos concurrentes no gastan el mismo saldo.
- [x] Ninguna operación confirma antes del commit.
- [x] Cada movimiento confirmado tiene balance anterior, posterior y revisión correctos.

**Constancia de cierre:** [x] Terminado | Fecha: 7/14/26 | 

## Bloque 5: Transferencias atómicas

**Objetivo:** cerrar la operación multicuenta más compleja antes de agregar caché o sincronización.

**Prerrequisito:** bloque 4 cerrado.

**Detalle en el diseño:** secciones 10, 11.4, 12 y 31.1-31.2.

**Trabajo incluido:**

- Rechazar auto-transferencias y cantidades inválidas antes de abrir la transacción.
- Ordenar UUIDs canónicamente para creación y locks.
- Integrar ambas cuentas con la serialización local en el mismo orden canónico.
- Validar fondos del emisor y capacidad del receptor bajo lock.
- Persistir una operación, dos balances y dos entradas de ledger en un solo commit.

- Probar transferencias cruzadas, pagos simultáneos, compra concurrente y receptor alcanzando el máximo.
- Verificar conservación de suma y rollback total ante cualquier fallo.
- Simular deadlock, lock timeout, conexión perdida y respuesta ambigua.
- Confirmar que un retry usa el mismo request y no compensa manualmente movimientos.

**El bloque se considera terminado cuando:**

- [x] No existe estado observable con una transferencia aplicada parcialmente.
- [x] Los locks se toman siempre en orden canónico explícito.
- [x] Deadlocks y timeouts clasificados se reintentan de forma segura.
- [x] La suma se conserva en todos los casos exitosos.

**Constancia de cierre:** [x] Terminado | Fecha: 7/14/26 | 

## Bloque 6: Caché, cargas y publicación post-commit

**Objetivo:** añadir lecturas rápidas sin convertir la caché en autoridad y cerrar el orden observable de las mutaciones.

**Prerrequisito:** bloque 5 cerrado.

**Detalle en el diseño:** secciones 10, 13, 16.1-16.4 y 18.3-18.5.

**Trabajo incluido:**

- Implementar snapshots inmutables en Caffeine con size y expiración configurables.
- Publicar únicamente revisiones mayores.
- Implementar `cached`, cargas coalescidas, `load` y `refresh` sin representar ausencia como cero.
- Probar carreras entre una carga lenta y una mutación más nueva.

- Actualizar snapshots únicamente después del commit.
- Despachar eventos Paper observacionales en el scheduler correcto.
- Aislar excepciones de listeners y rechazo del scheduler durante shutdown.
- Completar el future después de caché y evento local, sin esperar Redis ni feedback.

- Conectar operaciones, cargas y lecturas al contrato `PointsService`.
- Mantener el servicio sin registro público hasta completar awards, lifecycle y startup final.
- Medir cargas y mutaciones en vuelo para uso operativo posterior.
- Probar threading, cierre y comportamiento cuando MySQL no está disponible.

**El bloque se considera terminado cuando:**

- [x] Una revisión vieja nunca reemplaza una nueva.
- [x] Ausencia de snapshot permanece distinguible de balance cero.
- [x] Caché y eventos solo reflejan commits confirmados.
- [x] Los futures respetan el orden observable definido.
- [x] Lecturas cacheadas son O(1) y no ejecutan I/O.

**Constancia de cierre:** [x] Terminado | Fecha: 7/14/26 | 

## Bloque 7: NetworkBoosters y awards

**Objetivo:** completar la última mutación pública con cálculo de boosters, prepare event y redondeo correcto.

**Prerrequisito:** bloque 6 cerrado.

**Detalle en el diseño:** secciones 5.3, 11.1, 17, 18.2 y 31.4.

**Trabajo incluido:**

- Integrar únicamente el servicio público y el target `network_progression:points`.
- Obtener o esperar el snapshot requerido fuera del main thread.
- Convertir `BigDecimal` a `long` mediante `FLOOR`, validando cap, overflow y balance máximo.
- Modelar claramente integración ausente, disponible y fallida.

- Despachar `PointsAwardPrepareEvent` antes del cálculo de boosters.
- Revalidar la cantidad después de listeners y respetar cancelación.
- Ejecutar el crédito transaccional idempotente con tipo award.
- Construir el resultado con base, multiplicador, final, boosters, cap y recibo.
- Resolver un resultado `NO_POINTS_AWARDED` durable cuando `FLOOR` produzca cero, sin crear movimiento ni ledger.

- Probar booster no listo, carga fallida, multiplicador extremo, cap, decimales y overflow.
- Probar rollout sin NetworkBoosters sin simular que hubo boosters.
- Garantizar que un fallo de carga no concede silenciosamente el valor base.
- Confirmar que credit y el resto de operaciones jamás aplican boosters.

**El bloque se considera terminado cuando:**

- [x] Award es idempotente y auditable como tipo propio.
- [x] Prepare, boosters, persistencia, caché y eventos ocurren en el orden correcto.
- [x] Un booster no listo se espera; uno fallido no produce un award incorrecto.
- [x] Todas las mutaciones del `PointsService` tienen implementación real.

**Constancia de cierre:** [x] Terminado | Fecha: 7/15/26 |

## Bloque 8: Lifecycle y readiness del jugador

**Objetivo:** conectar balances, NetworkPlayerSettings, NetworkBoosters y nombres sin carreras entre login, quit y cambio de servidor.

**Prerrequisito:** bloque 7 cerrado.

**Detalle en el diseño:** secciones 8.4, 13.4, 15, 16.6, 17.4 y 31.3.

**Trabajo incluido:**

- Iniciar carga asíncrona en pre-login y compartir futures por UUID.
- Coordinarla con `PlayerSettingsReadyEvent`.
- Implementar epoch de lifecycle para descartar publicaciones tardías.
- Actualizar el mapping de nombre de forma transaccional durante join.

- Esperar balance y snapshot de boosters cuando corresponda.
- Publicar el snapshot y marcar ready solo al completar todos los prerrequisitos.
- Emitir `PlayerPointsReadyEvent` una vez por epoch válido.
- Mantener comandos, placeholders y compras cerrados antes de ready.

- Invalidar el epoch, evitar feedback a entidades inválidas y limpiar estado temporal.
- Permitir que operaciones ya enviadas a MySQL resuelvan su resultado durable.
- Forzar carga autoritativa en la siguiente conexión.
- Probar carga posterior al quit y cambio de servidor durante una mutación.

**El bloque se considera terminado cuando:**

- [x] Ningún jugador aparece ready con dependencias incompletas.
- [x] Una carga de un epoch viejo no publica estado.
- [x] Logout no cancela ni falsea una operación ya enviada.
- [x] Nombres actuales se mantienen correctos ante rename y reutilización.

**Constancia de cierre:** [x] Terminado | Fecha: 7/15/26

## Bloque 9: Localización, identidad y feedback

**Objetivo:** construir una única capa de presentación segura y consistente antes de exponer comandos.

**Prerrequisito:** bloque 8 cerrado.

**Detalle en el diseño:** secciones 19, 20, 21, 25.1-25.4 y 31.5-31.6.

**Trabajo incluido:**

- Cargar catálogos completos por idioma con fallback y warnings controlados.
- Resolver el idioma actual mediante NetworkPlayerSettings en cada envío.
- Implementar formatos raw, formatted y compact deterministas.
- Probar idioma no ready, keys faltantes y cambio de idioma en runtime.

- Implementar un único `PlayerIdentityRenderer` para online y offline.
- Componer prefix de LuckPerms, Nick Style y bandera sin separadores sobrantes.
- Resolver identidad offline fuera del main thread y cachearla temporalmente.
- Invalidar por eventos de LuckPerms y NetworkPlayerSettings.

- Implementar chat, action bar, title, sound y boss bar como acciones ordenadas.
- Usar resolvers seguros para valores dinámicos y evitar reinterpretar input como MiniMessage.
- Implementar canales y limpieza de boss bars, además de coalescing visual de awards.
- Omitir acciones incompatibles con consola y entidades desconectadas.

- Incorporar messages e identity al candidato atómico de configuración.
- Validar MiniMessage, sonidos, tiempos, boss bars, referencias y formatos antes de publicar.
- Aplicar cambios en caliente permitidos y marcar con claridad los que requieren reinicio.
- Probar reload parcialmente inválido y conservación integral del snapshot anterior.

**El bloque se considera terminado cuando:**

- [x] Todo nombre visible pasa por el renderer central.
- [x] Todo feedback de jugador usa el idioma efectivo actual.
- [x] Input dinámico no se interpreta como MiniMessage administrado.
- [x] Boss bars y coalescing solo afectan presentación.
- [x] Un reload inválido conserva íntegramente la configuración anterior.

**Constancia de cierre:** [x] Terminado | Fecha: 7/15/26 | 


**Objetivo:** propagar cambios rápidamente y garantizar convergencia aun cuando Pub/Sub pierda mensajes.

**Prerrequisito:** bloque 9 cerrado.

**Detalle en el diseño:** secciones 4.3, 14, 18.4, 27.2, 31.3 y 32.4.

**Trabajo incluido:**

- Crear codecs versionados y validación estricta para invalidaciones y avisos de transferencia.
- Iniciar CraftKit Redis en `RECOVER` y observar conexión y ACK real de suscripciones.
- Conectar transiciones `READY` y `DEGRADED_REDIS` sin afectar la autoridad MySQL.
- Probar payloads inválidos, mensajes propios y cambios de conectividad.

- Publicar invalidaciones best-effort después del commit.
- Ignorar revisiones iguales o anteriores y refrescar solo cuentas cacheadas relevantes.
- Emitir cambios remotos sin inventar commits locales.
- Deduplicar avisos de transferencia y entregar feedback localizado solo al receptor online válido.

- Consultar revisiones online en lotes y cargar balances completos solo cuando cambien.
- Usar intervalos normal y degradado dentro de rangos seguros.
- Forzar refreshes relevantes después de recuperar suscripciones.
- Probar pérdida, duplicación, desorden de mensajes y reconciliación compensatoria.

**El bloque se considera terminado cuando:**

- [x] Eliminar Redis no pierde ni bloquea mutaciones económicas.
- [x] Mensajes duplicados o fuera de orden no hacen retroceder snapshots.
- [x] La reconciliación corrige mensajes perdidos.
- [x] La recuperación espera suscripciones reales antes de volver a estado normal.

**Constancia de cierre:** [x] Terminado | Fecha: 7/15/26

## Bloque 11: Comandos de jugador y administración

**Objetivo:** exponer la economía mediante Cloud sin bloquear el main thread ni debilitar las garantías del servicio.

**Prerrequisito:** bloque 10 cerrado.

**Detalle en el diseño:** secciones 22, 28, 29 y 31.5-31.6.

**Trabajo incluido:**

- Completar parsers, sender types, permisos, captions, excepciones, help y Brigadier.
- Implementar parser estricto de cantidades `long`.
- Implementar target online, offline conocido y UUID administrativo.
- Alimentar suggestions desde el índice cacheado, nunca desde MySQL en el main thread.

- Implementar `/points`, balance, history y help.
- Consultar historial de forma asíncrona y paginada.
- Renderizar targets y actores mediante identidad central.
- Mostrar loading, unknown target y fallos de infraestructura sin convertirlos en cero.

- Implementar mínimo, máximo, permiso y cooldown por emisor.
- Crear tokens impredecibles, de un solo uso, expirable y ligados a revisión e intención.
- Revalidar todas las reglas dentro de la transferencia final.
- Reutilizar el mismo `OperationId` al resolver una respuesta ambigua de una intención confirmada.

- Implementar add, remove, set, reset e historial administrativo con permisos separados.
- Implementar reload mostrando cambios aplicados y cambios que requieren reinicio.
- Implementar status con estado runtime, DB, Redis, suscripciones, caché, tareas, reconciliación y schema.
- Garantizar razones default explícitas y auditoría completa.

**El bloque se considera terminado cuando:**

- [x] Todos los comandos definidos por el diseño existen y respetan permisos.
- [x] Ningún handler usa `.join()`, `.get()` o I/O bloqueante en el main thread.
- [x] Pay solo comunica éxito después del commit.
- [x] Tokens vencidos, usados o ligados a otra revisión no autorizan pagos.
- [x] History pagina y status diferencia salud de MySQL y Redis.

**Constancia de cierre:** [x] Terminado | Fecha: 7/15.26 |

## Bloque 12: PlaceholderAPI

**Objetivo:** exponer lecturas O(1) estrictamente locales y representar readiness sin resultados engañosos.

**Prerrequisito:** bloque 11 cerrado.

**Detalle en el diseño:** secciones 23, 30.2 y 31.6.

**Trabajo incluido:**

- Registrar condicionalmente la expansión persistente cuando PlaceholderAPI esté presente.
- Implementar points, points formatted, points compact y ready.
- Consultar exclusivamente snapshots locales y formatos ya disponibles.
- Definir fallbacks para jugador ausente y estado no ready.

- Desregistrar correctamente durante shutdown.
- Probar plugin opcional ausente, reload, jugador no ready y contexto sin jugador.
- Verificar que ninguna ruta alcanza MySQL, Redis, LuckPerms o NetworkPlayerSettings desde el request.
- Medir que la resolución permanezca O(1).

**El bloque se considera terminado cuando:**

- [x] Los cuatro placeholders cumplen su contrato exacto.
- [x] Loading nunca se representa como balance cero.
- [x] Los requests no producen I/O ni futures.
- [x] La ausencia de PlaceholderAPI no afecta el runtime económico.

**Constancia de cierre:** [x] Terminado | Fecha: 7/16/26 |

## Bloque 13: Startup, shutdown y hardening operativo

**Objetivo:** conectar todos los componentes en su orden definitivo y hacer seguro cada camino de fallo.

**Prerrequisito:** bloque 12 cerrado.

**Detalle en el diseño:** secciones 16.6, 26, 27, 28, 29 y 30.

**Trabajo incluido:**

- Ejecutar configuración, Database, migraciones, Redis y resolución de dependencias en el orden establecido.
- Construir repositorios, caché, servicio, lifecycle, integraciones, comandos y tareas.
- Registrar `PointsService`, listeners, comandos y PlaceholderAPI solo después de una inicialización válida.
- Declarar NetworkBoosters con la opcionalidad correspondiente al rollout.

- Cambiar a `SHUTTING_DOWN` antes de rechazar nuevas mutaciones.
- Cancelar precargas y reconciliación y esperar operaciones en vuelo con timeout acotado.
- Desregistrar interfaces públicas y limpiar feedback antes de cerrar Redis y MySQL.
- Resolver correctamente callbacks tardíos y scheduler no disponible.

- Fallar cerrado ante MySQL no disponible sin modificar caché ni emitir éxito.
- Mantener Redis como degradación recuperable con transiciones sin spam.
- Estructurar logs de mutación con identificadores y errores SQL sin secretos.
- Verificar límites de caché, tareas en vuelo, última reconciliación y salud controlada.

**El bloque se considera terminado cuando:**

- [x] El servicio público nunca es visible antes de migraciones e inicialización completa.
- [x] La ausencia de una dependencia requerida impide publicar el runtime.
- [x] Shutdown no acepta mutaciones nuevas y trata correctamente las ya enviadas.
- [x] MySQL caído falla cerrado; Redis caído degrada sin pérdida económica.
- [x] No queda feedback temporal, suscripción, caché o executor propio sin cerrar.

**Constancia de cierre:** [x] Terminado | Fecha: 7/16/26 |

## Bloque 14: Verificación integral y cierre de 1.0

**Objetivo:** demostrar que la implementación completa satisface el producto, no solo que sus piezas compilan por separado.

**Prerrequisito:** bloque 13 cerrado.

**Detalle en el diseño:** secciones 24, 30, 31, 32, 33, 35, 36 y 37.

**Trabajo incluido:**

- Ejecutar todos los casos puros de contratos, validación, aritmética, fingerprint, codecs, caché, epochs, configuración, identidad y feedback.
- Añadir cualquier edge case del diseño que no haya quedado cubierto en su bloque.
- Confirmar ausencia de tests ignorados sin una causa ambiental explícita.
- Confirmar ausencia total de Testcontainers.

- Ejecutar la matriz completa contra MySQL real externo de la misma versión mayor que producción.
- Ejecutar Redis externo para invalidación, desorden, pérdida y recuperación.
- Validar planes de ejecución de historial, operación y reconciliación.
- Repetir pruebas concurrentes para detectar carreras intermitentes.

- Validar startup, migraciones, ServicesManager y shutdown en servidor real.
- Validar comandos Brigadier, eventos, feedback Adventure y PlaceholderAPI.
- Validar NetworkPlayerSettings, LuckPerms y NetworkBoosters en sus estados relevantes.
- Validar cambio de servidor, dependencia opcional ausente y Redis degradado.

- Recorrer uno por uno todos los criterios de aceptación del diseño con evidencia ejecutable.
- Buscar I/O bloqueante, `.join()`, `.get()`, write-behind, mutaciones desde Redis y contratos síncronos inseguros.
- Verificar que no exista código para capacidades excluidas.
- Construir el plugin sombreado y el artefacto API final desde un checkout limpio.

**El bloque y ProgressEngine 1.0 se consideran terminados cuando:**

- [ ] Todos los criterios de aceptación de ProgressEngine 1.0 están demostrados.
- [ ] Todos los edge cases obligatorios están cubiertos y pasan.
- [ ] Las suites unitarias, MySQL, Redis y Paper real terminan correctamente.
- [ ] El build final genera artefactos reproducibles sin dependencias filtradas.
- [ ] No existen rutas que confirmen éxito antes de MySQL, bloqueen el main thread o usen caché/Redis como autoridad.
- [ ] El alcance implementado coincide exactamente con ProgressEngine 1.0.

**Constancia de cierre:** [ ] Terminado | Fecha: __________ | 

## Matriz de cobertura

Esta matriz permite comprobar que ninguna parte del diseño quedó sin un bloque responsable.

| Secciones del diseño | Bloques responsables |
| --- | --- |
| 1-4: alcance, principios y fundamentos | 1, 2, 3, 10 y 14 |
| 5-6: modelo numérico y dominio | 1, 4, 5 y 7 |
| 7: arquitectura y empaquetado | 1, 2, 13 y 14 |
| 8: persistencia | 3, 4 y 5 |
| 9-12: idempotencia, mutaciones y concurrencia | 4, 5, 6 y 7 |
| 13: caché local | 6 y 8 |
| 14: Redis y sincronización | 10 |
| 15: lifecycle del jugador | 8 |
| 16: API pública | 1, 6, 7, 8 y 13 |
| 17: NetworkBoosters | 7 y 8 |
| 18: eventos Paper | 1, 6, 7, 8, 9 y 10 |
| 19-21: feedback, localización e identidad | 9 |
| 22: comandos | 11 |
| 23: PlaceholderAPI | 12 |
| 24: integraciones síncronas excluidas | 1 y 14 como control de alcance |
| 25: configuración | 2, 9 y 11 |
| 26-27: lifecycle del plugin y fallos | 2, 10 y 13 |
| 28: observabilidad y auditoría | 3, 11 y 13 |
| 29-30: seguridad y rendimiento | todos los bloques; auditoría final en 14 |
| 31-32: edge cases y pruebas | cierre de cada bloque y verificación integral en 14 |
| 33: criterios de aceptación | 14 |
| 34: orden recomendado | refinado por la cadena de dependencias de este plan |
| 35-36: capacidades pospuestas y alternativas rechazadas | control permanente de alcance y auditoría en 14 |
| 37: evolución | control permanente y auditoría en 14 |

## Definición de bloque terminado

Un bloque solo puede marcarse como terminado cuando cumple simultáneamente:

- el comportamiento previsto está implementado, no simulado;
- las pruebas nuevas pasan y las anteriores continúan pasando;
- no se introdujo I/O bloqueante en el main thread;
- errores y resultados conservan su semántica tipada;
- recursos y tareas tienen ownership y cierre definidos;
- no hay cambios laterales fuera del alcance del bloque;
- el siguiente trabajo puede comenzar sin tener que reabrir decisiones o reparar la base recién construida.

Si cualquiera de estas condiciones falla, el bloque sigue abierto y el siguiente no comienza. La constancia de cierre se completa únicamente después de verificar todos sus criterios.
