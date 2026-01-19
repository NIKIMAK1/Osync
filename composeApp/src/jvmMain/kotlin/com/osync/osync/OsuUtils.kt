package osync.osync

import java.io.File
import java.net.NetworkInterface

object OsuUtils {
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
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
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

    fun getLazerFileByHash(osuDir: File, hash: String): File {
        if (hash.length < 2) return File(osuDir, "files/$hash")
        val p1 = hash.substring(0, 1)
        val p2 = hash.substring(0, 2)
        return File(osuDir, "files/$p1/$p2/$hash")
    }
}