plugins {
    alias(libs.plugins.vela.android.library)
    alias(libs.plugins.vela.android.hilt)
    alias(libs.plugins.vela.android.room)
}

// MigrationTestHelper reads the exported schemas as assets; Robolectric unit tests need them too.
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.androidx.paging.runtime)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
}
