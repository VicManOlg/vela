pluginManagement {
    includeBuild("build-logic")
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
    }
}

rootProject.name = "Vela"

include(":app")
include(":core:model")
include(":core:common")
include(":core:catalog")
include(":core:database")
include(":core:settings")
include(":core:scanner")
include(":core:launcher")
include(":core:scraper")
include(":core:apps")
include(":core:data")
include(":core:ui")
include(":feature:home")
include(":feature:library")
include(":feature:apps")
include(":feature:search")
include(":feature:settings")
include(":feature:setup")
