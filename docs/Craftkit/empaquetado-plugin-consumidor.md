# Empaquetado de plugins consumidores

## Regla

Todo lo declarado como `implementation` es runtime privado del plugin: se incluye con Shadow y se relocaliza bajo `tu.paquete.plugin.libs`.

Todo lo provisto por el servidor o por otro plugin es `compileOnly`: no se incluye ni se relocaliza.

| No empaquetar | Motivo |
| --- | --- |
| Paper, Bukkit o Velocity API | La plataforma las provee. |
| APIs de otros plugins, como LuckPerms | El plugin proveedor las carga. |

`craftkit-database` y `craftkit-redis` encapsulan sus dependencias y no exponen tipos de HikariCP, Flyway, Lettuce, Netty o Reactor en su API pública. Por eso se consideran runtime privado y siempre se relocalizan.

## Paquetes de CraftKit

| Módulo | Paquetes a relocalizar |
| --- | --- |
| CraftKit | `com.hera.craftkit` |
| Database | `com.zaxxer`, `org.flywaydb`, `tools.jackson`, `com.fasterxml.jackson`, `com.mysql`, `com.google.protobuf` |
| Redis | `io.lettuce`, `redis.clients.authentication`, `io.netty`, `reactor`, `org.reactivestreams` |

También se relocaliza cualquier otra dependencia propia declarada como `implementation`, con sus transitivos privados.

## Configuración base

Reemplazá `com.example.myplugin` por el paquete raíz del plugin.

```kotlin
tasks.shadowJar {
    mergeServiceFiles()


    relocate("com.hera.craftkit", "com.example.myplugin.libs.craftkit")

    relocate("com.zaxxer", "com.example.myplugin.libs.hikari")
    relocate("org.flywaydb", "com.example.myplugin.libs.flyway")
    relocate("tools.jackson", "com.example.myplugin.libs.jackson3")
    relocate("com.fasterxml.jackson", "com.example.myplugin.libs.jackson")
    relocate("com.mysql", "com.example.myplugin.libs.mysql")
    relocate("com.google.protobuf", "com.example.myplugin.libs.protobuf")

    relocate("io.lettuce", "com.example.myplugin.libs.lettuce")
    relocate("redis.clients.authentication", "com.example.myplugin.libs.redisAuthx")
    relocate("io.netty", "com.example.myplugin.libs.netty")
    relocate("reactor", "com.example.myplugin.libs.reactor")
    relocate("org.reactivestreams", "com.example.myplugin.libs.reactiveStreams")
}
```

`mergeServiceFiles()` es obligatorio para conservar los proveedores descubiertos mediante `META-INF/services`.

## Verificación

```powershell
.\gradlew.bat dependencies --configuration runtimeClasspath
.\gradlew.bat shadowJar
```

Revisá que el JAR no tenga paquetes privados sin relocalizar, por ejemplo `org/flywaydb/`, `com/mysql/` o `io/lettuce/`.
