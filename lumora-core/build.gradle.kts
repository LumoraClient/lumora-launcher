// Minecraft-independent implementation of the Lumora API: event bus, addon manager, widget registry,
// HUD layout persistence, editor logic, keybinds, tasks and notifications. The version adapters in
// client/mc<version> connect it to Minecraft through the Platform interface.
plugins {
    `java-library`
}

dependencies {
    api(project(":lumora-api"))
    api(project(":lumora-social"))
    // Minecraft ships Gson on every supported version; the companion mod does not bundle it. Compiled against the
    // oldest one (Gson 2.2.4 in Minecraft 1.8.9 / 1.12.2); the tests run with the current Gson.
    compileOnly(libs.gson.minecraft.min)

    testImplementation(libs.gson)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}
