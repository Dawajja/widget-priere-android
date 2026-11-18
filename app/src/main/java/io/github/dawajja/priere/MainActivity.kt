// ═════════════════════════════════════════════════════════════════
//  HORAIRES DE PRIÈRE — Android (version 1.0 — 15 novembre 2026) : l'application (même page que index.tsx sur l'iPhone)
//  Mosquées, Jumu'a, style des widgets, « Mes widgets » et « Comment lire les widgets ».
//  Tout s'applique tout de suite aux widgets.
// ═════════════════════════════════════════════════════════════════
package io.github.dawajja.priere

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainActivity : ComponentActivity() {
    private val askNotif = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Widgets.ensureNightly(this)
        Updater.ensureDaily(this)
        // notifications : seulement pour prévenir d'une mise à jour
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            askNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        setContent { App() }
    }
}

private fun Int.toColor() = Color(this)

@Composable
fun App() {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val green = Palette(dark, false).green.toColor()
    val scheme = if (dark) darkColorScheme(
        primary = green, onPrimary = Color(0xFF00210F), primaryContainer = green.copy(alpha = 0.25f), onPrimaryContainer = Color(0xFFE3E3E3),
        background = Color(0xFF131314), surface = Color(0xFF131314), surfaceContainer = Color(0xFF1E1F20),
        surfaceContainerHigh = Color(0xFF2A2B2D), onSurface = Color(0xFFE3E3E3), onSurfaceVariant = Color(0xFFC4C7C5), outline = Color(0xFF444746),
    ) else lightColorScheme(
        primary = green, onPrimary = Color.White, primaryContainer = green.copy(alpha = 0.18f), onPrimaryContainer = Color(0xFF1F1F1F),
        background = Color(0xFFF6F8F6), surface = Color(0xFFF6F8F6), surfaceContainer = Color.White,
        surfaceContainerHigh = Color(0xFFEEF1EE), onSurface = Color(0xFF1F1F1F), onSurfaceVariant = Color(0xFF444746), outline = Color(0xFFC4C7C5),
    )
    MaterialTheme(colorScheme = scheme) {
        var page by remember { mutableStateOf("reglages") }
        BackHandler(enabled = page != "reglages") { page = "reglages" }
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            when (page) {
                "widgets" -> MyWidgets { page = "reglages" }
                "aide" -> Help { page = "reglages" }
                else -> SettingsPage(onWidgets = { page = "widgets" }, onHelp = { page = "aide" })
            }
        }
    }
}

// ───────────── Éléments communs ─────────────

@Composable
private fun Section(title: String) {
    Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp))
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer), content = content)
}

@Composable
private fun Foot(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp))
}

@Composable
private fun Row56(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@Composable
private fun Divide() = HorizontalDivider(color = MaterialTheme.colorScheme.background, thickness = 1.dp)

@Composable
private fun RoundIcon(icon: ImageVector, filled: Boolean = true) {
    Box(Modifier.size(40.dp).clip(CircleShape).then(
        if (filled) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier
    ), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = if (filled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun AddRow(label: String, onClick: () -> Unit) = Row56(onClick) {
    RoundIcon(Icons.Filled.Add, filled = false)
    Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Segmented(options: List<Pair<String, String>>, value: String, onChange: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
        options.forEachIndexed { i, (k, label) ->
            SegmentedButton(selected = value == k, onClick = { onChange(k) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label) }
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
        Text(title, fontSize = 22.sp)
    }
}

// ───────────── Réglages ─────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(onWidgets: () -> Unit, onHelp: () -> Unit) {
    val ctx = LocalContext.current
    val set = remember { Settings(ctx) }
    val scope = rememberCoroutineScope()
    var mosques by remember { mutableStateOf(set.mosques) }
    var selected by remember { mutableIntStateOf(set.selected) }
    var mantes by remember { mutableStateOf(set.mantesJumua) }
    var style by remember { mutableStateOf(set.style) }
    var adding by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(Store.read(ctx, set.source)?.let { Downloader.status(it) } ?: "") }
    var editing by remember { mutableStateOf<Int?>(null) }   // index de l'horaire de Jumu'a en cours de modification

    fun apply() { Widgets.refresh(ctx) }
    fun download() {
        status = "Téléchargement des horaires…"
        val src = set.source
        scope.launch {
            val msg = withContext(Dispatchers.IO) { Downloader.download(ctx, src) }
            // Jumu'a publiées par la mosquée, gardées avec elle
            if (src.type == "mawaqit") Store.read(ctx, src)?.let { d ->
                set.mosques = set.mosques.map { if (it.source.key == src.key) it.copy(pub = d.jumua) else it }
                mosques = set.mosques
            }
            status = msg; apply()
        }
    }
    fun select(i: Int) {
        set.selected = i; selected = i; editing = null
        if (Store.read(ctx, set.source) == null) download() else { status = Downloader.status(Store.read(ctx, set.source)!!); apply() }
    }
    fun addMosque() {
        val ok = Regex("mawaqit\\.net/(?:[a-z]{1,2}/)*[^/?#\\s]{5,}", RegexOption.IGNORE_CASE).containsMatchIn(link)
        val src = Source.parse(link)
        if (!ok || src == null) { note = "Lien non reconnu. Colle le lien de la page de la mosquée sur Mawaqit."; return }
        val exists = mosques.indexOfFirst { it.source.key == src.key }
        if (exists < 0) { set.mosques = mosques + Mosque(src.url, Mosque.nameOf(src.url), "mawaqit", emptyList(), emptyList()); mosques = set.mosques }
        adding = false; link = ""; note = ""
        select(if (exists >= 0) exists + 1 else mosques.size)
    }

    // premier lancement : les horaires de la mosquée sont téléchargés tout de suite
    LaunchedEffect(Unit) { if (Store.read(ctx, set.source) == null) download() else apply() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(bottom = 32.dp)) {
        Text("Widget de prière", fontSize = 30.sp, modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 10.dp))
        // diagnostic : si un widget ne peut pas s'afficher, la raison est écrite ici
        val problem = remember { Widgets.diagnose(ctx) }
        if (problem != null) Text("Problème de widget : $problem", color = Color(0xFFE5484D), fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        // batterie : sans cette autorisation, certains téléphones (Samsung…) bloquent les réveils du widget
        var free by remember { mutableStateOf(Widgets.unrestricted(ctx)) }
        LaunchedEffect(free) { while (!free) { kotlinx.coroutines.delay(1000); free = Widgets.unrestricted(ctx) } }
        if (!free) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Autoriser en arrière-plan", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Sans ça, le téléphone peut endormir l’app et le widget reste bloqué sur une prière passée.",
                        fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:" + ctx.packageName))) }
                            .onFailure { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:" + ctx.packageName))) }
                    }) { Text("Autoriser") }
                }
            }
        }

        // ── Mosquées ──
        Section("Mosquées")
        Card {
            val rows = listOf(Triple("Mantes-la-Jolie", "Guide Musulman", false)) + mosques.map { Triple(it.name, "Mawaqit", true) }
            rows.forEachIndexed { i, (name, sub, removable) ->
                if (i > 0) Divide()
                Row56({ select(i) }) {
                    RadioButton(selected = selected == i, onClick = { select(i) })
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 16.sp, maxLines = 1)
                        Text(sub, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (removable) IconButton(onClick = {
                        val gone = mosques[i - 1]
                        set.mosques = mosques.filterIndexed { k, _ -> k != i - 1 }; mosques = set.mosques
                        Store.forget(ctx, gone.source)
                        select(if (selected == i) 0 else if (selected > i) selected - 1 else selected)
                    }) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer $name", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Divide()
            if (adding) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(value = link, onValueChange = { link = it }, placeholder = { Text("Lien Mawaqit de la mosquée") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addMosque() }), modifier = Modifier.weight(1f))
                Button(onClick = { addMosque() }) { Text("Ajouter") }
            } else if (mosques.size < MAX_MOSQUES - 1) AddRow("Ajouter une mosquée") { adding = true }
        }
        Foot(note.ifEmpty { if (adding) "Colle le lien de la page de la mosquée sur Mawaqit." else "${mosques.size + 1} / $MAX_MOSQUES mosquées. Touche une mosquée pour l’utiliser ; ses horaires de Jumu’a sont gardés avec elle." })
        if (status.isNotEmpty()) Foot(status)

        // ── Jumu'a ──
        Section("Jumu’a")
        val cur = if (selected == 0) null else mosques.getOrNull(selected - 1)
        val times = if (cur == null) mantes else cur.jum
        fun saveTimes(list: List<String>): List<String> {
            val sorted = list.distinct().sorted()
            if (cur == null) { set.mantesJumua = sorted; mantes = sorted }
            else { set.mosques = mosques.map { if (it == cur) it.copy(jum = sorted) else it }; mosques = set.mosques }
            apply()
            return sorted
        }
        Card {
            if (cur != null) Segmented(listOf("mawaqit" to "Mawaqit", "perso" to "Mes horaires"), cur.mode) { mode ->
                set.mosques = mosques.map { if (it == cur) it.copy(mode = mode) else it }; mosques = set.mosques; editing = null; apply()
            }
            if (cur != null && cur.mode == "mawaqit") {
                val pub = Store.read(ctx, cur.source)?.jumua?.takeIf { it.isNotEmpty() } ?: cur.pub
                pub.forEachIndexed { i, t ->
                    Divide()
                    Row56 { Text("Jumu’a ${i + 1}", Modifier.weight(1f), fontSize = 16.sp); Text(t, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp) }
                }
            } else {
                times.forEachIndexed { i, t ->
                    if (i > 0 || cur != null) Divide()
                    Row56 {
                        Text("Jumu’a ${i + 1}", Modifier.weight(1f), fontSize = 16.sp)
                        FilledTonalButton(onClick = { editing = i }, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text(t, fontSize = 15.sp) }
                        IconButton(onClick = { saveTimes(times.filterIndexed { k, _ -> k != i }); Unit }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Supprimer la Jumu’a de $t", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Divide()
                AddRow("Ajouter un horaire") {
                    val t = if (times.isEmpty()) "13:00" else if ("14:00" in times) "15:00" else "14:00"
                    editing = saveTimes(times + t).indexOf(t)
                }
                if (cur == null) {
                    val changed = mantes != MANTES_JUMUA
                    Divide()
                    Row56(if (changed) ({ saveTimes(MANTES_JUMUA); Unit }) else null) {
                        RoundIcon(Icons.Filled.Refresh, filled = false)
                        Text("Réinitialiser les horaires", color = MaterialTheme.colorScheme.primary.copy(alpha = if (changed) 1f else 0.38f), fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    }
                }
            }
        }
        Foot(when {
            cur == null -> if (mantes != MANTES_JUMUA) "Tes horaires pour Mantes-la-Jolie : ${mantes.joinToString(" · ").ifEmpty { "aucun" }}. « Réinitialiser » remet ceux de la mosquée (12:30 · 14:00)."
                else "Horaires de Jumu’a de la mosquée de Mantes-la-Jolie. Tu peux les modifier."
            cur.mode == "mawaqit" -> { val pub = Store.read(ctx, cur.source)?.jumua?.takeIf { it.isNotEmpty() } ?: cur.pub
                if (pub.isNotEmpty()) "Horaires publiés par la mosquée sur Mawaqit (${pub.joinToString(" · ")})." else "La mosquée ne publie pas d’horaire de Jumu’a sur Mawaqit. Choisis « Mes horaires » pour les saisir." }
            cur.jum.isNotEmpty() -> "Le vendredi : ${cur.jum.joinToString(" · ")}. Affichés sous Duhr."
            else -> "Aucun horaire : ceux publiés par la mosquée. Affichés sous Duhr le vendredi."
        })

        // ── Style ──
        Section("Style des widgets")
        Card { Segmented(listOf("sys" to "Système", "ciel" to "Ciel"), style) { set.style = it; style = it; apply() } }
        Foot(if (style == "sys") "Système : le vert et l’orange de ${BRANDS[brandKey()]?.name?.let { if (it == "Autre") "Google" else it } ?: "Google"}, sur fond clair ou sombre selon le téléphone."
            else "Ciel : le fond suit le ciel de chaque moment de la journée, comme Google Météo. Tout le contenu est en blanc.")

        // ── Aperçu ──
        Section("Aperçu des widgets")
        Card {
            Row56(onWidgets) { RoundIcon(Icons.Filled.Widgets); Column { Text("Voir mes widgets", fontSize = 16.sp); Text("Avec tes horaires et ton style", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            Divide()
            Row56(onHelp) { RoundIcon(Icons.Filled.Info); Column { Text("Comment lire les widgets", fontSize = 16.sp); Text("Cercle, points, lignes, minuteur", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
        Foot("Les horaires sont gardés pour 3 mois et mis à jour chaque nuit : les widgets marchent sans Internet.")
        UpdateRow()
    }

    // roue d'Android pour l'heure d'une Jumu'a
    editing?.let { i ->
        val list = if (selected == 0) mantes else mosques.getOrNull(selected - 1)?.jum ?: emptyList()
        val (h, m) = (list.getOrNull(i) ?: "13:00").split(":").map { it.toInt() }
        val tp = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
        AlertDialog(
            onDismissRequest = { editing = null },
            confirmButton = { TextButton(onClick = {
                val l = list.toMutableList(); if (i < l.size) l[i] = hhmm(tp.hour, tp.minute) else l.add(hhmm(tp.hour, tp.minute))
                val sorted = l.distinct().sorted()
                if (selected == 0) { set.mantesJumua = sorted; mantes = sorted }
                else { val c = mosques[selected - 1]; set.mosques = mosques.map { if (it == c) it.copy(jum = sorted) else it }; mosques = set.mosques }
                editing = null; apply()
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Annuler") } },
            title = { Text("Jumu’a ${i + 1}") },
            text = { TimeInput(state = tp) },
        )
    }
}

// ───────────── Mes widgets ─────────────

@Composable
fun MyWidgets(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val density = LocalDensity.current.density
    val screenW = LocalConfiguration.current.screenWidthDp.toFloat()
    val start = remember { Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) } / 5 * 5 }
    var minutes by remember { mutableFloatStateOf(start.toFloat()) }
    val day = remember { Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis }
    val at = day + minutes.toLong() * 60000L
    val set = remember { Settings(ctx) }
    val data = remember { Store.read(ctx, set.source) }
    val state = data?.let { State.of(it, at, set.jumua(it)) }
    val pal = Palette(dark, set.style == "ciel")
    val r = remember { Renderer(density) }
    val mW = minOf(screenW - 48f, 380f)

    Column(Modifier.fillMaxSize()) {
        TopBar("Mes widgets", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Section("Écran d’accueil")
            if (state == null) Foot("Pas encore d’horaires : reviens en arrière, ils se téléchargent.")
            else Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val radius = Widgets.radius(ctx)
                val small = r.draw(false, 170f, 170f, radius, state, at, pal, timer = true).first
                Image(small.asImageBitmap(), contentDescription = "Aperçu du petit widget", modifier = Modifier.size(170.dp))
                Text("Petit (2 × 2)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                val medium = r.draw(true, mW, 170f, radius, state, at, pal, timer = true).first
                Image(medium.asImageBitmap(), contentDescription = "Aperçu du moyen widget", modifier = Modifier.size(mW.dp, 170.dp))
                Text("Moyen (4 × 2)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            Section("Écran verrouillé")
            if (data != null) {
                val ls = State.of(data, at, set.jumua(data), withGrace = false)
                val lw = minOf(screenW - 48f, 300f)
                val lock = r.drawLock(lw, lw * 76f / 262f, ls, at, timer = true).first
                Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF6B3A24), Color(0xFF2A1610)))).padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center) {
                    Image(lock.asImageBitmap(), contentDescription = "Aperçu du widget de l’écran verrouillé", modifier = Modifier.size(lw.dp, (lw * 76f / 262f).dp))
                }
            }
            Foot("Samsung : avec Good Lock (module LockStar), appui long sur l’écran verrouillé → Ajouter un widget → Horaires de prière. Pixel (Android 16) : appui long sur l’écran verrouillé → Widgets.")
            Spacer(Modifier.height(24.dp))
        }
        // curseur toujours à portée du pouce, en bas
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(hhmm(minutes.toInt() / 60, minutes.toInt() % 60), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(state?.moment ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
            }
            Slider(value = minutes, onValueChange = { minutes = (it / 5).toInt() * 5f }, valueRange = 0f..1435f)
            if (data != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val (times, _) = data.dayOf(Calendar.getInstance())
                PRAYERS.forEachIndexed { i, n ->
                    Text(n, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable {
                        val (h, m) = times[i].split(":").map { it.toInt() }; minutes = (h * 60 + m + 1).toFloat()
                    }.padding(4.dp))
                }
            }
        }
    }
}

// ───────────── Comment lire les widgets ─────────────

@Composable
fun Help(onBack: () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val p = Palette(dark, false)
    val g = p.green.toColor(); val o = p.orange.toColor(); val t = p.text.toColor(); val tr = p.track.toColor()
    @Composable fun Item(vis: @Composable () -> Unit, title: String, sub: String) = Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) { vis() }
        Column { Text(title, fontSize = 16.sp); Text(sub, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    @Composable fun Line(c: Color, f: Float) = Box(Modifier.width(34.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(tr)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(c))
    }
    @Composable fun Small(text: String) = Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 12.sp)
    Column(Modifier.fillMaxSize()) {
        TopBar("Lire les widgets", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Section("La frise")
            Card {
                Item({ Box(Modifier.size(12.dp).clip(CircleShape).background(g).padding(2.4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)) }, "Cercle vert", "La prochaine prière, ou celle en cours.")
                Divide(); Item({ Box(Modifier.size(6.dp).clip(CircleShape).background(g)) }, "Point vert", "Une prière déjà passée aujourd’hui.")
                Divide(); Item({ Box(Modifier.size(6.dp).clip(CircleShape).background(t)) }, "Point plein", "Une prière à venir.")
                Divide(); Item({ Line(g, 0.6f) }, "Ligne qui avance", "Le temps écoulé jusqu’à la prière suivante. Elle se remplit toute seule.")
                Divide(); Item({ Line(o, 0.45f) }, "Ligne orange", "Entre Fajr et le lever du soleil : elle atteint le Chourouk pile au lever.")
            }
            Section("Le reste")
            Card {
                Item({ Text("1:42", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }, "Minuteur", "Le temps restant avant la prochaine prière. « Maintenant » quand c’est l’heure.")
                Divide(); Item({ Small("07:45") }, "Petite heure sous Fajr", "Le lever du soleil (Chourouk). La prière de Fajr se termine à cette heure.")
                Divide(); Item({ Small("12:30\n14:00") }, "Le vendredi", "Les horaires de Jumu’a s’affichent à côté de Duhr.")
                Divide(); Item({ Small("demain") }, "Après Isha", "Le widget montre déjà les horaires du lendemain.")
            }
            Foot("Les horaires sont gardés pour 3 mois et mis à jour chaque nuit : les widgets marchent sans Internet.")
        }
    }
}

// ───────────── Mise à jour de l'app ─────────────

@Composable
private fun UpdateRow() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<Release?>(null) }
    var msg by remember { mutableStateOf("Version 1.0 (Android) · construction ${Updater.installed(ctx)}") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        release = withContext(Dispatchers.IO) { Updater.available(ctx) }
        if (release != null) msg = "Une nouvelle version est disponible."
    }
    Section("Application")
    Card {
        Row56(if (busy) null else ({
            busy = true
            scope.launch {
                val r = release ?: withContext(Dispatchers.IO) { Updater.available(ctx) }
                if (r == null) { msg = "Tu as la dernière version. Version 1.0 (Android) · construction ${Updater.installed(ctx)}"; busy = false; return@launch }
                release = r
                // Android doit autoriser l'app à installer ses mises à jour (réglage demandé une seule fois)
                if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
                    msg = "Autorise « Horaires de prière » à installer des applis, reviens, puis touche encore « Mettre à jour »."
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + ctx.packageName)))
                    busy = false; return@launch
                }
                msg = "Téléchargement de la mise à jour…"
                val err = withContext(Dispatchers.IO) { Updater.install(ctx, r) }
                msg = err ?: "Confirme l’installation dans la fenêtre d’Android."
                busy = false
            }
            Unit
        })) {
            RoundIcon(Icons.Filled.Refresh)
            Column(Modifier.weight(1f)) {
                Text(if (release != null) "Mettre à jour" else "Rechercher une mise à jour", fontSize = 16.sp,
                    color = if (release != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (release != null) FontWeight.Medium else FontWeight.Normal)
                Text(if (busy) "Un instant…" else "Les mises à jour arrivent aussi par notification.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Foot(msg)
}
