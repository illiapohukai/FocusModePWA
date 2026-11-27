import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions

internal const val BASE_NAMESPACE = "app.focusmodeplus"
internal val JAVA_TARGET = JavaVersion.VERSION_17

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalStateException("Missing catalog library: $alias") }

internal fun VersionCatalog.int(alias: String): Int =
    findVersion(alias).orElseThrow { IllegalStateException("Missing catalog version: $alias") }
        .requiredVersion.toInt()

/** ":core:data" -> "app.focusmodeplus.core.data" */
internal fun Project.focusNamespace(): String =
    BASE_NAMESPACE + path.replace(':', '.').replace('-', '_')

/** Shared Kotlin compiler policy for every module. */
internal fun Project.configureKotlinCompiler() {
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        val options = compilerOptions
        if (options is KotlinJvmCompilerOptions) {
            options.jvmTarget.set(JvmTarget.JVM_17)
            options.freeCompilerArgs.add("-Xjsr305=strict")
        }
        options.optIn.add("kotlin.time.ExperimentalTime")
    }
}
