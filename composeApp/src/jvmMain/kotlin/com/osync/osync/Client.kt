package osync.osync

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

class SyncClient {
    private val httpClient = HttpClient(CIO) {
        engine {
            requestTimeout = 0
            endpoint {
                maxConnectionsPerRoute = 100
                pipelineMaxSize = 20
                keepAliveTime = 5000
                connectTimeout = 5000
            }
        }
    }

    private val semaphore = Semaphore(50)

    suspend fun syncFrom(inputAddress: String, localOsuDir: File, gameType: String, onLog: (String) -> Unit) = withContext(Dispatchers.IO) {
        val baseUrl = if (inputAddress.startsWith("http")) inputAddress.removeSuffix("/") else "http://$inputAddress:8085"

        if (!localOsuDir.exists()) {
            onLog(AppRes.string.errFolderNotFound)
            localOsuDir.mkdirs()
        }

        try {
            try {
                val remoteType = httpClient.get("$baseUrl/ping").bodyAsText()
                if (remoteType != gameType) {
                    onLog("Error: Mode mismatch (Server: $remoteType, You: $gameType)")
                    return@withContext
                }
            } catch (e: Exception) {
                onLog("Connection error: $baseUrl")
                return@withContext
            }

            if (gameType == "LAZER") {
                syncLazer(baseUrl, localOsuDir, onLog)
            } else {
                syncStable(baseUrl, localOsuDir, onLog)
            }

            onLog(AppRes.string.statusDone)
            if (gameType == "STABLE") onLog("Osu! Stable: Press F5 to refresh.")

        } catch (e: kotlinx.coroutines.CancellationException) {
            onLog(AppRes.string.statusStoppedByUser)
            throw e
        } catch (e: Exception) {
            onLog("Error: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun syncStable(baseUrl: String, localOsuDir: File, onLog: (String) -> Unit) = coroutineScope {
        val localSongs = File(localOsuDir, "Songs")
        if (!localSongs.exists()) localSongs.mkdirs()

        onLog(AppRes.string.statusChecking)
        val manifestResponse = httpClient.get("$baseUrl/manifest")
        val remoteFiles = manifestResponse.bodyAsText().lines().filter { it.isNotBlank() }.toSet()

        val localFiles = localSongs.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(localSongs).path.replace("\\", "/") }
            .toSet()

        val toDownload = (remoteFiles - localFiles).toList()
        if (toDownload.isEmpty()) {
            onLog(AppRes.string.statusDone)
            return@coroutineScope
        }
        onLog("Files to download: ${toDownload.size}")

        val downloaded = AtomicInteger(0)
        val total = toDownload.size

        toDownload.map { relativePath ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    coroutineContext.ensureActive()
                    val targetFile = File(localSongs, relativePath)
                    if (!targetFile.parentFile.exists()) targetFile.parentFile.mkdirs()
                    try {
                        val encodedPath = URLEncoder.encode(relativePath, "UTF-8").replace("+", "%20")
                        val fileResp = httpClient.get("$baseUrl/stable-file?path=$encodedPath")
                        if (fileResp.status == HttpStatusCode.OK) {
                            val ch = fileResp.bodyAsChannel()
                            val fos = targetFile.outputStream()
                            ch.copyTo(fos)
                            fos.close()
                            val current = downloaded.incrementAndGet()
                            if (current % 50 == 0 || current == total) onLog("Loaded: $current / $total")
                        }
                    } catch (e: Exception) { }
                }
            }
        }.awaitAll()
    }

    private suspend fun syncLazer(baseUrl: String, localOsuDir: File, onLog: (String) -> Unit) = coroutineScope {
        onLog("Downloading client.realm...")
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
            onLog("Database updated.")
        }

        onLog(AppRes.string.statusChecking)
        val manifestResponse = httpClient.get("$baseUrl/manifest")
        val remoteHashes = manifestResponse.bodyAsText().lines().filter { it.isNotBlank() }.toSet()
        val localFilesDir = File(localOsuDir, "files")
        val localHashes = localFilesDir.walkTopDown().filter { it.isFile }.map { it.name }.toSet()
        val toDownload = (remoteHashes - localHashes).toList()

        onLog("Files to download: ${toDownload.size}")
        val downloaded = AtomicInteger(0)
        val total = toDownload.size

        toDownload.map { hash ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    coroutineContext.ensureActive()
                    val targetFile = OsuUtils.getLazerFileByHash(localOsuDir, hash)
                    if (!targetFile.parentFile.exists()) targetFile.parentFile.mkdirs()
                    try {
                        val resp = httpClient.get("$baseUrl/file/$hash")
                        if (resp.status == HttpStatusCode.OK) {
                            val ch = resp.bodyAsChannel()
                            val fos = targetFile.outputStream()
                            ch.copyTo(fos)
                            fos.close()
                            val current = downloaded.incrementAndGet()
                            if (current % 50 == 0 || current == total) onLog("Loaded: $current / $total")
                        }
                    } catch (e: Exception) { }
                }
            }
        }.awaitAll()
    }
}