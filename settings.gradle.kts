pluginManagement {
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Google-hosted mirror of Maven Central (identical artifacts).
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
    }
}
rootProject.name = "SmartManagerPersonalAI"
include(":app", ":core", ":license-core", ":license-generator")
