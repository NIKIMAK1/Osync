package osync.osync

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch
import java.io.File

val OsuPink = Color(0xFFFF66AA)
val DarkSurface = Color(0xFF1C1B1F)

val AppColorScheme = darkColorScheme(
    primary = OsuPink,
    onPrimary = Color.White,
    primaryContainer = OsuPink.copy(alpha = 0.2f),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFF00E5FF),
    background = Color(0xFF121212),
    surface = DarkSurface,
    surfaceVariant = Color(0xFF2A2830),
)

@Composable
fun OsyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography(),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val scope = rememberCoroutineScope()

    var gameType by remember { mutableStateOf("LAZER") }
    var osuPath by remember { mutableStateOf("") }

    LaunchedEffect(gameType) {
        val path = if (gameType == "LAZER") OsuUtils.getLazerPath() else OsuUtils.getStablePath()
        osuPath = path?.absolutePath ?: ""
    }

    var logs by remember { mutableStateOf("Добро пожаловать в Osync v2.0\n") }
    var mode by remember { mutableStateOf("MENU") } // MENU, SERVER, CLIENT

    var serverInstance by remember { mutableStateOf<SyncServer?>(null) }
    var isServerRunning by remember { mutableStateOf(false) }
    val myIp = remember { OsuUtils.getLocalIp() }

    var targetIpInput by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    fun log(msg: String) { logs += "• $msg\n" }

    OsyncTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Osync", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    ),
                    navigationIcon = {
                        if (mode != "MENU") {
                            IconButton(onClick = {
                                if (mode == "SERVER") {
                                    serverInstance?.stop()
                                    isServerRunning = false
                                }
                                mode = "MENU"
                            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                if (mode == "MENU") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.Center // Центрируем кнопки
                    ) {
                        GameTypeButton("Osu! Lazer", gameType == "LAZER") { gameType = "LAZER" }
                        Spacer(Modifier.width(16.dp))
                        GameTypeButton("Osu! Stable", gameType == "STABLE") { gameType = "STABLE" }
                    }
                }

                OutlinedTextField(
                    value = osuPath,
                    onValueChange = { osuPath = it },
                    label = { Text(if (gameType == "LAZER") "Путь к Lazer" else "Путь к Stable (папка Songs)") },
                    leadingIcon = { Icon(Icons.Default.Folder, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(targetState = mode) { currentMode ->
                        when (currentMode) {
                            "MENU" -> MenuScreen(
                                onServerClick = { mode = "SERVER" },
                                onClientClick = { mode = "CLIENT" }
                            )
                            "SERVER" -> ServerScreen(
                                ip = myIp,
                                isRunning = isServerRunning,
                                gameType = gameType,
                                onToggleServer = {
                                    if (isServerRunning) {
                                        serverInstance?.stop()
                                        isServerRunning = false
                                        log("Сервер остановлен")
                                    } else {
                                        try {
                                            val srv = SyncServer(File(osuPath), gameType, 8085)
                                            srv.start()
                                            serverInstance = srv
                                            isServerRunning = true
                                            log("Сервер ($gameType) запущен: 8085")
                                        } catch (e: Exception) {
                                            log("Ошибка: ${e.message}")
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            )
                            "CLIENT" -> ClientScreen(
                                targetIp = targetIpInput,
                                onIpChange = { targetIpInput = it },
                                isSyncing = isSyncing,
                                onStartSync = {
                                    isSyncing = true
                                    scope.launch {
                                        SyncClient().syncFrom(targetIpInput, File(osuPath), gameType) { log(it) }
                                        isSyncing = false
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.Black.copy(alpha=0.5f))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("LOGS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Divider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha=0.3f))
                        SelectionContainer {
                            Box(Modifier.fillMaxSize()) {
                                Text(
                                    text = logs,
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.verticalScroll(scrollState)
                                )
                            }
                        }
                    }
                    LaunchedEffect(logs) { scrollState.animateScrollTo(scrollState.maxValue) }
                }
            }
        }
    }
}


@Composable
fun GameTypeButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) Color.Black else Color.White
        ),
        shape = RoundedCornerShape(50)
    ) {
        Text(text)
    }
}

@Composable
fun MenuScreen(onServerClick: () -> Unit, onClientClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BigOptionCard("Раздать", "Я сервер", Icons.Default.CloudUpload, MaterialTheme.colorScheme.primaryContainer, onServerClick)
            BigOptionCard("Скачать", "Я клиент", Icons.Default.CloudDownload, MaterialTheme.colorScheme.secondaryContainer, onClientClick)
        }
    }
}

@Composable
fun BigOptionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.size(180.dp, 200.dp), // Чуть увеличили размер
        colors = CardDefaults.elevatedCardColors(containerColor = color),
        elevation = CardDefaults.elevatedCardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween, // Распределяем контент
            horizontalAlignment = Alignment.CenterHorizontally // Всё по центру
        ) {
            Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurface)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ServerScreen(ip: String, isRunning: Boolean, gameType: String, onToggleServer: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Wifi, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))

        Text("Режим раздачи ($gameType)", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(
                text = ip,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Text("Введите этот IP на втором компьютере", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onToggleServer,
            modifier = Modifier.height(64.dp).fillMaxWidth(0.5f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(12.dp))
            Text(if (isRunning) "Остановить сервер" else "Запустить сервер", style = MaterialTheme.typography.titleMedium)
        }

        if (isRunning) {
            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(modifier = Modifier.width(250.dp), color = MaterialTheme.colorScheme.secondary)
            Text("Сервер работает...", modifier = Modifier.padding(top=8.dp), color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun ClientScreen(targetIp: String, onIpChange: (String) -> Unit, isSyncing: Boolean, onStartSync: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Скачивание карт", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = targetIp,
            onValueChange = onIpChange,
            label = { Text("IP Адрес сервера") },
            placeholder = { Text("Например: 192.168.1.5") },
            leadingIcon = { Icon(Icons.Default.Computer, null) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(0.6f),
            textStyle = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(48.dp))

        FilledTonalButton(
            onClick = onStartSync,
            enabled = !isSyncing && targetIp.isNotBlank(),
            modifier = Modifier.height(64.dp).fillMaxWidth(0.5f)
        ) {
            if (isSyncing) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(16.dp))
                Text("Синхронизация...", style = MaterialTheme.typography.titleMedium)
            } else {
                Icon(Icons.Default.Sync, null)
                Spacer(Modifier.width(12.dp))
                Text("Начать скачивание", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

fun main() {
    application {
        val windowState = rememberWindowState(width = 1000.dp, height = 800.dp)
        Window(onCloseRequest = ::exitApplication, title = "Osync", state = windowState) {
            App()
        }
    }
}