package com.daytabs.planner

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.view.View
import android.widget.RemoteViews
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.roundToInt

internal fun readDocument(context: Context, uri: Uri, limit: Int, limitError: String): ByteArray {
    return requireNotNull(context.contentResolver.openInputStream(uri)) { "Could not open the selected file." }.use { input ->
        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream()
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            require(output.size() + count <= limit) { limitError }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
}

internal fun readAvatar(context: Context, uri: Uri): AvatarPhoto {
    val bytes = readDocument(context, uri, 20 * 1024 * 1024, "Choose an image under 20 MB.")
    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        val source = ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val scale = minOf(1f, 240f / maxOf(width, height))
            decoder.setTargetSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        decodeLegacyAvatar(bytes)
    }
    try {
        val output = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        return AvatarPhoto(UUID.randomUUID().toString(), AVATAR_PREFIX + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP))
    } finally {
        bitmap.recycle()
    }
}

private fun decodeLegacyAvatar(bytes: ByteArray): Bitmap {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    require(options.outWidth > 0 && options.outHeight > 0)
    val scale = minOf(1f, 240f / maxOf(options.outWidth, options.outHeight))
    val width = (options.outWidth * scale).roundToInt().coerceAtLeast(1)
    val height = (options.outHeight * scale).roundToInt().coerceAtLeast(1)
    options.inJustDecodeBounds = false
    options.inSampleSize = Integer.highestOneBit((1 / scale).toInt())
    val original = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
    val resized = Bitmap.createScaledBitmap(original, width, height, true)
    if (resized !== original) original.recycle()
    val orientation = runCatching {
        ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
    }
    if (matrix.isIdentity) return resized
    val corrected = Bitmap.createBitmap(resized, 0, 0, width, height, matrix, true)
    if (corrected !== resized) resized.recycle()
    return corrected
}

private const val CHANNEL = "daytabs-reminders"
internal enum class ReminderKind(val title: String, val hour: Int) {
    TODOS("Unfinished to-dos", 19), GOALS("Goal deadlines", 9)
}

internal fun todoMessage(count: Int) = "You have $count ${if (count == 1) "to-do" else "to-dos"} left today."

internal fun goalMessage(goals: List<Entry>, day: LocalDate, includeSingleDayTomorrow: Boolean = true): String {
    val pending = goals.filterNot { it.completed }
    val dueToday = pending.count { it.schedule?.finish == day }
    val dueTomorrow = pending.count {
        val schedule = it.schedule ?: return@count false
        schedule.finish == day.plusDays(1) && (includeSingleDayTomorrow || schedule.start < schedule.finish)
    }
    return listOf("today" to dueToday, "tomorrow" to dueTomorrow).mapNotNull { (whenDue, count) ->
        if (count == 0) null else "$count ${if (count == 1) "goal is" else "goals are"} due $whenDue."
    }.joinToString(" ")
}

internal object DeviceReminders {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Kaizen reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 26 ||
                context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance !=
                NotificationManager.IMPORTANCE_NONE)

    private data class Alarm(val kind: ReminderKind, val day: LocalDate) {
        val key: String get() = "${kind.name}:$day"
        val time: Long get() = day.atTime(kind.hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    @Synchronized
    fun reconcile(context: Context, state: PlannerState, reset: Boolean = false) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val stored = context.getSharedPreferences("kaizen_alarms", Context.MODE_PRIVATE)
        val existing = stored.getStringSet("scheduled", emptySet()).orEmpty()
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val candidates = buildList {
            if (!canNotify(context)) return@buildList
            if (state.reminders.todos && state.todos(today).any { !it.completed }) {
                add(Alarm(ReminderKind.TODOS, today))
            }
            if (state.reminders.goals) {
                val days = state.goals.asSequence().filterNot { it.completed }.mapNotNull { it.schedule }
                    .flatMap { if (it.start < it.finish) sequenceOf(it.finish.minusDays(1), it.finish) else sequenceOf(it.finish) }
                    .filter { it >= today }.distinct().sorted().take(61)
                addAll(days.map { Alarm(ReminderKind.GOALS, it) })
            }
        }
        val desired = candidates.filter { it.time > now || (!reset && "${it.key}:${it.time}" in existing) }
            .associateBy { "${it.key}:${it.time}" }
        (existing - desired.keys).forEach { saved ->
            val parts = saved.split(':')
            val kind = ReminderKind.entries.firstOrNull { it.name == parts.firstOrNull() }
            val day = parseDay(parts.getOrNull(1))
            if (kind != null && day != null) {
                val intent = pending(context, Alarm(kind, day), PendingIntent.FLAG_NO_CREATE)
                if (intent != null) {
                    alarms.cancel(intent)
                    intent.cancel()
                }
            }
        }
        val active = mutableSetOf<String>()
        desired.forEach { (key, alarm) ->
            if (!reset && key in existing) {
                active += key
            } else if (alarm.time > now) {
                val intent = requireNotNull(pending(context, alarm, PendingIntent.FLAG_UPDATE_CURRENT))
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.time, intent)
                active += key
            }
        }
        if (active != existing) stored.edit().putStringSet("scheduled", active).apply()
    }

    private fun pending(context: Context, alarm: Alarm, flag: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setData(Uri.parse("kaizen://reminder/${alarm.kind.name}/${alarm.day}"))
        return PendingIntent.getBroadcast(context, 0, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    @Synchronized
    fun deliver(context: Context, intent: Intent) {
        val route = intent.data?.pathSegments.orEmpty()
        val kind = ReminderKind.entries.firstOrNull { it.name == route.getOrNull(0) } ?: return
        val day = parseDay(route.getOrNull(1)) ?: return
        val alarm = Alarm(kind, day)
        val stored = context.getSharedPreferences("kaizen_alarms", Context.MODE_PRIVATE)
        val scheduled = stored.getStringSet("scheduled", emptySet()).orEmpty()
        val updated = scheduled.filterNot { it.startsWith("${alarm.key}:") }.toSet()
        if (scheduled == updated) return
        stored.edit().putStringSet("scheduled", updated).apply()
        val state = PlannerStore(context).load()
        if (day == LocalDate.now() && canNotify(context)) {
            val body = when (kind) {
                ReminderKind.TODOS -> if (state.reminders.todos)
                    state.todos(day).count { !it.completed }.takeIf { it > 0 }?.let(::todoMessage) else null
                ReminderKind.GOALS -> if (state.reminders.goals) goalMessage(state.goals, day, false) else null
            }
            if (!body.isNullOrEmpty()) {
                ensureChannel(context)
                show(context, kind, body)
            }
        }
        reconcile(context, state)
    }

    @Suppress("DEPRECATION")
    private fun show(context: Context, kind: ReminderKind, message: String) {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        builder.setSmallIcon(R.drawable.ic_reminder).setContentTitle(kind.title).setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message)).setAutoCancel(true).setContentIntent(tap)
        if (Build.VERSION.SDK_INT < 26) builder.setDefaults(Notification.DEFAULT_ALL)
        context.getSystemService(NotificationManager::class.java).notify(100 + kind.ordinal, builder.build())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = inBackground {
        DeviceReminders.deliver(context.applicationContext, intent)
    }
}

class DeviceClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> inBackground {
                val app = context.applicationContext
                DeviceReminders.reconcile(app, PlannerStore(app).load(), reset = true)
                DaytabsMotivationProvider.refresh(app)
            }
        }
    }
}

private val receiverExecutor = Executors.newSingleThreadExecutor()

private fun BroadcastReceiver.inBackground(work: () -> Unit) {
    val pending = goAsync()
    receiverExecutor.execute {
        try {
            work()
        } catch (failure: Exception) {
            android.util.Log.w("Kaizen", "Could not refresh scheduled planner features.", failure)
        } finally {
            pending.finish()
        }
    }
}

class DaytabsMotivationProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            refresh(context, manager, manager.getAppWidgetIds(ComponentName(context, DaytabsMotivationProvider::class.java)))
        }

        private fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val quote = dailyMotivation(LocalDate.now())
            val views = RemoteViews(context.packageName, R.layout.motivation_widget)
            views.setTextViewText(R.id.widget_quote, quote.displayLine)
            views.setTextViewText(R.id.widget_credit, quote.credit)
            views.setViewVisibility(R.id.widget_credit, if (quote.credit == null) View.GONE else View.VISIBLE)
            val tap = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, tap)
            manager.updateAppWidget(ids, views)
        }
    }
}
