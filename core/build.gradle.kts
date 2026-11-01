import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Everything that needs no Android: the game engine, the wire protocol, the
// question bank and the embedded web server. Plain JVM, so tests run in
// seconds and the logic cannot reach for a Context.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    runtimeOnly(libs.slf4j.nop)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.websockets)
}

// ./gradlew :core:devServer runs the real server and phone page on a laptop, with bots.
tasks.register<JavaExec>("devServer") {
    group = "application"
    description = "Serves the phone page and plays against bots, with no TV."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.fabriquefutur.qupa.DevServerKt")
}
