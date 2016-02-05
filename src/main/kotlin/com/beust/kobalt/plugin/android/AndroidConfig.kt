package com.beust.kobalt.plugin.android

import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive

class AndroidConfig(val project: Project,
        var compileSdkVersion : String? = null,
        var buildToolsVersion: String? = null,
        var applicationId: String? = null,
        val androidHome: String? = null) {

    val signingConfigs = hashMapOf<String, SigningConfig>()

    fun addSigningConfig(name: String, project: Project, signingConfig: SigningConfig) {
        signingConfigs.put(name, signingConfig)
    }

    var defaultConfig: DefaultConfig = DefaultConfig()

    @Directive
    fun defaultConfig(init: DefaultConfig.() -> Unit) {
        defaultConfig = DefaultConfig().apply { init() }
    }
}

