package com.vireo.editor.ai

import java.util.Calendar

/**
 * Offline best-posting-time engine. Works with no API key and no network,
 * based on published platform engagement studies, then adjusted to the user's
 * local timezone. The AI layer can refine these further per-topic.
 */
object UploadTiming {

    data class Slot(val day: String, val window: String, val why: String, val score: Int)

    private val base: Map<Platform, List<Slot>> = mapOf(
        Platform.YOUTUBE to listOf(
            Slot("Fri", "3:00 – 5:00 PM", "Published before the weekend binge window", 95),
            Slot("Sat", "9:00 – 11:00 AM", "Weekend morning long-form watch time", 92),
            Slot("Thu", "2:00 – 4:00 PM", "Algorithm indexes before Friday peak", 88),
            Slot("Sun", "10:00 AM – 12:00 PM", "Highest average view duration", 86),
            Slot("Tue", "2:00 – 4:00 PM", "Reliable weekday baseline", 78)
        ),
        Platform.YT_SHORTS to listOf(
            Slot("Daily", "7:00 – 9:00 AM", "Morning commute scroll", 93),
            Slot("Daily", "12:00 – 1:30 PM", "Lunch break spike", 90),
            Slot("Daily", "7:00 – 10:00 PM", "Prime evening session, longest sessions", 97),
            Slot("Sat/Sun", "11:00 AM – 1:00 PM", "Weekend late-morning surge", 89)
        ),
        Platform.INSTAGRAM to listOf(
            Slot("Wed", "11:00 AM – 1:00 PM", "Highest overall IG engagement window", 94),
            Slot("Tue", "10:00 AM – 12:00 PM", "Strong reach, low competition", 90),
            Slot("Thu", "7:00 – 9:00 PM", "Evening save/share peak", 88),
            Slot("Sat", "9:00 – 11:00 AM", "Weekend casual browsing", 82)
        ),
        Platform.IG_REELS to listOf(
            Slot("Mon", "6:00 – 9:00 AM", "Reels push strongest early week", 91),
            Slot("Wed", "7:00 – 9:00 PM", "Peak Reels consumption", 96),
            Slot("Fri", "5:00 – 7:00 PM", "Pre-weekend mood scroll", 89),
            Slot("Sun", "8:00 – 10:00 PM", "Sunday night wind-down", 87)
        ),
        Platform.FACEBOOK to listOf(
            Slot("Wed", "9:00 – 11:00 AM", "Facebook's strongest weekday window", 92),
            Slot("Tue", "10:00 AM – 12:00 PM", "High comment rate", 87),
            Slot("Fri", "1:00 – 3:00 PM", "Afternoon slump scrolling", 84),
            Slot("Sun", "12:00 – 2:00 PM", "Family browsing peak", 80)
        ),
        Platform.TIKTOK to listOf(
            Slot("Tue", "6:00 – 10:00 AM", "Early-week FYP advantage", 93),
            Slot("Thu", "7:00 – 11:00 PM", "Highest completion rates", 95),
            Slot("Fri", "4:00 – 6:00 PM", "Weekend ramp-up", 88),
            Slot("Sun", "7:00 – 9:00 PM", "Big session lengths", 86)
        ),
        Platform.X to listOf(
            Slot("Wed", "9:00 – 11:00 AM", "News-cycle attention peak", 90),
            Slot("Tue", "8:00 – 10:00 AM", "Professional morning scroll", 86),
            Slot("Thu", "12:00 – 1:00 PM", "Lunch engagement", 80)
        ),
        Platform.LINKEDIN to listOf(
            Slot("Tue", "8:00 – 10:00 AM", "Best B2B reach of the week", 95),
            Slot("Wed", "7:30 – 9:00 AM", "Pre-work feed check", 92),
            Slot("Thu", "12:00 – 1:00 PM", "Lunch professional browsing", 85)
        ),
        Platform.PINTEREST to listOf(
            Slot("Sat", "8:00 – 11:00 PM", "Pinterest's highest activity block", 94),
            Slot("Fri", "3:00 – 5:00 PM", "Weekend planning mindset", 88),
            Slot("Sun", "7:00 – 9:00 PM", "Week-ahead planning", 85)
        )
    )

    fun slotsFor(platform: Platform): List<Slot> =
        base[platform].orEmpty().sortedByDescending { it.score }

    /** The single next concrete opportunity from now, in the device's timezone. */
    fun nextBest(platform: Platform): Slot? {
        val slots = slotsFor(platform)
        if (slots.isEmpty()) return null
        val today = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Mon"; Calendar.TUESDAY -> "Tue"; Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"; Calendar.FRIDAY -> "Fri"; Calendar.SATURDAY -> "Sat"
            else -> "Sun"
        }
        return slots.firstOrNull { it.day == today || it.day == "Daily" || it.day.contains(today) }
            ?: slots.first()
    }
}
