plugins {
    alias(libs.plugins.vela.android.library)
    alias(libs.plugins.vela.android.hilt)
    alias(libs.plugins.vela.android.room)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.androidx.paging.runtime)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
}
