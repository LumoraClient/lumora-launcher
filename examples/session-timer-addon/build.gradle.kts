// Example Lumora addon that only uses the public API (no Minecraft or Fabric classes), so the same jar works on
// every Minecraft version Lumora supports. Install: copy build/libs/lumora-session-timer-<version>.jar to mods/.
plugins {
    `java-library`
}

version = "1.0.0"

dependencies {
    // provided at runtime by the Lumora client mod
    compileOnly(project(":lumora-api"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.processResources {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching(listOf("fabric.mod.json", "lumora-addon.json")) {
        expand("version" to v)
    }
}

tasks.jar {
    archiveFileName.set("lumora-session-timer-${project.version}.jar")
    from(rootProject.file("LICENSE")) { rename { "LICENSE_lumora" } }
}
