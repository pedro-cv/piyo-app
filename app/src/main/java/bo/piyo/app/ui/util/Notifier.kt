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
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import bo.piyo.app.R

/**
 * Every notification Piyo posts. Local: "your report/contribution is now in the data" after a dataset update
 * is installed (docs §8), opening piyo://gracias. From push (PiyoMessagingService): review results and new
 * data releases, opening the app. Shown only when the setting is on (checked by the caller) *and* the
 * system permission is granted.
 *
 * Every notification carries the piyo silhouette (ic_stat_piyo, white on transparent as Android requires)
 * tinted brand green, and goes to a high-importance channel so it also drops down as a banner instead of
 * appearing only in the shade. Channel importance cannot be raised once a channel exists, so the channels
 * were renamed (piyo_*) and the old default-importance ones are deleted.
 */
object Notifier {
    private const val CHANNEL_DATA = "piyo_data"
    private const val CHANNEL_REVIEWS = "piyo_reviews"
    private val LEGACY_CHANNELS = listOf("data_updates", "reviews")
    private const val ID_DATA_UPDATED = 1001
    private const val ID_DATASET = 1002
    private const val ID_CONTRIBUTION = 1003
    private const val ID_REPORT = 1004

    /** True when the app may post notifications right now (setting handled by the caller). */
    fun permitted(ctx: Context): Boolean {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33) return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return true
    }

    fun dataUpdated(ctx: Context) {
        val open = Intent(Intent.ACTION_VIEW, Uri.parse("piyo://gracias")).setPackage(ctx.packageName)
        post(ctx, CHANNEL_DATA, R.string.notif_channel, ID_DATA_UPDATED, R.string.notif_title, R.string.notif_text, open)
    }

    fun datasetAvailable(ctx: Context) =
        post(ctx, CHANNEL_DATA, R.string.notif_channel, ID_DATASET, R.string.notif_dataset_title, R.string.notif_dataset_text)

    fun contributionReviewed(ctx: Context, accepted: Boolean) =
        if (accepted) post(ctx, CHANNEL_REVIEWS, R.string.notif_channel_reviews, ID_CONTRIBUTION, R.string.notif_contribution_accepted_title, R.string.notif_contribution_accepted_text)
        else post(ctx, CHANNEL_REVIEWS, R.string.notif_channel_reviews, ID_CONTRIBUTION, R.string.notif_contribution_rejected_title, R.string.notif_contribution_rejected_text)

    fun contributionPublished(ctx: Context) =
        post(ctx, CHANNEL_REVIEWS, R.string.notif_channel_reviews, ID_CONTRIBUTION, R.string.notif_contribution_published_title, R.string.notif_contribution_published_text)

    fun reportAccepted(ctx: Context) =
        post(ctx, CHANNEL_REVIEWS, R.string.notif_channel_reviews, ID_REPORT, R.string.notif_report_accepted_title, R.string.notif_report_accepted_text)

    private fun post(ctx: Context, channel: String, @StringRes channelName: Int, id: Int, @StringRes title: Int, @StringRes text: Int, open: Intent? = null) {
        if (!permitted(ctx)) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            LEGACY_CHANNELS.forEach { runCatching { nm.deleteNotificationChannel(it) } }
            nm.createNotificationChannel(NotificationChannel(channel, ctx.getString(channelName), NotificationManager.IMPORTANCE_HIGH))
        }
        val intent = open ?: ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
        val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_piyo)
            .setColor(ContextCompat.getColor(ctx, R.color.brand_green))
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Android 7.x: heads-up without channels
            .setContentTitle(ctx.getString(title))
            .setContentText(ctx.getString(text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(ctx.getString(text)))
            .setContentIntent(pi).setAutoCancel(true)
            .build()
        runCatching { nm.notify(id, n) }
    }

    /** System settings page for this app's notifications (used when the permission was denied). */
    fun settingsIntent(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= 26) Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        else Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
}
