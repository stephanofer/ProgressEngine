plugins {
    java
    alias(libs.plugins.shadow)
}

val integrationTestSourceSet = sourceSets.create("integrationTest") {
    java.srcDir("src/integrationTest/java")
    resources.srcDir("src/integrationTest/resources")
    compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

val redisIntegrationTestSourceSet = sourceSets.create("redisIntegrationTest") {
    java.srcDir("src/redisIntegrationTest/java")
    resources.srcDir("src/redisIntegrationTest/resources")
    compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

configurations.named(integrationTestSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.testImplementation.get())
}

configurations.named(integrationTestSourceSet.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.testRuntimeOnly.get())
}

configurations.named(redisIntegrationTestSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.testImplementation.get())
}

configurations.named(redisIntegrationTestSourceSet.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.testRuntimeOnly.get())
}

val validateIntegrationTestEnvironment = tasks.register("validateIntegrationTestEnvironment") {
    group = "verification"
    description = "Validates ProgressEngine external integration test environment variables."

    doLast {
        val names = listOf(
            "PROGRESSENGINE_TEST_DB_HOST",
            "PROGRESSENGINE_TEST_DB_PORT",
            "PROGRESSENGINE_TEST_DB_NAME",
            "PROGRESSENGINE_TEST_DB_USER",
            "PROGRESSENGINE_TEST_DB_PASSWORD"
        )
        val missing = names.filter { System.getenv(it).isNullOrBlank() }
        if (missing.isNotEmpty() && missing.size != names.size) {
            throw GradleException("Missing ProgressEngine integration test variables: ${missing.joinToString()}")
        }
    }
}

val validateRedisIntegrationTestEnvironment = tasks.register("validateRedisIntegrationTestEnvironment") {
    group = "verification"
    description = "Validates ProgressEngine external Redis integration test environment variables."

    doLast {
        val required = listOf(
            "PROGRESSENGINE_TEST_REDIS_HOST",
            "PROGRESSENGINE_TEST_REDIS_PORT"
        )
        val missing = required.filter { System.getenv(it).isNullOrBlank() }
        if (missing.isNotEmpty() && missing.size != required.size) {
            throw GradleException("Missing ProgressEngine Redis integration test variables: ${missing.joinToString()}")
        }
    }
}

dependencies {
    implementation(project(":progressengine-api"))

    compileOnly(libs.paper.api)
    compileOnly(libs.network.player.settings)
    compileOnly(libs.network.boosters.api)
    compileOnly(libs.luckperms.api)
    compileOnly(libs.placeholder.api)

    implementation(libs.craftkit.database)
    implementation(libs.craftkit.redis)
    implementation(libs.boosted.yaml)
    implementation(libs.caffeine)
    implementation(libs.cloud.paper)
    implementation(libs.cloud.minecraft.extras)
    implementation(libs.gson)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.paper.api)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks {
    processResources {
        val pluginVersion = project.version.toString()
        inputs.property("version", pluginVersion)

        filesMatching("paper-plugin.yml") {
            expand("version" to pluginVersion)
        }
    }

    jar {
        enabled = false
    }

    shadowJar {
        archiveClassifier = ""
        destinationDirectory = rootProject.layout.projectDirectory.dir("target")
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles()
        append("META-INF/io.netty.versions.properties")

        relocate("com.hera.craftkit", "com.stephanofer.progressengine.libs.craftkit")
        relocate("com.zaxxer", "com.stephanofer.progressengine.libs.hikari")
        relocate("org.flywaydb", "com.stephanofer.progressengine.libs.flyway")
        relocate("tools.jackson", "com.stephanofer.progressengine.libs.jackson3")
        relocate("com.fasterxml.jackson", "com.stephanofer.progressengine.libs.jackson")
        relocate("com.mysql", "com.stephanofer.progressengine.libs.mysql")
        relocate("com.google.protobuf", "com.stephanofer.progressengine.libs.protobuf")
        relocate("io.lettuce", "com.stephanofer.progressengine.libs.lettuce")
        relocate("redis.clients.authentication", "com.stephanofer.progressengine.libs.redisAuthx")
        relocate("io.netty", "com.stephanofer.progressengine.libs.netty")
        relocate("reactor", "com.stephanofer.progressengine.libs.reactor")
        relocate("org.reactivestreams", "com.stephanofer.progressengine.libs.reactiveStreams")
        relocate("dev.dejvokep.boostedyaml", "com.stephanofer.progressengine.libs.boostedyaml")
        relocate("org.incendo.cloud", "com.stephanofer.progressengine.libs.cloud")
        relocate("io.leangen.geantyref", "com.stephanofer.progressengine.libs.geantyref")
        relocate("com.github.benmanes.caffeine", "com.stephanofer.progressengine.libs.caffeine")
        relocate("com.google.gson", "com.stephanofer.progressengine.libs.gson")
    }

    assemble {
        dependsOn(shadowJar)
    }

    register<Test>("integrationTest") {
        description = "Runs ProgressEngine integration tests against externally managed services."
        group = "verification"
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath
        shouldRunAfter(test)
        dependsOn(validateIntegrationTestEnvironment)

        onlyIf("ProgressEngine external MySQL variables are partially or fully configured") {
            val names = listOf(
                "PROGRESSENGINE_TEST_DB_HOST",
                "PROGRESSENGINE_TEST_DB_PORT",
                "PROGRESSENGINE_TEST_DB_NAME",
                "PROGRESSENGINE_TEST_DB_USER",
                "PROGRESSENGINE_TEST_DB_PASSWORD"
            )
            val missing = names.filter { System.getenv(it).isNullOrBlank() }
            missing.size != names.size
        }

        doFirst {
            val names = listOf(
                "PROGRESSENGINE_TEST_DB_HOST",
                "PROGRESSENGINE_TEST_DB_PORT",
                "PROGRESSENGINE_TEST_DB_NAME",
                "PROGRESSENGINE_TEST_DB_USER",
                "PROGRESSENGINE_TEST_DB_PASSWORD"
            )
            val missing = names.filter { System.getenv(it).isNullOrBlank() }
            if (missing.isNotEmpty()) {
                throw GradleException("Missing ProgressEngine integration test variables: ${missing.joinToString()}")
            }
        }
    }

    register<Test>("redisIntegrationTest") {
        description = "Runs ProgressEngine Redis integration tests against externally managed Redis."
        group = "verification"
        testClassesDirs = redisIntegrationTestSourceSet.output.classesDirs
        classpath = redisIntegrationTestSourceSet.runtimeClasspath
        shouldRunAfter(test)
        dependsOn(validateRedisIntegrationTestEnvironment)

        onlyIf("ProgressEngine external Redis host and port are configured") {
            val required = listOf(
                "PROGRESSENGINE_TEST_REDIS_HOST",
                "PROGRESSENGINE_TEST_REDIS_PORT"
            )
            required.all { !System.getenv(it).isNullOrBlank() }
        }
    }
}
