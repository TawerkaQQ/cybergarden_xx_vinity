plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":api"))
    implementation(project(":data"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.logback.classic)
}

application {
    mainClass.set("cybergarden.vinity.server.ApplicationKt")
}

tasks.named<JavaExec>("run") {
    systemProperty("vinity.root", rootDir.parentFile.absolutePath)
}
