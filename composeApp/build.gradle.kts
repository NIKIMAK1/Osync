import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)

            // ВАЖНО: Используем Material 3
            implementation(compose.material3)

            // Иконки (если еще нет)
            implementation(compose.materialIconsExtended)

            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)

            // Ktor (оставляем как было)
            implementation("io.ktor:ktor-server-core:2.3.7")
            implementation("io.ktor:ktor-server-netty:2.3.7")
            implementation("io.ktor:ktor-server-content-negotiation:2.3.7")
            implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
            implementation("io.ktor:ktor-client-core:2.3.7")
            implementation("io.ktor:ktor-client-cio:2.3.7")
            implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
        }
    }
}

compose.desktop {
    application {
        // Указываем точку входа. Пакет будет "osync"
        mainClass = "osync.osync.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Osync"
            packageVersion = "1.0.0"
        }
    }
}