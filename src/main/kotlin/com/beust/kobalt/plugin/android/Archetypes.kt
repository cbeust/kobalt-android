package com.beust.kobalt.plugin.android

import com.beust.kobalt.Args
import com.beust.kobalt.api.IArchetype
import com.beust.kobalt.api.IInitContributor
import com.beust.kobalt.homeDir
import com.beust.kobalt.misc.log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.jar.JarInputStream

/**
 * Run the Android archetype.
 */
class Archetypes : IInitContributor {
    override val archetypes = arrayListOf(Archetype())

    class Archetype: IArchetype {
        override val archetypeName = "android-java"
        override val archetypeDescription = "Generate a simple Android Java project"
        override val pluginName = AndroidPlugin.PLUGIN_NAME

        companion object {
            val JAR_FILE = "android-java-archetype.jar"

            fun extractFile(ins: JarInputStream, destDir: File) {
                var entry = ins.nextEntry
                while (entry != null) {
                    val f = File(destDir.path + File.separator + entry.name)
                    if (entry.isDirectory) {
                        f.mkdir()
                        entry = ins.nextEntry
                        continue
                    }

                    log(1, "Extracting: $entry to ${f.absolutePath}")
                    FileOutputStream(f).use { fos ->
                        while (ins.available() > 0) {
                            fos.write(ins.read())
                        }
                    }
                    entry = ins.nextEntry
                }
            }

            fun log(level: Int, s: String) {
                println("   " + s)
            }
        }

        override fun generateArchetype(args: Args, classLoader: ClassLoader) {
            log(1, "Generating archetype for Android with class loader $classLoader")
            val destDir = File(".")
            val ins = JarInputStream(classLoader.getResource(JAR_FILE).openConnection().inputStream)
            extractFile(ins, destDir)
        }
    }
}

fun main(argv: Array<String>) {
    val jarFile = homeDir("kotlin", "kobalt-android", "src", "main", "resources",
            Archetypes.Archetype.JAR_FILE)
    Archetypes.Archetype.extractFile(JarInputStream(FileInputStream(jarFile)), File("."))
}
