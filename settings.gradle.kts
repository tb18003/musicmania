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

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "MusicMania"

include(":app")
include(":baselineprofile")

include(":core:common")
include(":core:model")
include(":core:domain")
include(":core:data")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:auth")
include(":core:designsystem")
include(":core:ui")
include(":core:testing")

include(":sync:work")

include(":feature:auth")
include(":feature:home")
include(":feature:top")
include(":feature:history")
include(":feature:detail")
include(":feature:insights")
include(":feature:recap")
include(":feature:importer")
include(":feature:settings")

include(":widget")
