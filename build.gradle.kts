import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    signing
    java
    `maven-publish`
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.google.cloud.tools.jib")
    id("io.gitlab.arturbosch.detekt")
}

group = "tel.schich"
version = "2.0.1-SNAPSHOT"

val ktorVersion = "3.0.0"
val coroutinesVersion = "1.9.0"
val serializationVersion = " 1.7.3"

repositories {
    mavenCentral()
}

tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvm {
        withJava()
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JVM_11)
                    freeCompilerArgs.add("-progressive")
                }
            }
        }
    }

    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("io.ktor:ktor-client-core:$ktorVersion")
                api("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                api("io.ktor:ktor-network:$ktorVersion")
                api("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                api("org.jetbrains.kotlinx:kotlinx-io-core:0.5.4")
                api("io.github.oshai:kotlin-logging:7.0.0")
            }
        }

        val jvmMain by getting {
            dependencies {
                api("io.ktor:ktor-client-java:$ktorVersion")
            }
        }

        val linuxX64Main by getting {
            dependencies {
                api("io.ktor:ktor-client-cio:$ktorVersion")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("io.ktor:ktor-client-mock:$ktorVersion")
            }
        }
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
