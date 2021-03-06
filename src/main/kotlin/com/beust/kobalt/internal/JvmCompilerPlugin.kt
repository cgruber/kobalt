package com.beust.kobalt.internal

import com.beust.kobalt.api.BasePlugin
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.ExportedProperty
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.maven.*
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.log
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
abstract class JvmCompilerPlugin @Inject constructor(
        open val localRepo: LocalRepo,
        open val files: KFiles,
        open val depFactory: DepFactory,
        open val dependencyManager: DependencyManager,
        open val executors: KobaltExecutors,
        open val jvmCompiler: JvmCompiler) : BasePlugin() {

    companion object {
        @ExportedProperty
        const val BUILD_DIR = "buildDir"

        const val TASK_CLEAN = "clean"
        const val TASK_TEST = "test"

        const val SOURCE_SET_MAIN = "main"
        const val SOURCE_SET_TEST = "test"
        const val DOCS_DIRECTORY = "docs/javadoc"
    }

    /**
     * Log with a project.
     */
    protected fun lp(project: Project, s: String) {
        log(2, "${project.name}: $s")
    }

    override fun apply(project: Project, context: KobaltContext) {
        super.apply(project, context)
        context.pluginProperties.put(name, BUILD_DIR, project.buildDirectory + File.separator + "classes")
    }

    /**
     * @return the test dependencies for this project, including the contributors.
     */
    protected fun testDependencies(project: Project) : List<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        result.add(FileDependency(makeOutputDir(project).absolutePath))
        result.add(FileDependency(makeOutputTestDir(project).absolutePath))
        with(project) {
            arrayListOf(compileDependencies, compileProvidedDependencies, testDependencies,
                    testProvidedDependencies).forEach {
                result.addAll(jvmCompiler.calculateDependencies(project, context, it))
            }
        }
        return dependencyManager.reorderDependencies(result)
    }

    @Task(name = TASK_TEST, description = "Run the tests", runAfter = arrayOf("compile", "compileTest"))
    fun taskTest(project: Project) : TaskResult {
        lp(project, "Running tests")
        if (project.testDependencies.any { it.id.contains("testng")} ) {
            TestNgRunner(project, testDependencies(project)).runTests()
        } else {
            JUnitRunner(project, testDependencies(project)).runTests()
        }
        return TaskResult()
    }

    @Task(name = TASK_CLEAN, description = "Clean the project", runBefore = arrayOf("compile"))
    fun taskClean(project : Project ) : TaskResult {
        java.io.File(project.buildDirectory).deleteRecursively()
        return TaskResult()
    }

    protected fun makeOutputDir(project: Project) : File = makeDir(project, KFiles.CLASSES_DIR)

    protected fun makeOutputTestDir(project: Project) : File = makeDir(project, KFiles.TEST_CLASSES_DIR)

    private fun makeDir(project: Project, suffix: String) : File {
        return File(project.directory, project.buildDirectory + File.separator + suffix).apply { mkdirs() }
    }

    /**
     * Copy the resources from a source directory to the build one
     */
    protected fun copyResources(project: Project, sourceSet: String) {
        val sourceDirs: ArrayList<String> = arrayListOf()
        var outputDir: String?
        if (sourceSet == "main") {
            sourceDirs.addAll(project.sourceDirectories.filter { it.contains("resources") })
            outputDir = KFiles.CLASSES_DIR
        } else if (sourceSet == "test") {
            sourceDirs.addAll(project.sourceDirectoriesTest.filter { it.contains("resources") })
            outputDir = KFiles.TEST_CLASSES_DIR
        } else {
            throw IllegalArgumentException("Custom source sets not supported yet: $sourceSet")
        }

        if (sourceDirs.size > 0) {
            lp(project, "Copying $sourceSet resources")
            val absOutputDir = File(KFiles.joinDir(project.directory, project.buildDirectory!!, outputDir))
            sourceDirs.map { File(it) }.filter { it.exists() } .forEach {
                log(2, "Copying from $sourceDirs to $absOutputDir")
                KFiles.copyRecursively(it, absOutputDir)
            }
        } else {
            lp(project, "No resources to copy for $sourceSet")
        }
    }

    protected val compilerArgs = arrayListOf<String>()

    fun addCompilerArgs(vararg args: String) {
        compilerArgs.addAll(args)
    }
}

class TestConfig(val project: Project) {
    fun args(vararg arg: String) {
        project.testArgs.addAll(arg)
    }
}

@Directive
fun Project.test(init: TestConfig.() -> Unit) : TestConfig {
    val result = TestConfig(this)
    result.init()
    return result
}

