
import com.beust.kobalt.file
import com.beust.kobalt.homeDir
import com.beust.kobalt.plugin.kotlin.kotlinCompiler
import com.beust.kobalt.plugin.packaging.assemble
import com.beust.kobalt.plugin.publish.bintray
import com.beust.kobalt.project
import com.beust.kobalt.repos

val r = repos("https://dl.bintray.com/cbeust/maven")

//val pl = plugins(file(homeDir("kotlin/kobalt-retrolambda/kobaltBuild/libs/kobalt-retrolambda-0.3.jar")))

val p = project {
    name = "kobalt-android"
    artifactId = name
    group = "com.beust"
    version = "0.14"

    dependencies {
        compile("com.android.tools.build:builder:2.0.0-alpha3",
                file(homeDir("kotlin/kobalt/kobaltBuild/libs/kobalt-0.506.jar")))
//        "com.beust:kobalt:0.505"
//         compile("com.beust:kobalt:0.356")
//
//      compile(
//              "com.beust:kobalt:0.505"
//              "com.beust:kobalt-plugin-api:0.505"
//              ,"com.android.tools.build:builder:2.0.0-alpha3"
//              )
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
