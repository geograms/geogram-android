pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven("https://jitpack.io") // Add JitPack here
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") // Add JitPack here as well
        maven("https://dl.cloudsmith.io/public/libp2p/jvm-libp2p/maven/") // libp2p Maven repository
        maven("https://artifacts.consensys.net/public/maven/maven/") // ConsenSys repository for noise-java
    }
}

rootProject.name = "Geogram"
include(":app")
 