plugins {
    `java-library`
    `maven-publish`
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    destinationDirectory = rootProject.layout.projectDirectory.dir("target-api")
}

dependencies {
    compileOnly(libs.paper.api)

    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

publishing {
    publications {
        create<MavenPublication>("api") {
            from(components["java"])
            artifactId = "progressengine-api"
        }
    }
}
