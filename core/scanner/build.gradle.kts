plugins {
    alias(libs.plugins.vela.android.library)
    alias(libs.plugins.vela.android.hilt)

}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:catalog"))
    implementation(project(":core:database"))
    implementation(libs.androidx.documentfile)
    testImplementation(libs.robolectric)
}
