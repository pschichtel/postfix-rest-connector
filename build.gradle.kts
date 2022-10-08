import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    signing
    java
    `maven-publish`
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka")
    id("io.github.gradle-nexus.publish-plugin")
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
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("io.github.microutils:kotlin-logging:3.0.0")
    implementation("ch.qos.logback:logback-classic:1.4.3")
    testImplementation(kotlin("test"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

val sourcesJar by tasks.creating(Jar::class) {
    dependsOn(JavaPlugin.CLASSES_TASK_NAME)
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

val javadocJar by tasks.creating(Jar::class) {
    dependsOn(tasks.dokkaJavadoc)
    archiveClassifier.set("javadoc")
    from(tasks.dokkaJavadoc)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)
            pom {
                name.set("Postfix REST Connector")
                description.set("A simple TCP server that can be used as tcp lookup, socketmap lookup or policy check server for the Postfix mail server.")
                url.set("https://github.com/pschichtel/postfix-rest-connector")
                licenses {
                    license {
                        name.set("GPLv3")
                        url.set("http://www.gnu.org/licenses/gpl.txt")
                    }
                }
                developers {
                    developer {
                        id.set("pschichtel")
                        name.set("Phillip Schichtel")
                        email.set("phillip@schich.tel")
                    }
                }
                scm {
                    url.set("https://github.com/pschichtel/postfix-rest-connector")
                    connection.set("scm:git:https://github.com/pschichtel/postfix-rest-connector")
                    developerConnection.set("scm:git:git@github.com:pschichtel/postfix-rest-connector")
                }
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["mavenJava"])
}

nexusPublishing {
    repositories {
        sonatype()
    }
}
