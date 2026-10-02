import java.util.Properties

plugins {
    alias(libs.plugins.vela.android.application)
    alias(libs.plugins.vela.android.compose)
    alias(libs.plugins.vela.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.vela"

    defaultConfig {
        applicationId = "io.vela.frontend"
        // Bump both for every GitHub release: Obtainium updates only when versionCode grows.
        versionCode = 8
        versionName = "0.4.0"
    }

    // Release signing comes from the untracked secrets.properties (see README > Releases).
    // Without it the release APK is left unsigned: never signed with the debug key, which would
    // install fine but break updates for every existing copy.
    val secrets = Properties().apply {
        val file = rootProject.file("secrets.properties")
        if (file.exists()) file.inputStream().use(::load)
    }
    val releaseStore = secrets.getProperty("release.storeFile")?.let(rootProject::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = secrets.getProperty("release.storePassword")
                keyAlias = secrets.getProperty("release.keyAlias")
                keyPassword = secrets.getProperty("release.keyPassword")
            }
        }
    }

    if (releaseStore == null) logger.warn("secrets.properties has no release keystore: release builds will be unsigned")

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // Optimised build that installs over the debug app: same package, same signature, so the
        // library and settings on the device are kept. Not debuggable, so ART runs compiled code
        // instead of the interpreter, and R8 strips and inlines. Use it for day-to-day testing.
        create("perf") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:catalog"))
    implementation(project(":core:database"))
    implementation(project(":core:settings"))
    implementation(project(":core:scanner"))
    implementation(project(":core:launcher"))
    implementation(project(":core:scraper"))
    implementation(project(":core:apps"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:home"))
    implementation(project(":feature:library"))
    implementation(project(":feature:apps"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:setup"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.video)
    implementation(libs.okhttp)
    implementation(libs.timber)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
