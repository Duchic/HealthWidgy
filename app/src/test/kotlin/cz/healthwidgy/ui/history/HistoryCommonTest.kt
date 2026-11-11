package cz.healthwidgy.ui.history

import cz.healthwidgy.data.db.DailyMetricEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryCommonTest {
  private val today = LocalDate.of(2026, 11, 10) // Tuesday

  private fun row(date: String, value: Double) = DailyMetricEntity(date, "steps", "GARMIN_ONLY", value, updatedAt = 0)

  @Test
  fun weekHasSevenDaysEndingTodayWithGaps() {
    val slots = buildSlots(listOf(row("2026-11-10", 8432.0), row("2026-11-08", 12000.0)), HistoryPeriod.WEEK, today)
    assertEquals(7, slots.size)
    assertEquals(LocalDate.of(2026, 11, 4), slots.first().date)
    assertEquals(today, slots.last().date)
    assertEquals(8432.0, slots.last().value!!, 0.0)
    assertNull(slots[5].value) // 9. 11. has no data
    assertEquals("Út", slots.last().label)
    assertEquals("Út 10. 11.", slots.last().fullLabel)
  }

  @Test
  fun monthLabelsEveryWeekFromToday() {
    val slots = buildSlots(emptyList(), HistoryPeriod.MONTH, today)
    assertEquals(30, slots.size)
    assertEquals("10. 11.", slots.last().label)
    assertEquals("3. 11.", slots[slots.size - 8].label)
    assertNull(slots[slots.size - 2].label)
  }

  @Test
  fun yearAveragesDaysWithDataPerMonth() {
    val rows = listOf(row("2026-11-01", 10000.0), row("2026-11-02", 6000.0), row("2026-10-15", 9000.0))
    val slots = buildSlots(rows, HistoryPeriod.YEAR, today)
    assertEquals(12, slots.size)
    assertEquals(8000.0, slots.last().value!!, 0.001)
    assertEquals(9000.0, slots[10].value!!, 0.001)
    assertNull(slots.first().value)
    assertEquals(LocalDate.of(2025, 12, 1), slots.first().date)
  }

  @Test
  fun summaryCountsOnlyDaysWithDataSinceStart() {
    val rows = listOf(row("2026-11-01", 4000.0), row("2026-11-05", 12000.0), row("2026-11-10", 11000.0))
    val s = summarize(rows, LocalDate.of(2026, 11, 4), isSum = true, goal = 10000.0)
    assertEquals(2, s.daysWithData)
    assertEquals(11500.0, s.average!!, 0.001)
    assertEquals(23000.0, s.total!!, 0.001)
    assertEquals(12000.0, s.best!!.value!!, 0.001)
    assertEquals(2, s.goalHits)
  }
}
