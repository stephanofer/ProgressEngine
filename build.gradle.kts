plugins {
    base
    alias(libs.plugins.shadow) apply false
}

tasks.register("integrationTest") {
    group = "verification"
    description = "Runs integration tests that depend on externally managed services."
    dependsOn(":progressengine-paper:integrationTest", ":progressengine-paper:redisIntegrationTest")
}

tasks.register("validateReleaseEnvironment") {
    group = "verification"
    description = "Fails when the external services required for ProgressEngine release verification are not configured."

    doLast {
        val required = listOf(
            "PROGRESSENGINE_TEST_DB_HOST",
            "PROGRESSENGINE_TEST_DB_PORT",
            "PROGRESSENGINE_TEST_DB_NAME",
            "PROGRESSENGINE_TEST_DB_USER",
            "PROGRESSENGINE_TEST_DB_PASSWORD",
            "PROGRESSENGINE_TEST_REDIS_HOST",
            "PROGRESSENGINE_TEST_REDIS_PORT",
            "PROGRESSENGINE_PAPER_SMOKE_EVIDENCE"
        )
        val missing = required.filter { System.getenv(it).isNullOrBlank() }
        if (missing.isNotEmpty()) {
            throw GradleException("Release verification requires external MySQL and Redis variables: ${missing.joinToString()}")
        }
    }
}

tasks.register("validatePaperSmokeEvidence") {
    group = "verification"
    description = "Validates that the real Paper smoke test evidence was produced before release closure."

    doLast {
        val evidencePath = System.getenv("PROGRESSENGINE_PAPER_SMOKE_EVIDENCE")
        if (evidencePath.isNullOrBlank()) {
            throw GradleException("PROGRESSENGINE_PAPER_SMOKE_EVIDENCE must point to the Paper smoke evidence file")
        }
        val evidenceFile = java.io.File(evidencePath)
        if (!evidenceFile.isFile) {
            throw GradleException("Paper smoke evidence file does not exist: $evidencePath")
        }
        val text = evidenceFile.readText()
        val requiredMarkers = listOf(
            "ProgressEngine Paper smoke: PASS",
            "profile=complete PASS",
            "profile=without-optionals PASS",
            "profile=degraded-redis PASS",
            "profile=mysql-down PASS",
            "profile=required-dependency-missing PASS"
        )
        val missingMarkers = requiredMarkers.filterNot(text::contains)
        if (missingMarkers.isNotEmpty()) {
            throw GradleException("Paper smoke evidence is incomplete. Missing markers: ${missingMarkers.joinToString()}")
        }
    }
}

tasks.register("releaseStaticAudit") {
    group = "verification"
    description = "Runs lightweight source audits for ProgressEngine 1.0 release scope."

    val sourceRoots = files("progressengine-api", "progressengine-paper")
    val rootDirectory = rootDir
    inputs.files(sourceRoots.asFileTree.matching {
        include("**/*.java", "**/*.kt", "**/*.kts", "**/*.gradle", "**/*.gradle.kts")
        exclude("**/build/**")
    })

    doLast {
        val forbidden = listOf(
            Regex("org\\.testcontainers|testcontainers", RegexOption.IGNORE_CASE) to "Testcontainers is forbidden",
            Regex("leaderboard|leaderboards", RegexOption.IGNORE_CASE) to "Leaderboards are out of ProgressEngine 1.0 scope",
            Regex("write[-_ ]?behind", RegexOption.IGNORE_CASE) to "Write-behind is forbidden for economic mutations"
        )
        val findings = mutableListOf<String>()
        sourceRoots.asFileTree.matching {
            include("**/*.java", "**/*.kt", "**/*.kts", "**/*.gradle", "**/*.gradle.kts")
            exclude("**/build/**")
        }.files.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                forbidden.forEach { (pattern, message) ->
                    if (pattern.containsMatchIn(line)) {
                        findings += "${file.relativeTo(rootDirectory).invariantSeparatorsPath}:${index + 1}: $message"
                    }
                }
            }
        }
        if (findings.isNotEmpty()) {
            throw GradleException("Release static audit failed:\n" + findings.joinToString("\n"))
        }
    }
}

tasks.register("verifyReleaseArtifacts") {
    group = "verification"
    description = "Verifies ProgressEngine release artifacts and shaded package boundaries."
    dependsOn("build")
    val targetDirectory = layout.projectDirectory.dir("target").asFile
    val targetApiDirectory = layout.projectDirectory.dir("target-api").asFile
    val releaseVersion = version.toString()

    doLast {
        val pluginJars = targetDirectory.listFiles { file ->
            file.isFile && file.extension == "jar"
        }?.toList().orEmpty()
        if (pluginJars.size != 1) {
            throw GradleException("Expected exactly one shaded plugin JAR in target/, found ${pluginJars.size}")
        }

        val apiArtifacts = targetApiDirectory.listFiles { file ->
            file.isFile && file.extension == "jar"
        }?.map { it.name }.orEmpty()
        val requiredApiArtifacts = listOf(
            "progressengine-api-$releaseVersion.jar",
            "progressengine-api-$releaseVersion-sources.jar",
            "progressengine-api-$releaseVersion-javadoc.jar"
        )
        val missingApiArtifacts = requiredApiArtifacts.filterNot(apiArtifacts::contains)
        if (missingApiArtifacts.isNotEmpty()) {
            throw GradleException("Missing API artifacts in target-api/: ${missingApiArtifacts.joinToString()}")
        }

        val shadedJar = pluginJars.single()
        java.util.zip.ZipFile(shadedJar).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            val requiredEntries = listOf(
                "paper-plugin.yml",
                "config.yml",
                "commands.yml",
                "identity.yml",
                "messages/en.yml",
                "messages/es.yml",
                "db/migration/V1__create_progress_accounts.sql"
            )
            val missingEntries = requiredEntries.filterNot(entries::contains)
            if (missingEntries.isNotEmpty()) {
                throw GradleException("Missing required plugin resources: ${missingEntries.joinToString()}")
            }
            val forbiddenPackages = listOf(
                "com/hera/craftkit/",
                "com/zaxxer/",
                "org/flywaydb/",
                "io/lettuce/",
                "io/netty/",
                "org/incendo/cloud/",
                "com/github/benmanes/caffeine/",
                "dev/dejvokep/boostedyaml/",
                "com/google/gson/"
            )
            val leaked = entries.filter { entry -> forbiddenPackages.any(entry::startsWith) }
            if (leaked.isNotEmpty()) {
                throw GradleException("Shaded plugin leaks unrelocated packages, first entries: ${leaked.take(10).joinToString()}")
            }
        }
    }
}

tasks.register("releaseVerification") {
    group = "verification"
    description = "Runs the automated ProgressEngine 1.0 verification gate before manual real-environment validation."
    dependsOn(
        "clean",
        "releaseStaticAudit",
        ":progressengine-api:test",
        ":progressengine-paper:test",
        ":progressengine-paper:compileIntegrationTestJava",
        ":progressengine-paper:compileRedisIntegrationTestJava",
        "verifyReleaseArtifacts",
        "build"
    )
}

allprojects {
    group = providers.gradleProperty("group").get()
    version = providers.gradleProperty("version").get()

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.extendedclip.com/releases/")
    }

    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.testcontainers") {
                throw GradleException("Testcontainers is forbidden in ProgressEngine")
            }
        }
    }
}

subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion = JavaLanguageVersion.of(25)
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release = 25
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}

tasks.build {
    dependsOn(subprojects.map { it.tasks.named("build") })
    mustRunAfter(tasks.clean)
}

tasks.clean {
    delete(layout.projectDirectory.dir("target"))
    delete(layout.projectDirectory.dir("target-api"))
}

subprojects {
    tasks.matching { it.name != "clean" }.configureEach {
        mustRunAfter(rootProject.tasks.named("clean"))
    }
}
