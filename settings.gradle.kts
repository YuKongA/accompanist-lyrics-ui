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
        maven {
            url = uri("file:///D:/Android/Maven")
            mavenContent {
                includeGroupAndSubgroups("com.mocharealm")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "lyrics-ui"
include(":src")
include(":sample")
 