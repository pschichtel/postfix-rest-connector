
rootProject.name = "postfix-rest-connector"

pluginManagement {

    val kotlinVersion: String by settings
    val jibVersion: String by settings
    val detektVersion: String by settings

    plugins {
        kotlin("jvm") version(kotlinVersion)
        kotlin("plugin.serialization") version(kotlinVersion)
        id("com.google.cloud.tools.jib") version(jibVersion)
        id("io.gitlab.arturbosch.detekt") version(detektVersion)
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
}
