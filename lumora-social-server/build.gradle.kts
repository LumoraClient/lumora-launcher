// Lumora Social server: friends, groups and chat for Lumora clients. A single runnable jar
// (build/libs/lumora-social-server.jar, Gson, the PostgreSQL JDBC driver and lumora-social included). See docs/SOCIAL.md.
plugins {
    java
}

dependencies {
    implementation(project(":lumora-social"))
    implementation(libs.gson)
    implementation("org.postgresql:postgresql:42.7.7")
    // real PostgreSQL in the tests (binaries from Maven Central, started in a temporary folder)
    testImplementation("io.zonky.test:embedded-postgres:2.1.0")
    testImplementation(enforcedPlatform("io.zonky.test.postgres:embedded-postgres-binaries-bom:17.5.0"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.jar {
    archiveFileName.set("lumora-social-server.jar")
    manifest {
        attributes("Main-Class" to "dev.lumora.social.server.Main", "Implementation-Version" to project.version)
    }
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.map { cp -> cp.filter { it.name.endsWith(".jar") }.map { zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF", "module-info.class",
                "META-INF/versions/*/module-info.class")
    }
    // license of the bundled PostgreSQL JDBC driver (BSD-2-Clause), next to the ones of its own dependencies
    from(rootProject.file("packaging/licenses/BSD-2-Clause-pgjdbc.txt")) {
        into("META-INF")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
