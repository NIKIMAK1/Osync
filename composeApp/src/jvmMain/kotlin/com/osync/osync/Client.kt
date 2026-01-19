package osync.osync

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder

class SyncClient {
    private val httpClient = HttpClient(CIO) {
        engine { requestTimeout = 0 }
    }

    suspend fun syncFrom(serverIp: String, localOsuDir: File, gameType: String, onLog: (String) -> Unit) = withContext(Dispatchers.IO) {
        val baseUrl = "http://$serverIp:8085"

        if (!localOsuDir.exists()) localOsuDir.mkdirs()

        try {
            try {
                val remoteType = httpClient.get("$baseUrl/ping").bodyAsText()
                if (remoteType != gameType) {
                    onLog("ОШИБКА: На сервере выбран режим $remoteType, а у вас $gameType. Они должны совпадать!")
                    return@withContext
                }
            } catch (e: Exception) {
                onLog("Ошибка подключения к $serverIp. Проверьте IP и фаервол.")
                return@withContext
            }

            if (gameType == "LAZER") {
                syncLazer(baseUrl, localOsuDir, onLog)
            } else {
                syncStable(baseUrl, localOsuDir, onLog)
            }

            onLog("=== Готово! ===")
            if (gameType == "STABLE") onLog("В Osu! Stable нажмите F5 в меню выбора песен.")

        } catch (e: Exception) {
            onLog("Критическая ошибка: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun syncStable(baseUrl: String, localOsuDir: File, onLog: (String) -> Unit) {
        val localSongs = File(localOsuDir, "Songs")
        if (!localSongs.exists()) localSongs.mkdirs()

        onLog("Получение списка песен с сервера...")
        val manifestResponse = httpClient.get("$baseUrl/manifest")

        val remoteFiles = manifestResponse.bodyAsText().lines()
            .filter { it.isNotBlank() }
            .toSet()

        onLog("Файлов на сервере: ${remoteFiles.size}")

        val localFiles = localSongs.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(localSongs).path.replace("\\", "/") }
            .toSet()

        val toDownload = remoteFiles - localFiles

        if (toDownload.isEmpty()) {
            onLog("Все песни уже на месте!")
            return
        }

        onLog("Нужно скачать файлов: ${toDownload.size}")

        var downloaded = 0
        toDownload.forEach { relativePath ->
            val targetFile = File(localSongs, relativePath)
            targetFile.parentFile.mkdirs()

            try {
                val encodedPath = URLEncoder.encode(relativePath, "UTF-8").replace("+", "%20")
                val fileResp = httpClient.get("$baseUrl/stable-file?path=$encodedPath")

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
                    onLog("Ошибка скачивания: $relativePath")
                }
            } catch (e: Exception) {
                onLog("Сбой: $relativePath (${e.message})")
            }
        }
    }

    private suspend fun syncLazer(baseUrl: String, localOsuDir: File, onLog: (String) -> Unit) {
        onLog("Скачивание client.realm...")
        val dbResponse = httpClient.get("$baseUrl/realm")
        if (dbResponse.status == HttpStatusCode.OK) {
            val localDb = File(localOsuDir, "client.realm")
            if (localDb.exists()) localDb.renameTo(File(localOsuDir, "client.realm.bak"))

            val tempFile = File(localOsuDir, "client.realm.tmp")
            val ch = dbResponse.bodyAsChannel()
            val fos = tempFile.outputStream()
            ch.copyTo(fos)
            fos.close()

            if (localDb.exists()) localDb.delete()
            tempFile.renameTo(localDb)
            onLog("База данных обновлена.")
        }

        onLog("Получение списка хешей...")
        val manifestResponse = httpClient.get("$baseUrl/manifest")
        val remoteHashes = manifestResponse.bodyAsText().lines().filter { it.isNotBlank() }.toSet()

        val localFilesDir = File(localOsuDir, "files")
        val localHashes = localFilesDir.walkTopDown().filter { it.isFile }.map { it.name }.toSet()
        val toDownload = remoteHashes - localHashes

        onLog("Нужно скачать: ${toDownload.size}")

        var downloaded = 0
        toDownload.forEach { hash ->
            val targetFile = OsuUtils.getLazerFileByHash(localOsuDir, hash)
            targetFile.parentFile.mkdirs()
            try {
                val resp = httpClient.get("$baseUrl/file/$hash")
                if (resp.status == HttpStatusCode.OK) {
                    val ch = resp.bodyAsChannel()
                    val fos = targetFile.outputStream()
                    ch.copyTo(fos)
                    fos.close()
                    downloaded++
                    if (downloaded % 10 == 0) onLog("Скачано: $downloaded")
                }
            } catch (e: Exception) { onLog("Сбой: $hash") }
        }
    }
}