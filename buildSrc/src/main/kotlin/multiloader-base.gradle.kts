plugins {
    id("architectury-plugin")
    id("dev.architectury.loom")
    id("maven-publish")
    id("base")
    id("java")
    id("java-library")
}

architectury {
    minecraft = rootProject.property("minecraft_version") as String
}

base {
    archivesName = "${rootProject.property("archives_base_name")}${(if(project.name.isEmpty()) "" else "-${project.name.replace("-mojmap", "")}")}"
}

version = "${project.property("mod_version")}+${rootProject.property("minecraft_base_version")}${(if(project.name.contains("mojmap")) "-mojmap" else "")}"
group = rootProject.property("maven_group")!!

loom {
    silentMojangMappingsLicense()
}

repositories {
    maven("https://maven.parchmentmc.org")
    maven("https://maven.wispforest.io/releases")
    maven("https://maven.fabricmc.net/")
    maven("https://maven.architectury.dev/")
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.parchmentmc.org")
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.property("minecraft_version")}")

    if (name == "common-mojmap") {
        mappings(loom.officialMojangMappings())
    } else {
        mappings(
            loom.layered {
                this.officialMojangMappings()
                this.parchment("org.parchmentmc.data:parchment-1.21:2024.07.28@zip")
            }
        )
    }

    implementation("io.wispforest:endec:${rootProject.property("endec_version")}")
    implementation("io.wispforest.endec:gson:${rootProject.property("endec_gson_version")}")
    implementation("io.wispforest.endec:jankson:${rootProject.property("endec_jankson_version")}")
    implementation("io.wispforest.endec:netty:${rootProject.property("endec_netty_version")}")
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
    options.release = 21
}

java {
    withSourcesJar()
}