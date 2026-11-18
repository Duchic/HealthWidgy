package cz.healthwidgy.demo

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Velocity
import cz.healthwidgy.data.hc.GARMIN_CONNECT_PACKAGE
import cz.healthwidgy.data.hc.endTimeOrNull
import cz.healthwidgy.data.hc.isInterval
import cz.healthwidgy.data.hc.startTimeOrNull
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random
import kotlin.reflect.KClass

/**
 * A fictional person's Garmin data, generated day by day: steps, distance, floors and calories in 15-minute
 * blocks, heart rate every few minutes, resting heart rate, sleep with stages, a weekly training plan with
 * speed and elevation, and weight with body fat slowly trending down. Every day comes from its own seed, so
 * the numbers stay the same between queries and app restarts. None of it is real.
 */
internal class DemoData {
  /** Read on every use, like the app does, so a time zone change does not shift the data against the app. */
  val zone: ZoneId
    get() = ZoneId.systemDefault()

  private var cachedZone: ZoneId? = null


  /** Records of one day up to [cutoff], by record type. A night belongs to the day it ends on. */
  private class Day(val records: Map<KClass<out Record>, List<Record>>, val cutoff: Instant, val complete: Boolean)

  private val cache =
    object : LinkedHashMap<LocalDate, Day>(128, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LocalDate, Day>?) = size > CACHED_DAYS
    }

  /**
   * Health Connect's record classes reject start times in the future, so today is generated only up to [now]
   * and again a minute later. The random draws do not depend on the cutoff, so earlier records stay the same.
   */
  private fun day(date: LocalDate, now: Instant): Day =
    synchronized(cache) {
      // Days are generated in local time: a new zone makes every cached day wrong.
      if (zone != cachedZone) {
        cache.clear()
        cachedZone = zone
      }
      val cached = cache[date]
      if (cached != null && (cached.complete || Duration.between(cached.cutoff, now) < Duration.ofMinutes(1))) cached
      else generate(date, now).also { cache[date] = it }
    }

  /**
   * Records of [type] that Health Connect would return for [from, to): interval records that overlap the
   * range, instantaneous ones inside it, nothing that ends after [now] and nothing older than the history.
   */
  fun records(type: KClass<out Record>, from: Instant, to: Instant, now: Instant): List<Record> {
    val today = now.atZone(zone).toLocalDate()
    // A night that ends on the next day starts the evening before, so look one day further both ways.
    var date = maxOf(from.atZone(zone).toLocalDate().minusDays(1), today.minusDays(HISTORY_DAYS))
    val last = minOf(to.atZone(zone).toLocalDate().plusDays(1), today)
    val out = ArrayList<Record>()
    while (!date.isAfter(last)) {
      day(date, now).records[type]?.forEach { record ->
        val start = record.startTimeOrNull() ?: return@forEach
        val end = record.endTimeOrNull() ?: return@forEach
        val inside = if (record.isInterval()) start < to && end > from else start >= from && start < to
        if (inside && end <= now) out += record
      }
      date = date.plusDays(1)
    }
    return out
  }

  // --- Plans shared by neighbouring days (a night spans midnight) -------------------------------------

  private class Stage(val start: Instant, val end: Instant, val type: Int)

  private class Night(val start: Instant, val end: Instant, val stages: List<Stage>) {
    fun stageAt(t: Instant): Int? = if (t < start || t >= end) null else stages.firstOrNull { t >= it.start && t < it.end }?.type
  }

  private class Workout(
    val type: Int,
    val title: String,
    val start: Instant,
    val end: Instant,
    val metersPerMinute: Double,
    val stepsPerMinute: Int,
    val kcalPerMinute: Double,
    val targetBpm: Int,
    val elevationMeters: Double,
    val intervals: Boolean = false,
  ) {
    val minutes: Double
      get() = Duration.between(start, end).toMillis() / 60_000.0

    operator fun contains(t: Instant) = t >= start && t < end
  }

  private fun random(date: LocalDate, salt: Int) = Random(date.toEpochDay() * 1_000_003L + salt)

  private fun LocalDate.at(hour: Int, minute: Int): Instant = atTime(hour, minute).atZone(zone).toInstant()

  private fun Instant.plusMinutes(minutes: Long): Instant = plusSeconds(minutes * 60)

  private fun offset(t: Instant): ZoneOffset = zone.rules.getOffset(t)

  private fun isWeekend(date: LocalDate) = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

  /** The night that ends on the morning of [wakeDate]: about 7–8 hours in 90-minute cycles. */
  private fun night(wakeDate: LocalDate): Night {
    val r = random(wakeDate, 1)
    val weekend = isWeekend(wakeDate)
    val bed = wakeDate.minusDays(1).at(22, 25).plusMinutes(r.nextLong(0, 60) + if (weekend) 40 else 0)
    val wake = wakeDate.at(6, 0).plusMinutes(r.nextLong(0, 45) + if (weekend) 90 else 0)
    val stages = mutableListOf<Stage>()
    var t = bed.plusMinutes(r.nextLong(5, 16))
    stages += Stage(bed, t, SleepSessionRecord.STAGE_TYPE_AWAKE)
    var cycle = 0
    while (t < wake) {
      val plan =
        listOf(
          SleepSessionRecord.STAGE_TYPE_LIGHT to r.nextLong(18, 33),
          SleepSessionRecord.STAGE_TYPE_DEEP to DEEP_MINUTES[min(cycle, DEEP_MINUTES.lastIndex)] + r.nextLong(-5, 6),
          SleepSessionRecord.STAGE_TYPE_LIGHT to r.nextLong(8, 17),
          SleepSessionRecord.STAGE_TYPE_REM to 8L + cycle * 5 + r.nextLong(-3, 6),
        )
      for ((type, minutes) in plan) {
        if (minutes <= 0 || t >= wake) continue
        val end = minOf(t.plusMinutes(minutes), wake)
        // Light sleep on both sides of a skipped deep phase is one stage.
        val previous = stages.last()
        if (previous.type == type) stages[stages.lastIndex] = Stage(previous.start, end, type) else stages += Stage(t, end, type)
        t = end
      }
      if (t < wake && r.nextInt(3) == 0) {
        val end = minOf(t.plusMinutes(r.nextLong(1, 5)), wake)
        stages += Stage(t, end, SleepSessionRecord.STAGE_TYPE_AWAKE)
        t = end
      }
      cycle++
    }
    return Night(bed, wake, stages)
  }

  /** The training plan: strength on Monday, runs on Tuesday and Thursday, a ride on Saturday, a long Sunday. */
  private fun workout(date: LocalDate): Workout? {
    val r = random(date, 2)
    if (r.nextInt(100) < 12) return null
    fun plan(type: Int, title: String, hour: Int, minute: Int, jitter: Int, minutes: IntRange, metersPerMinute: Double, steps: Int, kcal: Double, bpm: Int, elevation: Double, intervals: Boolean = false): Workout {
      val start = date.at(hour, minute).plusMinutes(r.nextLong(0, jitter + 1L))
      val end = start.plusMinutes(r.nextLong(minutes.first.toLong(), minutes.last + 1L))
      return Workout(type, title, start, end, metersPerMinute, steps, kcal, bpm, elevation, intervals)
    }
    return when (date.dayOfWeek) {
      DayOfWeek.MONDAY -> plan(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, "Posilovna", 18, 0, 20, 40..55, 0.0, 0, 6.5, 112, 0.0)
      DayOfWeek.TUESDAY -> plan(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, "Běh", 17, 30, 25, 42..55, r.nextDouble(180.0, 196.0), 168, 11.5, 148, r.nextDouble(40.0, 90.0))
      DayOfWeek.WEDNESDAY -> plan(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, "Polední procházka", 12, 5, 15, 28..40, r.nextDouble(82.0, 90.0), 118, 4.5, 98, r.nextDouble(5.0, 20.0))
      DayOfWeek.THURSDAY -> plan(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, "Intervaly", 17, 40, 20, 38..48, r.nextDouble(192.0, 206.0), 172, 12.5, 150, r.nextDouble(30.0, 60.0), intervals = true)
      DayOfWeek.FRIDAY -> null
      DayOfWeek.SATURDAY -> plan(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, "Vyjížďka", 9, 20, 40, 80..120, r.nextDouble(400.0, 470.0), 0, 9.0, 132, r.nextDouble(350.0, 750.0))
      DayOfWeek.SUNDAY ->
        if (r.nextInt(4) == 0) plan(ExerciseSessionRecord.EXERCISE_TYPE_HIKING, "Túra", 9, 45, 30, 150..210, r.nextDouble(65.0, 75.0), 105, 6.5, 112, r.nextDouble(400.0, 700.0))
        else plan(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, "Dlouhý běh", 8, 50, 30, 70..95, r.nextDouble(176.0, 186.0), 166, 11.0, 143, r.nextDouble(80.0, 160.0))
    }
  }

  /** Resting heart rate of a day: around 52, a little lower over the year, with slow waves. */
  private fun restingBpm(date: LocalDate): Double {
    val r = random(date, 3)
    val day = date.toEpochDay().toDouble()
    return 53.0 + 1.6 * sin(2 * PI * day / 47) + 0.9 * sin(2 * PI * day / 9) - 1.5 * yearProgress(date) + r.nextDouble(-0.8, 0.8)
  }

  /** 0 a year ago, 1 today: drives the slow trends (weight, body fat, resting heart rate). */
  private fun yearProgress(date: LocalDate): Double {
    val daysAgo = Duration.between(date.atStartOfDay(), LocalDate.now(zone).atStartOfDay()).toDays().toDouble()
    val x = (1.0 - daysAgo / HISTORY_DAYS).coerceIn(0.0, 1.0)
    return x * x * (3 - 2 * x)
  }

  // --- One day ---------------------------------------------------------------------------------------

  private fun generate(date: LocalDate, cutoff: Instant): Day {
    val r = random(date, 0)
    val out = HashMap<KClass<out Record>, MutableList<Record>>()
    /** Builds and keeps a record that has ended by [cutoff]; ids depend only on kind and start. */
    fun add(kind: String, start: Instant, end: Instant, device: Device = WATCH, make: (Metadata) -> Record) {
      if (end > cutoff) return
      val record = make(HcInternals.metadata("demo-$kind-${start.epochSecond}", ORIGIN, end, device))
      out.getOrPut(record::class) { mutableListOf() } += record
    }

    val dayStart = date.atStartOfDay(zone).toInstant()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
    val nights = listOf(night(date), night(date.plusDays(1)))
    fun stageAt(t: Instant): Int? = nights.firstNotNullOfOrNull { it.stageAt(t) }
    fun asleepMinutes(from: Instant, to: Instant): Double =
      nights.sumOf { n -> max(0L, Duration.between(maxOf(from, n.start), minOf(to, n.end)).toMillis()) } / 60_000.0
    val workout = workout(date)
    val resting = restingBpm(date)

    // 1. Everyday walking in 15-minute blocks, cut around the workout. Weights follow a commuter's day.
    val weekend = isWeekend(date)
    val profile = if (weekend) WEEKEND_PROFILE else WEEKDAY_PROFILE
    val season = 1.0 + 0.12 * cos(2 * PI * (date.dayOfYear - 196) / 365.25)
    val dailyWalking = (if (weekend) r.nextDouble(5_500.0, 11_500.0) else r.nextDouble(6_500.0, 10_000.0)) * season

    class Piece(val start: Instant, val end: Instant, val weight: Double) {
      val minutes = Duration.between(start, end).toMillis() / 60_000.0
      var steps = 0L
    }
    val pieces = mutableListOf<Piece>()
    var blockStart = dayStart
    while (blockStart < dayEnd) {
      val blockEnd = minOf(blockStart.plusMinutes(15), dayEnd)
      val parts =
        if (workout == null || workout.end <= blockStart || workout.start >= blockEnd) listOf(blockStart to blockEnd)
        else listOfNotNull((blockStart to workout.start).takeIf { workout.start > blockStart }, (workout.end to blockEnd).takeIf { workout.end < blockEnd })
      val hour = blockStart.atZone(zone).hour
      parts.forEach { (s, e) ->
        val minutes = Duration.between(s, e).toMillis() / 60_000.0
        val awake = ((minutes - asleepMinutes(s, e)) / 15.0).coerceAtLeast(0.0)
        pieces += Piece(s, e, profile[hour] * awake * r.nextDouble(0.55, 1.45))
      }
      blockStart = blockEnd
    }
    val totalWeight = pieces.sumOf { it.weight }.takeIf { it > 0 } ?: 1.0
    pieces.forEach { it.steps = (dailyWalking * it.weight / totalWeight).roundToLong() }

    val blockSteps = HashMap<Instant, Long>()
    pieces.forEach { p ->
      val active = p.steps * KCAL_PER_STEP
      val (s, e) = p.start to p.end
      if (p.steps > 0) {
        add("steps", s, e) { StepsRecord(s, offset(s), e, offset(e), p.steps, it) }
        add("distance", s, e) { DistanceRecord(s, offset(s), e, offset(e), Length.meters(p.steps * STRIDE_METERS), it) }
        add("active", s, e) { ActiveCaloriesBurnedRecord(s, offset(s), e, offset(e), Energy.kilocalories(active), it) }
      }
      add("total", s, e) { TotalCaloriesBurnedRecord(s, offset(s), e, offset(e), Energy.kilocalories(BMR_KCAL_PER_MINUTE * p.minutes + active), it) }
      if (p.steps > 120 && r.nextInt(100) < 40) {
        val floors = if (r.nextInt(100) < 25) 2.0 else 1.0
        add("floors", s, e) { FloorsClimbedRecord(s, offset(s), e, offset(e), floors, it) }
      }
      val block = p.start.atZone(zone).let { it.withMinute(it.minute / 15 * 15).withSecond(0).withNano(0).toInstant() }
      blockSteps[block] = (blockSteps[block] ?: 0L) + p.steps
    }

    // 2. The workout with its own totals, speed samples and elevation.
    workout?.let { w ->
      val s = w.start
      val e = w.end
      val active = w.kcalPerMinute * w.minutes
      add("workout", s, e) { ExerciseSessionRecord(s, offset(s), e, offset(e), it, w.type, w.title, null) }
      if (w.stepsPerMinute > 0) add("workout-steps", s, e) { StepsRecord(s, offset(s), e, offset(e), (w.stepsPerMinute * w.minutes).roundToLong(), it) }
      if (w.metersPerMinute > 0) add("workout-distance", s, e) { DistanceRecord(s, offset(s), e, offset(e), Length.meters(w.metersPerMinute * w.minutes), it) }
      add("workout-active", s, e) { ActiveCaloriesBurnedRecord(s, offset(s), e, offset(e), Energy.kilocalories(active), it) }
      add("workout-total", s, e) { TotalCaloriesBurnedRecord(s, offset(s), e, offset(e), Energy.kilocalories(BMR_KCAL_PER_MINUTE * w.minutes + active), it) }
      if (w.elevationMeters > 0) {
        add("elevation", s, e) { ElevationGainedRecord(s, offset(s), e, offset(e), Length.meters(w.elevationMeters), it) }
        // On foot, the watch counts part of the climb as floors (kept small so the floors chart stays readable).
        if (w.stepsPerMinute > 0) add("workout-floors", s, e) { FloorsClimbedRecord(s, offset(s), e, offset(e), min(w.elevationMeters / 12, 18.0).roundToLong().toDouble(), it) }
      }
      if (w.metersPerMinute > 0) {
        // Drawn even when the workout is still ahead, so the random sequence (and earlier records) never shift.
        val samples = mutableListOf<SpeedRecord.Sample>()
        var t = s
        while (t < e) {
          val minute = Duration.between(s, t).toMillis() / 60_000.0
          val noise = r.nextDouble(-0.06, 0.06)
          val factor =
            when {
              w.intervals && minute >= 10 && Duration.between(t, e).toMinutes() > 6 -> (if (((minute - 10) / 2).toInt() % 2 == 0) 1.22 else 0.78) + noise
              w.intervals -> 0.85 + 0.05 * sin(minute) + noise
              else -> 1.0 + 0.07 * sin(minute / 6.0) + noise
            }
          samples += SpeedRecord.Sample(t, Velocity.metersPerSecond(w.metersPerMinute / 60.0 * factor))
          t = t.plusSeconds(30)
        }
        add("speed", s, e) { SpeedRecord(s, offset(s), e, offset(e), samples, it) }
      }
    }

    // 3. Heart rate: every 3 minutes, every 30 seconds during the workout; one record per 15 minutes.
    fun bpm(t: Instant): Long {
      val noise = r.nextDouble(-3.0, 3.0)
      stageAt(t)?.let { stage ->
        val delta =
          when (stage) {
            SleepSessionRecord.STAGE_TYPE_DEEP -> -3.0
            SleepSessionRecord.STAGE_TYPE_LIGHT -> 1.0
            SleepSessionRecord.STAGE_TYPE_REM -> 5.0
            else -> 10.0
          }
        return (resting + delta + noise / 2).roundToLong()
      }
      if (workout != null && t in workout) {
        val elapsed = Duration.between(workout.start, t).toMillis() / 60_000.0
        val left = Duration.between(t, workout.end).toMillis() / 60_000.0
        var target = workout.targetBpm.toDouble()
        if (workout.intervals && elapsed >= 10 && left > 6) target += if (((elapsed - 10) / 2).toInt() % 2 == 0) 18.0 else -12.0
        val warm = min(1.0, elapsed / 8.0) * min(1.0, 0.4 + left / 8.0)
        return (95 + (target - 95) * warm + noise * 1.5).roundToLong()
      }
      val hour = t.atZone(zone).hour
      val block = t.atZone(zone).let { it.withMinute(it.minute / 15 * 15).withSecond(0).withNano(0).toInstant() }
      val stepsPerMinute = (blockSteps[block] ?: 0L) / 15.0
      val recovery = workout?.let { w -> if (t >= w.end && t < w.end.plusMinutes(40)) (1 - Duration.between(w.end, t).toMinutes() / 40.0) * 22 else 0.0 } ?: 0.0
      val base = if (stepsPerMinute > 45) 96.0 else if (hour in 9..17) 72.0 else 67.0
      return (base + recovery + noise * 2).roundToLong()
    }
    blockStart = dayStart
    while (blockStart < dayEnd) {
      val blockEnd = minOf(blockStart.plusMinutes(15), dayEnd)
      val samples = mutableListOf<HeartRateRecord.Sample>()
      var t = blockStart.plusSeconds(r.nextLong(0, 60))
      while (t < blockEnd) {
        samples += HeartRateRecord.Sample(t, bpm(t))
        t = t.plusSeconds(if (workout != null && t in workout) 30 else 180)
      }
      val (s, e) = blockStart to blockEnd
      if (samples.isNotEmpty()) add("heart", s, e) { HeartRateRecord(s, offset(s), e, offset(e), samples, it) }
      blockStart = blockEnd
    }

    // 4. The night that ended this morning, then resting heart rate and a weighing after getting up.
    val night = nights[0]
    add("sleep", night.start, night.end) {
      SleepSessionRecord(
        night.start, offset(night.start), night.end, offset(night.end), it, null, null,
        night.stages.map { stage -> SleepSessionRecord.Stage(stage.start, stage.end, stage.type) },
      )
    }
    val restingAt = night.end.plusMinutes(2)
    add("resting", restingAt, restingAt) { RestingHeartRateRecord(restingAt, offset(restingAt), resting.roundToLong(), it) }
    // Weighed on about every other morning (never depending on the cutoff, so a past day never changes).
    if (r.nextInt(100) < 55) {
      val at = night.end.plusMinutes(r.nextLong(10, 26))
      val trend = yearProgress(date)
      val kg = 84.6 - 4.6 * trend + 0.25 * sin(2 * PI * date.toEpochDay() / 7) + r.nextDouble(-0.3, 0.3)
      val fat = 23.4 - 3.2 * trend + r.nextDouble(-0.25, 0.25)
      add("weight", at, at, SCALE) { WeightRecord(at, offset(at), Mass.kilograms(kg), it) }
      add("body-fat", at, at, SCALE) { BodyFatRecord(at, offset(at), Percentage(fat), it) }
    }
    return Day(out, cutoff, complete = cutoff >= dayEnd)
  }

  companion object {
    val ORIGIN = DataOrigin(GARMIN_CONNECT_PACKAGE)
    private val WATCH = Device(Device.TYPE_WATCH, "Garmin", "Forerunner 265")
    private val SCALE = Device(Device.TYPE_SCALE, "Garmin", "Index S2")

    /** A bit more than the app's one-year backfill. */
    const val HISTORY_DAYS = 400L
    private const val CACHED_DAYS = 120

    private const val STRIDE_METERS = 0.74
    private const val KCAL_PER_STEP = 0.042
    private const val BMR_KCAL_PER_MINUTE = 1_720.0 / 1_440.0
    private val DEEP_MINUTES = listOf(38L, 28L, 16L, 8L, 0L)

    /** Relative walking per hour of the day (0–23). */
    private val WEEKDAY_PROFILE = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.1, 0.5, 1.6, 0.9, 0.35, 0.45, 0.4, 1.0, 0.45, 0.4, 0.5, 1.4, 0.9, 0.8, 0.6, 0.4, 0.3, 0.15, 0.05)
    private val WEEKEND_PROFILE = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.1, 0.3, 0.6, 0.9, 1.2, 1.0, 0.7, 0.6, 1.1, 1.2, 1.0, 0.8, 0.6, 0.5, 0.4, 0.3, 0.15, 0.05)
  }
}
