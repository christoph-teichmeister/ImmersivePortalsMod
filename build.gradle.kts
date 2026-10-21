plugins {
    id("net.fabricmc.fabric-loom")
}

version = "${property("mod.version")}+${sc.current.version}"
group = "qouteall"
base.archivesName = "immersive-portals"

repositories {
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    implementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    api("maven.modrinth:cloth-config:${property("deps.cloth_config")}")
    include("maven.modrinth:cloth-config:${property("deps.cloth_config")}")

    compileOnly("maven.modrinth:modmenu:${property("deps.modmenu")}")
    localRuntime("maven.modrinth:modmenu:${property("deps.modmenu")}")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// re-enabled with the Sodium/Iris compat port
// (IrisInterface/SodiumInterface stay compiled as no-op bridges)
sourceSets.main {
    java {
        exclude {
            val p = it.relativePath.pathString
            (p.startsWith("qouteall/imm_ptl/core/compat/iris_compatibility/") && !p.endsWith("/IrisInterface.java")) ||
                (p.startsWith("qouteall/imm_ptl/core/compat/sodium_compatibility/") && !p.endsWith("/SodiumInterface.java")) ||
                p.startsWith("qouteall/imm_ptl/core/compat/mixin/iris/") ||
                p.startsWith("qouteall/imm_ptl/core/compat/mixin/sodium/")
        }
    }
}

loom {
    accessWidenerPath = rootProject.file("src/main/resources/imm_ptl.accesswidener")

    runConfigs.all {
        runDirectory = rootProject.file("run") // shared between versions
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xmaxerrs", "10000")) // show every error while porting
    }

    processResources {
        val props = mapOf(
            "version" to project.version.toString(),
            "minecraft" to project.property("mod.mc_compat").toString(),
            "fabric_api" to project.property("deps.fabric_api").toString(),
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }

    test {
        useJUnitPlatform()
    }

    jar {
        from(rootProject.file("LICENSE")) { rename { "${it}_immersive-portals" } }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the jar for this version and copies it to build/libs/<mod version>/"
        from(jar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }
}
