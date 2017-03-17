package com.beust.kobalt.plugin.android

import com.beust.kobalt.api.ITemplateContributor
import com.beust.kobalt.api.ResourceJarTemplate

/**
 * Run the Android template.
 */
class Templates : ITemplateContributor {
    class TemplateInfo(val name: String, val description: String, val jarFileName: String)

    override val templates = listOf(
            Template(TemplateInfo("androidJava",
                    "Generate a simple Android Java project",
                    "templates/androidJavaTemplate.jar")),
            Template(TemplateInfo("androidKotlin",
                    "Generate a simple Android Kotlin project",
                    "templates/androidKotlinTemplate.jar")
                    ))

    class Template(info: TemplateInfo) : ResourceJarTemplate(info.jarFileName, Templates::class.java.classLoader) {
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
