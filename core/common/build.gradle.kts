plugins {
    alias(libs.plugins.vela.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:model"))
    api(libs.kotlinx.serialization.json)
}
