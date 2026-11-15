// ═════════════════════════════════════════════════════════════════
//  HORAIRES DE PRIÈRE — Android (version 1.0 — 15 novembre 2026) : les données
//  Données : réglages, horaires en mémoire, téléchargement (Guide Musulman, Mawaqit)
//  et calcul de l'état du widget à un instant donné (même logique que widget.tsx sur iOS).
// ═════════════════════════════════════════════════════════════════
package io.github.dawajja.priere

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Calendar

val PRAYERS = listOf("Fajr", "Duhr", "Asr", "Maghrib", "Isha")

// ───────────── Mosquées ─────────────

/** Une source d'horaires : Guide Musulman (id de ville) ou Mawaqit (slug de la mosquée). */
data class Source(val type: String, val id: String, val url: String) {
    val key get() = if (type == "guide") "guide-$id" else "mawaqit-$id"
    val page get() = if (type == "guide") url else "https://mawaqit.net/fr/$id"

    companion object {
        fun parse(text: String?): Source? {
            val t = text?.trim().orEmpty()
            if (t.isEmpty()) return null
            Regex("guidemusulman\\.com/.*?ville/(\\d+)", RegexOption.IGNORE_CASE).find(t)?.let {
                return Source("guide", it.groupValues[1], t.split('?', '#')[0])
            }
            // lien Mawaqit, avec ou sans https:// et www., avec ou sans langue (/fr/) ni /m/
            val m = Regex("mawaqit\\.net/(?:[a-z]{1,2}/)*([^/?#\\s]+)", RegexOption.IGNORE_CASE).find(t)
            val slug = m?.groupValues?.get(1) ?: if (Regex("^[\\w-]+$").matches(t)) t else ""
            if (slug.isEmpty()) return null
            val s = java.net.URLDecoder.decode(slug, "UTF-8").lowercase()
            return Source("mawaqit", s, "https://mawaqit.net/fr/$s")
        }
    }
}

/** Mosquée par défaut : Mantes-la-Jolie sur Guide Musulman (intégrée, non supprimable). */
val MANTES = Source("guide", "32104", "https://www.guidemusulman.com/horaires-prieres/mensuel/ville/32104/mantes-la-jolie-78200")
/** Horaires de Jumu'a de Mantes (Guide Musulman ne les publie pas). */
val MANTES_JUMUA = listOf("12:30", "14:00")
const val MAX_MOSQUES = 5

/** Mosquée Mawaqit enregistrée : lien, nom, Jumu'a publiées (pub) et « Mes horaires » (jum). */
data class Mosque(val url: String, val name: String, val mode: String, val pub: List<String>, val jum: List<String>) {
    val source get() = Source.parse(url)!!
    fun toJson() = JSONObject().put("url", url).put("name", name).put("mode", mode)
        .put("pub", JSONArray(pub)).put("jum", JSONArray(jum))

    companion object {
        fun fromJson(o: JSONObject) = Mosque(
            o.optString("url"), o.optString("name"), o.optString("mode", "mawaqit"),
            o.optJSONArray("pub").strings(), o.optJSONArray("jum").strings(),
        )
        fun nameOf(url: String): String {
            val s = Source.parse(url)?.id ?: return "Mosquée"
            return s.replace(Regex("-\\d{5}.*$"), "").replace('-', ' ')
                .split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        }
    }
}

fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }

/** Horaires de Jumu'a lus dans un texte (« 13:00 14:15 », « 13h », « 13.30 »…). */
fun parseTimes(text: String?): List<String> {
    val out = sortedSetOf<String>()
    Regex("(\\d{1,2})\\s*[h:.](\\d{2})?(?!\\d)", RegexOption.IGNORE_CASE).findAll(text.orEmpty()).forEach {
        val h = it.groupValues[1].toInt()
        val mi = it.groupValues[2].ifEmpty { "0" }.toInt()
        if (h < 24 && mi < 60) out.add(hhmm(h, mi))
    }
    return out.toList()
}

fun hhmm(h: Int, m: Int) = "%02d:%02d".format(h, m)

// ───────────── Réglages ─────────────

/** Réglages de l'app, lus aussi par les widgets. Tout est appliqué tout de suite. */
class Settings(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("reglages", Context.MODE_PRIVATE)

    var mosques: List<Mosque>
        get() = runCatching { JSONArray(p.getString("mosques", "[]")).let { a -> (0 until a.length()).map { Mosque.fromJson(a.getJSONObject(it)) } } }
            .getOrDefault(emptyList()).take(MAX_MOSQUES - 1)
        set(v) = p.edit().putString("mosques", JSONArray(v.map { it.toJson() }).toString()).apply()

    /** Mosquée utilisée : 0 = Mantes-la-Jolie, 1… = mosquées enregistrées. */
    var selected: Int
        get() = p.getInt("selected", 0).coerceIn(0, mosques.size)
        set(v) = p.edit().putInt("selected", v).apply()

    /** Horaires de Jumu'a de Mantes sur ce téléphone (ceux de la mosquée par défaut). */
    var mantesJumua: List<String>
        get() = p.getString("mantesJumua", null)?.let { parseTimes(it) } ?: MANTES_JUMUA
        set(v) = p.edit().putString("mantesJumua", v.joinToString(" ")).apply()

    /** Style des widgets : "sys" (Système) ou "ciel". */
    var style: String
        get() = p.getString("style", "sys") ?: "sys"
        set(v) = p.edit().putString("style", v).apply()

    val source: Source get() = if (selected == 0) MANTES else mosques[selected - 1].source

    /** Horaires de Jumu'a affichés le vendredi pour la mosquée utilisée. */
    fun jumua(data: PrayerData?): List<String> = if (selected == 0) mantesJumua else {
        val m = mosques[selected - 1]
        if (m.mode == "perso") m.jum else (data?.jumua?.takeIf { it.isNotEmpty() } ?: m.pub)
    }
}

// ───────────── Horaires en mémoire ─────────────

/** Horaires d'une mosquée : ceux du jour du téléchargement, et un calendrier jour par jour (mois 0-11 → jour → 6 heures). */
data class PrayerData(
    val fetchedOn: String, val times: List<String>, val shuruq: String?,
    val calendar: Map<Int, Map<Int, List<String>>>, val jumua: List<String>, val name: String?, val savedAt: Long,
) {
    fun toJson(): String {
        val cal = JSONArray()
        for (m in 0 until 12) {
            val days = calendar[m]
            cal.put(if (days == null) JSONObject.NULL else JSONObject().also { o -> days.forEach { (d, row) -> o.put(d.toString(), JSONArray(row)) } })
        }
        return JSONObject().put("fetchedOn", fetchedOn).put("times", JSONArray(times)).put("shuruq", shuruq ?: JSONObject.NULL)
            .put("calendar", cal).put("jumua", JSONArray(jumua)).put("name", name ?: JSONObject.NULL).put("savedAt", savedAt).toString()
    }

    /** Horaires du jour : [fajr, duhr, asr, maghrib, isha] et le Chourouk. */
    fun dayOf(c: Calendar): Pair<List<String>, String?> {
        val row = calendar[c.get(Calendar.MONTH)]?.get(c.get(Calendar.DAY_OF_MONTH))
        if (row != null && row.size >= 6 && row.all { it.isNotEmpty() }) return listOf(row[0], row[2], row[3], row[4], row[5]) to row[1]
        return times to shuruq
    }

    /** Dernier jour connu du calendrier, ou null. */
    fun lastDay(): Calendar? {
        var last: Calendar? = null
        val d = Calendar.getInstance()
        for (k in 0 until 400) {
            val row = calendar[d.get(Calendar.MONTH)]?.get(d.get(Calendar.DAY_OF_MONTH))
            if (row != null && row.isNotEmpty() && row[0].isNotEmpty()) last = d.clone() as Calendar else if (k > 0) break
            d.add(Calendar.DAY_OF_MONTH, 1)
        }
        return last
    }

    companion object {
        fun fromJson(s: String?): PrayerData? = runCatching {
            val o = JSONObject(s!!)
            PrayerData(
                o.optString("fetchedOn"), o.getJSONArray("times").strings(), o.optString("shuruq").takeIf { it.isNotEmpty() && it != "null" },
                parseCalendar(o.optJSONArray("calendar")), o.optJSONArray("jumua").strings(),
                o.optString("name").takeIf { it.isNotEmpty() && it != "null" }, o.optLong("savedAt"),
            )
        }.getOrNull()?.takeIf { it.times.size >= 5 }

        /** Calendrier Mawaqit / Guide : 12 mois, chacun {jour: [6 heures]} (Mawaqit peut donner des tableaux). */
        fun parseCalendar(a: JSONArray?): Map<Int, Map<Int, List<String>>> {
            val out = HashMap<Int, Map<Int, List<String>>>()
            if (a == null) return out
            for (m in 0 until minOf(12, a.length())) {
                val days = HashMap<Int, List<String>>()
                when (val v = a.opt(m)) {
                    is JSONObject -> v.keys().forEach { k -> k.toIntOrNull()?.let { d -> v.optJSONArray(k)?.let { days[d] = it.strings().map(::toHM) } } }
                    is JSONArray -> for (i in 0 until v.length()) v.optJSONArray(i)?.let { days[i + 1] = it.strings().map(::toHM) }
                }
                if (days.isNotEmpty()) out[m] = days
            }
            return out
        }
    }
}

fun toHM(t: Any?): String {
    val m = Regex("(\\d{1,2}):(\\d{2})").find(t?.toString().orEmpty()) ?: return ""
    return hhmm(m.groupValues[1].toInt(), m.groupValues[2].toInt())
}

fun dayKey(c: Calendar = Calendar.getInstance()) = "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}-${c.get(Calendar.DAY_OF_MONTH)}"

object Store {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("horaires", Context.MODE_PRIVATE)
    fun read(c: Context, src: Source): PrayerData? = PrayerData.fromJson(prefs(c).getString(src.key, null))
    fun write(c: Context, src: Source, d: PrayerData) = prefs(c).edit().putString(src.key, d.toJson()).apply()
    fun forget(c: Context, src: Source) = prefs(c).edit().remove(src.key).apply()
}

// ───────────── Téléchargement ─────────────

object Downloader {
    private const val UA_DESKTOP = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"
    private const val UA_PHONE = "Mozilla/5.0 (Linux; Android 14; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
    private val MONTHS = listOf("janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet", "aout", "septembre", "octobre", "novembre", "decembre")

    private fun get(url: String, ua: String): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 15000
        c.setRequestProperty("User-Agent", ua)
        c.setRequestProperty("Accept", "text/html,application/json")
        c.setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9")
        return try {
            val code = c.responseCode
            code to (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally { c.disconnect() }
    }

    private fun plain(html: String): String {
        val t = html.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ")
            .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
            .replace(Regex("&([a-zA-Z])(acute|grave|circ|uml|cedil);")) { it.groupValues[1] }
            .replace(Regex("\\s+"), " ")
        return Normalizer.normalize(t.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    }

    /** Guide Musulman : la page mensuelle donne 30 jours à partir d'aujourd'hui, avec le Chourouk. */
    private fun guide(src: Source): PrayerData {
        val text = plain(get(src.url, UA_PHONE).second)
        val t = "(\\d{1,2}:\\d{2})"; val sep = "\\D{1,40}?"
        val re = Regex("(?:lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)\\s+(\\d{1,2})\\s+(" + MONTHS.joinToString("|") + ")" + sep + List(6) { t }.joinToString(sep))
        val cal = HashMap<Int, HashMap<Int, List<String>>>()
        re.findAll(text).forEach { m ->
            val month = MONTHS.indexOf(m.groupValues[2])
            cal.getOrPut(month) { HashMap() }[m.groupValues[1].toInt()] = (3..8).map { toHM(m.groupValues[it]) }
        }
        val now = Calendar.getInstance()
        val row = cal[now.get(Calendar.MONTH)]?.get(now.get(Calendar.DAY_OF_MONTH))
            ?: throw Exception("Horaires indisponibles sur Guide Musulman. Vérifie le lien.")
        return PrayerData(dayKey(), listOf(row[0], row[2], row[3], row[4], row[5]), row[1], cal, emptyList(), null, System.currentTimeMillis())
    }

    /** « confData = {…} » dans la page Mawaqit : tout le calendrier de l'année. */
    private fun findConf(html: String): JSONObject? {
        val at = Regex("confData\\s*=").find(html)?.range?.first ?: return null
        val open = html.indexOf('{', at); if (open < 0) return null
        var depth = 0; var inStr = false; var esc = false
        for (i in open until html.length) {
            val c = html[i]
            if (inStr) { if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') inStr = false }
            else if (c == '"') inStr = true
            else if (c == '{') depth++
            else if (c == '}' && --depth == 0) {
                val raw = html.substring(open, i + 1)
                return runCatching { JSONObject(raw) }.getOrNull() ?: runCatching { JSONObject(raw.replace("\\'", "'")) }.getOrNull()
            }
        }
        return null
    }

    private fun jumuaOf(o: JSONObject) = parseTimes(listOf("jumua", "jumua2", "jumua3").mapNotNull { o.optString(it).takeIf { s -> s.isNotEmpty() && s != "null" } }.joinToString(" "))

    private fun mawaqit(slug: String): PrayerData {
        var status = 0
        for (ua in listOf(UA_DESKTOP, UA_PHONE)) {
            runCatching {
                val (code, html) = get("https://mawaqit.net/fr/" + URLEncoder.encode(slug, "UTF-8"), ua)
                status = code
                val conf = findConf(html)
                val times = conf?.optJSONArray("times")
                if (conf != null && times != null && times.length() >= 5) {
                    return PrayerData(dayKey(), times.strings().take(5).map(::toHM), toHM(conf.opt("shuruq")).ifEmpty { null },
                        PrayerData.parseCalendar(conf.optJSONArray("calendar")), jumuaOf(conf),
                        conf.optString("name").takeIf { it.isNotEmpty() }, System.currentTimeMillis())
                }
            }
        }
        // recherche publique : au moins les horaires du jour
        runCatching {
            val words = slug.replace(Regex("-\\d{5}.*$"), "").replace('-', ' ')
            val list = JSONArray(get("https://mawaqit.net/api/2.0/mosque/search?word=" + URLEncoder.encode(words, "UTF-8"), UA_DESKTOP).second)
            var m: JSONObject? = null
            for (i in 0 until list.length()) if (list.getJSONObject(i).optString("slug") == slug) m = list.getJSONObject(i)
            if (m == null && list.length() == 1) m = list.getJSONObject(0)
            val t = m?.optJSONArray("times")?.strings()?.map(::toHM)
            if (m != null && t != null && t.size >= 5) {
                val six = t.size >= 6
                return PrayerData(dayKey(), if (six) listOf(t[0], t[2], t[3], t[4], t[5]) else t.take(5),
                    toHM(m.opt("shuruq")).ifEmpty { if (six) t[1] else null }, emptyMap(), jumuaOf(m),
                    m.optString("name").takeIf { it.isNotEmpty() }, System.currentTimeMillis())
            }
        }
        throw Exception("Mosquée introuvable sur Mawaqit (code ${if (status == 0) "?" else status}). Vérifie le lien.")
    }

    /** Garde de 2 jours avant aujourd'hui à 3 mois après : le widget a toujours de l'avance. */
    private fun merge(old: PrayerData?, fresh: PrayerData): PrayerData {
        val cal = HashMap<Int, HashMap<Int, List<String>>>()
        val d = Calendar.getInstance(); d.add(Calendar.DAY_OF_MONTH, -2)
        repeat(95) {
            val m = d.get(Calendar.MONTH); val day = d.get(Calendar.DAY_OF_MONTH)
            val row = fresh.calendar[m]?.get(day) ?: old?.calendar?.get(m)?.get(day)
            if (row != null) cal.getOrPut(m) { HashMap() }[day] = row
            d.add(Calendar.DAY_OF_MONTH, 1)
        }
        return fresh.copy(calendar = cal)
    }

    /** Télécharge les horaires de la mosquée et les range. Renvoie un message d'état. */
    fun download(c: Context, src: Source): String = try {
        val fresh = if (src.type == "guide") guide(src) else mawaqit(src.id)
        val data = merge(Store.read(c, src), fresh)
        Store.write(c, src, data)
        Widgets.refresh(c)
        status(data)
    } catch (e: Exception) {
        val old = Store.read(c, src)
        "Téléchargement impossible (" + (e.message ?: e.toString()).take(80) + ")." + (old?.let { " " + status(it) } ?: "")
    }

    fun status(d: PrayerData): String {
        val last = d.lastDay() ?: return "Horaires du jour en mémoire. Mis à jour chaque nuit."
        val months = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
        return "Horaires en mémoire jusqu'au ${last.get(Calendar.DAY_OF_MONTH)} ${months[last.get(Calendar.MONTH)]}. Mis à jour chaque nuit."
    }
}

// ───────────── État du widget à un instant ─────────────

enum class Q { PAST, CUR, FUT }

data class Prayer(val name: String, val time: String, val at: Long)
data class Header(val name: String, val at: Long, val from: Long, val now: Boolean = false)

/** Tout ce que le widget affiche au moment « now » (en ms). */
class State(
    val list: List<Prayer>, val nextIndex: Int, val nextTime: String, val tomorrow: Boolean, val dawn: Boolean, val grace: Int?,
    val header: Header, val change: Long, val shuruq: String?, val sunrise: Long?, val jumuas: List<String>?, val fajr: Long,
) {
    fun st(i: Int, t: Long): Q = when {
        grace != null -> if (i == grace) Q.CUR else if (list[i].at <= t) Q.PAST else Q.FUT
        tomorrow -> if (i == 0) Q.CUR else Q.PAST
        dawn -> if (list[i].at <= t) Q.PAST else Q.FUT
        i == nextIndex -> Q.CUR
        else -> if (list[i].at <= t) Q.PAST else Q.FUT
    }

    /** Moment du ciel pour le style Ciel. */
    val sky: String get() = if (header.now) mapOf("Duhr" to "midi", "Asr" to "doré", "Maghrib" to "soir", "Isha" to "nuit")[header.name] ?: "midi"
        else mapOf("Fajr" to "nuit", "Chourouk" to "aube", "Duhr" to "matin", "Asr" to "midi", "Maghrib" to "doré", "Isha" to "soir")[header.name] ?: "nuit"

    /** Phrase courte pour la page « Mes widgets ». */
    val moment: String get() = when {
        header.now -> "L’heure de ${header.name} : « Maintenant »"
        dawn -> "Aube : la ligne va jusqu’au Chourouk"
        tomorrow -> "Après Isha : les horaires de demain"
        else -> "Prochaine prière : ${header.name}"
    }

    companion object {
        const val GRACE_MIN = 5

        private fun at(base: Calendar, time: String): Long {
            val parts = time.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val c = base.clone() as Calendar
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
        private fun shift(c: Calendar, days: Int) = (c.clone() as Calendar).also { it.add(Calendar.DAY_OF_MONTH, days) }

        fun of(data: PrayerData, nowMs: Long, jumua: List<String>, withGrace: Boolean = true): State {
            val now = Calendar.getInstance().also { it.timeInMillis = nowMs }
            val (todayP, todaySh) = data.dayOf(now)
            val list = todayP.mapIndexed { i, t -> Prayer(PRAYERS[i], t, at(now, t)) }
            val idx = list.indexOfFirst { it.at > nowMs }
            var tomorrow = idx == -1
            val tmrC = shift(now, 1)
            val (tmrP, tmrSh) = data.dayOf(tmrC)
            val nextIndex = if (tomorrow) 0 else idx
            val nextAt = if (tomorrow) at(tmrC, tmrP[0]) else list[idx].at
            val nextTime = if (tomorrow) tmrP[0] else list[idx].time
            val prev = when {
                tomorrow -> list[4].at
                idx == 0 -> at(shift(now, -1), data.dayOf(shift(now, -1)).first[4])
                else -> list[idx - 1].at
            }
            val shuruq = if (tomorrow) (tmrSh ?: todaySh) else todaySh
            val sunrise = shuruq?.takeIf { it.isNotEmpty() }?.let { at(now, it) }
            val dawn = !tomorrow && sunrise != null && nowMs >= list[0].at && nowMs < sunrise
            var header = if (dawn) Header("Chourouk", sunrise!!, list[0].at) else Header(PRAYERS[nextIndex], nextAt, prev)
            var grace: Int? = null
            if (withGrace) {
                val g = list.indices.firstOrNull { i -> i > 0 && nowMs >= list[i].at && nowMs < list[i].at + GRACE_MIN * 60000L }
                if (g != null) {
                    grace = g; tomorrow = false
                    header = Header(list[g].name, list[g].at, list[g - 1].at, now = true)
                }
            }
            val change = if (grace != null) list[grace].at + GRACE_MIN * 60000L else header.at
            val shown = if (tomorrow) tmrC else now
            val days = if (tomorrow) tmrP.mapIndexed { i, t -> Prayer(PRAYERS[i], t, at(tmrC, t)) } else list
            val jumuas = if (shown.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY || jumua.isEmpty()) null else jumua
            return State(days, nextIndex, nextTime, tomorrow, dawn, grace, header, change, shuruq, sunrise, jumuas, list[0].at)
        }
    }
}
