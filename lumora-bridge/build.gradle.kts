// Bridge that runs the Lumora Client inside a mod loader: fills the Lumora Loader API and starts the client entry points.
// Its classes are copied into the loader variants of every client jar (lumora-client-mc<version>-fabric.jar for
// Fabric/Quilt, -forge.jar for Forge/NeoForge). Fabric Loader is a compile-time dependency only; Forge's and
// NeoForge's @Mod annotations come from tiny stand-ins in the "stubs" source set, which are never packaged.
plugins {
    `java-library`
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") {
        name = "Fabric"
        content { includeGroup("net.fabricmc") }
    }
}

val stubs: SourceSet = sourceSets.create("stubs")

dependencies {
    compileOnly("net.fabricmc:fabric-loader:${libs.versions.mc26.fabric.loader.get()}")
    compileOnly(project(":lumora-loader"))
    compileOnly(stubs.output)

    testImplementation(project(":lumora-loader"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    // runs in every instance Lumora supports with a mod loader (they start on Java 21 or newer)
    options.release.set(21)
}
