plugins {
    alias(libs.plugins.vela.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.activity.compose)
}
