plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    kotlin("plugin.serialization") version "1.9.22"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}


dependencies {
    val libVersion = "2fe748d7c8"
    compileOnly("com.github.brahmkshatriya:echo:$libVersion")
    implementation("dev.toastbits.ytmkt:ytmkt:0.3.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    val ktorVersion = "3.0.0-beta-2"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.github.brahmkshatriya:echo:$libVersion")
}