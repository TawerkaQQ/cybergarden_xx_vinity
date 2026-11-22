plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
}

application {
    mainClass.set("cybergarden.vinity.cli.MainKt")
    applicationName = "vinity"
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
}
