package osync.osync

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SyncServer(private val osuDir: File, private val port: Int = 8080) {
    private var server: NettyApplicationEngine? = null

    fun start() {
        server = embeddedServer(Netty, port = port) {
            routing {
                // 1. Скачать базу данных
                get("/realm") {
                    val dbFile = File(osuDir, "client.realm")
                    if (dbFile.exists()) {
                        call.respondFile(dbFile)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "DB not found")
                    }
                }

                // 2. Получить список всех файлов (манифест)
                get("/manifest") {
                    val filesDir = File(osuDir, "files")
                    // Ищем все файлы внутри папки files, имена которых длинные (хеши)
                    val hashes = withContext(Dispatchers.IO) {
                        filesDir.walkTopDown()
                            .filter { it.isFile && it.name.length > 2 }
                            .map { it.name }
                            .joinToString("\n")
                    }
                    call.respondText(hashes)
                }

                // 3. Скачать конкретный файл
                get("/file/{hash}") {
                    val hash = call.parameters["hash"]
                    if (hash == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }

                    val file = OsuUtils.getFileByHash(osuDir, hash)
                    if (file.exists()) {
                        call.respondFile(file)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
    }
}