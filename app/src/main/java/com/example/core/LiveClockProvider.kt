package com.example.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class LiveClockSnapshot(
    val epochMillis: Long,
    val formattedTime12h: String,
    val formattedTime24h: String,
    val formattedShortDate: String,
    val formattedFullDate: String,
    val dayOfWeek: String,
    val timezoneId: String,
    val timezoneShort: String,
) {
    fun displayTime(use24Hour: Boolean): String =
        if (use24Hour) formattedTime24h else formattedTime12h

    fun headerLabel(use24Hour: Boolean): String =
        "$formattedShortDate • ${displayTime(use24Hour)}"

    /**
     * Exact live temporal grounding injected into every OpenRouter system context.
     */
    fun toPromptContextLine(use24Hour: Boolean): String =
        "[Current Local Time: $formattedFullDate, ${displayTime(use24Hour)} $timezoneShort ($timezoneId)]"
}

/**
 * Real-time Android system clock monitor synced to ACTION_TIME_TICK,
 * ACTION_TIME_CHANGED, and ACTION_TIMEZONE_CHANGED.
 */
class LiveClockProvider(private val context: Context) {

    private val _clockState = MutableStateFlow(captureSnapshot())
    val clockState: StateFlow<LiveClockSnapshot> = _clockState.asStateFlow()

    @Volatile
    private var isRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            refreshNow()
        }
    }

    fun start() {
        refreshNow()
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        isRegistered = true
    }

    fun stop() {
        if (!isRegistered) return
        runCatching { context.unregisterReceiver(receiver) }
        isRegistered = false
    }

    fun refreshNow(): LiveClockSnapshot {
        val snapshot = captureSnapshot()
        _clockState.value = snapshot
        return snapshot
    }

    companion object {
        fun captureSnapshot(epochMillis: Long = System.currentTimeMillis()): LiveClockSnapshot {
            val now = Date(epochMillis)
            val tz = TimeZone.getDefault()
            val locale = Locale.getDefault()

            val time12 = SimpleDateFormat("h:mm a", locale).apply { timeZone = tz }.format(now)
            val time24 = SimpleDateFormat("HH:mm", locale).apply { timeZone = tz }.format(now)
            val shortDate = SimpleDateFormat("EEE, MMM d", locale).apply { timeZone = tz }.format(now)
            val fullDate = SimpleDateFormat("EEEE, MMM d, yyyy", locale).apply { timeZone = tz }.format(now)
            val dayOfWeek = SimpleDateFormat("EEEE", locale).apply { timeZone = tz }.format(now)
            val tzShort = tz.getDisplayName(tz.inDaylightTime(now), TimeZone.SHORT, locale)

            return LiveClockSnapshot(
                epochMillis = epochMillis,
                formattedTime12h = time12,
                formattedTime24h = time24,
                formattedShortDate = shortDate,
                formattedFullDate = fullDate,
                dayOfWeek = dayOfWeek,
                timezoneId = tz.id,
                timezoneShort = tzShort,
            )
        }

        fun formatMessageTimestamp(epochMillis: Long, use24Hour: Boolean): String {
            val pattern = if (use24Hour) "HH:mm" else "h:mm a"
            return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMillis))
        }

        fun formatMessagePromptStamp(epochMillis: Long, use24Hour: Boolean): String {
            val pattern = if (use24Hour) "yyyy-MM-dd HH:mm" else "MMM d, yyyy h:mm a"
            return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMillis))
        }

        fun dayBucketKey(epochMillis: Long): Int {
            val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
            return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        }

        fun formatDayDividerLabel(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
            val target = Calendar.getInstance().apply { timeInMillis = epochMillis }
            val today = Calendar.getInstance().apply { timeInMillis = nowMillis }
            val yesterday = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, -1)
            }

            val datePart = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(epochMillis))
            return when {
                sameDay(target, today) -> "Today • $datePart"
                sameDay(target, yesterday) -> "Yesterday • $datePart"
                else -> datePart
            }
        }

        fun formatReleaseDate(createdEpochSeconds: Long): String {
            if (createdEpochSeconds <= 0L) return "Unknown release"
            return SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                .format(Date(createdEpochSeconds * 1000L))
        }

        private fun sameDay(a: Calendar, b: Calendar): Boolean =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}
