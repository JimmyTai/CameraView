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
        maven {
            this.url = uri("https://maven.wysaid.org/")
        }
    }
}

rootProject.name = "CameraView"
include(":example")
include(":camera_view")
