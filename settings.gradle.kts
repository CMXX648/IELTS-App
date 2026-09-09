pluginManagement {
    repositories {
        google()
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

rootProject.name = "VoxCoach"

include(":app")
include(":core:domain")
include(":core:speech")
include(":core:llm")
include(":core:data")
include(":core:designsystem")
include(":feature:conversation")
include(":feature:drill")
