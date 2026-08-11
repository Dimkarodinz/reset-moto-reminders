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

val privateResearchProjects = mapOf(
    ":triumphResearch" to "../research-builds/android/triumph",
    ":generalResearch" to "../research-builds/android/general",
)
privateResearchProjects.forEach { (name, path) ->
    val directory = file(path)
    if (directory.isDirectory) {
        include(name)
        project(name).projectDir = directory
    }
}
