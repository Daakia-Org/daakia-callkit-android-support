import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
}

/**
 * Backend credentials are read from the gitignored `local.properties` so no secret ever
 * lands in source control. Copy these keys into your own `local.properties`:
 *
 *     DAAKIA_BASE_URL=https://<your-daakia-backend>
 *     DAAKIA_SECRET=<your-customer-secret>
 *
 * They are injected as [BuildConfig] fields (empty when unset) and used only as the initial
 * values; the app's in-app Settings screen can override them at runtime.
 */
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

fun secretProperty(key: String): String = localProperties.getProperty(key).orEmpty()

android {
    namespace = "ai.daakia.callkit.sample"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ai.daakia.callkit.sample"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "DAAKIA_BASE_URL", "\"${secretProperty("DAAKIA_BASE_URL")}\"")
        buildConfigField("String", "DAAKIA_SECRET", "\"${secretProperty("DAAKIA_SECRET")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // ---------------------------------------------------------------------------------------
    // This sample depends on BOTH UI modules. Your app should not.
    //
    // callkit-ui-compose and callkit-ui-views each register an incoming-call Activity, and
    // whichever DaakiaIncomingCallUi.install() runs last wins — so shipping both in a real app
    // means the two compete and the outcome depends on initialisation order. See
    // docs/call-screen-ui.md, "Never add both".
    //
    // This app is a testbed, not an integration template. It carries both so the on-device
    // style picker can preview every preset from either toolkit side by side, which is the one
    // situation where having both is the point. Pick exactly one for your own app:
    //
    //     implementation("ai.daakia:callkit-ui-compose:0.1.0")   // Compose apps
    //     implementation("ai.daakia:callkit-ui-views:0.1.0")     // XML Views apps
    //
    // Either one pulls in callkit-core transitively — never declare it alongside them.
    // ---------------------------------------------------------------------------------------
    implementation(libs.daakia.callkit.ui.compose)
    implementation(libs.daakia.callkit.ui.views)

    implementation(libs.androidx.core.ktx)
    implementation(libs.firebase.messaging)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
