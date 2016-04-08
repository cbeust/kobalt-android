package com.beust.kobalt.plugin.android

import com.android.builder.core.AaptPackageProcessBuilder
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.ErrorReporter
import com.android.builder.core.LibraryRequest
import com.android.builder.dependency.ManifestDependency
import com.android.builder.dependency.SymbolFileProvider
import com.android.builder.model.AaptOptions
import com.android.builder.model.SyncIssue
import com.android.builder.sdk.DefaultSdkLoader
import com.android.builder.sdk.SdkLoader
import com.android.ide.common.blame.Message
import com.android.ide.common.internal.ExecutorSingleton
import com.android.ide.common.process.*
import com.android.ide.common.res2.*
import com.android.manifmerger.ManifestMerger2
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.SdkManager
import com.android.utils.ILogger
import com.android.utils.StdLogger
import com.beust.kobalt.Variant
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.MavenId
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltLogger
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.logWrap
import java.io.File
import java.nio.file.Paths
import java.util.*

class KobaltProcessResult : ProcessResult {
    override fun getExitValue(): Int {
        return 0
    }

    override fun assertNormalExitValue(): ProcessResult? {
        throw UnsupportedOperationException()
    }

    override fun rethrowFailure(): ProcessResult? {
        throw UnsupportedOperationException()
    }
}

class KobaltJavaProcessExecutor : JavaProcessExecutor {
    override fun execute(javaProcessInfo: JavaProcessInfo?, processOutputHandler: ProcessOutputHandler?)
            : ProcessResult? {
        log(1, "Executing " + javaProcessInfo!!)
        return KobaltProcessResult()
    }
}

class KobaltProcessOutputHandler : BaseProcessOutputHandler() {
    override fun handleOutput(processOutput: ProcessOutput) =
            log(3, "AndroidBuild output" + processOutput.standardOutput)
}

class KobaltErrorReporter : ErrorReporter(ErrorReporter.EvaluationMode.STANDARD) {
    override fun handleSyncError(data: String?, type: Int, msg: String?): SyncIssue? {
        throw UnsupportedOperationException()
    }

    override fun receiveMessage(message: Message?) {
        throw UnsupportedOperationException()
    }
}

class ProjectLayout {
    val mergeBlame: File? = null
    val publicText: File? = null

}

class KobaltResourceMerger(val androidHome: String) {
    fun run(project: Project, variant: Variant, config: AndroidConfig, aarDependencies: List<File>) : List<String> {
        val result = arrayListOf<String>()
        val level = when(KobaltLogger.LOG_LEVEL) {
            3 -> StdLogger.Level.VERBOSE
            2 -> StdLogger.Level.WARNING
            else -> StdLogger.Level.ERROR
        }
        val logger = StdLogger(level)
        val androidBuilder = createAndroidBuilder(project, config, logger)

        log(2, "Merging resources")

        //
        // Assets
        //
        processAssets(project, variant, androidBuilder, aarDependencies)

        //
        // Manifests
        //
        val appInfo = processManifests(project, variant, androidBuilder, config)

        //
        // Resources
        //
        KobaltProcessOutputHandler().let {
            processResources(project, variant, androidBuilder, aarDependencies, logger, it, appInfo.minSdkVersion)
            result.addAll(mergeResources(project, variant, androidBuilder, aarDependencies, it))
        }
        return result
    }

    private fun createAndroidBuilder(project: Project, config: AndroidConfig, logger: ILogger): AndroidBuilder {
        val processExecutor = DefaultProcessExecutor(logger)
        val javaProcessExecutor = KobaltJavaProcessExecutor()
        val sdkLoader : SdkLoader = DefaultSdkLoader.getLoader(File(androidHome))
        val result = AndroidBuilder(project.name, "kobalt-android-plugin",
                processExecutor,
                javaProcessExecutor,
                KobaltErrorReporter(),
                logger,
                false /* verbose */)

        val libraryRequests = arrayListOf<LibraryRequest>()
        val sdk = sdkLoader.getSdkInfo(logger)
        val sdkManager = SdkManager.createManager(androidHome, logger)
        val maxPlatformTarget = sdkManager.targets.filter { it.isPlatform }.last()
        val maxPlatformTargetHash = AndroidTargetHash.getPlatformHashString(maxPlatformTarget.version)

        result.setTargetInfo(sdk,
                sdkLoader.getTargetInfo(maxPlatformTargetHash, maxPlatformTarget.buildToolInfo.revision, logger),
                libraryRequests)
        return result
    }

    /**
     * Create a ManifestDependency suitable for the Android builder based on a Maven Dependency.
     */
    private fun create(project: Project, md: IClasspathDependency, shortIds: HashMap<String, ManifestDependency>) =
        object: ManifestDependency {
            override fun getManifest() = File(AndroidFiles.explodedManifest(project, MavenId.create(md.id)))

            override fun getName() = md.jarFile.get().path

            override fun getManifestDependencies(): List<ManifestDependency>
                    = createLibraryDependencies(project, md.directDependencies(), shortIds)
        }

    private fun createLibraryDependencies(project: Project, dependencies: List<IClasspathDependency>,
            shortIds : HashMap<String, ManifestDependency>)
            : List<ManifestDependency> {
        val result = arrayListOf<ManifestDependency>()
        dependencies.filter {
                it.jarFile.get().path.endsWith(".aar")
            }.forEach { dep ->
                val manifestDependency = shortIds.computeIfAbsent(dep.shortId, { create(project, dep, shortIds) })
                result.add(manifestDependency)
            }
            return result
        }

    private fun processAssets(project: Project, variant: Variant, androidBuilder: AndroidBuilder,
            aarDependencies: List<File>) {
        logWrap(2, "  Processing assets...", "done") {
            val intermediates = File(
                    KFiles.joinDir(AndroidFiles.intermediates(project), "assets", variant.toIntermediateDir()))
            aarDependencies.forEach {
                val assetDir = File(it, "assets")
                if (assetDir.exists()) {
                    KFiles.copyRecursively(assetDir, intermediates)
                }
            }
        }
    }

    private fun processManifests(project: Project, variant: Variant, androidBuilder: AndroidBuilder,
            config: AndroidConfig): AppInfo {
        val mainManifest = File(project.directory, "src/main/AndroidManifest.xml")
        val appInfo = AppInfo(mainManifest, config)
        logWrap(2, "  Processing manifests...", "done") {
            val manifestOverlays = variant.allDirectories(project).map {
                File(project.directory, "src/$it/AndroidManifest.xml")
            }.filter {
                it.exists()
            }
            val shortIds = hashMapOf<String, ManifestDependency>()
            val libraries = createLibraryDependencies(project, project.compileDependencies, shortIds)
            val outManifest = AndroidFiles.mergedManifest(project, variant)
            val outAaptSafeManifestLocation = KFiles.joinDir(project.directory, project.buildDirectory, "generatedSafeAapt")
            val reportFile = File(KFiles.joinDir(project.directory, project.buildDirectory, "manifest-merger-report.txt"))
            androidBuilder.mergeManifests(mainManifest, manifestOverlays, libraries,
                    null /* package override */,
                    appInfo.versionCode,
                    appInfo.versionName,
                    appInfo.minSdkVersion.toString(),
                    appInfo.targetSdkVersion,
                    appInfo.maxSdkVersion,
                    outManifest,
                    outAaptSafeManifestLocation,
                    // TODO: support aar too
                    ManifestMerger2.MergeType.APPLICATION,
//                    ManifestMerger2.MergeType.LIBRARY,
                    emptyMap() /* placeHolders */,
                    reportFile)
        }
        return appInfo
    }

    private fun processResources(project: Project, variant: Variant, androidBuilder: AndroidBuilder,
            aarDependencies: List<File>, logger: ILogger, processOutputHandler: KobaltProcessOutputHandler,
            minSdk: Int) {
        logWrap(2, "  Processing resources...", "done") {
            val layout = ProjectLayout()
            val preprocessor = NoOpResourcePreprocessor()
            val outputDir = AndroidFiles.mergedResources(project, variant)
            val resourceMerger = ResourceMerger(minSdk)
            val fullVariantDir = File(variant.toCamelcaseDir())
            val srcList = setOf("main", variant.productFlavor.name, variant.buildType.name, fullVariantDir.path)
                    .map { project.directory + File.separator + "src" + File.separator + it }

            // TODO: figure out why the badSrcList is bad. All this information should be coming from the Variant
            // Figured it out: using hardcoded "resources" instead of "res"
            val badSrcList = variant.resourceDirectories(project).map { it.path }
            val goodAarList = aarDependencies.map { it.path + File.separator }
            (goodAarList + srcList).map { it + File.separator + "res" }.forEach { path ->
                with(ResourceSet(path)) {
                    addSource(File(path))
                    loadFromFiles(logger)
                    setGeneratedSet(GeneratedResourceSet(this))
                    resourceMerger.addDataSet(this)
                }
            }

            val writer = MergedResourceWriter(File(outputDir),
                    androidBuilder.getAaptCruncher(processOutputHandler),
                    false /* don't crunch */,
                    false /* don't process 9patch */,
                    layout.publicText,
                    layout.mergeBlame,
                    preprocessor)
            resourceMerger.mergeData(writer, true)
        }
    }

    fun shutdown() {
        ExecutorSingleton.getExecutor().shutdown()
    }

    /**
     * @return the extra source directories
     */
    private fun mergeResources(project: Project, variant: Variant, androidBuilder: AndroidBuilder,
            aarDependencies: List<File>, processOutputHandler: KobaltProcessOutputHandler) : List<String> {
        val result = arrayListOf<String>()
        logWrap(2, "  Merging resources...", "done") {

            val aaptOptions = object : AaptOptions {
                override fun getAdditionalParameters() = emptyList<String>()
                override fun getFailOnMissingConfigEntry() = false
                override fun getIgnoreAssets() = null
                override fun getNoCompress() = null
            }

            val aaptCommand = AaptPackageProcessBuilder(File(AndroidFiles.mergedManifest(project, variant)),
                    aaptOptions)

            fun toSymbolFileProvider(aarDirectory: File) = object : SymbolFileProvider {
                override fun getManifest() = File(aarDirectory, "AndroidManifest.xml")
                override fun isOptional() = false
                override fun getSymbolFile() = File(aarDirectory, "R.txt")
            }

            val variantDir = variant.toIntermediateDir()
            val generated = KFiles.joinAndMakeDir(project.directory, project.buildDirectory, "symbols")

            val rDirectory = KFiles.joinAndMakeDir(KFiles.generatedSourceDir(project, variant, "r"))
            result.add(Paths.get(project.directory).relativize(Paths.get(rDirectory)).toString())

            with(aaptCommand) {
                sourceOutputDir = rDirectory
                val libraries = aarDependencies.map { toSymbolFileProvider(it) }
                // TODO: setType for libraries
                setLibraries(libraries)
                setResFolder(File(AndroidFiles.mergedResources(project, variant)))
                setAssetsFolder(File(KFiles.joinAndMakeDir(AndroidFiles.intermediates(project), "assets", variantDir)))
                setResPackageOutput(AndroidFiles.temporaryApk(project, variant.shortArchiveName))
                symbolOutputDir = generated

//            aaptCommand.setSourceOutputDir(generated)
//            aaptCommand.setPackageForR(pkg)
//            aaptCommand.setProguardOutput(proguardTxt)
//            aaptCommand.setType(if (lib) VariantType.LIBRARY else VariantType.DEFAULT)
//                setDebuggable(true)
//                setVerbose()
            }

            androidBuilder.processResources(aaptCommand, true, processOutputHandler)
        }
        return result
    }

    fun dex(project: Project) {
//        androidBuilder.createMainDexList()
    }
}
