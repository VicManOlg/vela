plugins {
    alias(libs.plugins.vela.android.library)
    alias(libs.plugins.vela.android.hilt)

}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    api(project(":core:catalog"))
    implementation(project(":core:database"))
    implementation(project(":core:settings"))
    implementation(project(":core:scanner"))
    implementation(project(":core:launcher"))
    implementation(project(":core:scraper"))
    implementation(project(":core:apps"))
    implementation(libs.androidx.paging.runtime)
}
