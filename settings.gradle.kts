pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // one jar per entry; 26.1.2 also covers 26.1 and 26.1.1 (see stonecutter.properties.toml)
        versions("26.1.2", "26.2", "26.3")
        vcsVersion = "26.3"
    }
}

rootProject.name = "immersive-portals"
