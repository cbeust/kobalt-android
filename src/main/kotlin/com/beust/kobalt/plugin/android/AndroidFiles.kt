package com.beust.kobalt.plugin.android

import com.beust.kobalt.Variant
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.MavenId
import com.beust.kobalt.misc.KFiles

class AndroidFiles {
    companion object {
        fun intermediates(project: Project) = KFiles.joinDir(project.directory, KFiles.KOBALT_BUILD_DIR,
                "intermediates", "classes")

        fun assets(project: Project) = KFiles.joinDir(intermediates(project), "assets")

        fun manifest(project: Project) =
                KFiles.joinDir(project.directory, "src", "main", "AndroidManifest.xml")

        fun mergedManifest(project: Project, variant: Variant) : String {
            val dir = KFiles.joinAndMakeDir(intermediates(project), "manifests", "full", variant.toIntermediateDir())
            return KFiles.joinDir(dir, "AndroidManifest.xml")
        }

        fun generatedSource(project: Project, context: KobaltContext) = KFiles.joinAndMakeDir(project.directory,
                project.buildDirectory, "generated", "source", "aidl", context.variant.toIntermediateDir())

        fun mergedResourcesNoVariant(project: Project) =
                KFiles.joinAndMakeDir(intermediates(project), "res", "merged")

        fun mergedResources(project: Project, variant: Variant) =
                KFiles.joinAndMakeDir(mergedResourcesNoVariant(project), variant.toIntermediateDir())

        fun exploded(project: Project, mavenId: MavenId) = KFiles.joinAndMakeDir(
                intermediates(project), "exploded-aar", mavenId.groupId, mavenId.artifactId, mavenId.version!!)

        fun explodedManifest(project: Project, mavenId: MavenId) =
                KFiles.joinDir(exploded(project, mavenId), "AndroidManifest.xml")

        fun aarClassesJar(dir: String) = KFiles.joinDir(dir, "classes.jar")

        fun apk(project: Project, flavor: String)
                = KFiles.joinFileAndMakeDir(project.directory, project.buildDirectory, "outputs", "apk",
                        "${project.name}$flavor.apk")

        fun explodedClassesJar(project: Project, mavenId: MavenId) = aarClassesJar(
                KFiles.joinDir(exploded(project, mavenId)))

        fun temporaryApk(project: Project, flavor: String)
                = KFiles.joinFileAndMakeDir(AndroidFiles.intermediates(project), "res", "resources$flavor.ap_")

        /** The R.txt directory */
        val rTxtDir = KFiles.joinDir(KFiles.KOBALT_BUILD_DIR, "symbols")

        val rTxtName = "R.txt"

        fun preDexed(project: Project, variant: Variant) =
                KFiles.joinAndMakeDir(intermediates(project), "pre-dexed", variant.toIntermediateDir())

        fun intermediatesClasses(project: Project, context: KobaltContext)
                = KFiles.joinAndMakeDir(intermediates(project), "classes", context.variant.toIntermediateDir())
    }
}
