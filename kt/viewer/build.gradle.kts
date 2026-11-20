plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.pdfbox)
}

compose.desktop {
    application {
        mainClass = "cybergarden.vinity.viewer.MainKt"
        jvmArgs += listOf("-Dvinity.root=${rootDir.parentFile.absolutePath}")
    }
}
