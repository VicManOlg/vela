import io.vela.buildlogic.libs
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.dependencies

/**
 * A feature module: Android library + Compose + Hilt with the shared UI/data modules wired in.
 * Features never depend on each other; they communicate through navigation callbacks.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    private companion object {
        val ALLOWED = setOf(":core:model", ":core:common", ":core:data", ":core:ui")
    }

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("vela.android.library")
            pluginManager.apply("vela.android.compose")
            pluginManager.apply("vela.android.hilt")

            dependencies {
                add("implementation", project(":core:model"))
                add("implementation", project(":core:common"))
                add("implementation", project(":core:data"))
                add("implementation", project(":core:ui"))

                add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
                add("implementation", libs.findLibrary("hilt-navigation-compose").get())
                add("implementation", libs.findLibrary("androidx-navigation-compose").get())
                add("implementation", libs.findLibrary("kotlinx-serialization-json").get())
                add("implementation", libs.findLibrary("coil-compose").get())
            }

            // The module rule (CLAUDE.md), enforced: anything else is reached through core:data.
            val feature = path
            configurations.configureEach {
                val configuration = name
                dependencies.withType(ProjectDependency::class.java).configureEach {
                    // AGP puts the module itself on its androidTest classpath.
                    if (path !in ALLOWED && path != feature) {
                        throw GradleException("$feature may only depend on ${ALLOWED.joinToString()}; '$configuration' adds $path. Go through core:data.")
                    }
                }
            }
        }
    }
}
