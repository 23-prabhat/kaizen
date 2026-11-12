package com.daytabs.planner

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.time.Instant
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

    fun canScheduleExact(context: Context) = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    @Synchronized
    fun reconcile(context: Context, state: PlannerState, reset: Boolean = false) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val stored = context.getSharedPreferences("kaizen_alarms", Context.MODE_PRIVATE)
        val existing = stored.getStringSet("scheduled", emptySet()).orEmpty()
        val exact = canScheduleExact(context)
        val exactChanged = exact != stored.getBoolean("exact", false)
        val payloadChanged = stored.getInt("version", 0) != 1
        val desired = if (canNotify(context)) {
            planReminders(state, Instant.now(), ZoneId.systemDefault(), if (reset) emptySet() else existing)
        } else emptyList()
        val keys = desired.mapTo(mutableSetOf()) { it.key }
        (existing - keys).mapNotNull(ReminderAlarm::fromKey).forEach { alarm ->
            pending(context, alarm, PendingIntent.FLAG_NO_CREATE)?.let {
                alarms.cancel(it)
                it.cancel()
            }
        }
        for (alarm in desired) {
            val oldIntent = pending(context, alarm, PendingIntent.FLAG_NO_CREATE)
            val timed = alarm.kind.timed
            if (!reset && !payloadChanged && alarm.key in existing && oldIntent != null && (!timed || !exactChanged)) continue
            val intent = requireNotNull(pending(context, alarm, PendingIntent.FLAG_UPDATE_CURRENT))
            if (timed && exact) {
                try {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.time, intent)
                    continue
                } catch (_: SecurityException) {
                    // Android can revoke exact alarm access while this plan is being applied.
                }
            }
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.time, intent)
        }
        if (keys != existing || exactChanged || payloadChanged) {
            stored.edit().putStringSet("scheduled", keys).putBoolean("exact", exact).putInt("version", 1).apply()
        }
    }

    private fun pending(context: Context, alarm: ReminderAlarm, flag: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setData(Uri.parse("kaizen://reminder/${alarm.kind.name}/${alarm.token}"))
            .putExtra("scheduledTime", alarm.time)
        return PendingIntent.getBroadcast(context, 0, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    @Synchronized
    fun deliver(context: Context, intent: Intent) {
        val route = intent.data?.pathSegments.orEmpty()
        val kind = ReminderKind.entries.firstOrNull { it.name == route.getOrNull(0) } ?: return
        val token = route.getOrNull(1) ?: return
        val stored = context.getSharedPreferences("kaizen_alarms", Context.MODE_PRIVATE)
        val scheduled = stored.getStringSet("scheduled", emptySet()).orEmpty()
        val time = intent.getLongExtra("scheduledTime", 0)
        val alarm = ReminderAlarm(kind, token, time)
        if (alarm.key !in scheduled || time > System.currentTimeMillis()) return
        stored.edit().putStringSet("scheduled", scheduled - alarm.key).apply()
        val state = PlannerStore(context).load()
        if (canNotify(context) && (!kind.timed ||
                System.currentTimeMillis() - time < 24 * 3_600_000L)) {
            reminderBody(state, alarm, LocalDate.now())?.let { body ->
                ensureChannel(context)
                show(context, alarm, body)
            }
        }
        reconcile(context, state)
    }

    @Suppress("DEPRECATION")
    private fun show(context: Context, alarm: ReminderAlarm, message: String) {
        val kind = alarm.kind
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
        val tab = if (kind == ReminderKind.GOALS || kind == ReminderKind.PROGRESS) Tab.GOALS else Tab.TODOS
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(REMINDER_TAB, tab.name)
        val tap = PendingIntent.getActivity(context, 100 + kind.ordinal, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        builder.setSmallIcon(R.drawable.ic_reminder).setContentTitle(kind.title).setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message)).setAutoCancel(true).setContentIntent(tap)
        if (Build.VERSION.SDK_INT < 26) builder.setDefaults(Notification.DEFAULT_ALL)
        val tag = alarm.token.takeIf { kind.timed }
        context.getSystemService(NotificationManager::class.java).notify(tag, 100 + kind.ordinal, builder.build())
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
            Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> inBackground {
                val app = context.applicationContext
                DeviceReminders.reconcile(app, PlannerStore(app).load(), reset = true)
                DaytabsMotivationProvider.refresh(app)
            }
        }
    }
}

private val receiverExecutor = Executors.newSingleThreadExecutor()

internal fun BroadcastReceiver.inBackground(work: () -> Unit) {
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
