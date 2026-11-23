package cz.healthwidgy.data.overview

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewDataTest {
  private val prague = ZoneId.of("Europe/Prague")

  private fun at(date: LocalDate, hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(prague).toInstant()

  @Test
  fun heartSamplesAreGroupedByLocalHour() {
    val day = LocalDate.of(2026, 11, 23)
    val hours = hourlyRanges(listOf(at(day, 13, 40) to 90L, at(day, 7, 5) to 60L, at(day, 7, 50) to 80L), prague)
    assertEquals(listOf(HourRange(7, 60, 80, 70.0), HourRange(13, 90, 90, 90.0)), hours)
    assertTrue(hourlyRanges(emptyList(), prague).isEmpty())
  }

  @Test
  fun theRepeatedHourOfTheAutumnClockChangeIsOneSlot() {
    // 25 Oct 2026: 2:00–3:00 happens twice in Prague (summer time, then winter time).
    val summer = LocalDate.of(2026, 10, 25).atTime(2, 30).atZone(prague).withEarlierOffsetAtOverlap().toInstant()
    val winter = LocalDate.of(2026, 10, 25).atTime(2, 30).atZone(prague).withLaterOffsetAtOverlap().toInstant()
    assertEquals(listOf(HourRange(2, 50, 58, 54.0)), hourlyRanges(listOf(summer to 50L, winter to 58L), prague))
  }

  @Test
  fun weightKeepsTheNewestValuePerDayInsideTheWindow() {
    val today = LocalDate.of(2026, 11, 23)
    val values =
      listOf(
        at(today, 7) to 79.9,
        at(today.minusDays(1), 7) to 80.4,
        at(today.minusDays(1), 20) to 80.8,
        at(today.minusDays(40), 7) to 82.0,
      )
    assertEquals(
      listOf(today.minusDays(1) to 80.8, today to 79.9),
      latestPerDay(values, prague, from = today.minusDays(29)),
    )
  }
}
