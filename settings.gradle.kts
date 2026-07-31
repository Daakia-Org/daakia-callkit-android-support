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
        mavenCentral()
        // Deliberately no mavenLocal(). The SDK is consumed from Maven Central like any other
        // dependency — that is the whole point of this sample. A stale ~/.m2 copy would make the
        // build pass against artifacts that are not the published ones.
    }
}

rootProject.name = "daakia-callkit-sample"
include(":sample")

// --- Daakia-internal only -----------------------------------------------------------------
//
// Nothing below affects you if you are reading this as a Daakia CallKit user: without the
// property, the sample resolves ai.daakia:* from Maven Central exactly as your own app would.
//
// Internally, `-Pdaakia.useLocalSdk=true` builds the sample against a checkout of the (private)
// SDK repository sitting next to this one, so an API break fails here before anything is
// published. Maven Central versions are permanent, so catching it now is the entire point.
//
// The explicit dependencySubstitution block is REQUIRED, not decorative. The SDK's publishing
// plugin applies its GROUP/VERSION_NAME to the published coordinates only — its Gradle projects
// still report `group=daakia-callkit-android, version=unspecified`. Gradle therefore has no
// `ai.daakia:*` to match, and a bare includeBuild(...) silently resolves from Maven Central
// while looking like it worked. Verified 30 Jul 2026.
val sdkRepo = file("../daakia-callkit-android")
if (providers.gradleProperty("daakia.useLocalSdk").orNull == "true") {
    require(sdkRepo.isDirectory) {
        "daakia.useLocalSdk=true but the SDK repo is not checked out at $sdkRepo"
    }
    includeBuild(sdkRepo) {
        dependencySubstitution {
            substitute(module("ai.daakia:callkit-core")).using(project(":callkit-core"))
            substitute(module("ai.daakia:callkit-ui-compose")).using(project(":callkit-ui-compose"))
            substitute(module("ai.daakia:callkit-ui-views")).using(project(":callkit-ui-views"))
        }
    }
}
