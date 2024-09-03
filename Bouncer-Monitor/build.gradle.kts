plugins {
    kotlin("jvm") version "1.9.24"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.sigma"
version = "1.0"

val aaddVersion = "0.0.5"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.github.tukcps:aadd:$aaddVersion")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

application {
    mainClass.set("MainKt")
}

// Configure Shadow plugin
tasks {
    val shadowJar by getting(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
        archiveClassifier.set("all") // Name of the JAR will be project-name-1.0-all.jar
    }

    build {
        dependsOn(shadowJar)
    }
}
