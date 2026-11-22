pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "cybergarden-vinity"

include(":domain", ":api", ":data", ":cli", ":server", ":app")
