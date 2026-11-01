package bo.piyo.app.ui.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import bo.piyo.app.R

/**
 * The one notification Piyo posts: "your report/contribution is now in the data" after a dataset update
 * is installed (docs §8). It is only shown when the setting is on *and* the system permission is granted;
 * tapping opens piyo://gracias.
 */
object Notifier {
    private const val CHANNEL = "data_updates"
    private const val ID = 1001

    /** True when the app may post notifications right now (setting handled by the caller). */
    fun permitted(ctx: Context): Boolean {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33) return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return true
    }

    fun dataUpdated(ctx: Context) {
        if (!permitted(ctx)) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = Intent(Intent.ACTION_VIEW, Uri.parse("piyo://gracias")).setPackage(ctx.packageName)
        val pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(ctx.getString(R.string.notif_title))
            .setContentText(ctx.getString(R.string.notif_text))
            .setContentIntent(pi).setAutoCancel(true)
            .build()
        runCatching { nm.notify(ID, n) }
    }

    /** System settings page for this app's notifications (used when the permission was denied). */
    fun settingsIntent(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= 26) Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        else Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
}
