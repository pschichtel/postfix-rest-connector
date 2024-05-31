import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    signing
    java
    `maven-publish`
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.google.cloud.tools.jib")
    id("io.gitlab.arturbosch.detekt")
}

group = "tel.schich"
version = "2.0.1-SNAPSHOT"

val ktorVersion = "2.3.11"
val coroutinesVersion = "1.8.1"
val serializationVersion = " 1.6.3"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-java:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-network:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.3.5")
    implementation("io.github.oshai:kotlin-logging:5.1.0")
    implementation("ch.qos.logback:logback-classic:1.5.6")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

jib {
    from {
        image = "docker.io/library/eclipse-temurin:21-jre-alpine@sha256:98963ed09c4fd82e128c6cc9e64d71798239b824164276626d79b8f9e666ac0e"
    }
    container {
        ports = listOf("8080")
    }
    to {
        image = "ghcr.io/pschichtel/$name:$version"
    }
}

detekt {
    parallel = true
    config.setFrom(files(project.rootDir.resolve("detekt.yml")))
}
