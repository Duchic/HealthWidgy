package cz.healthwidgy.data.hc

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
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
import java.time.Instant

// Health Connect keeps IntervalRecord, InstantaneousRecord and SeriesRecord internal to Kotlin callers,
// so time spans and samples are read per concrete record type (the types in HcTypes).

/** Start of an interval record, or the time of an instantaneous one. */
fun Record.startTimeOrNull(): Instant? =
  when (this) {
    is StepsRecord -> startTime
    is DistanceRecord -> startTime
    is FloorsClimbedRecord -> startTime
    is ElevationGainedRecord -> startTime
    is ActiveCaloriesBurnedRecord -> startTime
    is TotalCaloriesBurnedRecord -> startTime
    is HeartRateRecord -> startTime
    is SleepSessionRecord -> startTime
    is ExerciseSessionRecord -> startTime
    is SpeedRecord -> startTime
    is CyclingPedalingCadenceRecord -> startTime
    is RestingHeartRateRecord -> time
    is WeightRecord -> time
    is BodyFatRecord -> time
    else -> null
  }

/** End of an interval record, or the time of an instantaneous one. */
fun Record.endTimeOrNull(): Instant? =
  when (this) {
    is StepsRecord -> endTime
    is DistanceRecord -> endTime
    is FloorsClimbedRecord -> endTime
    is ElevationGainedRecord -> endTime
    is ActiveCaloriesBurnedRecord -> endTime
    is TotalCaloriesBurnedRecord -> endTime
    is HeartRateRecord -> endTime
    is SleepSessionRecord -> endTime
    is ExerciseSessionRecord -> endTime
    is SpeedRecord -> endTime
    is CyclingPedalingCadenceRecord -> endTime
    is RestingHeartRateRecord -> time
    is WeightRecord -> time
    is BodyFatRecord -> time
    else -> null
  }

/** True for records that span a time interval (as opposed to a single measurement). */
fun Record.isInterval(): Boolean = this !is RestingHeartRateRecord && this !is WeightRecord && this !is BodyFatRecord

/** Sample timestamps of series records (heart rate, speed, cadence); null for other types. */
fun Record.sampleTimesOrNull(): List<Instant>? =
  when (this) {
    is HeartRateRecord -> samples.map { it.time }
    is SpeedRecord -> samples.map { it.time }
    is CyclingPedalingCadenceRecord -> samples.map { it.time }
    else -> null
  }
