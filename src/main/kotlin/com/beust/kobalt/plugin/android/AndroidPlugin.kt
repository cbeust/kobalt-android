package com.beust.kobalt.plugin.android

import com.beust.kobalt.*
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.IncrementalTask
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.MavenId
import com.beust.kobalt.maven.Md5
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.misc.*
import com.google.common.collect.HashMultimap
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * The Android plug-in which executes:
 * library dependencies (android.library.reference.N)
 * ndk
 * aidl
 * renderscript
 * BuildConfig.java
 * aapt
 * compile
 * obfuscate
 * dex
 * png crunch
 * package resources
 * package apk
 * sign
 * zipalign
 */
@Singleton
class AndroidPlugin @Inject constructor(val dependencyManager: DependencyManager,
        val taskContributor : TaskContributor, val aarGenerator: AarGenerator)
            : BasePlugin(), IConfigActor<AndroidConfig>, IClasspathContributor, IRepoContributor,
                ICompilerFlagContributor, ICompilerInterceptor, IBuildDirectoryIncerceptor, IRunnerContributor,
                IClasspathInterceptor, ISourceDirectoryContributor, IBuildConfigFieldContributor, ITaskContributor,
                IMavenIdInterceptor, ICompilerContributor, ITemplateContributor, IAssemblyContributor {

    override val configurations : HashMap<String, AndroidConfig> = hashMapOf()
    private val resourceMerger = KobaltResourceMerger()

    private val idlCompiler = object: ICompiler {
        override val sourceSuffixes: List<String>
            get() = listOf("aidl", "idl")
        override val sourceDirectory = "idl"

        /*
        /Users/beust/android/adt-bundle-mac-x86_64-20140702/sdk/build-tools/23.0.2/aidl -p/Users/beust/android/adt-bundle-mac-x86_64-20140702/sdk/platforms/android-23/framework.aidl -o/Users/beust/t/MaterialAudiobookPlayer/audiobook/build/generated/source/aidl/debug -I/Users/beust/t/MaterialAudiobookPlayer/audiobook/src/main/aidl -I/Users/beust/t/MaterialAudiobookPlayer/audiobook/src/debug/aidl -I/Users/beust/t/MaterialAudiobookPlayer/audiobook/build/intermediates/exploded-aar/com.afollestad.material-dialogs/core/0.8.5.3/aidl -I/Users/beust/t/MaterialAudiobookPlayer/audiobook/build/intermediates/exploded-aar/me.zhanghai.android.materialprogressbar/library/1.1.4/aidl -I/Users/beust/t/MaterialAudiobookPlayer/audiobook/build/intermediates/exploded-aar/com.android.support/recyclerview-v7/23.1.1/aidl -I/Users/beust/t/MaterialAudiobookPlayer/audiobook/build/intermediates/exploded-aar/com.android.support/appcompat-v7/23.1.1/aidl -I/Users/beust/t/MaterialAudiobookPlayer/audiobook/build/intermediates/exploded-aar/com.getbase/floatingactionbutton/1.10.1/aidl -I/Users/beust/t/MaterialAudiobookPlayer/audiobook/build/intermediates/exploded-aar/com.android.support/support-v4/23.1.1/aidl -I/Users/beust/t/MaterialAudiobookPlayer/audiobook/build/intermediates/exploded-aar/com.jakewharton.timber/timber/4.1.0/aidl -I/Users/beust/t/MaterialAudiobookPlayer/audiobook/build/intermediates/exploded-aar/com.github.JakeWharton.RxBinding/rxbinding-kotlin/542cd7e8a4/aidl -I/Users/beust/t/MaterialAudiobookPlayer/audiobook/build/intermediates/exploded-aar/com.github.JakeWharton.RxBinding/rxbinding/542cd7e8a4/aidl -I/Users/beust/t/MaterialAudiobookPlayer/audiobook/build/intermediates/exploded-aar/io.reactivex/rxandroid/1.1.0/aidl -I/Users/beust/t/MaterialAudiobookPlayer/audiobook/build/intermediates/exploded-aar/com.squareup.leakcanary/leakcanary-android/1.4-beta1/aidl -d/var/folders/77/kjr_lq4x5tj5ymxdfvxs6c7c002p8z/T/aidl768872507241036042.d /Users/beust/t/MaterialAudiobookPlayer/audiobook/src/main/aidl/com/android/vending/billing/IInAppBillingService.aidl
         */
        override fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo): TaskResult {
            val version = configurationFor(project)?.compileSdkVersion
            val pArg = "-p" + androidHome(project) + "/platforms/android-$version/framework.aidl"
            val oArg = "-o" + AndroidFiles.generatedSource(project, context)
            val exp = explodedAarDirectories(project).map { it.second }
            val included = exp.map {
                "-I" + KFiles.joinDir(it.path, "aidl")
            }
            val success = runCommand {
                command = aidl(project)
                args = listOf(pArg) + listOf(oArg) + included + info.sourceFiles
                directory = if (info.directory == "") File(".") else File(info.directory)

            }
            return TaskResult(if (success == 0) true else false)
        }
    }

    override fun compilersFor(project: Project, context: KobaltContext): List<ICompiler> = listOf(idlCompiler)

    companion object {
        const val PLUGIN_NAME = "Android"
        const val TASK_GENERATE_DEX = "generateDex"
        const val TASK_SIGN_APK = "signApk"
        const val TASK_INSTALL= "install"
    }

    override val name = PLUGIN_NAME

    fun isAndroid(project: Project) = configurationFor(project) != null

    override fun shutdown() {
        resourceMerger.shutdown()
    }

    override fun apply(project: Project, context: KobaltContext) {
        super.apply(project, context)
        if (accept(project)) {
            classpathEntries.put(project.name, FileDependency(androidJar(project).toString()))
            project.compileDependencies.filter { it.jarFile.get().path.endsWith("jar")}.forEach {
                classpathEntries.put(project.name, FileDependency(it.jarFile.get().path))
            }

            taskContributor.addVariantTasks(this, project, context, "generateR", runBefore = listOf("compile"),
                    runTask = { taskGenerateRFile(project) })
            taskContributor.addIncrementalVariantTasks(this, project, context, "generateDex",
                    runAfter = listOf ("compile"),
                    runBefore = listOf("assemble"),
                    runTask = { taskGenerateDex(project) })
            taskContributor.addVariantTasks(this, project, context, "signApk", runAfter = listOf("generateDex"),
                    runBefore = listOf("assemble"),
                    runTask = { taskSignApk(project) })
            taskContributor.addVariantTasks(this, project, context, "install", runAfter = listOf("signApk"),
                    runTask = { taskInstall(project) })
            taskContributor.addVariantTasks(this, project, context, "proguard", runBefore = listOf("install"),
                    runAfter = listOf("compile"),
                    runTask = { taskProguard(project) })
        }
//        context.pluginInfo.classpathContributors.add(this)
    }


    override fun accept(project: Project) = isAndroid(project)

    fun compileSdkVersion(project: Project) = configurationFor(project)?.compileSdkVersion

    fun buildToolsVersion(project: Project): String {
        val version = configurationFor(project)?.buildToolsVersion
        if (OperatingSystem.current().isWindows() && version == "21.1.2")
            return "build-tools-$version"
        else
            return version as String
    }

    fun androidHome(project: Project?) = AndroidFiles.androidHome(project, configurationFor(project)!!)

    fun androidJar(project: Project): Path =
            Paths.get(androidHome(project), "platforms", "android-${compileSdkVersion(project)}", "android.jar")

    private fun buildToolCommand(project: Project, command: String)
        = "${androidHome(project)}/build-tools/${buildToolsVersion(project)}/$command"

    private fun aapt(project: Project) = buildToolCommand(project, "aapt")

    private fun aidl(project: Project) = buildToolCommand(project, "aidl")

    private fun adb(project: Project) = "${androidHome(project)}/platform-tools/adb"

    private val preDexFiles = arrayListOf<String>()

    @Task(name = "generateR", description = "Generate the R.java file", runAfter = arrayOf("clean"),
            runBefore = arrayOf ("compile"))
    fun taskGenerateRFile(project: Project): TaskResult {

        val aarDependencies = explodeAarFiles(project)
        preDexFiles.addAll(preDex(project, context.variant, aarDependencies))
        val extraSourceDirs = resourceMerger.run(project, context.variant, configurationFor(project)!!, aarDependencies)
        extraSourceDirectories.addAll(extraSourceDirs.map { File(it) })

        return TaskResult(true)
    }

    /**
     * Predex all the libraries that need to be predexed then return a list of them.
     */
    private fun preDex(project: Project, variant: Variant, aarDependencies: List<File>) : List<String> {
        log(2, "Predexing")
        val result = arrayListOf<String>()
        val aarFiles = aarDependencies.map { File(AndroidFiles.aarClassesJar(it.path))}
        val jarFiles = dependencies(project).map { File(it) }
        val allDependencies = (aarFiles + jarFiles).toHashSet().filter { it.exists() }

        allDependencies.forEach { dep ->
            val versionFile = File(dep.path).parentFile
            val artifactFile = versionFile.parentFile
            val name = (if (artifactFile != null) artifactFile.name else "") + "-" + versionFile.name
            val outputDir = AndroidFiles.preDexed(project, variant)
            val outputFile = File(outputDir, name + ".jar")
            if (! outputFile.exists()) {
                log(2, "  Predexing $dep")
                if (runDex(project, outputFile.path, dep.path)) {
                    if (outputFile.exists()) result.add(outputFile.path)
                } else {
                    log(2, "Dex command failed")
                }
            } else {
                log(2, "  $dep already predexed")
            }
        }
        return result
    }

    /**
     * aapt returns 0 even if it fails, so in order to detect whether it failed, we are checking
     * if its error stream contains anything.
     */
    inner class AaptCommand(project: Project, aapt: String, val aaptCommand: String,
            cwd: File = File(".")) : AndroidCommand(project, androidHome(project), aapt) {
        init {
            directory = cwd
            useErrorStreamAsErrorIndicator = true
        }

        override fun call(args: List<String>) = super.run(arrayListOf(aaptCommand) + args,
                errorCallback = { l: List<String> -> println("ERRORS: $l")},
                successCallback = { l: List<String> -> })
    }

    private fun explodedAarDirectories(project: Project) : List<Pair<IClasspathDependency, File>> {
        val result =
            if (project.dependencies != null) {
                val transitiveDependencies = dependencyManager.calculateDependencies(project, context,
                        allDependencies = project.dependencies!!.dependencies)
                transitiveDependencies.filter { it.isMaven && isAar(MavenId.create(it.id)) }
                        .map {
                            Pair(it, File(AndroidFiles.exploded(project, MavenId.create(it.id))))
                        }
            } else {
                emptyList()
            }

        return result
    }

    /**
     * Extract all the .aar files found in the dependencies and add their android.jar to classpathEntries,
     * which will be added to the classpath at compile time via the classpath interceptor.
     */
    private fun explodeAarFiles(project: Project) : List<File> {
        log(2, "Exploding aars")
        val result = arrayListOf<File>()
        explodedAarDirectories(project).forEach { pair ->
            val (dep, destDir) = pair
            val mavenId = MavenId.create(dep.id)
            if (!File(AndroidFiles.explodedManifest(project, mavenId)).exists()) {
                log(2, "  Exploding ${dep.jarFile.get()} to $destDir")
                JarUtils.extractJarFile(dep.jarFile.get(), destDir)
            } else {
                log(2, "  $destDir already exists, not extracting again")
            }
            val classesJar = AndroidFiles.explodedClassesJar(project, mavenId)

            // Add the classses.jar of this .aar to the classpath entries (which are returned via IClasspathContributor)
            classpathEntries.put(project.name, FileDependency(classesJar))
            // Also add all the jar files found in the libs/ directory
            File(destDir, "libs").let { libsDir ->
                if (libsDir.exists()) {
                    libsDir.listFiles().filter { it.name.endsWith(".jar") }.forEach {
                        classpathEntries.put(project.name, FileDependency(it.absolutePath))
                    }
                }
            }
            result.add(destDir)
        }
        return result
    }

    /**
     * Implements ICompilerFlagContributor
     * Make sure we compile and generate 1.6 sources unless the build file defined those (which can
     * happen if the developer is using RetroLambda for example).
     */
    override fun flagsFor(project: Project, context: KobaltContext, currentFlags: List<String>,
            suffixesBeingCompiled: List<String>) : List<String> {
        if (isAndroid(project) && suffixesBeingCompiled.contains("java")) {
            var found = currentFlags.any { it == "-source" || it == "-target" }
            val result = arrayListOf<String>().apply { addAll(currentFlags) }
            if (! found) {
                result.add("-source")
                result.add("1.6")
                result.add("-target")
                result.add("1.6")
                result.add("-nowarn")
            }
            return result
        } else {
            return emptyList()
        }
    }

    @Task(name = "proguard", description = "Run Proguard, if enabled", runBefore = arrayOf(TASK_GENERATE_DEX),
            runAfter = arrayOf("compile"))
    fun taskProguard(project: Project): TaskResult {
        val config = configurationFor(project)
        if (config != null) {
            val buildType = context.variant.buildType
            if (buildType.minifyEnabled) {
                log(1, "minifyEnabled is true, running Proguard (not implemented yet)")
//                val classesDir = project.classesDir(context)
//                val proguardHome = KFiles.joinDir(androidHome(project), "tools", "proguard")
//                val proguardCommand = KFiles.joinDir(proguardHome, "bin", "proguard.sh")
            }
        }
        return TaskResult()
    }

    private fun dependencies(project: Project) = dependencyManager.calculateDependencies(project,
            context,
            project.dependentProjects,
            allDependencies = project.compileDependencies).map {
            it.jarFile.get().path
        }.filterNot {
            it.contains("android.jar") || it.endsWith(".aar") || it.contains("retrolambda")
        }.toHashSet().toTypedArray()

    class DexCommand : RunCommand("java") {
        override fun isSuccess(callSucceeded: Boolean, input: List<String>, error: List<String>) =
                error.size == 0
    }

    private fun inputChecksum(classDirectory: String) = Md5.toMd5Directories(listOf(File(classDirectory)))

    /**
     * @return true if dex succeeded
     */
    private fun runDex(project: Project, outputJarFile: String, target: String) : Boolean {
//        DexProcessBuilder(File(jarFile)).
        val args = arrayListOf(
                "-cp", KFiles.joinDir(androidHome(project), "build-tools", buildToolsVersion(project), "lib", "dx.jar"),
                "com.android.dx.command.Main",
                "--dex",
                "--num-threads=4",
                "--output", outputJarFile)
        if (KobaltLogger.LOG_LEVEL == 3) {
            args.add("--verbose")
        }
        var hasFiles = false
        if (preDexFiles.size > 0) {
            args.addAll(preDexFiles.filter { File(it).exists() })
            hasFiles = true
        }
        if (File(target).isDirectory) {
            val classFiles = KFiles.findRecursively(File(target), { f -> f.endsWith(".class") })
            if (classFiles.size > 0) {
                args.add(target)
                hasFiles = true
            }
        } else {
            args.add(target)
            hasFiles = true
        }

        val exitCode = if (hasFiles) DexCommand().run(args) else 0
        return exitCode == 0
    }

    @IncrementalTask(name = TASK_GENERATE_DEX, description = "Generate the dex file", runBefore = arrayOf("assemble"),
            runAfter = arrayOf("compile"))
    fun taskGenerateDex(project: Project): IncrementalTaskInfo {
        File(project.classesDir(context)).mkdirs()
        return IncrementalTaskInfo(
                inputChecksum = inputChecksum(project.classesDir(context)),
                outputChecksum = { -> null }, // TODO: return real checksum
                task = { project -> doTaskGenerateDex(project) }
        )
    }

    fun doTaskGenerateDex(project: Project): TaskResult {
        // Don't run dex if we're producing an aar
        val runDex = aars[project.name] == null
        if (runDex) {
            //
            // Call dx to generate classes.dex
            //
            val classesDexDir = KFiles.joinDir(AndroidFiles.intermediates(project), "dex",
                    context.variant.toIntermediateDir())
            File(classesDexDir).mkdirs()
            val classesDex = "classes.dex"
            val outClassesDex = KFiles.joinDir(classesDexDir, classesDex)

            val dexSuccess =
                    runDex(project, outClassesDex, KFiles.joinDir(project.directory, project.classesDir(context)))

            val aaptSuccess =
                if (dexSuccess) {
                    //
                    // Add classes.dex to existing .ap_
                    // Because aapt doesn't handle directory moving, we need to cd to classes.dex's directory so
                    // that classes.dex ends up in the root directory of the .ap_.
                    //
                    val aaptExitCode = AaptCommand(project, aapt(project), "add").apply {
                        directory = File(outClassesDex).parentFile
                    }.call(listOf("-v", KFiles.joinDir(
                        File(AndroidFiles.temporaryApk(project, context.variant.shortArchiveName)).absolutePath),
                                classesDex))
                    aaptExitCode == 0
                } else {
                    false
                }

            return TaskResult(dexSuccess && aaptSuccess)
        } else {
            return TaskResult()
        }
    }

    private val DEFAULT_DEBUG_SIGNING_CONFIG = SigningConfig(
            SigningConfig.DEFAULT_STORE_FILE,
            SigningConfig.DEFAULT_STORE_PASSWORD,
            SigningConfig.DEFAULT_KEY_ALIAS,
            SigningConfig.DEFAULT_KEY_PASSWORD)

    /**
     * Sign the apk
     * Mac:
     * jarsigner -keystore ~/.android/debug.keystore -storepass android -keypass android -signedjar a.apk a.ap_
     * androiddebugkey
     */
    @Task(name = TASK_SIGN_APK, description = "Sign the apk file", runAfter = arrayOf(TASK_GENERATE_DEX),
            runBefore = arrayOf("assemble"))
    fun taskSignApk(project: Project): TaskResult {
        val apk = AndroidFiles.apk(project, context.variant.shortArchiveName)
        val temporaryApk = AndroidFiles.temporaryApk(project, context.variant.shortArchiveName)
        val buildType = context.variant.buildType.name

        val config = configurationFor(project)
        var signingConfig = config!!.signingConfigs[buildType]

        if (signingConfig == null && buildType != "debug") {
            log(2, "Warning: No signingConfig found for product type \"$buildType\", using the \"debug\" signConfig")
        }

        signingConfig = DEFAULT_DEBUG_SIGNING_CONFIG

        val success = RunCommand("jarsigner").apply {
//            useInputStreamAsErrorIndicator = true
        }.run(listOf(
                "-keystore", signingConfig.storeFile,
                "-storepass", signingConfig.storePassword,
                "-keypass", signingConfig.keyPassword,
                "-signedjar", apk,
                temporaryApk,
                signingConfig.keyAlias
            ))
            log(1, "Created $apk")

        return TaskResult(success == 0)
    }

    @Task(name = TASK_INSTALL, description = "Install the apk file", runAfter = arrayOf(TASK_GENERATE_DEX, "assemble"))
    fun taskInstall(project: Project): TaskResult {

        /**
         * adb has weird ways of signaling errors, that's the best I've found so far.
         */
        class AdbInstall : RunCommand(adb(project)) {
            override fun isSuccess(callSucceeded: Boolean, input: List<String>, error: List<String>)
                = input.filter { it.contains("Success")}.size > 0
        }

        val apk = AndroidFiles.apk(project, context.variant.shortArchiveName)
        val result = AdbInstall().useErrorStreamAsErrorIndicator(true).run(
                args = listOf("install", "-r", apk))
        log(1, "Installed $apk")
        return TaskResult(result == 0)
    }

    private val classpathEntries = HashMultimap.create<String, IClasspathDependency>()

    // IClasspathContributor
    override fun classpathEntriesFor(project: Project?, context: KobaltContext): Collection<IClasspathDependency> {
        if (project == null || ! accept(project)) return emptyList()

        val aarFiles : Collection<IClasspathDependency> = classpathEntries.get(project.name)
                ?: emptyList<IClasspathDependency>()
        val classes : Collection<IClasspathDependency>
                = listOf(FileDependency(AndroidFiles.intermediatesClasses(project, context)))

        return aarFiles + classes
    }

    // IRepoContributor
    override fun reposFor(project: Project?): List<HostConfig> {
        val config = configurationFor(project)
        var home = AndroidFiles.androidHomeNoThrows(project, config)

        return if (home != null) {
            listOf(KFiles.joinDir(home, "extras", "android", "m2repository"),
                    (KFiles.joinDir(home, "extras", "google", "m2repository")))
                .map { HostConfig(Paths.get(it).toUri().toString()) }
        } else {
            emptyList()
        }
    }

    // IBuildDirectoryInterceptor
    override fun intercept(project: Project, context: KobaltContext, buildDirectory: String)
        = if (isAndroid(project)) {
            val c = AndroidFiles.intermediatesClasses(project, context)
            val result = Paths.get(project.directory).relativize(Paths.get(c))
            result.toString()
        } else {
            buildDirectory
        }

    // ICompilerInterceptor
    /**
     * The output directory for the user's classes is kobaltBuild/intermediates/classes/classes (note: two "classes")
     * since that top directory contains additional directories for dex, pre-dexed, etc...)
     */
    override fun intercept(project: Project, context: KobaltContext, actionInfo: CompilerActionInfo)
            : CompilerActionInfo {
        val result: CompilerActionInfo =
            if (isAndroid(project)) {
                val newBuildDir = project.classesDir(context)
                actionInfo.copy(outputDir = File(newBuildDir))
            } else {
                actionInfo
            }
        return result
    }

    // IRunContributor
    override fun affinity(project: Project, context: KobaltContext): Int {
        val manifest = AndroidFiles.manifest(project)
        return if (File(manifest).exists()) IAffinity.DEFAULT_POSITIVE_AFFINITY else 0
    }

    override fun run(project: Project, context: KobaltContext, classpath: List<IClasspathDependency>): TaskResult {
        AndroidFiles.mergedManifest(project, context.variant).let { manifestPath ->
            FileInputStream(File(manifestPath)).use { ins ->
                // adb shell am start -n com.package.name/com.package.name.ActivityName
                val manifest = AndroidManifest(ins)
                RunCommand(adb(project)).useErrorStreamAsErrorIndicator(false).run(args = listOf(
                        "shell", "am", "start", "-n", manifest.pkg + "/" + manifest.mainActivity))
                return TaskResult()
            }
        }
    }

    private fun isAar(id: MavenId) = (id.groupId == "com.android.support" || id.groupId == "com.google.android")
            && id.artifactId != "support-annotations"

    /**
     * For each com.android.support dependency or aar packaging, add a classpath dependency that points to the
     * classes.jar inside that (exploded) aar.
     */
    // IClasspathInterceptor
    override fun intercept(project: Project, dependencies: List<IClasspathDependency>): List<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        dependencies.filter { it.isMaven }.forEach {
            val mavenId = MavenId.create(it.id)
            if (isAar(mavenId) || mavenId.packaging == "aar") {
                val newDep = context.dependencyManager.createFile(AndroidFiles.explodedClassesJar(project, mavenId))
                result.add(newDep)
                val id = MavenId.create(mavenId.groupId, mavenId.artifactId, "aar", it.version)
                result.add(context.dependencyManager.create(id.toId))
            } else {
                result.add(it)
            }
        }
        return result
    }

    // IMavenIdInterceptor
    override fun intercept(mavenId: MavenId) : MavenId =
        if (isAar(mavenId)) {
            val version = mavenId.version ?: ""
            MavenId.createNoInterceptors("${mavenId.groupId}:${mavenId.artifactId}:aar:$version")
        } else {
            mavenId
        }

    /**
     * The extra source directories (without the project directory, which will be added by Kobalt).
     */
    private val extraSourceDirectories = arrayListOf(File("src/main/aidl"))

    // ISourceDirectoryContributor
    override fun sourceDirectoriesFor(project: Project, context: KobaltContext): List<File> = extraSourceDirectories

    // IBuildConfigFieldContributor
    override fun fieldsFor(project: Project, context: KobaltContext): List<BuildConfigField> {
        val result = arrayListOf<BuildConfigField>()
        configurationFor(project)?.let { config ->
            result.add(BuildConfigField("String", "VERSION_NAME", "\"${config.defaultConfig.versionName}\""))
            result.add(BuildConfigField("int", "VERSION_CODE", "${config.defaultConfig.versionCode}"))
        }
        return result
    }

    //ITaskContributor
    override fun tasksFor(context: KobaltContext): List<DynamicTask> = taskContributor.dynamicTasks

    // ITemplateContributor
    override val templates = Templates().templates

    // IAssemblyContributor
    override fun assemble(project: Project, context: KobaltContext) : TaskResult {
        val pair = aars[project.name]
        if (pair != null) {
            aarGenerator.generate(project, context, pair.second)
        }
        return TaskResult()
    }

    val aars = hashMapOf<String, Pair<Project, AarConfig>>()

    fun addAar(project: Project, aarConfig: AarConfig) {
        aars[project.name] = Pair(project, aarConfig)
    }
}

class DefaultConfig(var minSdkVersion: Int? = 22,
        var maxSdkVersion: String? = null,
        var targetSdkVersion: String? = null,
        var versionCode: Int? = 1,
        var versionName: String? = null) {
    var buildConfig : BuildConfig? = BuildConfig()
}

@Directive
fun Project.android(init: AndroidConfig.() -> Unit) : AndroidConfig = let { project ->
    return AndroidConfig(project).apply {
        init()
        (Kobalt.findPlugin(AndroidPlugin.PLUGIN_NAME) as AndroidPlugin).addConfiguration(project, this)
    }
}

class SigningConfig(var storeFile: String = SigningConfig.DEFAULT_STORE_FILE,
        var storePassword: String = SigningConfig.DEFAULT_STORE_PASSWORD,
        var keyAlias: String = SigningConfig.DEFAULT_KEY_ALIAS,
        var keyPassword: String = SigningConfig.DEFAULT_KEY_ALIAS) {

    companion object {
        val DEFAULT_STORE_FILE = homeDir(".android", "debug.keystore")
        val DEFAULT_STORE_PASSWORD = "android"
        val DEFAULT_KEY_ALIAS = "androiddebugkey"
        val DEFAULT_KEY_PASSWORD = "android"
    }
}

@Directive
fun AndroidConfig.signingConfig(name: String, init: SigningConfig.() -> Unit) : SigningConfig = let { androidConfig ->
    SigningConfig().apply {
        init()
        androidConfig.addSigningConfig(name, project, this)
    }
}

class AarConfig{
    var name: String? = null
}

/**
 * Create an aar file.
 */
@Directive
fun AndroidConfig.aar(init: AarConfig.() -> Unit) {
    val aarConfig = AarConfig().apply { init() }
    (Kobalt.findPlugin(AndroidPlugin.PLUGIN_NAME) as AndroidPlugin).addAar(project, aarConfig)
}

//fun main(argv: Array<String>) {
//    com.beust.kobalt.main(argv)
//}
