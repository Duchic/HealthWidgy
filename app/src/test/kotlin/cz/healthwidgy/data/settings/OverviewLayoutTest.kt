package cz.healthwidgy.data.settings

import cz.healthwidgy.data.settings.OverviewCardType.CALORIES
import cz.healthwidgy.data.settings.OverviewCardType.HEART
import cz.healthwidgy.data.settings.OverviewCardType.SLEEP
import cz.healthwidgy.data.settings.OverviewCardType.STEPS
import cz.healthwidgy.data.settings.OverviewCardType.WEIGHT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewLayoutTest {
  private fun half(type: OverviewCardType) = OverviewCard(type, wide = false)

  private fun wide(type: OverviewCardType) = OverviewCard(type, wide = true)

  @Test
  fun layoutSurvivesStoringAndUnsetMeansDefault() {
    val cards = listOf(wide(STEPS), half(HEART), half(SLEEP), wide(WEIGHT))
    assertEquals("STEPS:W,HEART:H,SLEEP:H,WEIGHT:W", OverviewLayout.encode(cards))
    assertEquals(cards, OverviewLayout.decode(OverviewLayout.encode(cards)))
    assertEquals(OverviewLayout.DEFAULT, OverviewLayout.decode(null))
    // Emptied on purpose stays empty.
    assertTrue(OverviewLayout.decode(OverviewLayout.encode(emptyList())).isEmpty())
  }

  @Test
  fun unknownTypesAreSkippedAndDuplicatesDropped() {
    assertEquals(listOf(half(HEART), wide(SLEEP)), OverviewLayout.decode("HEART:H,STRESS:W,SLEEP:W,HEART:W"))
    // A missing size reads as half.
    assertEquals(listOf(half(CALORIES)), OverviewLayout.decode("CALORIES"))
  }

  @Test
  fun moveStaysInsideTheList() {
    val cards = listOf(half(HEART), half(SLEEP), half(CALORIES))
    assertEquals(listOf(half(SLEEP), half(HEART), half(CALORIES)), OverviewLayout.move(cards, 1, -1))
    assertEquals(listOf(half(HEART), half(CALORIES), half(SLEEP)), OverviewLayout.move(cards, 1, 1))
    assertEquals(cards, OverviewLayout.move(cards, 0, -1))
    assertEquals(cards, OverviewLayout.move(cards, 2, 1))
    assertEquals(cards, OverviewLayout.move(cards, 5, -1))
  }

  @Test
  fun availableListsTheTypesNotShown() {
    val all = OverviewLayout.available(emptyList())
    assertEquals(OverviewCardType.entries, all)
    assertTrue(STEPS !in OverviewLayout.available(OverviewLayout.DEFAULT))
    assertEquals(listOf(OverviewCardType.DISTANCE, OverviewCardType.FLOORS), OverviewLayout.available(OverviewLayout.DEFAULT))
  }

  @Test
  fun halfCardsPairUpAndWideCardsTakeTheirOwnRow() {
    // wide | half half | half (alone before a wide) | wide | half (alone at the end)
    val slots = OverviewLayout.slots(listOf(true, false, false, false, true, false))
    assertEquals(
      listOf(CardSlot(0, 0), CardSlot(1, 0), CardSlot(1, 1), CardSlot(2, 0), CardSlot(3, 0), CardSlot(4, 0)),
      slots,
    )
    assertEquals(listOf(CardSlot(0, 0), CardSlot(0, 1), CardSlot(1, 0), CardSlot(1, 1)), OverviewLayout.slots(listOf(false, false, false, false)))
    assertTrue(OverviewLayout.slots(emptyList()).isEmpty())
  }
}
