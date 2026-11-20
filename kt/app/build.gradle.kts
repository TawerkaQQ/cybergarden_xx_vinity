import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

kotlin {
    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "vinity"
        browser {
            commonWebpackConfig {
                outputFileName = "vinity.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":api"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.ktor.client.cio)
            }
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

compose.resources {
    packageOfResClass = "cybergarden.vinity.app.resources"
}

compose.desktop {
    application {
        mainClass = "cybergarden.vinity.app.MainKt"
    }
}
