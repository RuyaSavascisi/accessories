pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.parchmentmc.org")
        mavenCentral()
        gradlePluginPortal()
    }
}

include("neoforge-publish")

include("common")
include("fabric")
include("neoforge")

//include("cclayer")
//include("tclayer")

rootProject.name = "accessories"
