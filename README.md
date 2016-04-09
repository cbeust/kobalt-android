Android plug-in for [Kobalt](http://beust.com/kobalt).

# Features

The Kobalt Android plug-in offers the following features:

- Automatic SDK downloading
- Resource merging
- Manifest merging
- Predexing
- Generation of apk files
- Install and run activities on the device

It also supports features already offered by Kobalt itself:

- `BuildConfig`
- Incremental tasks

The plug-in uses Google's Android build library, which guarantees that all these operations are being performed the same way as the official Gradle Android plug-in. This also makes it easier to add new functionalities as they get added to the official Android builder library.

For a full example of a complex Android application being built with Kobalt, take a look at the [Kobalt u2020 fork](https://github.com/cbeust/u2020/blob/build-with-kobalt/kobalt/src/Build.kt).

# SDK Management

Kobalt will automatically everything that is necessary to build Android applications, including:

- The Android SDK
- Support libraries
- Build tools
- etc...

The plug-in will start by looking if `$ANDROID_HOME` is defined and if it is, use it. It it's not defined, the SDK will
be downloaded in `~/.android-sdk`. You can find more details about this feature [in this article](http://beust.com/weblog/2016/04/09/the-kobalt-diaries-automatic-android-sdk-management/).

# Build file

You introduce Android inside your project by including the Kobalt Android plug-in:

```
val pl = plugins("com.beust:kobalt-android:")
```

Import the symbols:

```
import com.beust.kobalt.plugin.android.*
```

You can now use the `android` directive inside your project:

```
val p = project {
    // ...

    android {
        defaultConfig {
            minSdkVersion = 15
            versionCode = 100
            versionName = "1.0.0"
            compileSdkVersion = "23"
            buildToolsVersion = "23.0.1"
            applicationId = "com.beust.myapp"
        }
    }
```

These fields should look familiar to Android developers. The `defaultConfig` directive defines values that will apply to all variants of your build, but each variant can override any of these values.

# Tasks

The Kobalt Android plug-in introduces a few new tasks (you can get the full list with `./kobaltw --tasks` with the plug-in enabled). The ones you will likely use the most often are:

- `install`. Build and install the apk on the device.
- `run`. Run the main activity of your application on the device.

The plug-in also creates tasks for each of the variants found in your build file, e.g. `installProductionDebug`, `runInternalRelease`, etc...

# Libraries

By default, an Android project will output an apk but you can request an aar file to be produced with the `aar{}`
directive:

```
val p = project {
    // ...

    android {
        aar {
        }
    }
}
```

This directive lets you specify additional configuration parameters such as the `name` of the aar file to produce. Refer to the documentation of the `AarConfig` class for more details.
