plugins {
    alias(libs.plugins.vela.android.library)
    alias(libs.plugins.vela.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    api(project(":core:catalog"))
    implementation(project(":core:database"))
    api(project(":core:settings"))
    implementation(project(":core:scanner"))
    api(project(":core:launcher"))
    api(project(":core:scraper"))
    implementation(project(":core:apps"))
    implementation(libs.androidx.paging.runtime)
    implementation(libs.kotlinx.serialization.json)
}
