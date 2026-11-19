package com.ucbps.app.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ucbps.app.Core
import com.ucbps.app.data.SecurityEvent
import com.ucbps.app.detection.Decision
import com.ucbps.app.network.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ------------------------------------------------------------------ app shell / navigation
@Composable
fun App() {
    val ctx = LocalContext.current
    val core = remember { Core.get(ctx) }
    var screen by remember { mutableStateOf("splash") }
    val back = { screen = "home" }
    BackHandler(enabled = screen != "splash" && screen != "login" && screen != "home") { back() }

    Box(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
        when (screen) {
            "splash" -> Splash { screen = if (core.prefs.token.isNotEmpty() || core.prefs.guest) "home" else "login" }
            "login" -> LoginScreen(core) { screen = "home" }
            "home" -> HomeScreen(core) { screen = it }
            "sms" -> SmsScreen(core, back)
            "calls" -> CallsScreen(core, back)
            "threats" -> ThreatsScreen(core, back)
            "blocked" -> BlockedScreen(core, back)
            "history" -> HistoryScreen(core, back)
            "settings" -> SettingsScreen(core, back) { screen = "login" }
            "emergency" -> EmergencyScreen(core, back)
        }
    }
}

@Composable
fun Shell(title: String, onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) TextButton(onClick = onBack) { Text("‹ Back") }
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Card, RoundedCornerShape(14.dp)).padding(14.dp), content = content)
}

@Composable fun Muted(text: String) = Text(text, color = MutedColor, fontSize = 13.sp)

fun riskColor(r: Double): Color = when { r >= 0.55 -> Danger; r >= 0.35 -> Warn; else -> Ok }
private val timeFmt = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault())

@Composable
fun EventRow(e: SecurityEvent) {
    Panel(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${if (e.kind == "SMS") "💬" else "📞"} ${e.source}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${e.action} ${(e.risk * 100).toInt()}%", color = riskColor(e.risk), fontWeight = FontWeight.Bold)
        }
        Muted(e.reason + if (e.simulated) "  (simulated)" else "")
        Muted(timeFmt.format(Date(e.ts)))
    }
}

fun has(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

// ------------------------------------------------------------------ splash + auth
@Composable
fun Splash(onDone: () -> Unit) {
    LaunchedEffect(Unit) { delay(1200); onDone() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🛡", fontSize = 64.sp)
        Text("UCBPS", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Cyan)
        Muted("Call & SMS Bomber Protection")
    }
}

@Composable
fun LoginScreen(core: Core, onDone: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun submit(register: Boolean) {
        busy = true; msg = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val country = Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase() ?: "ZZ"
                    Api.auth(core.prefs.apiBase, register, email.trim(), pw, country, consent)
                }
            }
            busy = false
            r.onSuccess { core.prefs.token = it; core.prefs.shareReports = register && consent; onDone() }
                .onFailure { msg = if (it is Api.ApiException && it.code == 401) "Wrong email or password." else "Could not reach the server (${it.message})." }
        }
    }

    Shell("Sign in", null) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(pw, { pw = it }, label = { Text("Password (min 8)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(consent, { consent = it })
                Text("Share anonymised threat indicators (hashed number + type only) to help others", fontSize = 13.sp)
            }
            if (msg.isNotEmpty()) Text(msg, color = Danger)
            Button({ submit(false) }, enabled = !busy && email.isNotBlank() && pw.length >= 8, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Please wait…" else "Log in") }
            OutlinedButton({ submit(true) }, enabled = !busy && email.isNotBlank() && pw.length >= 8, modifier = Modifier.fillMaxWidth()) { Text("Create account") }
            TextButton({ core.prefs.guest = true; onDone() }, modifier = Modifier.fillMaxWidth()) { Text("Continue offline (no account)") }
            Muted("Server: ${core.prefs.apiBase}. Free servers can take about a minute to wake up.")
        }
    }
}

// ------------------------------------------------------------------ home
@Composable
fun HomeScreen(core: Core, go: (String) -> Unit) {
    var on by remember { mutableStateOf(core.prefs.protectionOn) }
    val threats by core.dao.threats().collectAsState(initial = emptyList())
    val real = threats.filter { !it.simulated }
    val ctx = LocalContext.current
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33 && !has(ctx, Manifest.permission.POST_NOTIFICATIONS)) notif.launch(Manifest.permission.POST_NOTIFICATIONS) }

    Shell("UCBPS", null) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (on) "🛡 Protection ON" else "⚠ Protection OFF", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (on) Ok else Danger)
                        Muted(if (core.prefs.emergency) "Emergency mode is active" else "Monitoring SMS and calls on this device")
                    }
                    Switch(on, { on = it; core.prefs.protectionOn = it })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Panel(Modifier.weight(1f)) { Muted("Threats (real)"); Text("${real.size}", fontSize = 28.sp, color = Warn, fontWeight = FontWeight.Bold) }
                Panel(Modifier.weight(1f)) { Muted("Calls rejected"); Text("${real.count { it.kind == "CALL" && it.action == "BLOCK" }}", fontSize = 28.sp, color = Danger, fontWeight = FontWeight.Bold) }
            }
            listOf("sms" to "💬 SMS protection", "calls" to "📞 Call protection", "threats" to "🚨 Threat detection & simulator",
                "blocked" to "⛔ Blocked numbers", "history" to "🕘 Security history", "emergency" to "🆘 Emergency protection", "settings" to "⚙ Settings")
                .forEach { (id, label) -> Button({ go(id) }, Modifier.fillMaxWidth()) { Text(label) } }
        }
    }
}

// ------------------------------------------------------------------ SMS
@Composable
fun SmsScreen(core: Core, back: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(has(ctx, Manifest.permission.RECEIVE_SMS)) }
    var contacts by remember { mutableStateOf(has(ctx, Manifest.permission.READ_CONTACTS)) }
    var expecting by remember { mutableStateOf(core.prefs.expectingUntil > System.currentTimeMillis()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = has(ctx, Manifest.permission.RECEIVE_SMS); contacts = has(ctx, Manifest.permission.READ_CONTACTS)
    }
    val events by core.dao.events().collectAsState(initial = emptyList())

    Shell("SMS protection", back) {
        Panel {
            Text(if (granted) "✅ SMS analysis active" else "SMS permission needed", fontWeight = FontWeight.SemiBold)
            Muted("Messages are analysed on this device only. Android does not let non-default SMS apps hide or delete messages, so UCBPS alerts you and flags suspicious senders.")
            if (!granted || !contacts) Button({ launcher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS)) }) { Text("Grant permissions") }
            if (!contacts) Muted("Contacts permission lets UCBPS never flag people you know.")
        }
        Spacer(Modifier.height(8.dp))
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("I'm expecting a login code"); Muted("For 10 minutes, repeated codes from one service are treated as normal") }
                Switch(expecting, { expecting = it; core.prefs.expectingUntil = if (it) System.currentTimeMillis() + 10 * 60_000 else 0 })
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) { items(events.filter { it.kind == "SMS" }) { EventRow(it) } }
    }
}

// ------------------------------------------------------------------ calls
@Composable
fun CallsScreen(core: Core, back: () -> Unit) {
    val ctx = LocalContext.current
    val roleMgr = remember { ctx.getSystemService(RoleManager::class.java) }
    var held by remember { mutableStateOf(roleMgr.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { held = roleMgr.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) }
    val events by core.dao.events().collectAsState(initial = emptyList())

    Shell("Call protection", back) {
        Panel {
            Text(if (held) "✅ Call screening active" else "Call screening role needed", fontWeight = FontWeight.SemiBold)
            Muted("UCBPS uses Android's official call-screening role. Contacts are always allowed. 5 calls from one unknown number within 10 minutes are rejected temporarily; the number is not blocklisted automatically.")
            if (!held && roleMgr.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING))
                Button({ launcher.launch(roleMgr.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)) }) { Text("Enable call screening") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) { items(events.filter { it.kind == "CALL" }) { EventRow(it) } }
    }
}

// ------------------------------------------------------------------ threats + simulator
@Composable
fun ThreatsScreen(core: Core, back: () -> Unit) {
    val threats by core.dao.threats().collectAsState(initial = emptyList())
    var last by remember { mutableStateOf<Pair<String, Decision>?>(null) }

    Shell("Threat detection", back) {
        Panel {
            Text("Safe attack simulator", fontWeight = FontWeight.SemiBold)
            Muted("Generates synthetic events locally. Nothing is sent or dialled.")
            Simulator.scenarios.forEach { s -> OutlinedButton({ last = s to Simulator.run(core, s) }, Modifier.fillMaxWidth()) { Text(s) } }
            last?.let { (s, d) ->
                Spacer(Modifier.height(6.dp))
                Text("$s → ${d.action} (${(d.risk * 100).toInt()}%)", color = riskColor(d.risk), fontWeight = FontWeight.Bold)
                Muted(d.reason)
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) { items(threats) { EventRow(it) } }
    }
}

// ------------------------------------------------------------------ blocked
@Composable
fun BlockedScreen(core: Core, back: () -> Unit) {
    val list by core.dao.blocked().collectAsState(initial = emptyList())
    var number by remember { mutableStateOf("") }
    Shell("Blocked numbers", back) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(number, { number = it }, label = { Text("Number or sender") }, singleLine = true, modifier = Modifier.weight(1f))
            Button({ core.addBlocked(number); number = "" }, enabled = number.isNotBlank()) { Text("Add") }
        }
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) Muted("No blocked numbers yet.")
        LazyColumn(Modifier.weight(1f)) {
            items(list) { b ->
                Panel(Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.number, Modifier.weight(1f))
                        TextButton({ core.removeBlocked(b.number) }) { Text("Remove", color = Danger) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ history
@Composable
fun HistoryScreen(core: Core, back: () -> Unit) {
    val events by core.dao.events().collectAsState(initial = emptyList())
    Shell("Security history", back) {
        Row { TextButton({ core.exec.execute { core.dao.clearEvents() } }) { Text("Clear history", color = Danger) } }
        if (events.isEmpty()) Muted("Nothing recorded yet.")
        LazyColumn(Modifier.weight(1f)) { items(events) { EventRow(it) } }
    }
}

// ------------------------------------------------------------------ emergency
@Composable
fun EmergencyScreen(core: Core, back: () -> Unit) {
    var on by remember { mutableStateOf(core.prefs.emergency) }
    Shell("Emergency protection", back) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (on) "🆘 Emergency mode ON" else "Emergency mode OFF", fontWeight = FontWeight.Bold, color = if (on) Danger else Color.Unspecified)
                    Muted("While under attack: calls from numbers that are not in your contacts are silenced (not rejected, so they still appear in your call log and you can check them). Contacts always ring.")
                }
                Switch(on, { on = it; core.prefs.emergency = it })
            }
        }
        Spacer(Modifier.height(8.dp))
        Muted("Tip: if you are expecting a call from a hospital, bank or courier you do not have saved, turn this off first.")
    }
}

// ------------------------------------------------------------------ settings
@Composable
fun SettingsScreen(core: Core, back: () -> Unit, onLogout: () -> Unit) {
    var api by remember { mutableStateOf(core.prefs.apiBase) }
    var share by remember { mutableStateOf(core.prefs.shareReports) }
    var status by remember { mutableStateOf("") }
    val loggedIn = core.prefs.token.isNotEmpty()
    val scope = rememberCoroutineScope()

    fun bg(label: String, block: () -> Unit) {
        status = "$label…"
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching(block) }
            status = r.fold({ "$label: done" }, { "$label failed: ${it.message}" })
        }
    }

    Shell("Settings", back) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(api, { api = it; core.prefs.apiBase = it }, label = { Text("Backend URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Share threat indicators"); Muted("Only a keyed hash of the number, its type and country. Never message text or contacts.") }
                    Switch(share, {
                        share = it; core.prefs.shareReports = it
                        if (loggedIn) bg("Update consent") { Api.setConsent(core.prefs.apiBase, core.prefs.token, it) }
                    })
                }
            }
            if (loggedIn) {
                Button({ bg("Sync threat feed") { core.prefs.intelHashes = Api.feed(core.prefs.apiBase, core.prefs.token) } }, Modifier.fillMaxWidth()) { Text("Sync global threat feed") }
                OutlinedButton({ core.prefs.logout(); onLogout() }, Modifier.fillMaxWidth()) { Text("Log out") }
                OutlinedButton({
                    val t = core.prefs.token
                    val b = core.prefs.apiBase
                    bg("Delete cloud data") { Api.deleteAccount(b, t) }
                    core.prefs.logout(); onLogout()
                }, Modifier.fillMaxWidth()) { Text("Delete my account & uploaded data", color = Danger) }
            } else {
                Muted("You are in offline mode. Create an account on the login screen to enable the global threat feed.")
                OutlinedButton({ core.prefs.logout(); onLogout() }, Modifier.fillMaxWidth()) { Text("Go to login") }
            }
            if (status.isNotEmpty()) Muted(status)
            Muted("UCBPS 1.0.0 · Detection runs on-device. iOS does not allow third-party apps to read SMS or intercept calls.")
        }
    }
}
