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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException

class SyncServer(private val osuDir: File, private val gameType: String, private val port: Int = 8085) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var discoverySocket: DatagramSocket? = null
    private var discoveryThread: Thread? = null

    fun start() {
        server = embeddedServer(Netty, host = "0.0.0.0", port = port) {
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
                } else if (gameType == "STABLE") {
                    get("/manifest") {
                        val songsDir = File(osuDir, "Songs")
                        if (!songsDir.exists()) {
                            call.respondText("")
                            return@get
                        }
                        val fileList = withContext(Dispatchers.IO) {
                            songsDir.walkTopDown()
                                .filter { it.isFile }
                                .map { it.relativeTo(songsDir).path.replace("\\", "/") }
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

        startDiscoveryResponder()
    }

    fun stop() {
        discoverySocket?.close()
        discoveryThread?.interrupt()
        discoverySocket = null
        discoveryThread = null
        server?.stop(100, 500)
    }

    private fun startDiscoveryResponder() {
        discoverySocket = DatagramSocket(OsuUtils.DISCOVERY_PORT, InetAddress.getByName("0.0.0.0")).apply {
            broadcast = true
            reuseAddress = true
        }

        discoveryThread = Thread {
            val socket = discoverySocket ?: return@Thread
            val buffer = ByteArray(512)

            while (!socket.isClosed && !Thread.currentThread().isInterrupted) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    if (message != OsuUtils.DISCOVERY_REQUEST) continue

                    val response = "${OsuUtils.DISCOVERY_RESPONSE_PREFIX}|$gameType|$port"
                    val responseBytes = response.toByteArray(Charsets.UTF_8)
                    val responsePacket = DatagramPacket(
                        responseBytes,
                        responseBytes.size,
                        packet.address,
                        packet.port
                    )
                    socket.send(responsePacket)
                } catch (_: SocketException) {
                    break
                } catch (_: Exception) {
                }
            }
        }.apply {
            isDaemon = true
            name = "osync-discovery-responder"
            start()
        }
    }
}
