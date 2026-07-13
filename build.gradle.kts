plugins {
    base
    alias(libs.plugins.shadow) apply false
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
    }
}

tasks.build {
    dependsOn(subprojects.map { it.tasks.named("build") })
}
