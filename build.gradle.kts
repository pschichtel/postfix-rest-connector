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
version = "2.0.0-SNAPSHOT"

val ktorVersion = "2.1.2"
val coroutinesVersion = "1.6.4"
val serializationVersion = "1.4.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-network:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("io.github.microutils:kotlin-logging:3.0.0")
    implementation("ch.qos.logback:logback-classic:1.4.3")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

jib {
    from {
        image = "eclipse-temurin:17-jre-alpine"
    }
    container {
        ports = listOf("8080")
    }
    to {
        val dockerHubUsername = System.getenv("DOCKERHUB_USERNAME")
        val dockerHubPassword = System.getenv("DOCKERHUB_PASSWORD")
        if (dockerHubUsername != null && dockerHubPassword != null) {
            auth {
                username = dockerHubUsername
                password = dockerHubPassword
            }
        }
        image = "pschichtel/$name:$version"
    }
}

detekt {
    parallel = true
    config = files(project.rootDir.resolve("detekt.yml"))
}
