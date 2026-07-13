plugins {
    `java-library`
    `maven-publish`
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<AbstractArchiveTask>().configureEach {
    destinationDirectory = rootProject.layout.projectDirectory.dir("target-api")
}

dependencies {
    compileOnly(libs.paper.api)
}

publishing {
    publications {
        create<MavenPublication>("api") {
            from(components["java"])
            artifactId = "progressengine-api"
        }
    }
}
