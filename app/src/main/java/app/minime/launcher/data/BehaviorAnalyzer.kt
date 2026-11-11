package app.minime.launcher.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.math.roundToInt

data class DayStat(val date: LocalDate, val totalMs: Long, val unlocks: Int, val sessions: Int)

data class AppStat(val packageName: String, val label: String, val ms: Long)

data class BehaviorReport(
    val today: DayStat,
    /** Oldest first, ending with today. */
    val week: List<DayStat>,
    /** Average total of previous days measured up to the current time of day; null without history. */
    val typicalSoFarMs: Long?,
    val hourlyToday: List<Long>,
    val hourlyTypical: List<Long>,
    val topApps: List<AppStat>,
    val insights: List<String>,
    val generatedAt: Long,
)

/**
 * On-device behaviour analysis over the last [DAYS] days of usage events.
 * Nothing leaves the phone; the report is recomputed whenever the Insights screen is open.
 */
class BehaviorAnalyzer(
    private val usage: UsageRepository,
    private val labelOf: (String) -> String,
) {
    fun analyze(now: Long = System.currentTimeMillis()): BehaviorReport {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = (DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }
        fun startOf(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        val todayStart = startOf(today)
        val elapsedToday = now - todayStart

        val log = usage.read(startOf(days.first()), now)
        val sessions = log.sessions

        // --- per-day totals ---
        val week = days.map { d ->
            val from = startOf(d)
            val to = startOf(d.plusDays(1)).coerceAtMost(now)
            DayStat(
                date = d,
                totalMs = sessions.sumOf { it.overlap(from, to) },
                unlocks = log.unlocks.count { it in from until to },
                sessions = sessions.count { it.start in from until to },
            )
        }
        val todayStat = week.last()
        val history = week.dropLast(1).filter { it.totalMs > 0 }

        // Fair comparison: previous days measured only up to this same time of day.
        val typicalSoFar = history.takeIf { it.isNotEmpty() }?.let { prev ->
            prev.map { d -> val from = startOf(d.date); sessions.sumOf { it.overlap(from, from + elapsedToday) } }.average().toLong()
        }

        // --- hourly profiles ---
        fun hourly(d: LocalDate): List<Long> {
            val base = startOf(d)
            return (0 until 24).map { h -> sessions.sumOf { it.overlap(base + h * HOUR, base + (h + 1) * HOUR) } }
        }
        val hourlyToday = hourly(today)
        val hourlyTypical = if (history.isEmpty()) List(24) { 0L } else {
            val profiles = history.map { hourly(it.date) }
            (0 until 24).map { h -> profiles.sumOf { it[h] } / profiles.size }
        }

        // --- top apps today ---
        val todaySessions = sessions.filter { it.end > todayStart }
        val topApps = todaySessions
            .groupBy { it.packageName }
            .mapValues { (_, list) -> list.sumOf { it.overlap(todayStart, now) } }
            .filterValues { it >= 60_000L }
            .entries.sortedByDescending { it.value }
            .take(5)
            .map { AppStat(it.key, labelOf(it.key), it.value) }

        val insights = buildList {
            compareToTypical(todayStat.totalMs, typicalSoFar)?.let(::add)
            unlockRhythm(todayStat.unlocks, todaySessions.firstOrNull()?.start, now)?.let(::add)
            checkingHabit(sessions)?.let(::add)
            longestStretch(todaySessions, todayStart, now)?.let(::add)
            lateNight(sessions, startOf(today.minusDays(1)) + 23 * HOUR, todayStart + 4 * HOUR)?.let(::add)
            morningRitual(sessions, days.dropLast(1), ::startOf)?.let(::add)
            peakHour(hourlyTypical)?.let(::add)
            weeklyTrend(week.dropLast(1))?.let(::add)
        }.take(MAX_INSIGHTS)

        return BehaviorReport(todayStat, week, typicalSoFar, hourlyToday, hourlyTypical, topApps, insights, now)
    }

    // --- pattern rules: each returns one sentence, or null when there's nothing worth saying ---

    private fun compareToTypical(todayMs: Long, typicalMs: Long?): String? {
        if (typicalMs == null || typicalMs < 5 * MINUTE) return null
        val pct = ((todayMs - typicalMs) * 100.0 / typicalMs).roundToInt()
        return when {
            abs(pct) < 8 -> "You're right on your usual pace for this time of day."
            pct < 0 -> "You're ${-pct}% under a typical day at this hour. Nice."
            else -> "You're $pct% over a typical day at this hour."
        }
    }

    private fun unlockRhythm(unlocks: Int, firstUse: Long?, now: Long): String? {
        if (unlocks < 5 || firstUse == null) return null
        val every = (now - firstUse) / unlocks / MINUTE
        if (every < 1) return null
        return "$unlocks unlocks today, about one every ${duration(every * MINUTE)}."
    }

    /** Apps opened often but only briefly: checking, not using. */
    private fun checkingHabit(sessions: List<Session>): String? {
        val candidate = sessions.groupBy { it.packageName }
            .mapNotNull { (pkg, list) ->
                val short = list.count { it.length < MINUTE }
                if (list.size >= 15 && short * 2 >= list.size) Triple(pkg, short, list.size) else null
            }
            .maxByOrNull { it.second } ?: return null
        val share = candidate.second * 100 / candidate.third
        return "$share% of your ${labelOf(candidate.first)} visits this week lasted under a minute. That's checking, not using."
    }

    private fun longestStretch(todaySessions: List<Session>, todayStart: Long, now: Long): String? {
        val longest = todaySessions.maxByOrNull { it.overlap(todayStart, now) } ?: return null
        val ms = longest.overlap(todayStart, now)
        if (ms < 15 * MINUTE) return null
        return "Longest stretch today: ${duration(ms)} in ${labelOf(longest.packageName)}."
    }

    private fun lateNight(sessions: List<Session>, from: Long, to: Long): String? {
        val ms = sessions.sumOf { it.overlap(from, to) }
        if (ms < 15 * MINUTE) return null
        val top = sessions.filter { it.overlap(from, to) > 0 }
            .groupBy { it.packageName }.maxByOrNull { (_, l) -> l.sumOf { it.overlap(from, to) } }?.key
        return "Last night you spent ${duration(ms)} on your phone after ${hourLabel(23)}" +
            (top?.let { ", mostly ${labelOf(it)}." } ?: ".")
    }

    /** The app most often opened first thing (after 4 AM) on previous days. */
    private fun morningRitual(sessions: List<Session>, previousDays: List<LocalDate>, startOf: (LocalDate) -> Long): String? {
        val firsts = previousDays.mapNotNull { d ->
            val wake = startOf(d) + 4 * HOUR
            sessions.firstOrNull { it.start >= wake && it.start < startOf(d.plusDays(1)) }?.packageName
        }
        val (pkg, count) = firsts.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
        if (count < 3) return null
        return "Your day usually starts with ${labelOf(pkg)} ($count of the last ${firsts.size} days)."
    }

    private fun peakHour(hourlyTypical: List<Long>): String? {
        val max = hourlyTypical.maxOrNull() ?: return null
        if (max < 10 * MINUTE) return null
        val hour = hourlyTypical.indexOf(max)
        return "Your busiest hour is usually ${hourLabel(hour)}–${hourLabel((hour + 1) % 24)}."
    }

    /** Last 3 full days against the 3 before them. */
    private fun weeklyTrend(fullDays: List<DayStat>): String? {
        if (fullDays.size < 6) return null
        val recent = fullDays.takeLast(3).map { it.totalMs }.average()
        val before = fullDays.dropLast(3).takeLast(3).map { it.totalMs }.average()
        if (before < 30 * MINUTE) return null
        val pct = ((recent - before) * 100 / before).roundToInt()
        return when {
            pct <= -10 -> "Trending down: the last 3 days averaged ${-pct}% less than the 3 before."
            pct >= 10 -> "Trending up: the last 3 days averaged $pct% more than the 3 before."
            else -> null
        }
    }

    private fun hourLabel(hour: Int): String =
        LocalTime.of(hour, 0).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    private fun duration(ms: Long): String = formatScreenTime(ms) ?: "under a minute"

    companion object {
        const val DAYS = 7
        private const val MAX_INSIGHTS = 5
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE
    }
}
