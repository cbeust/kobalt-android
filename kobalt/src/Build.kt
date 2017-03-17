
import com.beust.kobalt.file
import com.beust.kobalt.homeDir
import com.beust.kobalt.plugin.kotlin.kotlinCompiler
import com.beust.kobalt.plugin.packaging.assemble
import com.beust.kobalt.plugin.publish.bintray
import com.beust.kobalt.project

val dev = false
val kobalt = "com.beust:kobalt-plugin-api:1.0.8"
val kobaltDev = file(homeDir("kotlin/kobalt/kobaltBuild/libs/kobalt-1.0.13.jar"))

val p = project {
    name = "kobalt-android"
    artifactId = name
    group = "com.beust"
    version = "0.98"

    dependencies {
//        provided("org.jetbrains:")
        compile("com.android.tools.build:builder:2.0.0-alpha3",
                "org.rauschig:jarchivelib:0.7.1")

        // Kobalt dependencies depending on whether I'm debugging or releasing.
        // To release, depend on "kobalt-plugin-api". For development, depend on "kobalt", which
        // provides a com.beust.kobalt.main() function so you can start Kobalt loaded with your
        // plug-in directly from your main plug-in class.
        compile(if (dev) kobaltDev else kobalt)
    }

    assemble {
        mavenJars {
            fatJar = true
        }
    }

    bintray {
        publish = true
    }

    kotlinCompiler {
        args("-nowarn")
    }
}
