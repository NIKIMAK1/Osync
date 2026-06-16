package osync.osync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

interface Strings {
    val appTitle: String
    val pathLazer: String
    val pathStable: String
    val chooseFolder: String
    val menuHost: String
    val menuHostDesc: String
    val menuClient: String
    val menuClientDesc: String
    val modeHostTitle: String
    val ipHint: String
    val btnStartServer: String
    val btnStopServer: String
    val serverRunning: String
    val serverStopped: String
    val modeClientTitle: String
    val ipFieldLabel: String
    val autoSearchTitle: String
    val autoSearchButton: String
    val manualInputButton: String
    val autoSearchInProgress: String
    val autoSearchEmpty: String
    val autoSearchFound: String
    val btnStartDownload: String
    val btnStopDownload: String
    val logsTitle: String
    val welcomeLog: String
    val errFolderNotFound: String
    val statusChecking: String
    val statusDone: String
    val statusStoppedByUser: String
    val connectionMethodLan: String
    val connectionMethodLanDesc: String
    val connectionMethodWan: String
    val connectionMethodWanDesc: String
    val btnCopy: String
    val btnCopied: String
    val p2pGenerating: String
    val p2pNotRunning: String
    val clientP2PBadge: String
}

object EnStrings : Strings {
    override val appTitle = "Osync"
    override val pathLazer = "Path to Osu! Lazer"
    override val pathStable = "Path to Osu! Stable"
    override val chooseFolder = "Select Folder"
    override val menuHost = "Host"
    override val menuHostDesc = "I am sending"
    override val menuClient = "Download"
    override val menuClientDesc = "I am receiving"
    override val modeHostTitle = "Hosting Mode"
    override val ipHint = "Enter this IP on the second computer"
    override val btnStartServer = "Start Server"
    override val btnStopServer = "Stop Server"
    override val serverRunning = "Server is running..."
    override val serverStopped = "Server stopped"
    override val modeClientTitle = "Download Maps"
    override val ipFieldLabel = "Server IP or Link"
    override val autoSearchTitle = "Local network hosts"
    override val autoSearchButton = "Search again"
    override val manualInputButton = "Manual input"
    override val autoSearchInProgress = "Searching local network..."
    override val autoSearchEmpty = "No active hosts found"
    override val autoSearchFound = "Select a host found automatically"
    override val btnStartDownload = "Start Download"
    override val btnStopDownload = "Stop"
    override val logsTitle = "LOGS"
    override val welcomeLog = "Osync is running"
    override val errFolderNotFound = "Folder not found, creating..."
    override val statusChecking = "Checking files..."
    override val statusDone = "All done!"
    override val statusStoppedByUser = "Stopped by user"
    override val connectionMethodLan = "Local Network (LAN)"
    override val connectionMethodLanDesc = "For devices on the same Wi-Fi/local network"
    override val connectionMethodWan = "Internet (P2P WAN)"
    override val connectionMethodWanDesc = "For connecting over the internet anywhere in the world"
    override val btnCopy = "Copy"
    override val btnCopied = "Copied!"
    override val p2pGenerating = "Generating address..."
    override val p2pNotRunning = "Server is not running"
    override val clientP2PBadge = "Connection Mode: Internet (P2P)"
}

object RuStrings : Strings {
    override val appTitle = "Osync"
    override val pathLazer = "Путь к Osu! Lazer"
    override val pathStable = "Путь к Osu! Stable"
    override val chooseFolder = "Выбрать папку"
    override val menuHost = "Раздать"
    override val menuHostDesc = "Я сервер"
    override val menuClient = "Скачать"
    override val menuClientDesc = "Я клиент"
    override val modeHostTitle = "Режим раздачи"
    override val ipHint = "Введите этот IP на втором компьютере"
    override val btnStartServer = "Запустить сервер"
    override val btnStopServer = "Остановить сервер"
    override val serverRunning = "Сервер работает..."
    override val serverStopped = "Сервер остановлен"
    override val modeClientTitle = "Скачивание карт"
    override val ipFieldLabel = "IP Адрес или ссылка"
    override val autoSearchTitle = "Компьютеры в локальной сети"
    override val autoSearchButton = "Искать снова"
    override val manualInputButton = "Ручной ввод"
    override val autoSearchInProgress = "Ищу серверы в локальной сети..."
    override val autoSearchEmpty = "Активные раздающие компьютеры не найдены"
    override val autoSearchFound = "Выберите найденный компьютер"
    override val btnStartDownload = "Начать скачивание"
    override val btnStopDownload = "Остановить"
    override val logsTitle = "ЛОГИ"
    override val welcomeLog = "Osync запущен"
    override val errFolderNotFound = "Папка не найдена, создаю..."
    override val statusChecking = "Проверка файлов..."
    override val statusDone = "Готово!"
    override val statusStoppedByUser = "Остановлено пользователем"
    override val connectionMethodLan = "Локальная сеть (LAN)"
    override val connectionMethodLanDesc = "Для устройств в одной Wi-Fi/локальной сети"
    override val connectionMethodWan = "Интернет (P2P WAN)"
    override val connectionMethodWanDesc = "Для подключения через интернет в любой точке мира"
    override val btnCopy = "Копировать"
    override val btnCopied = "Скопировано!"
    override val p2pGenerating = "Генерация адреса..."
    override val p2pNotRunning = "Сервер не запущен"
    override val clientP2PBadge = "Режим подключения: Интернет (P2P)"
}

enum class AppLanguage(val code: String, val title: String, val strings: Strings) {
    EN("en", "English", EnStrings),
    RU("ru", "Русский", RuStrings);

    companion object {
        fun getBySystem(): AppLanguage {
            val systemLang = Locale.getDefault().language.lowercase()
            return entries.find { it.code == systemLang } ?: EN
        }
    }
}

object AppRes {
    var currentLanguage: AppLanguage by mutableStateOf(AppLanguage.getBySystem())
    val string: Strings get() = currentLanguage.strings
}
