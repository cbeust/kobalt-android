package com.beust.kobalt.plugin.android

import com.beust.kobalt.IFileSpec
import com.beust.kobalt.JarGenerator
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.archive.Archives
import com.beust.kobalt.archive.Jar
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.From
import com.beust.kobalt.misc.IncludedFile
import com.beust.kobalt.misc.To
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import java.io.File

/**
 * Generates a .aar file based on http://tools.android.com/tech-docs/new-build-system/aar-format
 */
class AarGenerator @Inject constructor(val jarGenerator: JarGenerator, val dependencyManager: DependencyManager) {
    fun generate(project: Project, context: KobaltContext, config: AarConfig) {
        println("Creating aar for " + config)

        val variant = context.variant
        val archiveName = config.name ?: "${project.name}-${project.version}.aar"

        val includedFiles = arrayListOf<IncludedFile>()

        // AndroidManifest.xml
        val manifest = AndroidFiles.mergedManifest(project, variant)
        includedFiles.add(IncludedFile(From(File(manifest).parent), To(""),
                listOf(IFileSpec.FileSpec("AndroidManifest.xml"))))

        // classes.jar
        val jar = Jar("classes.jar", fatJar = false).apply {
            include(From(AndroidFiles.intermediates(project)), To(""), IFileSpec.GlobSpec("**/*.class"))
        }
        val file = jarGenerator.generateJar(project, context, jar)
        includedFiles.add(IncludedFile(From(file.parentFile.path), To(""),
                listOf(IFileSpec.FileSpec(file.name))))

        // res
        includedFiles.add(IncludedFile(From(AndroidFiles.mergedResources(project, variant)), To("res"),
                listOf(IFileSpec.GlobSpec("**/*"))))

        // R.txt
        includedFiles.add(IncludedFile(From(AndroidFiles.rTxtDir), To(""),
                listOf(IFileSpec.FileSpec(AndroidFiles.rTxtName))))

        // assets
        includedFiles.add(IncludedFile(From(AndroidFiles.assets(project)), To("assets"),
                listOf(IFileSpec.GlobSpec("**/*"))))

        // libs/*.jar
        val dependencies = dependencyManager.transitiveClosure(project.compileDependencies)
        dependencies.map { it.jarFile.get() }.filter {
                it.name.endsWith(".jar") && it.name != "classes.jar" && it.name != "android.jar"
            }.forEach { jar ->
                includedFiles.add(IncludedFile(From(jar.parentFile.path), To("libs"),
                        listOf(IFileSpec.FileSpec(jar.name))))
            }

        // jni
        includedFiles.add(IncludedFile(From("jni"), To("jni"), listOf(IFileSpec.GlobSpec("**/*"))))

        // proguard.txt, lint.jar
        includedFiles.add(IncludedFile(listOf("proguard.txt", "lint.jar").filter {
                File(it).exists()
            }.map {
                IFileSpec.FileSpec(it)
            }))

            val aar = Archives.generateArchive(project, context, archiveName, ".aar", includedFiles)
            log(1, "Generated " + aar.path)
    }
}
