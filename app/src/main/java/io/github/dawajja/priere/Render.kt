// ═════════════════════════════════════════════════════════════════
//  HORAIRES DE PRIÈRE — Android (version 1.0 — 15 novembre 2026) : dessin des widgets
//  Petit et moyen widget, dessinés comme sur l'iPhone (mêmes marges, frise, points, repère du Chourouk).
//  Couleurs : le vert et l'orange de la marque du téléphone, relevés dans son logiciel.
// ═════════════════════════════════════════════════════════════════
package io.github.dawajja.priere

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.core.graphics.PathParser
import androidx.core.graphics.toColorInt
import kotlin.math.max
import kotlin.math.min

// ───────────── Couleurs ─────────────

/** Vert et orange de chaque marque [clair, sombre], relevés dans le logiciel des téléphones. */
data class Brand(val name: String, val green: List<String>, val orange: List<String>, val card: List<String>? = null)

val BRANDS = linkedMapOf(
    // vert mesuré sur le widget Batterie du S22 Ultra ; fond des widgets Samsung (Entretien de l'appareil)
    "samsung" to Brand("Samsung", listOf("#03C66F", "#03C66F"), listOf("#EF5E16", "#F6874F"), listOf("#FCFCFC", "#1B1B1B")),
    // batterie d'Android (Google) ; le jaune ne se lit pas sur du blanc : orange de Google en clair
    "google" to Brand("Pixel", listOf("#18CC47", "#18CC47"), listOf("#E37400", "#FFC917")),
    "xiaomi" to Brand("Xiaomi", listOf("#47C200", "#47C200"), listOf("#FF9C1A", "#FF9C1A")),
    "oppo" to Brand("Oppo · OnePlus", listOf("#2BD982", "#00C570"), listOf("#FF7733", "#ED6825")),
    "vivo" to Brand("Vivo", listOf("#29E683", "#29E683"), listOf("#E37400", "#FFCC00")),
    "honor" to Brand("Honor", listOf("#47CC47", "#47CC47"), listOf("#E37400", "#FFB870")),
    "autre" to Brand("Autre", listOf("#188038", "#6DD58C"), listOf("#E37400", "#FFB870")),
)

/** Marque du téléphone, d'après le fabricant. */
fun brandKey(): String {
    val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
    return when {
        "samsung" in m -> "samsung"
        listOf("google", "motorola", "nothing", "fairphone", "nokia", "hmd").any { it in m } -> "google"
        listOf("xiaomi", "redmi", "poco").any { it in m } -> "xiaomi"
        listOf("oppo", "oneplus", "realme").any { it in m } -> "oppo"
        listOf("vivo", "iqoo").any { it in m } -> "vivo"
        "honor" in m -> "honor"
        else -> "autre"
    }
}

/** Ciel : mêmes moments de la journée qu'Apple, teintes du ciel de Google Météo. */
val SKIES = mapOf(
    "nuit" to ("#0F1C3F" to "#24356B"), "aube" to ("#2B3A67" to "#E59A7A"), "matin" to ("#1A73E8" to "#69A7F5"),
    "midi" to ("#1967D2" to "#4C8DF6"), "doré" to ("#3E6BC2" to "#F2A65A"), "soir" to ("#2A2457" to "#B65B74"),
)

private fun c(s: String) = s.toColorInt()
private fun alpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a * 255).toInt() shl 24)

/** Couleurs d'un dessin : style (Système ou Ciel), mode clair ou sombre, marque. */
class Palette(val dark: Boolean, val sky: Boolean, brand: String = brandKey()) {
    private val b = BRANDS[brand] ?: BRANDS.getValue("autre")
    private val k = if (dark) 1 else 0
    val card = c(b.card?.get(k) ?: if (dark) "#1E1F20" else "#FFFFFF")
    val green = if (sky) 0xFFFFFFFF.toInt() else c(b.green[k])
    val orange = if (sky) 0xFFFFFFFF.toInt() else c(b.orange[k])
    val text = if (sky) 0xFFFFFFFF.toInt() else c(if (dark) "#E3E3E3" else "#1F1F1F")
    val dim = if (sky) alpha(0xFFFFFFFF.toInt(), 0.7f) else c(if (dark) "#C4C7C5" else "#444746")
    val fut = if (sky) alpha(0xFFFFFFFF.toInt(), 0.55f) else text
    val gray = if (sky) alpha(0xFFFFFFFF.toInt(), 0.55f) else c("#8E918F")
    val track = if (sky) alpha(0xFFFFFFFF.toInt(), 0.3f) else c(if (dark) "#444746" else "#E3E3E3")
}

// ───────────── Icônes (même dessin que sur l'iPhone, case de 16) ─────────────

private val ICON_FILL = mapOf(
    "Chourouk" to "M4.9 10.2a3.1 3.1 0 0 1 6.2 0z",
    "Maghrib" to "M4.9 10.2a3.1 3.1 0 0 1 6.2 0z",
    "Isha" to "M10.5 1.5a6.5 6.5 0 1 0 4 11.6A7 7 0 0 1 10.5 1.5z",
)
private val ICON_STROKE = mapOf(
    "Fajr" to "M2.2 11.95H13.8M8 9.6V6.4M5.2 9.9L4.3 7.6M10.8 9.9L11.7 7.6",
    "Chourouk" to "M2.2 11.95H13.8M2.3 9.5H3.6M12.4 9.5H13.7M3.9 5.7L4.9 6.8M12.1 5.7L11.1 6.8M8 6.1V2.3M6.5 3.7L8 2.2L9.5 3.7",
    "Duhr" to "M12.70 8.00L14.60 8.00M11.32 11.32L12.67 12.67M8.00 12.70L8.00 14.60M4.68 11.32L3.33 12.67M3.30 8.00L1.40 8.00M4.68 4.68L3.33 3.33M8.00 3.30L8.00 1.40M11.32 4.68L12.67 3.33",
    "Asr" to "M12.40 8.00L13.50 8.00M11.11 11.11L11.89 11.89M8.00 12.40L8.00 13.50M4.89 11.11L4.11 11.89M3.60 8.00L2.50 8.00M4.89 4.89L4.11 4.11M8.00 3.60L8.00 2.50M11.11 4.89L11.89 4.11",
    "Maghrib" to "M2.2 11.95H13.8M2.3 9.5H3.6M12.4 9.5H13.7M3.9 5.7L4.9 6.8M12.1 5.7L11.1 6.8M8 2.2V6M6.5 4.6L8 6.1L9.5 4.6",
)
private val ICON_SUN = mapOf("Duhr" to 2.9f, "Asr" to 2.5f)

// ───────────── Dessin ─────────────

/** Ce qu'il faut savoir pour poser le minuteur animé d'Android par-dessus le dessin (en dp). */
data class TimerSpot(val show: Boolean, val color: Int, val size: Float, val x: Float, val top: Float, val end: Boolean, val base: Long)

class Renderer(private val density: Float) {
    private val MARK_POS = 0.27f

    private fun face(weight: Int): Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, weight, false)
        else Typeface.create(if (weight >= 500) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)

    private fun paint(color: Int, size: Float = 0f, weight: Int = 400, tnum: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; textSize = size; typeface = face(weight)
        if (tnum) fontFeatureSettings = "tnum"
    }
    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    /** Hauteur d'une ligne de texte (sans marge de police). */
    fun lineHeight(size: Float, weight: Int): Float { val fm = paint(0, size, weight).fontMetrics; return fm.descent - fm.ascent }

    /** Texte posé par le haut de sa ligne ; align : 0 gauche, 1 centre, 2 droite. Renvoie sa largeur. */
    private fun Canvas.text(s: String, x: Float, top: Float, p: Paint, align: Int = 0, maxW: Float = 0f): Float {
        var w = p.measureText(s)
        if (maxW > 0 && w > maxW) { p.textSize *= maxW / w; w = maxW }
        val fm = p.fontMetrics
        val left = when (align) { 1 -> x - w / 2; 2 -> x - w; else -> x }
        drawText(s, left, top - fm.ascent, p)
        return w
    }

    private fun Canvas.icon(name: String, x: Float, y: Float, size: Float, color: Int) {
        val m = Matrix().apply { setScale(size / 16f, size / 16f); postTranslate(x, y) }
        ICON_FILL[name]?.let { d -> val p = PathParser.createPathFromPathData(d); p.transform(m); drawPath(p, fill(color)) }
        ICON_SUN[name]?.let { r -> drawCircle(x + 8f * size / 16f, y + 8f * size / 16f, r * size / 16f, fill(color)) }
        ICON_STROKE[name]?.let { d ->
            val p = PathParser.createPathFromPathData(d); p.transform(m)
            drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.2f
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            })
        }
    }

    private fun fmtTimer(ms: Long): String {
        val s = max(0L, (ms + 999) / 1000)
        val h = s / 3600; val m = s % 3600 / 60; val ss = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, ss) else "%02d:%02d".format(m, ss)
    }

    private fun Canvas.background(w: Float, h: Float, radius: Float, s: State, pal: Palette) {
        val r = RectF(0f, 0f, w, h)
        val p = fill(pal.card)
        if (pal.sky) {
            val (a, b) = SKIES.getValue(s.sky)
            p.shader = LinearGradient(0f, 0f, 0f, h, c(a), c(b), Shader.TileMode.CLAMP)
        }
        drawRoundRect(r, radius, radius, p)
    }

    /** Barre arrondie (rail + partie remplie), horizontale ou verticale. */
    private fun Canvas.bar(x: Float, y: Float, w: Float, h: Float, frac: Float, color: Int, track: Int, vertical: Boolean) {
        val r = min(w, h) / 2
        drawRoundRect(RectF(x, y, x + w, y + h), r, r, fill(track))
        val f = frac.coerceIn(0f, 1f)
        if (f <= 0f) return
        save()
        val path = Path().apply { addRoundRect(RectF(x, y, x + w, y + h), r, r, Path.Direction.CW) }
        clipPath(path)
        if (vertical) drawRoundRect(RectF(x, y - r, x + w, y + h * f), r, r, fill(color))
        else drawRoundRect(RectF(x - r, y, x + w * f, y + h), r, r, fill(color))
        restore()
    }

    private fun frac(now: Long, a: Long, b: Long) = if (b <= a) 0f else ((now - a).toFloat() / (b - a)).coerceIn(0f, 1f)

    /** Dessine le widget (dimensions en dp). timer = true : le minuteur est dessiné (aperçu dans l'app). */
    fun draw(medium: Boolean, wDp: Float, hDp: Float, radiusDp: Float, s: State, now: Long, pal: Palette, timer: Boolean): Pair<Bitmap, TimerSpot> {
        val bmp = Bitmap.createBitmap(max(1, (wDp * density).toInt()), max(1, (hDp * density).toInt()), Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.scale(density, density)
        cv.background(wDp, hDp, radiusDp, s, pal)
        // Même format que l'iPhone (petit 158 × 158, moyen 338 × 158) : le dessin est agrandi d'un bloc
        // jusqu'à remplir la case du téléphone (cases Samsung plus grandes), sans changer ses proportions
        val (rw, rh) = if (medium) 338f to 158f else 158f to 158f
        val k = min(wDp / rw, hDp / rh)
        cv.save(); cv.scale(k, k)
        val spot = if (medium) cv.medium(wDp / k, hDp / k, s, now, pal, timer) else cv.small(wDp / k, hDp / k, s, now, pal, timer)
        cv.restore()
        return bmp to spot.copy(size = spot.size * k, x = spot.x * k, top = spot.top * k)
    }

    // ───── Écran verrouillé : le rond et le rectangle de l'iPhone, en blanc, sans fond ─────
    /** Dessine le widget de l'écran verrouillé (dimensions en dp, format de l'iPhone 262 × 76 agrandi d'un bloc). */
    fun drawLock(wDp: Float, hDp: Float, s: State, now: Long, timer: Boolean): Pair<Bitmap, TimerSpot> {
        val bmp = Bitmap.createBitmap(max(1, (wDp * density).toInt()), max(1, (hDp * density).toInt()), Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.scale(density, density)
        val k = min(wDp / 262f, hDp / 76f)
        val ox = (wDp / k - 262f) / 2; val oy = (hDp / k - 76f) / 2      // centré dans la case
        cv.save(); cv.scale(k, k); cv.translate(ox, oy)
        val white = 0xFFFFFFFF.toInt()
        val h = s.header
        val f = frac(now, h.from, h.at)
        // rond : fond blanc léger, rail, anneau qui se remplit depuis la prière précédente
        cv.drawCircle(38f, 38f, 38f, fill(alpha(white, 0.14f)))
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND }
        cv.drawCircle(38f, 38f, 34f, ring.apply { color = alpha(white, 0.25f) })
        if (f > 0f) cv.drawArc(RectF(4f, 4f, 72f, 72f), -90f, 360f * f, false, ring.apply { color = white })
        val tp = paint(white, 12f, 600, true)
        val th = lineHeight(12f, 600)
        val top = 38f - (16f + 1f + th) / 2
        cv.icon(h.name, 30f, top, 16f, white)
        cv.text(java.text.SimpleDateFormat("HH:mm").format(h.at), 38f, top + 17f, tp, 1)
        // rectangle : nom (avec « demain »), minuteur en grand, « à 15:02 »
        val X = 76f + 14f
        val h1 = lineHeight(15f, 600); val h2 = lineHeight(22f, 400); val h3 = lineHeight(13f, 400)
        val y1 = 38f - (h1 + h2 + h3 + 2f) / 2
        cv.icon(h.name, X, y1 + (h1 - 14f) / 2, 14f, white)
        cv.text(h.name + if (s.tomorrow) " demain" else "", X + 18f, y1, paint(white, 15f, 600))
        val y2 = y1 + h1 + 1f
        if (timer) cv.text(fmtTimer(h.at - now), X, y2, paint(white, 22f, 400, true))
        cv.text("à " + java.text.SimpleDateFormat("HH:mm").format(h.at), X, y2 + h2 + 1f, paint(alpha(white, 0.62f), 13f, 400, true))
        cv.restore()
        return bmp to TimerSpot(!timer, white, 22f * k, (X + ox) * k, (y2 + oy) * k, false, h.at)
    }

    // ───── Petit widget : frise verticale ─────
    private fun Canvas.small(W: Float, H: Float, s: State, now: Long, pal: Palette, timer: Boolean): TimerSpot {
        val TOP = 14.7f; val HEAD = 16.2f; val ROW = 20.3f; val LEAD = 12f; val TRAIL = 16f - 2f / 3f
        val g = max(0f, (H - TOP - 12.3f - HEAD - 5 * ROW) / 5.5f)
        fun yc(i: Int) = TOP + HEAD + 1.5f * g + ROW / 2 + i * (ROW + g)
        val X = LEAD + 12.7f / 2; val NAME_X = LEAD + 12.7f + 4f; val RIGHT = W - TRAIL
        val tint = if (s.dawn) pal.orange else pal.green

        // en-tête : icône, nom, minuteur (ou « Maintenant »)
        icon(s.header.name, LEAD, TOP + (HEAD - 12.7f) / 2, 12.7f, tint)
        val hp = paint(tint, 12f, 600)
        val nameTop = TOP + (HEAD - lineHeight(12f, 600)) / 2
        text(s.header.name, NAME_X, nameTop, hp)
        val tc = if (s.header.now) pal.green else if (s.dawn) pal.orange else pal.text
        val tTop = TOP + (HEAD - lineHeight(12f, 500)) / 2
        if (s.header.now) text("Maintenant", RIGHT, tTop, paint(pal.green, 12f, 500), 2)
        else if (timer) text(fmtTimer(s.header.at - now), RIGHT, tTop, paint(tc, 12f, 500, true), 2)

        // ligne entre les points (passe sous les points, s'arrête sous le trait du cercle en cours)
        fun rS(i: Int) = if (s.st(i, now) == Q.CUR) 25f / 6f else 2.5f
        val LW = 7f / 3f
        for (k in 0 until 4) {
            val y1 = yc(k); val y2 = yc(k + 1)
            val ringT = if (s.st(k, now) == Q.CUR) rS(k) - 1.9f else 0f
            val ringB = if (s.st(k + 1, now) == Q.CUR) rS(k + 1) - 1.9f else 0f
            val sT = y1 + ringT; val sH = (y2 - ringB) - sT
            var f = if (s.tomorrow) 0f else frac(now, s.list[k].at, s.list[k + 1].at)
            if (k == 0 && s.dawn && s.sunrise != null) f = 0.5f * frac(now, s.fajr, s.sunrise)
            val hT = if (s.st(k, now) == Q.CUR) 1.9f else rS(k); val hB = if (s.st(k + 1, now) == Q.CUR) 1.9f else rS(k + 1)
            val ff = if (f >= 1f) 1f else (hT + f * max(1f, sH - hT - hB)) / sH
            bar(X - LW / 2, sT, LW, sH, ff, if (k == 0 && s.dawn) pal.orange else pal.green, pal.track, true)
            // à l'aube : repère du Chourouk au milieu du segment Fajr → Duhr
            if (k == 0 && s.dawn) drawCircle(X, y1 + (y2 - y1) * 0.5f, 2.5f, fill(pal.orange))
        }
        // points et lignes
        for (i in 0 until 5) {
            val q = s.st(i, now); val y = yc(i); val p = s.list[i]
            dot(X, y, q, i, s, pal, ring = 25f / 3f, size = 5f)
            val col = when (q) { Q.PAST -> pal.dim; Q.CUR -> pal.green; Q.FUT -> pal.text }
            val w = if (q == Q.CUR) 600 else 400
            val np = paint(col, 12f, w)
            val top = y - lineHeight(12f, w) / 2
            val nw = text(p.name, NAME_X, top, np)
            val base = top - np.fontMetrics.ascent
            if (i == 0 && !s.shuruq.isNullOrEmpty()) {
                val sp = paint(if (s.dawn) pal.orange else pal.dim, 9f, 400, true)
                drawText(s.shuruq, NAME_X + nw + 4f, base, sp)
            }
            if (i == 1 && s.jumuas != null) {
                val jp = paint(pal.dim, 9f, 400, true)
                val js = s.jumuas.take(2)
                val lh = 9.5f
                val y0 = y - lh * js.size / 2
                js.forEachIndexed { j, t -> text(t, NAME_X + nw + 4f, y0 + j * lh - (lineHeight(9f, 400) - lh) / 2, jp) }
            }
            text(if (q == Q.CUR && s.tomorrow) s.nextTime else p.time, RIGHT, top, paint(col, 12f, w, true), 2)
        }
        return TimerSpot(!s.header.now && !timer, tc, 12f, RIGHT, tTop, true, s.header.at)
    }

    private fun Canvas.dot(x: Float, y: Float, q: Q, i: Int, s: State, pal: Palette, ring: Float, size: Float) {
        when (q) {
            Q.CUR -> {
                if (!pal.sky) drawCircle(x, y, ring / 2, fill(pal.card))
                if (s.grace == i) drawCircle(x, y, ring * 0.35f, fill(pal.text))
                drawCircle(x, y, ring / 2 - 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.green; style = Paint.Style.STROKE; strokeWidth = 2f })
            }
            Q.PAST -> drawCircle(x, y, size / 2, fill(if (s.tomorrow) pal.gray else if (s.dawn && i == 0) pal.orange else pal.green))
            Q.FUT -> drawCircle(x, y, size / 2, fill(pal.fut))
        }
    }

    // ───── Moyen widget : en-tête comme Météo, frise horizontale ─────
    private fun Canvas.medium(W: Float, H: Float, s: State, now: Long, pal: Palette, timer: Boolean): TimerSpot {
        val M = 16f; val TOP = M - 0.3f; val BOTTOM = H - (M - 0.7f)
        val tint = if (s.dawn) pal.orange else pal.green
        // nom (et « demain »), minuteur en grand, icône en haut à droite
        val np = paint(tint, 15f, 600)
        val nw = text(s.header.name, M, TOP, np)
        if (s.tomorrow) text("demain", M + nw + 4f, TOP, paint(pal.dim, 15f, 400))
        val tTop = TOP + lineHeight(15f, 600) + 2f
        val tc = if (s.header.now) pal.green else if (s.dawn) pal.orange else pal.text
        if (s.header.now) text("Maintenant", M, tTop, paint(pal.green, 34f, 400), maxW = W / 2)
        else if (timer) text(fmtTimer(s.header.at - now), M, tTop, paint(tc, 34f, 400, true))
        icon(s.header.name, W - M - 20f, TOP, 20f, if (pal.sky || s.header.now || s.dawn) tint else pal.gray)

        // frise : le premier horaire part de la marge gauche, le dernier finit sur la marge droite
        val TW = W - 2 * M; val TIME_W = paint(0, 15f, 600, true).measureText("18:44")
        val COL = (TW - TIME_W) / 4
        fun x(i: Int) = M + TIME_W / 2 + i * COL
        val hEx = lineHeight(11f, 400); val hT = lineHeight(15f, 400); val hN = lineHeight(12f, 400)
        val exTop = BOTTOM - hEx; val tTop2 = exTop - hT; val lineY = tTop2 - 2f - 6f; val nTop = lineY - 6f - 2f - 2f - hN
        val SEG = 2.5f
        fun rM(i: Int) = if (s.st(i, now) == Q.CUR) 5f else 3.75f
        fun segA(k: Int) = x(k) + rM(k) + SEG
        fun segB(k: Int) = x(k + 1) - rM(k + 1) - SEG
        for (k in 0 until 4) {
            val sL = segA(k); val sW = segB(k) - segA(k)
            if (k == 0 && s.dawn && s.sunrise != null) {
                // à l'aube : deux morceaux autour du repère du Chourouk ; la barre orange l'atteint au lever
                val sx = sL + sW * MARK_POS
                val w1 = sx - 3f - SEG - sL; val a2 = sx + 3f + SEG
                bar(sL, lineY - 2f, w1, 4f, frac(now, s.fajr, s.sunrise), pal.orange, pal.track, false)
                bar(a2, lineY - 2f, sL + sW - a2, 4f, 0f, pal.green, pal.track, false)
                drawCircle(sx, lineY, 3f, fill(pal.orange))
                continue
            }
            var f = if (s.tomorrow) 0f else frac(now, s.list[k].at, s.list[k + 1].at)
            if (k == 0 && !s.tomorrow && s.sunrise != null && now >= s.fajr)
                f = if (now < s.sunrise) MARK_POS * frac(now, s.fajr, s.sunrise) else MARK_POS + (1 - MARK_POS) * frac(now, s.sunrise, s.list[1].at)
            bar(sL, lineY - 2f, sW, 4f, f, pal.green, pal.track, false)
        }
        for (i in 0 until 5) {
            val q = s.st(i, now); val p = s.list[i]
            dot(x(i), lineY, q, i, s, pal, ring = 10f, size = 7.5f)
            val col = when (q) { Q.PAST -> pal.dim; Q.CUR -> pal.green; Q.FUT -> pal.text }
            val w = if (q == Q.CUR) 600 else 400
            text(p.name, x(i), nTop, paint(col, 12f, w), 1)
            text(if (q == Q.CUR && s.tomorrow) s.nextTime else p.time, x(i), tTop2, paint(col, 15f, w, true), 1)
        }
        if (!s.shuruq.isNullOrEmpty()) text(s.shuruq, x(0), exTop, paint(if (s.dawn) pal.orange else pal.dim, 11f, if (s.dawn) 600 else 400, true), 1)
        s.jumuas?.let { text(it.joinToString(" · "), x(1), exTop, paint(pal.dim, 11f, 400, true), 1, maxW = COL * 1.1f) }
        return TimerSpot(!s.header.now && !timer, tc, 34f, M, tTop, false, s.header.at)
    }
}
