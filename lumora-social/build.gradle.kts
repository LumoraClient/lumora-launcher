// Lumora Social: protocol, data model and client of the Lumora friends/group chat (launcher and companion mod), plus
// the official Mojang session join used to prove the account to a Lumora Social server and skin-face loading.
// Plain Java 21 without Minecraft classes; bundled into every companion mod like lumora-core.
plugins {
    `java-library`
}

dependencies {
    // like lumora-core: compiled against the oldest Gson shipped by Minecraft (2.2.4), not bundled
    compileOnly(libs.gson.minecraft.min)

    testImplementation(libs.gson)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}
