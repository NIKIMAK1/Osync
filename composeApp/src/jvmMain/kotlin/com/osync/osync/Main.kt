package osync.osync

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.formdev.flatlaf.FlatDarkLaf
import io.github.vinceglb.filekit.compose.rememberDirectoryPickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

val OsuPink = Color(0xFFFF66AA)
val DarkSurface = Color(0xFF1C1B1F)
val AppColorScheme = darkColorScheme(
    primary = OsuPink,
    onPrimary = Color.White,
    primaryContainer = OsuPink.copy(alpha = 0.2f),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFFF66AA),
    background = Color(0xFF121212),
    surface = DarkSurface,
    surfaceVariant = Color(0xFF2A2830),
)

@Composable
fun OsyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColorScheme, typography = Typography(), content = content)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun WindowScope.App(window: FrameWindowScope) {
    val scope = rememberCoroutineScope()
    val STR = AppRes.string

    var gameType by remember { mutableStateOf("LAZER") }
    var osuPath by remember { mutableStateOf("") }

    var syncJob by remember { mutableStateOf<Job?>(null) }
    val isSyncing = syncJob?.isActive == true

    LaunchedEffect(gameType) {
        if (osuPath.isEmpty()) {
            val path = if (gameType == "LAZER") OsuUtils.getLazerPath() else OsuUtils.getStablePath()
            osuPath = path?.absolutePath ?: ""
        }
    }

    var logs by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { logs = "${STR.welcomeLog}\n" }
    fun log(msg: String) { logs += "• $msg\n" }

    var mode by remember { mutableStateOf("MENU") }
    var serverInstance by remember { mutableStateOf<SyncServer?>(null) }
    var isServerRunning by remember { mutableStateOf(false) }
    val networkDiscovery = remember { LocalNetworkDiscovery() }
    var discoveryJob by remember { mutableStateOf<Job?>(null) }
    var isDiscovering by remember { mutableStateOf(false) }
    var discoveredServers by remember { mutableStateOf<List<DiscoveredServer>>(emptyList()) }
    val myIp = remember { OsuUtils.getLocalIp() }
    var targetIpInput by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()
    val directoryPicker = rememberDirectoryPickerLauncher(title = STR.chooseFolder) { directory ->
        directory?.path?.let { osuPath = it }
    }
    var isLanguageMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        (window.window as? java.awt.Frame)?.isResizable = false
    }

    fun startDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            isDiscovering = true
            discoveredServers = emptyList()
            log("${STR.autoSearchInProgress} (${OsuUtils.getLocalSiteLocalAddresses().joinToString().ifBlank { "no local IPv4" }})")
            try {
                val found = networkDiscovery.discoverServers(gameType)
                discoveredServers = found
                if (found.isEmpty()) {
                    log(STR.autoSearchEmpty)
                } else {
                    log("${STR.autoSearchTitle}: ${found.joinToString { it.address }}")
                }
            } finally {
                isDiscovering = false
            }
        }
    }

    LaunchedEffect(mode, gameType) {
        if (mode == "CLIENT") {
            startDiscovery()
        } else {
            discoveryJob?.cancel()
            isDiscovering = false
            discoveredServers = emptyList()
        }
    }

    OsyncTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 3.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CustomTitleBar(
                    title = STR.appTitle,
                    window = window.window,
                    onClose = {
                        System.exit(0)
                    },
                    showBackButton = mode != "MENU",
                    onBack = {
                        if (mode == "SERVER") {
                            scope.launch(Dispatchers.IO) {
                                serverInstance?.stop()
                                withContext(Dispatchers.Main) { isServerRunning = false }
                            }
                        }
                        discoveryJob?.cancel()
                        if (isSyncing) syncJob?.cancel()
                        mode = "MENU"
                    },
                    isLanguageMenuExpanded = isLanguageMenuExpanded,
                    onLanguageMenuToggle = { isLanguageMenuExpanded = !isLanguageMenuExpanded },
                    onLanguageMenuDismiss = { isLanguageMenuExpanded = false }
                )

                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (mode == "MENU") {
                        Row(modifier = Modifier.padding(bottom = 16.dp)) {
                            GameTypeButton("Osu! Lazer", gameType == "LAZER") { gameType = "LAZER" }
                            Spacer(Modifier.width(16.dp))
                            GameTypeButton("Osu! Stable", gameType == "STABLE") { gameType = "STABLE" }
                        }
                    }

                    OutlinedTextField(
                        value = osuPath,
                        onValueChange = { osuPath = it },
                        label = { Text(if (gameType == "LAZER") STR.pathLazer else STR.pathStable) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = { directoryPicker.launch() },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Icon(Icons.Default.FolderOpen, null)
                            }
                        }
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
                                "SERVER" -> ServerScreen(myIp, isServerRunning, gameType) {
                                    if (isServerRunning) {
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                serverInstance?.stop()
                                                withContext(Dispatchers.Main) {
                                                    isServerRunning = false
                                                    log(STR.serverStopped)
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    log("Error: ${e.message}")
                                                }
                                            }
                                        }
                                    } else {
                                        try {
                                            val srv = SyncServer(File(osuPath), gameType, 8085)
                                            srv.start()
                                            serverInstance = srv
                                            isServerRunning = true
                                            log("${STR.serverRunning} (Port: 8085)")
                                        } catch (e: Exception) {
                                            log("Error: ${e.message}")
                                            e.printStackTrace()
                                        }
                                    }
                                }
                                "CLIENT" -> ClientScreen(
                                    targetIpInput,
                                    { targetIpInput = it },
                                    discoveredServers,
                                    isDiscovering,
                                    { startDiscovery() },
                                    { targetIpInput = it.address },
                                    isSyncing,
                                    {
                                        syncJob = scope.launch {
                                            SyncClient().syncFrom(
                                                targetIpInput,
                                                File(osuPath),
                                                gameType
                                            ) { log(it) }
                                        }
                                    },
                                    { log(STR.statusStoppedByUser); syncJob?.cancel() }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = Color.Black.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                STR.logsTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Divider(
                                Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
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
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WindowScope.CustomTitleBar(
    title: String,
    window: java.awt.Window,
    onClose: () -> Unit,
    showBackButton: Boolean,
    onBack: () -> Unit,
    isLanguageMenuExpanded: Boolean,
    onLanguageMenuToggle: () -> Unit,
    onLanguageMenuDismiss: () -> Unit
) {
    WindowDraggableArea(
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = onLanguageMenuToggle) {
                            Icon(Icons.Default.Language, "Language")
                        }
                        DropdownMenu(
                            expanded = isLanguageMenuExpanded,
                            onDismissRequest = onLanguageMenuDismiss
                        ) {
                            AppLanguage.entries.forEach { lang ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            lang.title,
                                            fontWeight = if (AppRes.currentLanguage == lang)
                                                FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        AppRes.currentLanguage = lang
                                        onLanguageMenuDismiss()
                                    },
                                    leadingIcon = {
                                        if (AppRes.currentLanguage == lang)
                                            Icon(Icons.Default.Check, null)
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = {
                        if (window is java.awt.Frame) {
                            window.extendedState = java.awt.Frame.ICONIFIED
                        }
                    }) {
                        Icon(Icons.Default.Minimize, "Minimize", modifier = Modifier.size(20.dp))
                    }

                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            "Close",
                            tint = Color(0xFFE81123),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClientScreen(
    targetIp: String,
    onIpChange: (String) -> Unit,
    discoveredServers: List<DiscoveredServer>,
    isDiscovering: Boolean,
    onDiscover: () -> Unit,
    onServerSelected: (DiscoveredServer) -> Unit,
    isSyncing: Boolean,
    onStartSync: () -> Unit,
    onStopSync: () -> Unit
) {
    var manualMode by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(AppRes.string.modeClientTitle, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(0.6f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    manualMode = false
                    onDiscover()
                },
                enabled = !isSyncing && !isDiscovering,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Search, null)
                Spacer(Modifier.width(8.dp))
                Text(AppRes.string.autoSearchButton)
            }
            OutlinedButton(
                onClick = {
                    manualMode = true
                    onIpChange("")
                },
                enabled = !isSyncing,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Edit, null)
                Spacer(Modifier.width(8.dp))
                Text(AppRes.string.manualInputButton)
            }
        }
        Spacer(Modifier.height(20.dp))
        if (manualMode) {
            OutlinedTextField(
                value = targetIp,
                onValueChange = onIpChange,
                label = { Text(AppRes.string.ipFieldLabel) },
                enabled = !isSyncing,
                leadingIcon = { Icon(Icons.Default.Computer, null) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(0.6f)
            )
        } else {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(0.6f).heightIn(min = 120.dp, max = 220.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text(AppRes.string.autoSearchTitle, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    when {
                        isDiscovering -> {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(12.dp))
                            Text(AppRes.string.autoSearchInProgress)
                        }

                        discoveredServers.isEmpty() -> {
                            Text(AppRes.string.autoSearchEmpty, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        else -> {
                            Text(AppRes.string.autoSearchFound, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(discoveredServers) { server ->
                                    ElevatedCard(
                                        onClick = {
                                            manualMode = true
                                            onServerSelected(server)
                                        },
                                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Router, null)
                                                Spacer(Modifier.width(10.dp))
                                                Text(server.address)
                                            }
                                            if (targetIp == server.address) {
                                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        if (isSyncing) {
            Button(onClick = onStopSync, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.height(64.dp).fillMaxWidth(0.5f)) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 3.dp)
                Spacer(Modifier.width(16.dp))
                Text(AppRes.string.btnStopDownload, style = MaterialTheme.typography.titleMedium)
            }
        } else {
            FilledTonalButton(onClick = onStartSync, enabled = targetIp.isNotBlank(), modifier = Modifier.height(64.dp).fillMaxWidth(0.5f)) {
                Icon(Icons.Default.Sync, null)
                Spacer(Modifier.width(12.dp))
                Text(AppRes.string.btnStartDownload, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun GameTypeButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (isSelected) Color.Black else Color.White), shape = RoundedCornerShape(50)) { Text(text) }
}

@Composable
fun MenuScreen(onServerClick: () -> Unit, onClientClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            BigOptionCard(AppRes.string.menuHost, AppRes.string.menuHostDesc, Icons.Default.CloudUpload, MaterialTheme.colorScheme.primaryContainer, onServerClick)
            BigOptionCard(AppRes.string.menuClient, AppRes.string.menuClientDesc, Icons.Default.CloudDownload, MaterialTheme.colorScheme.secondaryContainer, onClientClick)
        }
    }
}

@Composable
fun BigOptionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.size(180.dp, 200.dp), colors = CardDefaults.elevatedCardColors(containerColor = color)) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurface)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                Text(subtitle)
            }
        }
    }
}

@Composable
fun ServerScreen(ip: String, isRunning: Boolean, gameType: String, onToggleServer: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("${AppRes.string.modeHostTitle} ($gameType)", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(24.dp)) {
            Text(ip, style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(48.dp, 24.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Text(AppRes.string.ipHint, modifier = Modifier.padding(top = 16.dp))
        Spacer(Modifier.height(48.dp))
        Button(onClick = onToggleServer, modifier = Modifier.height(64.dp).fillMaxWidth(0.5f), colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) {
            Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, null)
            Text(if (isRunning) " ${AppRes.string.btnStopServer}" else " ${AppRes.string.btnStartServer}")
        }
        if (isRunning) {
            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(modifier = Modifier.width(250.dp))
            Text(AppRes.string.serverRunning, modifier = Modifier.padding(top=8.dp))
        }
    }
}

fun main() {
    FlatDarkLaf.setup()

    application {
        val windowState = rememberWindowState(width = 1000.dp, height = 800.dp)

        Window(
            onCloseRequest = ::exitApplication,
            title = "Osync",
            state = windowState,
            icon = painterResource("icon.png"),
            undecorated = true,
            transparent = true,
            resizable = false
        ) {
            App(this)
        }
    }
}
