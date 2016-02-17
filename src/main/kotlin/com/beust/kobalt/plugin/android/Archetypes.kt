package com.beust.kobalt.plugin.android

import com.beust.kobalt.api.ITemplateContributor
import com.beust.kobalt.api.JarTemplate

/**
 * Run the Android archetype.
 */
class Archetypes : ITemplateContributor {
    class TemplateInfo(val name: String, val description: String, val jarFile: String)

    override val templates = listOf(
            Template(TemplateInfo("android-java",
                    "Generate a simple Android Java project",
                    "android-java-archetype.jar")))

    class Template(val info: TemplateInfo) : JarTemplate("templates/androidJavaTemplate.jar") {
        override val templateName = info.name
        override val templateDescription = info.description
        override val pluginName = AndroidPlugin.PLUGIN_NAME
    }
}

//fun main(argv: Array<String>) {
//    val jarFile = homeDir("kotlin", "kobalt-android", "src", "main", "resources",
//            "android-java-archetype.jar")
//    Archetypes.Template.extractFile(JarInputStream(FileInputStream(jarFile)), File(homeDir("t/arch2")))
//}
