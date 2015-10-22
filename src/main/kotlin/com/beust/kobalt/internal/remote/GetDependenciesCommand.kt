package com.beust.kobalt.internal.remote

import com.beust.kobalt.Args
import com.beust.kobalt.kotlin.BuildFile
import com.beust.kobalt.kotlin.BuildFileCompiler
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.maven.MavenDependency
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.inject.Inject
import java.nio.file.Paths

class GetDependenciesCommand @Inject constructor(val executors: KobaltExecutors,
        val buildFileCompilerFactory: BuildFileCompiler.IFactory, val args: Args) : ICommand {
    override val name = "getDependencies"
    override fun run(sender: ICommandSender, received: JsonObject) {
        val buildFile = BuildFile(Paths.get(received.get("buildFile").asString), "GetDependenciesCommand")
        val scriptCompiler = buildFileCompilerFactory.create(listOf(buildFile))
        scriptCompiler.observable.subscribe {
            buildScriptInfo -> if (buildScriptInfo.projects.size() > 0) {
                sender.sendData(toJson(buildScriptInfo))
            }
        }
        scriptCompiler.compileBuildFiles(args)
    }

    private fun toJson(info: BuildFileCompiler.BuildScriptInfo) : String {
        val executor = executors.miscExecutor
        val projects = arrayListOf<ProjectData>()

        fun toDependencyData(d: IClasspathDependency, scope: String) : DependencyData {
            val dep = MavenDependency.create(d.id, executor)
            return DependencyData(d.id, scope, dep.jarFile.get().absolutePath)
        }

        info.projects.forEach { project ->
            val allDependencies =
                    project.compileDependencies.map { toDependencyData(it, "compile") } +
                            project.compileProvidedDependencies.map { toDependencyData(it, "provided") } +
                            project.compileRuntimeDependencies.map { toDependencyData(it, "runtime") } +
                            project.testDependencies.map { toDependencyData(it, "testCompile") } +
                            project.testProvidedDependencies.map { toDependencyData(it, "testProvided") }

            projects.add(ProjectData(project.name!!, allDependencies))
        }
        log(1, "Returning BuildScriptInfo")
        val result = Gson().toJson(GetDependenciesData(projects).toData())
        log(2, "  $result")
        return result
    }
}

class DependencyData(val id: String, val scope: String, val path: String)

class ProjectData( val name: String, val dependencies: List<DependencyData>)

class GetDependenciesData(val projects: List<ProjectData>) {
    fun toData() : CommandData {
        val data = Gson().toJson(this)
        return CommandData("getDependencies", data)
    }
}

