package com.example.freevpn.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.freevpn.data.*
import com.wireguard.android.backend.GoBackend
import kotlinx.coroutines.delay

@Composable
fun FreeVpnApp(vm: VpnViewModel = viewModel()) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    FreeVpnTheme(theme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "splash") {
                composable("splash") {
                    LaunchedEffect(Unit) {
                        delay(1000)
                        nav.navigate(if (vm.prefs.onboarded) "home" else "onboarding") { popUpTo("splash") { inclusive = true } }
                    }
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Shield, null, Modifier.size(88.dp), tint = ConnectGreen)
                            Spacer(Modifier.height(12.dp)); Text("Free VPN", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                composable("onboarding") { Onboarding { vm.prefs.onboarded = true; nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } } }
                composable("home") { Home(vm, nav::navigate) }
                composable("servers") { Servers(vm) { nav.popBackStack() } }
                composable("stats") { Page("Statistics", nav::popBackStack) { StatsBody(vm) } }
                composable("settings") { Page("Settings", nav::popBackStack) { SettingsBody(vm, nav::navigate) } }
                composable("privacy") { Page("Privacy policy", nav::popBackStack) { TextBody(PRIVACY) } }
                composable("help") { Page("Help", nav::popBackStack) { TextBody(HELP) } }
                composable("about") { Page("About", nav::popBackStack) { TextBody(ABOUT) } }
            }
        }
    }
}

@Composable
private fun Onboarding(done: () -> Unit) {
    val pages = listOf(
        Triple(Icons.Default.Lock, "Encrypted tunnel", "Your traffic is encrypted with WireGuard between your phone and our server."),
        Triple(Icons.Default.Public, "Pick a location", "Choose a country or let the app pick the fastest server."),
        Triple(Icons.Default.Info, "Free, with limits", "Basic access needs no account. Fair-use limits apply; see Privacy policy for what we log."))
    var i by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(32.dp).systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(pages[i].first, null, Modifier.size(96.dp), tint = ConnectGreen)
        Spacer(Modifier.height(24.dp)); Text(pages[i].second, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp)); Text(pages[i].third, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(40.dp))
        Button({ if (i < pages.lastIndex) i++ else done() }, Modifier.fillMaxWidth().height(52.dp)) { Text(if (i < pages.lastIndex) "Next" else "Get started") }
    }
}

@Composable
private fun Home(vm: VpnViewModel, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val traffic by vm.traffic.collectAsStateWithLifecycle()
    val sel by vm.selected.collectAsStateWithLifecycle()
    val list by vm.servers.collectAsStateWithLifecycle()

    // System VPN consent dialog
    var showPermissionHelp by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) vm.connect() else showPermissionHelp = true
    }
    if (showPermissionHelp) AlertDialog(
        onDismissRequest = { showPermissionHelp = false },
        title = { Text("VPN permission needed") },
        text = { Text("Android must approve a VPN connection before traffic can be protected. Tap Allow and choose OK in the system dialog.") },
        confirmButton = { TextButton({ showPermissionHelp = false; GoBackend.VpnService.prepare(ctx)?.let { permission.launch(it) } }) { Text("Allow") } },
        dismissButton = { TextButton({ showPermissionHelp = false }) { Text("Cancel") } })

    // Optional: connect automatically once per launch, only if permission was already granted
    LaunchedEffect(list.loading, list.error) {
        if (vm.prefs.autoConnect && !vm.autoConnectTried && !list.loading && list.error == null &&
            state is ConnState.Disconnected && vm.canConnect() && GoBackend.VpnService.prepare(ctx) == null) {
            vm.autoConnectTried = true; vm.connect()
        }
    }
    fun toggle() {
        when (state) {
            is ConnState.Connected, is ConnState.Connecting -> vm.disconnect()
            else -> { val i = GoBackend.VpnService.prepare(ctx); if (i != null) permission.launch(i) else vm.connect() }
        }
    }

    val busy = state is ConnState.Connecting
    val on = state is ConnState.Connected
    val color by animateColorAsState(when { on -> ConnectGreen; busy -> Amber; else -> ConnectGreen.copy(alpha = .85f) }, label = "c")
    val pulse = rememberInfiniteTransition(label = "p").animateFloat(1f, 1.07f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Free VPN", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton({ go("stats") }) { Icon(Icons.Default.BarChart, "Statistics") }
            IconButton({ go("settings") }) { Icon(Icons.Default.Settings, "Settings") }
        }
        Spacer(Modifier.weight(1f))
        Text(when (val s = state) {
            is ConnState.Connected -> "Connected"; is ConnState.Connecting -> s.message
            is ConnState.Error -> "Not connected"; else -> "Not connected" }, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        (state as? ConnState.Error)?.let { Text(it.message, color = Danger, modifier = Modifier.padding(top = 6.dp)) }
        if (state is ConnState.Connected) {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
            Text(formatDuration(now - (state as ConnState.Connected).since), fontSize = 32.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(28.dp))
        Box(Modifier.size(190.dp).scale(if (busy) pulse.value else 1f).background(color, CircleShape)
            .clickable(enabled = state !is ConnState.Disconnected || vm.canConnect()) { toggle() }, Alignment.Center) {
            Icon(Icons.Default.PowerSettingsNew, if (on || busy) "Disconnect" else "Connect", Modifier.size(84.dp), tint = Color.Black)
        }
        Spacer(Modifier.height(12.dp))
        Text(if (on || busy) "Tap to disconnect" else "Tap to connect", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))

        Card(Modifier.fillMaxWidth().clickable { go("servers") }, shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val shown = (state as? ConnState.Connected)?.server ?: sel
                Text(if (shown != null) flagEmoji(shown.countryCode) else "⚡", fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(shown?.let { "${it.country}${if (it.city.isNotBlank()) ", ${it.city}" else ""}" } ?: "Fastest server", fontWeight = FontWeight.SemiBold)
                    Text(when { list.loading -> "Measuring servers…"; list.error != null -> "Servers unavailable"; shown?.latencyMs != null -> "${shown.latencyMs} ms"; else -> "Automatic" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                Icon(Icons.Default.ChevronRight, null)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Download", formatBytes(traffic.rxBytes), Modifier.weight(1f))
            StatCard("Upload", formatBytes(traffic.txBytes), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) =
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Servers(vm: VpnViewModel, back: () -> Unit) {
    val ui by vm.servers.collectAsStateWithLifecycle()
    val sel by vm.selected.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TopAppBar(title = { Text("Locations") }, navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton({ vm.refreshServers() }) { Icon(Icons.Default.Refresh, "Refresh") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true,
            placeholder = { Text("Search country or city") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        when {
            ui.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            ui.error != null -> Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text(ui.error!!); Spacer(Modifier.height(12.dp)); Button({ vm.refreshServers() }) { Text("Try again") } }
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { ServerRow("⚡", "Fastest server", "Automatic", sel == null) { vm.select(null); back() } }
                items(filterServers(ui.servers, q).sortedByDescending { it.id in favs }, key = { it.id }) { s ->
                    ServerRow(flagEmoji(s.countryCode), s.country + if (s.city.isNotBlank()) ", ${s.city}" else "",
                        s.latencyMs?.let { "$it ms" } ?: "Unavailable", sel?.id == s.id, enabled = s.latencyMs != null,
                        fav = s.id in favs, onFav = { vm.toggleFavorite(s.id) }) { vm.select(s); back() }
                }
            }
        }
    }
}

@Composable
private fun ServerRow(flag: String, title: String, sub: String, selected: Boolean, enabled: Boolean = true,
                      fav: Boolean? = null, onFav: () -> Unit = {}, onClick: () -> Unit) =
    Card(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick), shape = RoundedCornerShape(16.dp),
        border = if (selected) BorderStroke(2.dp, ConnectGreen) else null) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(flag, fontSize = 26.sp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(sub, fontSize = 13.sp, color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else Danger) }
            if (fav != null) IconButton(onFav) { Icon(if (fav) Icons.Default.Star else Icons.Default.StarBorder, "Favorite", tint = if (fav) Amber else MaterialTheme.colorScheme.onSurfaceVariant) }
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = ConnectGreen)
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Page(title: String, back: () -> Unit, body: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = body)
    }

@Composable
private fun StatsBody(vm: VpnViewModel) {
    val t by vm.traffic.collectAsStateWithLifecycle()
    val s by vm.state.collectAsStateWithLifecycle()
    StatCard("Status", if (s is ConnState.Connected) "Connected" else "Disconnected", Modifier.fillMaxWidth())
    StatCard("Downloaded", formatBytes(t.rxBytes), Modifier.fillMaxWidth())
    StatCard("Uploaded", formatBytes(t.txBytes), Modifier.fillMaxWidth())
    StatCard("Download speed", formatBytes(t.rxBps) + "/s", Modifier.fillMaxWidth())
    StatCard("Upload speed", formatBytes(t.txBps) + "/s", Modifier.fillMaxWidth())
    val check by vm.check.collectAsStateWithLifecycle()
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Connection check", fontWeight = FontWeight.SemiBold)
            when (val c = check) {
                CheckUi.Idle -> Text("Confirms the internet sees your VPN server's IP instead of yours.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CheckUi.Running -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is CheckUi.Done -> Text(
                    if (c.ok) "Passed: traffic exits through your VPN server (${c.seenIp})."
                    else "Warning: the server sees ${c.seenIp}, which is not your VPN server. Disconnect, reconnect, and run the manual leak tests.",
                    color = if (c.ok) ConnectGreen else Danger)
                is CheckUi.Failed -> Text(c.message, color = Danger)
            }
            Text("IPv4 exit only. DNS and IPv6 need the manual tests in docs/LEAK_TESTS.md.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button({ vm.runCheck() }, enabled = check !is CheckUi.Running) { Text("Run check") }
        }
    }
}

@Composable
private fun SettingsBody(vm: VpnViewModel, go: (String) -> Unit) {
    val ctx = LocalContext.current
    var auto by remember { mutableStateOf(vm.prefs.autoReconnect) }
    val theme by vm.theme.collectAsStateWithLifecycle()
    Text("Theme", fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { m -> FilterChip(theme == m, { vm.setTheme(m) }, { Text(m.name) }) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("Auto-reconnect", fontWeight = FontWeight.SemiBold); Text("Retry and recover after network changes", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(auto, { auto = it; vm.prefs.autoReconnect = it })
    }
    var autoLaunch by remember { mutableStateOf(vm.prefs.autoConnect) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("Connect on launch", fontWeight = FontWeight.SemiBold); Text("Uses your last or fastest server", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(autoLaunch, { autoLaunch = it; vm.prefs.autoConnect = it })
    }
    val dns by vm.dnsId.collectAsStateWithLifecycle()
    Text("DNS", fontWeight = FontWeight.SemiBold)
    Text("Applies on next connect. Queries always go through the tunnel.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    DNS_OPTIONS.forEach { o ->
        Card(Modifier.fillMaxWidth().clickable { vm.setDns(o.id) }, shape = RoundedCornerShape(14.dp),
            border = if (dns == o.id) BorderStroke(2.dp, ConnectGreen) else null) {
            Column(Modifier.padding(12.dp)) { Text(o.name, fontWeight = FontWeight.SemiBold); Text(o.note, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    Text("Protocol", fontWeight = FontWeight.SemiBold); Text("WireGuard (only protocol supported)", color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }, Modifier.fillMaxWidth()) { Text("Always-on VPN and kill switch (system settings)") }
    HorizontalDivider()
    listOf("privacy" to "Privacy policy", "help" to "Help", "about" to "About").forEach { (r, l) ->
        TextButton({ go(r) }, Modifier.fillMaxWidth()) { Text(l, Modifier.fillMaxWidth()) }
    }
}

@Composable private fun TextBody(t: String) = Text(t, lineHeight = 22.sp)

private const val PRIVACY = """What we collect: your device's WireGuard public key and the time/assigned tunnel IP while connected (kept for abuse prevention; edit this to match your real server logging).
What we do not collect: the sites you visit or the contents of your traffic.
The private key is generated on your device and never leaves it.
Free-tier limits: fair-use bandwidth cap per device; edit to match your server policy.
Contact: add your support email before publishing."""
private const val HELP = """Can't connect? Check your internet, pick another location, and tap Connect again.
Permission dialog: Android asks once to allow a VPN connection. This is required.
Always-on / kill switch: Settings > Always-on VPN and kill switch."""
private const val ABOUT = "Free VPN 1.0.0\nWireGuard® is a registered trademark of Jason A. Donenfeld.\nBuilt on the open-source wireguard-android tunnel library."
