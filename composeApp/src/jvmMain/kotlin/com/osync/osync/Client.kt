package osync.osync

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SyncClient {
    private val httpClient = HttpClient(CIO) {
        engine {
            requestTimeout = 0 // Отключаем таймаут для больших файлов
        }
    }

    suspend fun syncFrom(serverIp: String, localOsuDir: File, onLog: (String) -> Unit) = withContext(Dispatchers.IO) {
        val baseUrl = "http://$serverIp:8080"

        try {
            // ШАГ 1: База данных
            onLog("=== Начало синхронизации ===")
            onLog("Скачивание client.realm...")

            val dbResponse = httpClient.get("$baseUrl/realm")
            if (dbResponse.status == HttpStatusCode.OK) {
                val localDb = File(localOsuDir, "client.realm")

                // Бекап старой базы
                if (localDb.exists()) {
                    val backup = File(localOsuDir, "client.realm.bak")
                    if (backup.exists()) backup.delete()
                    localDb.renameTo(backup)
                }

                // Сохранение новой
                val channel = dbResponse.bodyAsChannel()
                val tempFile = File(localOsuDir, "client.realm.tmp")
                val stream = tempFile.outputStream()
                channel.copyTo(stream)
                stream.close()
                tempFile.renameTo(localDb)
                onLog("База данных обновлена.")
            } else {
                onLog("Ошибка: Не удалось скачать client.realm")
                return@withContext
            }

            // ШАГ 2: Список файлов
            onLog("Получение списка файлов с сервера...")
            val manifestResponse = httpClient.get("$baseUrl/manifest")
            if (manifestResponse.status != HttpStatusCode.OK) {
                onLog("Ошибка получения списка файлов")
                return@withContext
            }

            val remoteHashes = manifestResponse.bodyAsText().lines()
                .filter { it.isNotBlank() }
                .toSet()

            onLog("Файлов на сервере: ${remoteHashes.size}")

            // ШАГ 3: Сравнение
            val localFilesDir = File(localOsuDir, "files")
            val localHashes = localFilesDir.walkTopDown()
                .filter { it.isFile }
                .map { it.name }
                .toSet()

            val toDownload = remoteHashes - localHashes

            if (toDownload.isEmpty()) {
                onLog("Все файлы уже на месте. Синхронизация завершена!")
                return@withContext
            }

            onLog("Нужно скачать файлов: ${toDownload.size}")

            // ШАГ 4: Скачивание
            var downloaded = 0
            toDownload.forEach { hash ->
                val targetFile = OsuUtils.getFileByHash(localOsuDir, hash)
                targetFile.parentFile.mkdirs() // Создаем папки

                try {
                    val fileResp = httpClient.get("$baseUrl/file/$hash")
                    if (fileResp.status == HttpStatusCode.OK) {
                        val ch = fileResp.bodyAsChannel()
                        val fos = targetFile.outputStream()
                        ch.copyTo(fos)
                        fos.close()

                        downloaded++
                        if (downloaded % 10 == 0 || downloaded == toDownload.size) {
                            onLog("Скачано: $downloaded / ${toDownload.size}")
                        }
                    } else {
                        onLog("Ошибка скачивания файла: $hash")
                    }
                } catch (e: Exception) {
                    onLog("Сбой при скачивании $hash: ${e.message}")
                }
            }

            onLog("=== Готово! Перезапустите Osu! ===")

        } catch (e: Exception) {
            onLog("Критическая ошибка: ${e.message}")
            e.printStackTrace()
        }
    }
}