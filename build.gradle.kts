import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
import org.jetbrains.kotlin.gradle.plugin.HasKotlinDependencies
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    signing
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
val serializationVersion = "1.7.3"

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

    fun KotlinNativeTarget.configureNativeTarget() {
        binaries {
            executable(listOf(NativeBuildType.RELEASE, NativeBuildType.DEBUG)) {
                entryPoint = "tel.schich.postfixrestconnector.main"
            }
        }
    }

    linuxX64 {
        configureNativeTarget()
    }

    linuxArm64 {
        configureNativeTarget()
    }

    mingwX64 {
        configureNativeTarget()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project.dependencies.platform("io.ktor:ktor-bom:$ktorVersion"))
                api(project.dependencies.platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:$serializationVersion"))
                api(project.dependencies.platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:$coroutinesVersion"))
                api("io.ktor:ktor-client-core")
                api("io.ktor:ktor-client-content-negotiation")
                api("io.ktor:ktor-network")
                api("io.ktor:ktor-serialization-kotlinx-json")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json")
                api("org.jetbrains.kotlinx:kotlinx-io-core:0.5.4")
                api("io.github.oshai:kotlin-logging:7.0.0")
            }
        }

        val jvmMain by getting {
            dependencies {
                api("io.ktor:ktor-client-java")
            }
        }

        fun HasKotlinDependencies.nativeDependencies() {
            dependencies {
                api("io.ktor:ktor-client-cio")
            }
        }

        val linuxX64Main by getting {
            nativeDependencies()
        }

        val linuxArm64Main by getting {
            nativeDependencies()
        }

        val mingwX64Main by getting {
            nativeDependencies()
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("io.ktor:ktor-client-mock")
                implementation("io.ktor:ktor-server-core")
                implementation("io.ktor:ktor-server-cio")
                implementation("io.ktor:ktor-server-content-negotiation")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation("ch.qos.logback:logback-classic:1.5.12")
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
