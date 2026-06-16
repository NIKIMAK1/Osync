package osync.osync

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

data class DiscoveredServer(
    val address: String,
    val gameType: String
)

class LocalNetworkDiscovery {
    private val httpClient = HttpClient(CIO) {
        engine {
            requestTimeout = 1500
            endpoint {
                connectTimeout = 1000
                keepAliveTime = 1000
                maxConnectionsPerRoute = 128
                pipelineMaxSize = 20
            }
        }
    }

    suspend fun discoverServers(expectedGameType: String): List<DiscoveredServer> = withContext(Dispatchers.IO) {
        val udpDiscovered = discoverServersByBroadcast(expectedGameType)
        if (udpDiscovered.isNotEmpty()) return@withContext udpDiscovered

        val localAddresses = OsuUtils.getLocalSiteLocalAddresses()
        if (localAddresses.isEmpty()) return@withContext emptyList()

        val hostsToCheck = localAddresses
            .flatMap { localIp -> subnetHosts(localIp).filter { it != localIp } }
            .toSet()

        val found = ConcurrentHashMap<String, DiscoveredServer>()
        val semaphore = Semaphore(64)

        coroutineScope {
            hostsToCheck.map { host ->
                async {
                    semaphore.withPermit {
                        try {
                            val response = httpClient.get("http://$host:8085/ping")
                            val remoteType = response.bodyAsText().trim()
                            if (remoteType == expectedGameType) {
                                found[host] = DiscoveredServer(address = host, gameType = remoteType)
                            }
                        } catch (_: SocketTimeoutException) {
                        } catch (_: Exception) {
                        }
                    }
                }
            }.awaitAll()
        }

        found.values.sortedBy { it.address }
    }

    private fun discoverServersByBroadcast(expectedGameType: String): List<DiscoveredServer> {
        val found = ConcurrentHashMap<String, DiscoveredServer>()

        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 1200

            val requestBytes = OsuUtils.DISCOVERY_REQUEST.toByteArray(Charsets.UTF_8)
            val targets = (OsuUtils.getBroadcastAddresses() + "255.255.255.255").distinct()

            targets.forEach { address ->
                try {
                    val packet = DatagramPacket(
                        requestBytes,
                        requestBytes.size,
                        java.net.InetAddress.getByName(address),
                        OsuUtils.DISCOVERY_PORT
                    )
                    socket.send(packet)
                } catch (_: Exception) {
                }
            }

            val deadline = System.currentTimeMillis() + 1500
            while (System.currentTimeMillis() < deadline) {
                try {
                    val buffer = ByteArray(512)
                    val responsePacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(responsePacket)
                    val response = String(responsePacket.data, 0, responsePacket.length, Charsets.UTF_8).trim()
                    val parts = response.split("|")
                    if (parts.size != 3 || parts[0] != OsuUtils.DISCOVERY_RESPONSE_PREFIX) continue
                    val remoteType = parts[1]
                    if (remoteType != expectedGameType) continue
                    found[responsePacket.address.hostAddress] = DiscoveredServer(
                        address = responsePacket.address.hostAddress,
                        gameType = remoteType
                    )
                } catch (_: SocketTimeoutException) {
                    break
                } catch (_: Exception) {
                }
            }
        }

        return found.values.sortedBy { it.address }
    }

    private fun subnetHosts(ip: String): List<String> {
        val parts = ip.split(".")
        if (parts.size != 4) return emptyList()
        val prefix = parts.take(3).joinToString(".")
        return (1..254).map { "$prefix.$it" }
    }
}

object OsuUtils {
    const val DISCOVERY_PORT = 8086
    const val DISCOVERY_REQUEST = "OSYNC_DISCOVER"
    const val DISCOVERY_RESPONSE_PREFIX = "OSYNC_HERE"

    fun getLazerPath(): File? {
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        return when {
            os.contains("win") -> File(home, "AppData/Roaming/osu")
            os.contains("nux") -> File(home, ".local/share/osu")
            os.contains("mac") -> File(home, "Library/Application Support/osu")
            else -> null
        }
    }

    fun getStablePath(): File? {
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        if (os.contains("win")) {
            val localApp = File(home, "AppData/Local/osu!")
            if (localApp.exists()) return localApp
            val programFiles = File("C:/Program Files (x86)/osu!")
            if (programFiles.exists()) return programFiles
        }
        return null
    }

    fun getLocalIp(): String {
        return try {
            val interfaces = getFilteredInterfaces()
            val homeIp = interfaces.asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .filter { !it.isLoopbackAddress && it.isSiteLocalAddress && it.hostAddress.indexOf(':') == -1 }
                .find { it.hostAddress.startsWith("192.168") }
                ?.hostAddress
            if (homeIp != null) return homeIp

            interfaces.asSequence()
                .filter {
                    val name = it.displayName.lowercase()
                    !name.contains("docker") && !name.contains("br-") && !name.contains("veth")
                }
                .flatMap { it.inetAddresses.asSequence() }
                .filter { !it.isLoopbackAddress && it.isSiteLocalAddress && it.hostAddress.indexOf(':') == -1 }
                .map { it.hostAddress }
                .firstOrNull() ?: "127.0.0.1"
        } catch (e: Exception) {
            "Ошибка сети"
        }
    }

    fun getLocalSiteLocalAddresses(): List<String> {
        return try {
            getFilteredInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
                .map { it.hostAddress }
                .distinct()
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getBroadcastAddresses(): List<String> {
        return try {
            getFilteredInterfaces().flatMap { networkInterface ->
                networkInterface.interfaceAddresses.asSequence()
                    .mapNotNull { it.broadcast?.hostAddress }
                    .filter { it.indexOf(':') == -1 }
                    .toList()
            }.distinct()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getFilteredInterfaces(): List<NetworkInterface> {
        return NetworkInterface.getNetworkInterfaces().toList().filter {
            val name = "${it.name} ${it.displayName}".lowercase()
            !name.contains("docker") &&
                !name.contains("br-") &&
                !name.contains("veth") &&
                !name.contains("virtual") &&
                !name.contains("vmware") &&
                it.isUp &&
                !it.isLoopback
        }
    }

    fun getLazerFileByHash(osuDir: File, hash: String): File {
        if (hash.length < 2) return File(osuDir, "files/$hash")
        val p1 = hash.substring(0, 1)
        val p2 = hash.substring(0, 2)
        return File(osuDir, "files/$p1/$p2/$hash")
    }
}
