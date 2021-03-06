package com.beust.kobalt.internal

import com.beust.kobalt.api.IClasspathContributor
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.maven.KobaltException
import com.google.inject.Inject
import java.io.File

/**
 * Abstract the compilation process by running an ICompilerAction parameterized  by a CompilerActionInfo.
 * Also validates the classpath and run all the contributors.
 */
class JvmCompiler @Inject constructor(val dependencyManager: DependencyManager) {

    /**
     * Create a final, enriched CompilerActionInfo from the contributors and the transitive dependencies and
     * then pass it to the ICompilerAction.
     */
    fun doCompile(project: Project?, context: KobaltContext?, action: ICompilerAction, info: CompilerActionInfo)
            : TaskResult {

        val allDependencies = info.dependencies + calculateDependencies(project, context, info.dependencies)

        validateClasspath(allDependencies.map { it.jarFile.get().absolutePath })
        return action.compile(info.copy(dependencies = allDependencies))
    }

    private fun validateClasspath(cp: List<String>) {
        cp.forEach {
            if (! File(it).exists()) {
                throw KobaltException("Couldn't find $it")
            }
        }
    }

    /**
     * @return the classpath for this project, including the IClasspathContributors.
     */
    fun calculateDependencies(project: Project?, context: KobaltContext?,
            vararg allDependencies: List<IClasspathDependency>): List<IClasspathDependency> {
        var result = arrayListOf<IClasspathDependency>()
        allDependencies.forEach { dependencies ->
            result.addAll(dependencyManager.transitiveClosure(dependencies))
        }
        result.addAll(runClasspathContributors(project, context))

        return result
    }

    private fun runClasspathContributors(project: Project?, context: KobaltContext?) :
            Collection<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        context!!.pluginInfo.classpathContributors.forEach { it: IClasspathContributor ->
            result.addAll(it.entriesFor(project))
        }
        return result
    }

}

data class CompilerActionInfo(val directory: String?, val dependencies: List<IClasspathDependency>,
        val sourceFiles: List<String>, val outputDir: File, val compilerArgs: List<String>)

interface ICompilerAction {
    fun compile(info: CompilerActionInfo): TaskResult
}