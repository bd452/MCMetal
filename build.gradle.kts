plugins {
    id("fabric-loom") version "1.15.4"
    id("maven-publish")
}

version = property("modVersion") as String
group = property("mavenGroup") as String

base {
    archivesName.set(property("archivesBaseName") as String)
}

val minecraftVersion: String by project
val yarnMappings: String by project
val loaderVersion: String by project
val fabricApiVersion: String by project
val junitVersion: String by project
val nativeBridgeVersion: String by project

val isMacOs = System.getProperty("os.name").contains("Mac", ignoreCase = true)
val nativeBuildType = providers.gradleProperty("nativeBuildType").getOrElse("Release")
val nativeGenerator = providers.gradleProperty("nativeGenerator").getOrElse("Xcode")
val nativeSourceDir = layout.projectDirectory.dir("native")
val nativeBuildDir = layout.buildDirectory.dir("native")
val nativeBundleDir = layout.buildDirectory.dir("generated/native")
val nativeLibraryFile = nativeBuildDir.map { it.file("libminecraft_metal.dylib") }

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val nativeConfigure by tasks.registering(Exec::class) {
    group = "native"
    description = "Configure CMake project for native Metal bridge."
    onlyIf { isMacOs }
    inputs.dir(nativeSourceDir)
    outputs.dir(nativeBuildDir)
    commandLine(
        "cmake",
        "-S",
        nativeSourceDir.asFile.absolutePath,
        "-B",
        nativeBuildDir.get().asFile.absolutePath,
        "-G",
        nativeGenerator
    )
}

val nativeBuild by tasks.registering(Exec::class) {
    group = "native"
    description = "Build libminecraft_metal.dylib with CMake."
    onlyIf { isMacOs }
    dependsOn(nativeConfigure)
    inputs.dir(nativeSourceDir)
    outputs.file(nativeLibraryFile)
    commandLine(
        "cmake",
        "--build",
        nativeBuildDir.get().asFile.absolutePath,
        "--config",
        nativeBuildType,
        "--target",
        "minecraft_metal"
    )
}

val nativeTest by tasks.registering {
    group = "verification"
    description = "Smoke-test native artifact output."
    onlyIf { isMacOs }
    dependsOn(nativeBuild)
    doLast {
        val artifact = nativeLibraryFile.get().asFile
        check(artifact.exists()) {
            "Expected native library artifact at ${artifact.absolutePath}"
        }
    }
}

val nativePackage by tasks.registering(Copy::class) {
    group = "build"
    description = "Stage native dylib for packaging."
    onlyIf { isMacOs }
    dependsOn(nativeBuild)
    from(nativeLibraryFile)
    into(nativeBundleDir)
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("nativeBridgeVersion", nativeBridgeVersion)

    dependsOn(nativePackage)

    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to project.version,
                "loaderVersion" to loaderVersion,
                "nativeBridgeVersion" to nativeBridgeVersion
            )
        )
    }

    filesMatching("mcmetal-native.properties") {
        expand(
            mapOf(
                "nativeBridgeVersion" to nativeBridgeVersion
            )
        )
    }

    from(nativeBundleDir) {
        into("natives/macos")
    }
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

tasks.assemble {
    dependsOn(nativeBuild)
}

tasks.check {
    dependsOn(nativeTest)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(nativeLibraryFile) {
                classifier = "natives-macos"
                extension = "dylib"
                builtBy(nativeBuild)
            }
        }
    }
}
