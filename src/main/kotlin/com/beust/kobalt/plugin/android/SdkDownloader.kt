package com.beust.kobalt.plugin.android

import com.android.SdkConstants
import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.Project
import com.beust.kobalt.homeDir
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
import org.rauschig.jarchivelib.ArchiveFormat
import org.rauschig.jarchivelib.ArchiverFactory
import org.rauschig.jarchivelib.CompressionType
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URL
import java.nio.file.Files

/**
 * Automatically download the Android SDK if it can't be found and then any other necessary components.
 * If the Android Home is not passed in parameter, look it up in $ANDROID_HOME and if that variable
 * isn't defined, download it in ~/.android-sdk/. Adapted from Jake Wharton's android-sdk-manager
 * plug-in.
 *
 * @author Cedric Beust <cedric@beust.com>
 */
class SdkUpdater(val configAndroidHome: String?, val compileSdkVersion: String?, val buildToolsVersion: String?,
        val dryMode: Boolean = false) {
    val FD_BUILD_TOOLS = com.android.SdkConstants.FD_BUILD_TOOLS
    val FD_PLATFORM = com.android.SdkConstants.FD_PLATFORMS
    val FD_PLATFORM_TOOLS = com.android.SdkConstants.FD_PLATFORM_TOOLS

    val androidHome: String by lazy {
        maybeInstallAndroid()
    }

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

    fun maybeInstall(project: Project): String {
        logVerbose("Android home is $androidHome")

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

        // Android Support libraries
        // android update sdk --all --filter extra-android-m2repository --no-ui
        maybeInstallRepository(project, "com.android.support", "extra-android-m2repository",
                hasAndroidM2Repository(androidHome))

        // Google Support libraries
        // android update sdk --all --filter extra-google-m2repository --no-ui
        maybeInstallRepository(project, "com.google.android", "extra-google-m2repository",
                hasGoogleM2Repository(androidHome))

        return androidHome
    }

    private fun maybeInstallRepository(project: Project, id: String, filter: String, directoryExists: Boolean) {
        if (! directoryExists && project.compileDependencies.any { it.id.contains(id) }) {
            log("Downloading Maven repository for $filter")
            update(filter)
        } else {
            logVerbose("Found Maven repository for $filter")
        }
    }

    private fun hasAndroidM2Repository(androidHome: String)
            = File(KFiles.joinDir(androidHome, "extras", "android", "m2repository")).exists()

    private fun hasGoogleM2Repository(androidHome: String)
        = File(KFiles.joinDir(androidHome, "extras", "google", "m2repository")).exists()

    private val sdk: SdkDownload
        get() {
            val osName = System.getProperty("os.name").toLowerCase()
            return if (osName.contains("windows")) SdkDownload.WINDOWS
                else if (osName.contains("mac os x") || osName.contains("darwin")
                    || osName.contains("osx")) SdkDownload.DARWIN
                else SdkDownload.LINUX
        }

    private fun androidZipName(sdkVersion: String, suffix: String, ext: String)
            = "android-sdk_r$sdkVersion-$suffix.$ext"

    private fun downloadUrl(sdkVersion: String, suffix: String, ext: String)
            = "http://dl.google.com/android/android-sdk_r$sdkVersion-$suffix.$ext"

    private val SDK_LATEST_VERSION = "24.4.1"
    private val androidDownloadDirectory = homeDir(".android-sdk")
    private fun newAndroidHome(platform: String) =
            KFiles.makeDir(androidDownloadDirectory, "android-sdk-$platform").absolutePath

    private fun maybeInstallAndroid(): String {
        val envHome = System.getenv("ANDROID_HOME")
        fun validAndroidHome(home: String?) = home != null && File(androidCommand(home)).exists()
        val androidHome =
            if (envHome != null) {
                if (! validAndroidHome(envHome)) {
                    throw KobaltException("Invalid \$ANDROID_HOME $envHome, please specify a valid one or none at all")
                } else {
                    envHome
                }
            } else {
                configAndroidHome ?: newAndroidHome(sdk.platform)
            }

        val androidHomeDir = File(androidHome)

        // Download
        val androidHomeParent = File(androidHome).parentFile
        val zipFile = File(androidDownloadDirectory, androidZipName(SDK_LATEST_VERSION, sdk.platform, sdk.extension))
        val androidCommand = File(androidCommand(androidHomeDir.absolutePath))

        if (! androidHomeDir.exists() || ! androidCommand.exists()) {
            val downloadUrl = downloadUrl(SDK_LATEST_VERSION, sdk.platform, sdk.extension)
            if (!dryMode) {
                log("Android SDK not found at $androidHome, downloading it")
                if (! zipFile.exists()) {
                    downloadFile(downloadUrl, zipFile.absolutePath)
                } else {
                    log("Found an existing distribution, not downloading it again")
                }

                val archiver = if (sdk.extension == "zip") ArchiverFactory.createArchiver(ArchiveFormat.ZIP)
                    else ArchiverFactory.createArchiver(ArchiveFormat.TAR, CompressionType.GZIP)
                archiver.extract(zipFile, androidHomeParent)
                File(androidCommand(androidHomeDir.absolutePath)).setExecutable(true)
            } else {
                logVerbose("dryMode is enabled, not downloading $downloadUrl")
            }
        }

        return androidHome
    }

    /**
     * Download the given url to a file.
     */
    private fun downloadFile(url: String, outFile: String): File {
        val buffer = ByteArray(1000000)
        val hasTerminal = System.console() != null
        log("Downloading " + url)
        val from = URL(url).openConnection().inputStream
        val tmpFile = Files.createTempFile("kobalt-android-sdk", "").toFile()
        tmpFile.outputStream().use { to ->
            var bytesRead = from.read(buffer)
            var bytesSoFar = 0L
            while (bytesRead != -1) {
                to.write(buffer, 0, bytesRead)
                bytesSoFar += bytesRead.toLong()
                bytesRead = from.read(buffer)
            }
        }

        val toFile = File(outFile).apply { delete() }
        tmpFile.renameTo(File(outFile))
        logVerbose("Downloaded the Android SDK to $toFile")
        return toFile
    }

    private fun maybeInstall(filter: String, dirList: List<String>) {
        val dir = KFiles.joinDir(androidHome, *dirList.toTypedArray())
        if (!File(dir).exists()) {
            log("Downloading $dir")
            update(filter)
        } else {
            logVerbose("Found $dir")
        }
    }

    private fun androidCommand(androidHome: String) = KFiles.joinDir(androidHome, "tools",
            SdkConstants.androidCmdName())

    /**
     * Launch the "android" command with the given filter.
     */
    private fun update(filter: String) : Int {
        val fullCommandArgs = listOf(androidCommand(androidHome), "update", "sdk", "--all", "--filter", filter,
                "--no-ui") +
                (if (dryMode) listOf("-n") else emptyList())
        val fullCommand = fullCommandArgs.joinToString(" ")
        logVerbose("Launching " + fullCommand)
        val process = ProcessBuilder(fullCommandArgs)
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

        // Save the error stream in case something goes wrong
        val errors = arrayListOf<String>()
        InputStreamReader(process.errorStream).useLines { seq ->
            seq.forEach {
                errors.add(it)
            }
        }

        val result = process.waitFor()
        if (result != 0) {
            warn("$fullCommand didn't complete successfully: $result")
            errors.forEach { warn("    $it") }
        } else {
            logVerbose("$fullCommand completed successfully")
        }
        return result
    }
}

fun main(argv: Array<String>) {
    val extension = "zip"
    val archiver = if (extension == "zip") ArchiverFactory.createArchiver(ArchiveFormat.ZIP)
        else ArchiverFactory.createArchiver(ArchiveFormat.TAR, CompressionType.GZIP)
    //                archiver.extract(zipFile, File(androidBaseDir))
    archiver.extract(File(homeDir("t/android-macosx.zip")), File(homeDir("t/abcDir")))

    //    SdkDownload.downloader.download()
//    SdkUpdater(null, "22", "21.1.0").maybeInstall()
}
