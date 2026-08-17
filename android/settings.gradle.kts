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

rootProject.name = "ResetMotoReminders"
include(":app")
include(":triumphResearch")
project(":triumphResearch").projectDir = file("../research-builds/android/triumph")
include(":generalResearch")
project(":generalResearch").projectDir = file("../research-builds/android/general")
