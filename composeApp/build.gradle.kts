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

            // UI: Material 3 + Иконки
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)

            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)

            // --- ВАЖНОЕ ИСПРАВЛЕНИЕ ---
            // Мы принудительно ставим версию 1.7.3, чтобы починить вылет (NoSuchMethodError).
            // Ktor 2.3.7 не работает с версией 1.8.0+, которая у тебя стояла раньше.
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")

            // Логирование (чтобы видеть ошибки сервера)
            implementation("ch.qos.logback:logback-classic:1.4.14")

            // Ktor Сервер
            implementation("io.ktor:ktor-server-core:2.3.7")
            implementation("io.ktor:ktor-server-netty:2.3.7")
            implementation("io.ktor:ktor-server-content-negotiation:2.3.7")
            implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")

            // Ktor Клиент
            implementation("io.ktor:ktor-client-core:2.3.7")
            implementation("io.ktor:ktor-client-cio:2.3.7")
            implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
        }
    }
}

compose.desktop {
    application {
        // Точка входа. У тебя файлы лежат в пакете osync.osync
        mainClass = "osync.osync.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Osync"
            packageVersion = "1.0.0"

            // Если нужно будет видеть консоль с ошибками на Windows, раскомментируй:
            // windows {
            //    console = true
            // }
        }
    }
}