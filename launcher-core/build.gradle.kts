plugins {
    `java-library`
}

dependencies {
    api(libs.gson)
    api(project(":lumora-social"))
    implementation(libs.jna.platform)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform { excludeTags("integration") }
}

/** Downloads the real pinned mods and checks their metadata together. Needs network. */
val integrationTest = tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Network integration test with the real Sodium/ViaFabricPlus/extension artifacts of every version"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    // production jars of every companion mod build (remapped for the obfuscated versions)
    @Suppress("UNCHECKED_CAST")
    val modJars = files((gradle.extra["lumoraClientVersions"] as List<String>).map { v ->
        project(":client-mc$v").tasks.named<AbstractArchiveTask>(if (v.startsWith("1.")) "remapJar" else "jar").flatMap { it.archiveFile }
    })
    dependsOn(modJars)
    val cache = layout.buildDirectory.dir("it-cache")
    doFirst {
        systemProperty("lumora.modJars", modJars.files.joinToString(File.pathSeparator) { it.absolutePath })
        systemProperty("lumora.itCache", cache.get().asFile.absolutePath)
    }
    outputs.upToDateWhen { false }
}

tasks.processResources {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching("lumora-build.properties") {
        expand("version" to v)
    }
}

tasks.jar {
    from(rootProject.file("LICENSE")) { rename { "LICENSE_lumora" } }
}
