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
import java.net.URLEncoder

class SyncServer(private val osuDir: File, private val gameType: String, private val port: Int = 8085) {
    private var server: NettyApplicationEngine? = null

    fun start() {
        server = embeddedServer(Netty, port = port) {
            routing {

                get("/ping") { call.respondText(gameType) }

                if (gameType == "LAZER") {
                    get("/realm") {
                        val dbFile = File(osuDir, "client.realm")
                        if (dbFile.exists()) call.respondFile(dbFile) else call.respond(HttpStatusCode.NotFound)
                    }

                    get("/manifest") {
                        val filesDir = File(osuDir, "files")
                        val hashes = withContext(Dispatchers.IO) {
                            filesDir.walkTopDown()
                                .filter { it.isFile && it.name.length > 2 }
                                .map { it.name }
                                .joinToString("\n")
                        }
                        call.respondText(hashes)
                    }

                    get("/file/{hash}") {
                        val hash = call.parameters["hash"] ?: return@get
                        val file = OsuUtils.getLazerFileByHash(osuDir, hash)
                        if (file.exists()) call.respondFile(file) else call.respond(HttpStatusCode.NotFound)
                    }
                }
                else if (gameType == "STABLE") {

                    get("/manifest") {
                        val songsDir = File(osuDir, "Songs")
                        if (!songsDir.exists()) {
                            call.respondText("")
                            return@get
                        }

                        val fileList = withContext(Dispatchers.IO) {
                            songsDir.walkTopDown()
                                .filter { it.isFile }
                                .map { it.relativeTo(songsDir).path.replace("\\", "/") } // Windows слэши в Unix
                                .joinToString("\n")
                        }
                        call.respondText(fileList)
                    }

                    get("/stable-file") {
                        val path = call.request.queryParameters["path"]
                        if (path == null) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@get
                        }

                        val songsDir = File(osuDir, "Songs")
                        val requestedFile = File(songsDir, path)

                        if (requestedFile.canonicalPath.startsWith(songsDir.canonicalPath) && requestedFile.exists()) {
                            call.respondFile(requestedFile)
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
    }
}