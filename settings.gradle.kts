
rootProject.name = "postfix-rest-connector"

pluginManagement {

    val kotlinVersion: String by settings
    val jibVersion: String by settings
    val detektVersion: String by settings
    val shadowVersion: String by settings

    plugins {
        kotlin("multiplatform") version(kotlinVersion)
        kotlin("plugin.serialization") version(kotlinVersion)
        id("com.google.cloud.tools.jib") version(jibVersion)
        id("io.gitlab.arturbosch.detekt") version(detektVersion)
        id("com.gradleup.shadow") version(shadowVersion)
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
