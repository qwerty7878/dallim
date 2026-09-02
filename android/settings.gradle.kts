pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 네이버맵 SDK (S-16/S-21 지도 연동, docs/01-feature-spec.md §1.2). module-level
        // repositories 블록은 FAIL_ON_PROJECT_REPOS 때문에 쓸 수 없어 여기 중앙에 추가한다.
        maven(url = "https://repository.map.naver.com/archive/maven")
    }
}

rootProject.name = "dallim-android"

include(":app")
include(":core-network")
include(":core-ui")
