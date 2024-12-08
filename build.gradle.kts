import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    signing
    `maven-publish`
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.jib)
    alias(libs.plugins.detekt)
    alias(libs.plugins.shadow)
}

group = "tel.schich"
version = "3.0.0"

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
                api(libs.ktorClientCore)
                api(libs.ktorClientContentNegotiation)
                api(libs.ktorNetwork)
                api(libs.ktorSerializationKotlinxJson)
                api(libs.kotlinxCoroutinesCore)
                api(libs.kotlinxSerializationJson)
                api(libs.kotlinxIoCore)
                api(libs.kotlinLogging)
            }
        }

        val jvmMain by getting {
            dependencies {
                api(libs.ktorClientJava)
            }
        }

        val nativeMain by creating {
            dependencies {
                api(libs.ktorClientCio)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.ktorClientMock)
                implementation(libs.ktorServerCore)
                implementation(libs.ktorServerCio)
                implementation(libs.ktorServerContentNegotiation)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.logbackClassic)
            }
        }
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget = JVM_11
    }
}

tasks.withType<JavaCompile> {
    targetCompatibility = JVM_11.target
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

val shadowJar by tasks.registering(ShadowJar::class) {
    group = "shadow"

    val mainCompilation = kotlin.jvm().compilations.getByName("main")
    from(mainCompilation.output)
    configurations.add(mainCompilation.compileDependencyFiles)
    manifest {
        attributes["Main-Class"] = "tel.schich.postfixrestconnector.MainKt"
    }
}
