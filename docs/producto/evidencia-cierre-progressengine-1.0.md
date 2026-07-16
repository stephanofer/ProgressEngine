# Evidencia de cierre de ProgressEngine 1.0

Este documento registra la evidencia ejecutable necesaria para cerrar ProgressEngine 1.0. Un criterio solo puede marcarse como `PASS` cuando existe una ejecución verificable, no por haber sido implementado en un bloque anterior.

## Estado

| Campo | Valor |
| --- | --- |
| Estado general | `OPEN` |
| Commit verificado | Pendiente |
| Fecha de cierre | Pendiente |
| Java | Pendiente |
| Paper | Pendiente |
| MySQL | Pendiente |
| Redis | Pendiente |

## Comandos de cierre

Estos comandos son la puerta automatizada previa a la validación manual en entorno real. Deben ejecutarse desde PowerShell en un checkout limpio.

```powershell
.\gradlew.bat clean
.\gradlew.bat releaseVerification --no-build-cache --rerun-tasks
```

`releaseVerification` no levanta ni exige MySQL, Redis o Paper reales. Esas validaciones quedan como cierre manual posterior en el entorno real de HERA.

## Variables requeridas

| Variable | Uso |
| --- | --- |
| `PROGRESSENGINE_TEST_DB_HOST` | Manual / integración externa opcional |
| `PROGRESSENGINE_TEST_DB_PORT` | Manual / integración externa opcional |
| `PROGRESSENGINE_TEST_DB_NAME` | Manual / integración externa opcional |
| `PROGRESSENGINE_TEST_DB_USER` | Manual / integración externa opcional |
| `PROGRESSENGINE_TEST_DB_PASSWORD` | Manual / integración externa opcional |
| `PROGRESSENGINE_TEST_REDIS_HOST` | Manual / integración externa opcional |
| `PROGRESSENGINE_TEST_REDIS_PORT` | Manual / integración externa opcional |

## Suites

| Suite | Comando | Estado | Evidencia |
| --- | --- | --- | --- |
| Unitarias API y Paper | `.\gradlew.bat test --no-build-cache --rerun-tasks` | Pendiente | Pendiente |
| MySQL externo | Validación manual en entorno real | Manual | Pendiente |
| Redis externo | Validación manual en entorno real | Manual | Pendiente |
| Paper real | Smoke test manual operativo | Manual | Pendiente |
| Release completo | `.\gradlew.bat releaseVerification --no-build-cache --rerun-tasks` | Pendiente | Pendiente |

## Criterios de aceptación

| Criterio | Estado | Evidencia |
| --- | --- | --- |
| Las migraciones funcionan sobre una base vacía y una ya migrada. | Pendiente | Pendiente |
| Ninguna mutación realiza I/O en main thread. | Pendiente | Pendiente |
| Débitos concurrentes no producen saldo negativo ni doble gasto. | Pendiente | Pendiente |
| Transferencias son atómicas y conservan la suma. | Pendiente | Pendiente |
| Replays idempotentes devuelven el mismo resultado. | Pendiente | Pendiente |
| Fingerprints distintos con el mismo ID son rechazados. | Pendiente | Pendiente |
| Caché solo cambia después del commit y por revisión más nueva. | Pendiente | Pendiente |
| Redis puede desaparecer sin pérdida económica. | Pendiente | Pendiente |
| Reconciliación corrige cachés tras mensajes perdidos. | Pendiente | Pendiente |
| Awards esperan NetworkBoosters cuando está presente. | Pendiente | Pendiente |
| Toda identidad visible usa el renderer central. | Pendiente | Pendiente |
| Todo feedback respeta el idioma de NetworkPlayerSettings. | Pendiente | Pendiente |
| Placeholders nunca hacen I/O ni ocultan loading como cero. | Pendiente | Pendiente |
| Reload inválido conserva el snapshot anterior. | Pendiente | Pendiente |
| Historial pagina sin cargar todo en memoria. | Pendiente | Pendiente |
| Status operativo diferencia MySQL y Redis. | Pendiente | Pendiente |
| No existe código de leaderboards, rewards o múltiples monedas. | Pendiente | Pendiente |
| La suite no usa Testcontainers. | Pendiente | Pendiente |

## Artefactos

| Artefacto | SHA-256 | Estado |
| --- | --- | --- |
| Plugin sombreado `target/*.jar` | Pendiente | Pendiente |
| API JAR `target-api/*.jar` | Pendiente | Pendiente |
| API sources JAR | Pendiente | Pendiente |
| API Javadocs JAR | Pendiente | Pendiente |

## Planes SQL

| Consulta | Índice esperado | Estado | Evidencia |
| --- | --- | --- | --- |
| Historial por jugador | `idx_progress_ledger_player_history` | Pendiente | Pendiente |
| Operación por ID | `PRIMARY` en `progress_operations` | Pendiente | Pendiente |
| Reconciliación por UUID | `PRIMARY` en `progress_accounts` | Pendiente | Pendiente |
| Cooldown de pay | `idx_progress_operations_actor_pay_cooldown` | Pendiente | Pendiente |
| Expiración de intents | `idx_progress_command_intents_expiry` | Pendiente | Pendiente |

## Smoke Test Paper

La evidencia manual recomendada debe incluir estos marcadores en logs o reporte operativo:

```text
ProgressEngine Paper smoke: PASS
profile=complete PASS
profile=without-optionals PASS
profile=degraded-redis PASS
profile=mysql-down PASS
profile=required-dependency-missing PASS
```

| Perfil | Estado | Evidencia |
| --- | --- | --- |
| Completo con NetworkPlayerSettings, LuckPerms, NetworkBoosters, PlaceholderAPI, MySQL y Redis | Pendiente | Pendiente |
| Sin dependencias opcionales | Pendiente | Pendiente |
| Redis degradado | Pendiente | Pendiente |
| MySQL caído | Pendiente | Pendiente |
| Dependencia requerida ausente | Pendiente | Pendiente |

## Reglas de cierre

- `PASS` requiere comando, fecha, entorno y resultado verificable.
- `SKIPPED` no es aceptable para release.
- Un criterio `FAIL` o `BLOCKED` mantiene ProgressEngine 1.0 abierto.
- La evidencia debe actualizarse en el mismo cambio que marca el bloque como terminado.
