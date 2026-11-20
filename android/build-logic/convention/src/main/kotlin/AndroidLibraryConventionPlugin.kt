import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Android library defaults: SDK levels, Java target, unit tests with Android resources, lint. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            namespace = focusNamespace()
            compileSdk = libs.int("compileSdk")
            compileSdkMinor = libs.int("compileSdkMinor")
            defaultConfig {
                minSdk = libs.int("minSdk")
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JAVA_TARGET
                targetCompatibility = JAVA_TARGET
            }
            testOptions {
                unitTests.isIncludeAndroidResources = true
                unitTests.all { test ->
                    test.systemProperty("robolectric.logging", "stderr")
                    test.maxHeapSize = "2g"
                }
            }
            lint {
                abortOnError = true
                checkReleaseBuilds = false
            }
        }
        configureKotlinCompiler()
        dependencies {
            add("testImplementation", libs.lib("junit"))
        }
    }
}
