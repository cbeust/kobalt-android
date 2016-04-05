package com.beust.kobalt.plugin.android

import com.android.SdkConstants
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

enum class SdkDownload(val platform: String, val extension: String) {
    WINDOWS("windows", "zip"),
    LINUX("linux", "tgz"),
    DARWIN("macosx", "zip");

    companion object {
        val downloader: SdkDownloader get() {
            val osName = System.getProperty("os.name").toLowerCase()
            val s = if (osName.contains("windows")) WINDOWS
                else if (osName.contains("mac os x") || osName.contains("darwin") || osName.contains("osx")) DARWIN
                else LINUX
            return SdkDownloader(s.platform, s.extension)
        }
    }
}

val LOG_LEVEL = 1

class SdkDownloader(val suffix: String, val ext: String) {
    companion object {
        val SDK_VERSION_MAJOR = "24.4.1"
    }

    val DOWNLOAD_URL = "http://dl.google.com/android/android-sdk_r$SDK_VERSION_MAJOR-$suffix.$ext"

    fun download() {
        log(LOG_LEVEL, "Downloading $DOWNLOAD_URL")
    }
}

class SdkUpdates(val androidHome: String, val compileSdkVersion: String, val buildToolsVersion: String) {
    val FD_BUILD_TOOLS = com.android.SdkConstants.FD_BUILD_TOOLS
    val FD_PLATFORM = com.android.SdkConstants.FD_PLATFORMS
    val FD_PLATFORM_TOOLS = com.android.SdkConstants.FD_PLATFORM_TOOLS

    fun download() {
        // Build tools
        // android update sdk --all --filter build-tools-21.1.0 --no-ui
        maybeInstall(FD_BUILD_TOOLS + "-" + buildToolsVersion,
                listOf(FD_BUILD_TOOLS, buildToolsVersion))

        // Platform tools
        // android update sdk --all --filter platform-tools --no-ui
        maybeInstall(FD_PLATFORM_TOOLS, listOf(FD_PLATFORM_TOOLS))

        // Compilation version
        // android update sdk --all --filter android-22 --no-ui
        maybeInstall("android-$compileSdkVersion", listOf(FD_PLATFORM, "android-$compileSdkVersion"))
    }

    private fun logNotFound(s: String) = log(LOG_LEVEL, "Couldn't find $s, downloading...")
    private fun logFound(s: String) = log(LOG_LEVEL, "$s is up to date")

    private fun maybeInstall(filter: String, dirList: List<String>) {
        val dir = KFiles.joinDir(androidHome, *dirList.toTypedArray())
        log(1, "Maybe install $filter, directory: $dir")
        if (!File(dir).exists()) {
            logNotFound(filter)
            update(filter)
        } else {
            logFound(filter)
        }
    }

    /**
     * Launch the "android" command with the given filter.
     */
    private fun update(filter: String) {
        val command = SdkConstants.androidCmdName()

        val fullCommand = listOf(command, "update", "sdk", "--all", "--filter", filter, "--no-ui", "-n")
        log(LOG_LEVEL, "Launching " + fullCommand.joinToString(" "))
        val process = ProcessBuilder(fullCommand)
            .redirectErrorStream(true)
            .start()

        // Press 'y' and then enter on the license prompt.
        OutputStreamWriter(process.outputStream).use {
            it.write("y\n")
        }

        // Pipe the command output to our log.
        InputStreamReader(process.inputStream).useLines { seq ->
            seq.forEach {
                log(1, it)
            }
        }

        val rc = process.waitFor()

        log(1, "Result of update: " + rc)
    }
}
fun main(argv: Array<String>) {
//    SdkDownload.downloader.download()
    SdkUpdates("/Users/beust/android/adt-bundle-mac-x86_64-20140702/sdk", "22", "21.1.0").download()
}
