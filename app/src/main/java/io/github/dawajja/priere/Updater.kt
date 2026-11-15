// ═════════════════════════════════════════════════════════════════
//  HORAIRES DE PRIÈRE — Android (version 1.0 — 15 novembre 2026) : mises à jour de l'app
//  Chaque jour, l'app regarde sur GitHub (Dawajja/widget-priere-android, onglet Releases)
//  s'il y a une version plus récente. Si oui : notification « Mise à jour disponible ».
//  « Mettre à jour » télécharge l'app et demande à Android de l'installer (une touche à confirmer).
// ═════════════════════════════════════════════════════════════════
package io.github.dawajja.priere

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Une version publiée sur GitHub : son numéro de construction et le lien du fichier à installer. */
data class Release(val build: Long, val apk: String)

object Updater {
    private const val API = "https://api.github.com/repos/Dawajja/widget-priere-android/releases/latest"
    private const val CHANNEL = "maj"
    const val EXTRA_UPDATE = "maj"

    /** Numéro de construction de l'app installée (le même que dans « v1.0.N »). */
    fun installed(c: Context): Long =
        PackageInfoCompat.getLongVersionCode(c.packageManager.getPackageInfo(c.packageName, 0))

    /** Dernière version publiée, ou null (pas d'Internet, GitHub ne répond pas…). */
    fun latest(): Release? = runCatching {
        val con = URL(API).openConnection() as HttpURLConnection
        con.connectTimeout = 15000; con.readTimeout = 15000
        con.setRequestProperty("Accept", "application/vnd.github+json")
        con.setRequestProperty("User-Agent", "widget-priere-android")
        val o = try { JSONObject(con.inputStream.bufferedReader().use { it.readText() }) } finally { con.disconnect() }
        val build = Regex("(\\d+)$").find(o.getString("tag_name"))!!.groupValues[1].toLong()
        val assets = o.getJSONArray("assets")
        var apk = ""
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) apk = a.getString("browser_download_url")
        }
        if (apk.isEmpty()) null else Release(build, apk)
    }.getOrNull()

    /** Version plus récente que celle installée, ou null. */
    fun available(c: Context): Release? = latest()?.takeIf { it.build > installed(c) }

    fun ensureDaily(c: Context) {
        val req = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("mise-a-jour", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun notify(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Mises à jour", NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(c, 2, Intent(c, MainActivity::class.java).putExtra(EXTRA_UPDATE, true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Mise à jour disponible")
            .setContentText("Touche pour mettre à jour Horaires de prière.")
            .setContentIntent(open).setAutoCancel(true).build()
        NotificationManagerCompat.from(c).notify(1, n)
    }

    /** Télécharge l'app et demande à Android de l'installer. Renvoie un message d'erreur, ou null si tout va bien. */
    fun install(c: Context, r: Release): String? = try {
        val pi = c.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            val con = URL(r.apk).openConnection() as HttpURLConnection
            con.instanceFollowRedirects = true
            con.connectTimeout = 20000; con.readTimeout = 60000
            con.setRequestProperty("User-Agent", "widget-priere-android")
            try {
                con.inputStream.use { input -> s.openWrite("app.apk", 0, -1).use { out -> input.copyTo(out); s.fsync(out) } }
            } finally { con.disconnect() }
            val done = PendingIntent.getBroadcast(c, 3, Intent(c, InstallReceiver::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            s.commit(done.intentSender)
        }
        null
    } catch (e: Exception) {
        "Mise à jour impossible (" + (e.message ?: e.toString()).take(80) + ")."
    }
}

/** Réponse d'Android pendant l'installation : il demande la confirmation, on lui montre l'écran. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999) == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
            confirm?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (Updater.available(applicationContext) != null) Updater.notify(applicationContext)
        Result.success()
    }
}
