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
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // FFmpeg-Kit repository
        maven { url = uri("https://jitpack.io") }
    }
    // libs.versions.toml is auto-discovered by Gradle from gradle/libs.versions.toml
}

rootProject.name = "ViMal"
include(":app")
include(":core:domain")
include(":core:data")
include(":core:ui")
include(":feature:normalizer")
