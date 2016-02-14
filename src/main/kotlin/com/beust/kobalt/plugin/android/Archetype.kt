package com.beust.kobalt.plugin.android

import com.beust.kobalt.file
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarInputStream

/**
 * Run the Android archetype.
 */
class Archetype {
    companion object {
        val JAR_FILE = "android-basic-project.jar"
    }

    fun run() {
        val ins = javaClass.classLoader.getResource(JAR_FILE).openConnection().inputStream
        extractJarFile(JarInputStream(ins), File("."))
    }

    private fun extractJarFile(jarFile: JarInputStream, destDir: File) {
        val entry = jarFile.nextJarEntry
        while (entry != null) {
            val f = File(destDir.path + File.separator + entry.name)
            if (entry.isDirectory) {
                f.mkdir()
                continue
            }

            println("Entry: $entry")
//            jarFile.getInputStream(entry).use { ins ->
//                f.parentFile.mkdirs()
//                FileOutputStream(f).use { fos ->
//                    while (ins.available() > 0) {
//                        fos.write(ins.read())
//                    }
//                }
//            }
        }
    }

}
