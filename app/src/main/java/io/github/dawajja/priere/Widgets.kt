// ═════════════════════════════════════════════════════════════════
//  HORAIRES DE PRIÈRE — Android (version 1.0 — 15 novembre 2026) : widgets de l'écran d'accueil
//  Le dessin est refait pile à l'heure de chaque prière et du Chourouk, et toutes les 5 minutes
//  écran allumé (frise). Le minuteur est celui d'Android : il avance tout seul, à la seconde.
//  Deux dessins (clair et sombre) : le téléphone montre le bon selon son mode, sans attendre.
// ═════════════════════════════════════════════════════════════════
package io.github.dawajja.priere

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

open class PrayerWidget(private val medium: Boolean) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widgets.refresh(context)
        Widgets.ensureNightly(context)
    }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        Widgets.update(context, manager, id, medium)
    }
    override fun onEnabled(context: Context) { Widgets.ensureNightly(context) }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Widgets.TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_LOCALE_CHANGED -> Widgets.refresh(context)
        }
    }
}

class SmallWidget : PrayerWidget(false)
class MediumWidget : PrayerWidget(true)

/** Écran verrouillé (Good Lock / LockStar sur Samsung, Android 16 sur Pixel). */
class LockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { Widgets.refresh(context); Widgets.ensureNightly(context) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) { Widgets.updateLock(context, manager, id) }
}

object Widgets {
    const val TICK = "io.github.dawajja.priere.TICK"

    fun refresh(context: Context) {
        val m = AppWidgetManager.getInstance(context)
        m.getAppWidgetIds(ComponentName(context, SmallWidget::class.java)).forEach { update(context, m, it, false) }
        m.getAppWidgetIds(ComponentName(context, MediumWidget::class.java)).forEach { update(context, m, it, true) }
        m.getAppWidgetIds(ComponentName(context, LockWidget::class.java)).forEach { updateLock(context, m, it) }
        schedule(context)
    }

    /** Taille du widget en dp (portrait), ou celle d'un widget standard. */
    private fun sizeOf(m: AppWidgetManager, id: Int, medium: Boolean): Pair<Float, Float> {
        val o = m.getAppWidgetOptions(id)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return (if (w > 0) w.toFloat() else if (medium) 340f else 160f) to (if (h > 0) h.toFloat() else 160f)
    }

    /** Arrondi des widgets du téléphone (Android 12 et plus), sinon 24 dp. */
    fun radius(context: Context): Float {
        if (Build.VERSION.SDK_INT >= 31) runCatching {
            val px = context.resources.getDimension(android.R.dimen.system_app_widget_background_radius)
            return px / context.resources.displayMetrics.density
        }
        return 24f
    }

    /** État du widget maintenant, ou null si aucun horaire n'est encore en mémoire. */
    fun state(context: Context, now: Long = System.currentTimeMillis()): State? {
        val set = Settings(context)
        val data = Store.read(context, set.source) ?: return null
        return runCatching { State.of(data, now, set.jumua(data)) }.getOrNull()
    }

    fun update(context: Context, manager: AppWidgetManager, id: Int, medium: Boolean) {
        try {
            val (w, h) = sizeOf(manager, id, medium)
            val views = build(context, medium, w, h)
            manager.updateAppWidget(id, views)
            context.getSharedPreferences("widgets", Context.MODE_PRIVATE).edit().remove("erreur").apply()
            if (state(context) == null) downloadNow(context)
        } catch (t: Throwable) { report(context, "mise à jour du widget", t) }
    }

    /** Widget de l'écran verrouillé : blanc, sans fond ; pas de « Maintenant » (comme sur l'iPhone). */
    fun updateLock(context: Context, manager: AppWidgetManager, id: Int) {
        try {
            val o = manager.getAppWidgetOptions(id)
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 }?.toFloat() ?: 262f
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 }?.toFloat() ?: 76f
            manager.updateAppWidget(id, buildLock(context, w, h))
        } catch (t: Throwable) { report(context, "widget de l'écran verrouillé", t) }
    }

    fun buildLock(context: Context, w: Float, h: Float): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_lock)
        val page = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Settings(context).source.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        views.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(context, 5, page, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        val now = System.currentTimeMillis()
        val set = Settings(context)
        val data = Store.read(context, set.source)
        val s = data?.let { runCatching { State.of(it, now, set.jumua(it), withGrace = false) }.getOrNull() }
        if (s == null) {
            views.setViewVisibility(R.id.img_lock, View.GONE); views.setViewVisibility(R.id.chrono_lock, View.GONE)
            views.setViewVisibility(R.id.empty, View.VISIBLE)
            return views
        }
        val density = context.resources.displayMetrics.density
        val (bmp, spot) = Renderer(density).drawLock(w, h, s, now, timer = false)
        views.setImageViewBitmap(R.id.img_lock, bmp)
        views.setContentDescription(R.id.img_lock, spoken(s))
        views.setTextViewTextSize(R.id.chrono_lock, TypedValue.COMPLEX_UNIT_DIP, spot.size)
        views.setViewPadding(R.id.chrono_lock, (spot.x * density).toInt(), (spot.top * density).toInt(), 0, 0)
        views.setChronometer(R.id.chrono_lock, SystemClock.elapsedRealtime() + (spot.base - now), null, true)
        views.setChronometerCountDown(R.id.chrono_lock, true)
        return views
    }

    /** Dernière erreur rencontrée par un widget (affichée dans l'app pour la corriger). */
    fun report(context: Context, where: String, t: Throwable) {
        context.getSharedPreferences("widgets", Context.MODE_PRIVATE).edit()
            .putString("erreur", "$where : ${t.javaClass.simpleName} : ${t.message ?: ""}".take(400)).apply()
    }

    /** Essaie de construire et d'afficher les deux widgets dans l'app : renvoie l'erreur, ou null si tout va bien. */
    fun diagnose(context: Context): String? {
        for (medium in listOf(false, true)) try {
            val v = build(context, medium, if (medium) 340f else 160f, 160f)
            v.apply(context, android.widget.FrameLayout(context))
        } catch (t: Throwable) {
            var c: Throwable = t; while (c.cause != null && c.cause !== c) c = c.cause!!
            return "${if (medium) "Moyen" else "Petit"} widget : ${t.javaClass.simpleName} : ${t.message ?: ""} / ${c.javaClass.simpleName} : ${c.message ?: ""}".take(500)
        }
        try { buildLock(context, 262f, 76f).apply(context, android.widget.FrameLayout(context)) }
        catch (t: Throwable) { return "Écran verrouillé : ${t.javaClass.simpleName} : ${t.message ?: ""}".take(500) }
        return context.getSharedPreferences("widgets", Context.MODE_PRIVATE).getString("erreur", null)
    }

    fun build(context: Context, medium: Boolean, w: Float, h: Float): RemoteViews {
        val views = RemoteViews(context.packageName, if (medium) R.layout.widget_medium else R.layout.widget_small)
        // comme sur l'iPhone : toucher le widget ouvre le calendrier de la mosquée sur le web
        val page = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Settings(context).source.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val open = PendingIntent.getActivity(context, 0, page, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        views.setOnClickPendingIntent(R.id.root, open)
        val now = System.currentTimeMillis()
        val s = state(context, now)
        if (s == null) {
            // pas encore d'horaires : on demande le téléchargement
            views.setViewVisibility(R.id.light, View.GONE); views.setViewVisibility(R.id.dark, View.GONE)
            views.setViewVisibility(R.id.empty, View.VISIBLE)
            // pas encore d'horaires : toucher le widget ouvre l'app (qui les télécharge)
            views.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(context, 4, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            return views
        }
        views.setViewVisibility(R.id.empty, View.GONE)
        val r = Renderer(context.resources.displayMetrics.density)
        val sky = Settings(context).style == "ciel"
        val density = context.resources.displayMetrics.density
        for ((dark, img, chrono) in listOf(Triple(false, R.id.img_light, R.id.chrono_light), Triple(true, R.id.img_dark, R.id.chrono_dark))) {
            val (bmp, spot) = r.draw(medium, w, h, radius(context), s, now, Palette(dark, sky), timer = false)
            views.setImageViewBitmap(img, bmp)
            views.setContentDescription(img, spoken(s))
            if (spot.show) {
                views.setViewVisibility(chrono, View.VISIBLE)
                views.setTextColor(chrono, spot.color)
                views.setTextViewTextSize(chrono, TypedValue.COMPLEX_UNIT_DIP, spot.size)
                val pad = { v: Float -> (v * density).toInt() }
                if (spot.end) views.setViewPadding(chrono, 0, pad(spot.top), pad(w - spot.x), 0)
                else views.setViewPadding(chrono, pad(spot.x), pad(spot.top), 0, 0)
                views.setChronometer(chrono, SystemClock.elapsedRealtime() + (spot.base - now), null, true)
                views.setChronometerCountDown(chrono, true)
            } else views.setViewVisibility(chrono, View.GONE)
        }
        return views
    }

    private fun spoken(s: State) = if (s.header.now) "L'heure de ${s.header.name}"
        else "Prochaine prière : ${s.header.name}${if (s.tomorrow) " demain" else ""} à ${java.text.SimpleDateFormat("HH:mm").format(s.header.at)}"

    /** Prochain dessin : pile au prochain changement (prière, Chourouk, fin de « Maintenant »), et au plus tard dans 5 minutes. */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(context, 1, Intent(context, SmallWidget::class.java).setAction(TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val now = System.currentTimeMillis()
        val change = state(context, now)?.change?.takeIf { it > now } ?: (now + 5 * 60000L)
        val five = (now / 300000L + 1) * 300000L
        val next = minOf(change, five) + 500
        // RTC (sans réveil) : si l'écran est éteint, le dessin attend qu'il se rallume
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExact(AlarmManager.RTC, next, pi)
        else am.setWindow(AlarmManager.RTC, next, 30000L, pi)
    }

    /** Téléchargement chaque nuit (avec Internet), comme l'automatisation de l'iPhone. */
    fun ensureNightly(context: Context) {
        val req = PeriodicWorkRequestBuilder<DownloadWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("nuit", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun downloadNow(context: Context) {
        // au plus une tentative tous les quarts d'heure si le site ne répond pas
        val p = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - p.getLong("essai", 0L) < 15 * 60000L) return
        p.edit().putLong("essai", now).apply()
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("maintenant", ExistingWorkPolicy.KEEP, req)
    }
}

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val src = Settings(applicationContext).source
        Downloader.download(applicationContext, src)
        Widgets.refresh(applicationContext)
        Result.success()
    }
}
