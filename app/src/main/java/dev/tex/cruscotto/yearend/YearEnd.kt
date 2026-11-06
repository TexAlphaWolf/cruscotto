package dev.tex.cruscotto.yearend

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.tex.cruscotto.MainActivity
import dev.tex.cruscotto.R
import dev.tex.cruscotto.prefs.AppPrefs
import dev.tex.cruscotto.prefs.Locales
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Year-end clean-up reminder. Data from past years is deleted at the first unlock on or after
 * 1 January (see Session). Two notifications remind the user to export a backup or the year's PDF:
 * on 1 December and a last one on 20 December. One inexact alarm at a time: no wake-ups, no
 * background service.
 */
object YearEnd {
    private const val CHANNEL = "year_end"
    private const val NOTIFICATION_ID = 1201
    private const val REQUEST = 1201

    const val FIRST_DAY = 1
    const val FINAL_DAY = 20

    /** True from 1 to 31 December, when the in-app reminder is shown. */
    fun isReminderPeriod(today: LocalDate = LocalDate.now()): Boolean = today.monthValue == 12

    /** True from 20 December: the in-app reminder becomes the last one. */
    fun isFinalPeriod(today: LocalDate = LocalDate.now()): Boolean = today.monthValue == 12 && today.dayOfMonth >= FINAL_DAY

    /** The next reminder at 10:00: 1 December, then 20 December (the last one), then next year's 1 December. */
    fun nextReminder(now: LocalDateTime = LocalDateTime.now()): Pair<LocalDateTime, Boolean> {
        val candidates = listOf(
            LocalDateTime.of(now.year, 12, FIRST_DAY, 10, 0) to false,
            LocalDateTime.of(now.year, 12, FINAL_DAY, 10, 0) to true,
            LocalDateTime.of(now.year + 1, 12, FIRST_DAY, 10, 0) to false,
        )
        return candidates.first { now.isBefore(it.first) }
    }

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val (time, final) = nextReminder()
        val pi = pendingIntent(context, final)
        if (!AppPrefs.purgeEnabled) { am.cancel(pi); return }
        val at = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // inexact: Android may deliver it a little later, which is fine for a reminder
        am.set(AlarmManager.RTC, at, pi)
    }

    /** Same request code for both reminders: scheduling one replaces the other. */
    private fun pendingIntent(context: Context, final: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST,
            Intent(context, YearEndReceiver::class.java).setAction(ACTION_REMIND).putExtra(EXTRA_FINAL, final),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    internal const val ACTION_REMIND = "dev.tex.cruscotto.YEAR_END_REMIND"
    internal const val EXTRA_FINAL = "final"

    internal fun notify(context: Context, final: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_year_end), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val year = LocalDate.now().year
        // generic text only: no amounts or other data on the lock screen
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(if (final) R.string.notif_year_end_final_title else R.string.notif_year_end_title, year))
            .setContentText(context.getString(R.string.notif_year_end_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_year_end_text)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
    }
}

/** Fires on 1 and 20 December, and re-arms the alarm after a reboot or an app update (alarms do not survive those). */
class YearEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppPrefs.init(context)
        val ctx = Locales.current(context)
        when (intent.action) {
            YearEnd.ACTION_REMIND -> {
                if (AppPrefs.purgeEnabled) YearEnd.notify(ctx, intent.getBooleanExtra(YearEnd.EXTRA_FINAL, false))
                YearEnd.schedule(ctx)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> YearEnd.schedule(ctx)
        }
    }
}
