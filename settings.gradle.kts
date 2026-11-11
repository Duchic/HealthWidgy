// Maven Central is used through Google's official mirror: the network this project is built on
// blocks repo1.maven.org behind an authenticating proxy, while the mirror is reachable.
val mavenCentralMirror = "https://maven-central-eu.storage-download.googleapis.com/maven2/"

pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("androidx.*")
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
      }
    }
    maven("https://maven-central-eu.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google {
      content {
        includeGroupByRegex("androidx.*")
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
      }
    }
    maven(mavenCentralMirror) { name = "MavenCentralMirror" }
  }
}

rootProject.name = "HealthWidgy"

include(":app")
