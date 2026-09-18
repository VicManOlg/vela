import java.util.Properties

plugins {
    alias(libs.plugins.vela.android.library)
    alias(libs.plugins.vela.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Developer credentials for providers that require them live in an untracked secrets.properties
// at the repository root (see README). Missing values simply disable that provider.
val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    buildFeatures.buildConfig = true
    defaultConfig {
        buildConfigField("String", "SCREENSCRAPER_DEV_ID", "\"${secrets.getProperty("screenscraper.devid", "")}\"")
        buildConfigField("String", "SCREENSCRAPER_DEV_PASSWORD", "\"${secrets.getProperty("screenscraper.devpassword", "")}\"")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:catalog"))
    implementation(project(":core:database"))
    implementation(project(":core:settings"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
}
