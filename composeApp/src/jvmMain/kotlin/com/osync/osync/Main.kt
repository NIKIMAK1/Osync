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
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.ui.window.rememberWindowState

// --- ЦВЕТОВАЯ ПАЛИТРА (Osu! Style) ---
val OsuPink = Color(0xFFFF66AA)
val OsuPurple = Color(0xFF2E294E)
val DarkSurface = Color(0xFF1C1B1F)

val AppColorScheme = darkColorScheme(
    primary = OsuPink,
    onPrimary = Color.White,
    primaryContainer = OsuPink.copy(alpha = 0.2f),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFF00E5FF), // Голубой акцент
    background = Color(0xFF121212),
    surface = DarkSurface,
    surfaceVariant = Color(0xFF2A2830), // Для карточек
)

@Composable
fun OsyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography(), // Стандартная M3 типографика
        content = content
    )
}

// --- ОСНОВНОЕ ПРИЛОЖЕНИЕ ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val scope = rememberCoroutineScope()

    // Состояние
    var osuPath by remember { mutableStateOf(OsuUtils.getLazerPath()?.absolutePath ?: "") }
    var logs by remember { mutableStateOf("Добро пожаловать в Osync v1.0\n") }
    var mode by remember { mutableStateOf("MENU") } // MENU, SERVER, CLIENT

    // Логика
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
                    title = {
                        Text(
                            "Osync",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    ),
                    navigationIcon = {
                        if (mode != "MENU") {
                            IconButton(onClick = {
                                // Останавливаем сервер при выходе
                                if (mode == "SERVER") {
                                    serverInstance?.stop()
                                    isServerRunning = false
                                }
                                mode = "MENU"
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            ) {
                // ПУТЬ К ИГРЕ (Общий блок)
                OutlinedTextField(
                    value = osuPath,
                    onValueChange = { osuPath = it },
                    label = { Text("Путь к папке osu! (lazer)") },
                    leadingIcon = { Icon(Icons.Default.Folder, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                Spacer(Modifier.height(24.dp))

                // АНИМАЦИЯ ПЕРЕКЛЮЧЕНИЯ ЭКРАНОВ
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = {
                        fadeIn() + slideInVertically { it / 10 } togetherWith fadeOut() + slideOutVertically { -it / 10 }
                    },
                    modifier = Modifier.weight(1f)
                ) { currentMode ->
                    when (currentMode) {
                        "MENU" -> MenuScreen(
                            onServerClick = { mode = "SERVER" },
                            onClientClick = { mode = "CLIENT" }
                        )
                        "SERVER" -> ServerScreen(
                            ip = myIp,
                            isRunning = isServerRunning,
                            onToggleServer = {
                                if (isServerRunning) {
                                    serverInstance?.stop()
                                    isServerRunning = false
                                    log("Сервер остановлен")
                                } else {
                                    try {
                                        val srv = SyncServer(File(osuPath))
                                        srv.start()
                                        serverInstance = srv
                                        isServerRunning = true
                                        log("Сервер запущен на порту 8080")
                                    } catch (e: Exception) {
                                        log("Ошибка: ${e.message}")
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
                                    SyncClient().syncFrom(targetIpInput, File(osuPath)) { log(it) }
                                    isSyncing = false
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ЛОГИ (Терминал)
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.Black.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "LOGS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Divider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha=0.3f))
                        SelectionContainer {
                            Box(Modifier.fillMaxSize()) {
                                Text(
                                    text = logs,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                    color = Color.LightGray,
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

// --- ЭКРАН МЕНЮ ---
@Composable
fun MenuScreen(onServerClick: () -> Unit, onClientClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Кнопка Сервера
            BigOptionCard(
                title = "Раздать",
                subtitle = "Я сервер",
                icon = Icons.Default.CloudUpload,
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = onServerClick
            )
            // Кнопка Клиента
            BigOptionCard(
                title = "Скачать",
                subtitle = "Я клиент",
                icon = Icons.Default.CloudDownload,
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onClientClick
            )
        }
    }
}

@Composable
fun BigOptionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.size(width = 160.dp, height = 180.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = color),
        elevation = CardDefaults.elevatedCardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurface)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// --- ЭКРАН СЕРВЕРА ---
@Composable
fun ServerScreen(ip: String, isRunning: Boolean, onToggleServer: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Wifi, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))

        Text("Режим раздачи", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        // IP Address Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(
                text = ip,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
            )
        }
        Text("Сообщите этот IP второму компьютеру", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onToggleServer,
            modifier = Modifier.height(56.dp).fillMaxWidth(0.6f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(if (isRunning) "Остановить сервер" else "Запустить сервер")
        }

        if (isRunning) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(modifier = Modifier.width(200.dp), color = MaterialTheme.colorScheme.primary)
            Text("Ожидание подключений...", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top=8.dp))
        }
    }
}

// --- ЭКРАН КЛИЕНТА ---
@Composable
fun ClientScreen(targetIp: String, onIpChange: (String) -> Unit, isSyncing: Boolean, onStartSync: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Скачивание карт", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = targetIp,
            onValueChange = onIpChange,
            label = { Text("IP Адрес сервера") },
            placeholder = { Text("Например: 192.168.1.5") },
            leadingIcon = { Icon(Icons.Default.Computer, null) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(0.8f)
        )

        Spacer(Modifier.height(32.dp))

        FilledTonalButton(
            onClick = onStartSync,
            enabled = !isSyncing && targetIp.isNotBlank(),
            modifier = Modifier.height(56.dp).fillMaxWidth(0.6f)
        ) {
            if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(16.dp))
                Text("Синхронизация...")
            } else {
                Icon(Icons.Default.Sync, null)
                Spacer(Modifier.width(8.dp))
                Text("Начать")
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(8.dp))
                Text("Закройте Osu! перед началом", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

fun main() {
    application {
        // Устанавливаем фиксированный размер окна при запуске
        val windowState = rememberWindowState(width = 900.dp, height = 700.dp)

        Window(
            onCloseRequest = ::exitApplication,
            title = "Osync",
            state = windowState // <-- Применяем размер здесь
        ) {
            App()
        }
    }
}