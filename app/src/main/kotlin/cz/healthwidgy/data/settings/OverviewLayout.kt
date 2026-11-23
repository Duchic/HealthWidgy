package cz.healthwidgy.data.settings

/** What one card on the Přehled tab shows. The names are stored in the settings: keep them stable. */
enum class OverviewCardType(val label: String, val description: String, val wideByDefault: Boolean) {
  STEPS("Kroky dnes", "Kroužek kroků; přes celou šířku i vzdálenost, patra a kalorie", true),
  HEART("Tep", "Poslední tep, rozsah dne a klidový tep", false),
  SLEEP("Spánek", "Délka a fáze poslední noci", false),
  CALORIES("Kalorie", "Celkový výdej a kolik při tréninku", false),
  WEIGHT("Váha", "Poslední vážení, změna za týden a tuk", false),
  DISTANCE("Vzdálenost", "Dnešní kilometry a plnění cíle", false),
  FLOORS("Patra", "Dnešní patra a plnění cíle", false),
  WORKOUT("Poslední trénink", "Poslední trénink za 30 dní", true),
  STEPS_WEEK("Kroky · 7 dní", "Graf kroků za týden s čarou cíle", true),
}

/** One card on the Přehled tab: what it shows and whether it spans the width or shares a row. */
data class OverviewCard(val type: OverviewCardType, val wide: Boolean)

/** Where a card sits: wide cards take a whole row, half cards fill a row two at a time. */
data class CardSlot(val row: Int, val column: Int)

/** The Přehled layout the user sets in "Upravit přehled", stored as "STEPS:W,HEART:H,…". */
object OverviewLayout {
  /** The look before the layout became configurable. */
  val DEFAULT: List<OverviewCard> =
    listOf(
      OverviewCard(OverviewCardType.STEPS, wide = true),
      OverviewCard(OverviewCardType.HEART, wide = false),
      OverviewCard(OverviewCardType.SLEEP, wide = false),
      OverviewCard(OverviewCardType.CALORIES, wide = false),
      OverviewCard(OverviewCardType.WEIGHT, wide = false),
      OverviewCard(OverviewCardType.WORKOUT, wide = true),
      OverviewCard(OverviewCardType.STEPS_WEEK, wide = true),
    )

  fun encode(cards: List<OverviewCard>): String = cards.joinToString(",") { "${it.type.name}:${if (it.wide) "W" else "H"}" }

  /**
   * Null (never set) gives [DEFAULT]; an empty text is a layout the user emptied on purpose. Unknown
   * types (from a newer version) are skipped and every type appears at most once.
   */
  fun decode(text: String?): List<OverviewCard> {
    if (text == null) return DEFAULT
    return text
      .split(',')
      .mapNotNull { part ->
        val type = OverviewCardType.entries.firstOrNull { it.name == part.substringBefore(':') } ?: return@mapNotNull null
        OverviewCard(type, wide = part.substringAfter(':', "") == "W")
      }
      .distinctBy { it.type }
  }

  /** Card types not on the layout yet, in their natural order. */
  fun available(cards: List<OverviewCard>): List<OverviewCardType> = OverviewCardType.entries.filter { type -> cards.none { it.type == type } }

  /** [cards] with the card at [index] moved [by] places (negative = up), clamped to the list. */
  fun move(cards: List<OverviewCard>, index: Int, by: Int): List<OverviewCard> {
    if (index !in cards.indices) return cards
    val target = (index + by).coerceIn(0, cards.lastIndex)
    if (target == index) return cards
    return cards.toMutableList().apply { add(target, removeAt(index)) }
  }

  /**
   * Row and column of each card. Half cards pair up in order; a half card followed by a wide one (or
   * last) stays alone in its row, on the left.
   */
  fun slots(wide: List<Boolean>): List<CardSlot> {
    var row = 0
    var column = 0
    return wide.map { isWide ->
      if (isWide) {
        if (column == 1) {
          row++
          column = 0
        }
        CardSlot(row++, 0)
      } else {
        CardSlot(row, column).also {
          if (column == 0) column = 1
          else {
            column = 0
            row++
          }
        }
      }
    }
  }
}
