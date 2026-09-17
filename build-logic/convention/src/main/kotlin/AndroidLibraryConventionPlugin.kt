import com.android.build.api.dsl.LibraryExtension
import io.vela.buildlogic.configureKotlinAndroid
import io.vela.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                // Derive a stable, unique namespace from the module path: core:database -> io.vela.core.database
                namespace = "io.vela" + path.replace(':', '.').replace('-', '_')
                defaultConfig.consumerProguardFiles("consumer-rules.pro")
                testOptions.unitTests.isIncludeAndroidResources = true
            }

            dependencies {
                add("implementation", libs.findLibrary("kotlinx-coroutines-core").get())
                add("implementation", libs.findLibrary("timber").get())
                add("testImplementation", libs.findLibrary("junit").get())
                add("testImplementation", libs.findLibrary("truth").get())
                add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
            }
        }
    }
}
