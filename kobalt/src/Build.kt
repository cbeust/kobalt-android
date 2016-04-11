
import com.beust.kobalt.file
import com.beust.kobalt.homeDir
import com.beust.kobalt.plugin.kotlin.kotlinCompiler
import com.beust.kobalt.plugin.packaging.assemble
import com.beust.kobalt.plugin.publish.bintray
import com.beust.kobalt.project
import com.beust.kobalt.repos

val r = repos("https://dl.bintray.com/cbeust/maven")

//val pl = plugins(file(homeDir("kotlin/kobalt-retrolambda/kobaltBuild/libs/kobalt-retrolambda-0.3.jar")))

val dev = false
val version = 735
val devVersion = version + 1
val dependency = "com.beust:kobalt-plugin-api:0.$version"
val devDependency = file(homeDir("kotlin/kobalt/kobaltBuild/libs/kobalt-0.$devVersion"))

val p = project {
    name = "kobalt-android"
    artifactId = name
    group = "com.beust"
    version = "0.84"

    dependencies {
        compile("com.android.tools.build:builder:2.0.0-alpha3",
                "org.rauschig:jarchivelib:0.7.1")

        // Kobalt dependencies depending on whether I'm debugging or releasing.
        // To release, depend on "kobalt-plugin-api". For development, depend on "kobalt", which
        // provides a com.beust.kobalt.main() function so you can start Kobalt loaded with your
        // plug-in directly from your main plug-in class.
        compile(if (dev) devDependency else dependency)
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
