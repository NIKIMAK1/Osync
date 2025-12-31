package osync.osync

import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface

object OsuUtils {
    // Автопоиск папки osu!
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

    // Получение локального IP адреса (кроме localhost)
    fun getLocalIp(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .filter { !it.isLoopbackAddress && it.isSiteLocalAddress && it.hostAddress.indexOf(':') == -1 }
                .map { it.hostAddress }
                .firstOrNull() ?: "127.0.0.1"
        } catch (e: Exception) {
            "Неизвестно"
        }
    }

    // Формирование пути к файлу по хешу (files/a/ab/abc...)
    fun getFileByHash(osuDir: File, hash: String): File {
        if (hash.length < 2) return File(osuDir, "files/$hash") // Защита от странных имен
        val p1 = hash.substring(0, 1)
        val p2 = hash.substring(0, 2)
        return File(osuDir, "files/$p1/$p2/$hash")
    }
}