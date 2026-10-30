// Lumora Social server: friends, groups and chat for Lumora clients. A single runnable jar
// (build/libs/lumora-social-server.jar, Gson and lumora-social included). See docs/SOCIAL.md.
plugins {
    java
}

dependencies {
    implementation(project(":lumora-social"))
    implementation(libs.gson)

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
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
