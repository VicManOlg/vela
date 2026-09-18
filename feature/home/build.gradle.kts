plugins {
    alias(libs.plugins.vela.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:settings"))
    implementation(libs.androidx.paging.compose)
}
