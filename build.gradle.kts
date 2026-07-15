plugins {
    base
    alias(libs.plugins.shadow) apply false
}

tasks.register("integrationTest") {
    group = "verification"
    description = "Runs integration tests that depend on externally managed services."
    dependsOn(":progressengine-paper:integrationTest")
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
}
