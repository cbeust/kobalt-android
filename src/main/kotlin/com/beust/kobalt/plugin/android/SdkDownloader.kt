package com.beust.kobalt.plugin.android

import com.android.SdkConstants
import com.beust.kobalt.homeDir
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URL
import java.util.zip.ZipFile

/**
 * Automatically download the Android SDK if it can't be found and then any other necessary components.
 * If the Android Home is not passed in parameter, look it up in $ANDROID_HOME and if that variable
 * isn't defined, download it in ~/.android/android-sdk-<platform>. Adapted from Jake Wharton's SDK Manager Gradle
 * plug-in.
 *
 * @author Cedric Beust <cedric@beust.com>
 */
class SdkUpdater(val configAndroidHome: String?, val compileSdkVersion: String?, val buildToolsVersion: String?,
        val dryMode: Boolean = false) {
    val FD_BUILD_TOOLS = com.android.SdkConstants.FD_BUILD_TOOLS
    val FD_PLATFORM = com.android.SdkConstants.FD_PLATFORMS
    val FD_PLATFORM_TOOLS = com.android.SdkConstants.FD_PLATFORM_TOOLS

    private lateinit var androidHome: String

    enum class SdkDownload(val platform: String, val extension: String) {
        WINDOWS("windows", "zip"),
        LINUX("linux", "tgz"),
        DARWIN("macosx", "zip")
    }

    companion object {
        private fun log(s: String) = log(1, s)
        private fun logVerbose(s: String) = log(2, "  $s")
        private fun logVeryVerbose(s: String) = log(3, "      s")
    }

    fun maybeInstall(): String {
        // Android SDK
        androidHome = maybeInstallAndroid()

        // Build tools
        // android update sdk --all --filter build-tools-21.1.0 --no-ui
        if (buildToolsVersion != null) {
            maybeInstall(FD_BUILD_TOOLS + "-" + buildToolsVersion,
                    listOf(FD_BUILD_TOOLS, buildToolsVersion))
        }

        // Platform tools
        // android update sdk --all --filter platform-tools --no-ui
        maybeInstall(FD_PLATFORM_TOOLS, listOf(FD_PLATFORM_TOOLS))

        // Compilation version
        // android update sdk --all --filter android-22 --no-ui
        if (compileSdkVersion != null) {
            maybeInstall("android-$compileSdkVersion", listOf(FD_PLATFORM, "android-$compileSdkVersion"))
        }

        return androidHome
    }

    private val sdk: SdkDownload
        get() {
            val osName = System.getProperty("os.name").toLowerCase()
            return if (osName.contains("windows")) SdkDownload.WINDOWS
                else if (osName.contains("mac os x") || osName.contains("darwin")
                    || osName.contains("osx")) SdkDownload.DARWIN
                else SdkDownload.LINUX
        }

    private fun androidZip(sdkVersion: String, suffix: String, ext: String)
            = "android-sdk_r$sdkVersion-$suffix.$ext"

    private fun downloadUrl(sdkVersion: String, suffix: String, ext: String)
            = "http://dl.google.com/android/android-sdk_r$sdkVersion-$suffix.$ext"

    private val SDK_LATEST_VERSION = "24.4.1"
    private val androidBaseDir = homeDir(".android")
    private fun newAndroidHome(platform: String) = KFiles.makeDir(androidBaseDir, "android-sdk-$platform")

    private fun maybeInstallAndroid(): String {
        val androidHome = configAndroidHome
                ?: System.getenv("ANDROID_HOME")
                ?: newAndroidHome(sdk.platform)
        logVerbose("Android home is $androidHome")

        // Download
        val zipFile = File(androidBaseDir, androidZip(SDK_LATEST_VERSION, sdk.platform, sdk.extension))
        val androidHomeDir = newAndroidHome(sdk.platform)
        val androidCommand = File(androidCommand(androidHomeDir.absolutePath))

        if (! androidHomeDir.exists() || ! androidCommand.exists()) {
            val downloadUrl = downloadUrl(SDK_LATEST_VERSION, sdk.platform, sdk.extension)
            if (!dryMode) {
                log("Couldn't locate $androidHome, downloading the Android SDK")
                if (! zipFile.exists()) {
                    downloadFile(downloadUrl, zipFile.absolutePath)
                } else {
                    log("Found an existing distribution, not downloading it again")
                }
                extractZipFile(ZipFile(zipFile), androidHomeDir)
                File(androidCommand(androidHomeDir.absolutePath)).setExecutable(true)
            } else {
                logVerbose("dryMode is enabled, not downloading $downloadUrl")
            }
        }

        val result = if (androidHomeDir.path.contains("android-sdk")) androidHomeDir
            else File(androidHomeDir, "android-sdk-${sdk.platform}")
        return result.absolutePath
    }

    /**
     * Extract the zip file in the given directory.
     */
    private fun extractZipFile(zipFile: ZipFile, destDir: File) {
        log("Extracting ${zipFile.name}")
        val enumEntries = zipFile.entries()
        while (enumEntries.hasMoreElements()) {
            val file = enumEntries.nextElement()
            val f = File(destDir.path + File.separator + file.name)
            if (file.isDirectory) {
                f.mkdir()
                continue
            }

            zipFile.getInputStream(file).use { ins ->
                f.parentFile.mkdirs()
                logVerbose("Extracting ${f.path}")
                FileOutputStream(f).use { fos ->
                    while (ins.available() > 0) {
                        fos.write(ins.read())
                    }
                }
            }
        }
    }

    /**
     * Download the given file to a file.
     */
    private fun downloadFile(url: String, outFile: String): File {
        val buffer = ByteArray(1000000)
        val hasTerminal = System.console() != null
        log("Downloading " + url)
        val from = URL(url).openConnection().inputStream
        val tmpFile = File(homeDir("t", "android.zip.tmp"))
        val to = tmpFile.outputStream()

        var bytesRead = from.read(buffer)
        var bytesSoFar = 0L
        while (bytesRead != -1) {
            to.write(buffer, 0, bytesRead)
            bytesSoFar += bytesRead.toLong()
            bytesRead = from.read(buffer)
        }
        val toFile = File(outFile).apply { delete() }
        tmpFile.renameTo(File(outFile))
        logVerbose("Downloaded the Android SDK to $toFile")
        return toFile
    }

    private fun maybeInstall(filter: String, dirList: List<String>) {
        val dir = KFiles.joinDir(androidHome, *dirList.toTypedArray())
        logVerbose("Maybe install $filter, directory: $dir")
        if (!File(dir).exists()) {
            log("Couldn't find $dir, downloading it...")
            update(filter)
        } else {
            logVerbose("$dir is up to date")
        }
    }

    private fun androidCommand(androidHome: String) = KFiles.joinDir(androidHome, "tools",
            SdkConstants.androidCmdName())

    /**
     * Launch the "android" command with the given filter.
     */
    private fun update(filter: String) {
        val fullCommand = listOf(androidCommand(androidHome), "update", "sdk", "--all", "--filter", filter,
                "--no-ui") +
                (if (dryMode) listOf("-n") else emptyList())
        logVerbose("Launching " + fullCommand.joinToString(" "))
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
                logVeryVerbose(it)
            }
        }

        val rc = process.waitFor()

        log(1, "Result of update: " + rc)
    }
}

fun main(argv: Array<String>) {
    //    SdkDownload.downloader.download()
    SdkUpdater(null, "22", "21.1.0").maybeInstall()
}
