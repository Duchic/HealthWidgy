package cz.healthwidgy.data.hc

import androidx.health.connect.client.permission.HealthPermission
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
import kotlin.reflect.KClass

/** Package name under which Garmin Connect writes its data into Health Connect. */
const val GARMIN_CONNECT_PACKAGE = "com.garmin.android.apps.connectmobile"

/** One Health Connect record type the app reads. */
data class HcType(
  val id: String,
  val label: String,
  val recordType: KClass<out Record>,
) {
  val readPermission: String
    get() = HealthPermission.getReadPermission(recordType)
}

/**
 * Every record type Garmin Connect may write (checked on the phone: Garmin Connect 5.31 holds write
 * permission for exactly these). Keep in sync with the <uses-permission> list in AndroidManifest.xml.
 */
object HcTypes {
  val Steps = HcType("steps", "Kroky", StepsRecord::class)
  val Distance = HcType("distance", "Vzdálenost", DistanceRecord::class)
  val Floors = HcType("floors", "Patra", FloorsClimbedRecord::class)
  val Elevation = HcType("elevation", "Převýšení", ElevationGainedRecord::class)
  val ActiveCalories = HcType("active_calories", "Aktivní kalorie", ActiveCaloriesBurnedRecord::class)
  val TotalCalories = HcType("total_calories", "Celkové kalorie", TotalCaloriesBurnedRecord::class)
  val HeartRate = HcType("heart_rate", "Srdeční tep", HeartRateRecord::class)
  val RestingHeartRate = HcType("resting_heart_rate", "Klidový tep", RestingHeartRateRecord::class)
  val Sleep = HcType("sleep", "Spánek", SleepSessionRecord::class)
  val Exercise = HcType("exercise", "Tréninky", ExerciseSessionRecord::class)
  val Speed = HcType("speed", "Rychlost", SpeedRecord::class)
  /** Covered by the READ_EXERCISE permission. */
  val CyclingCadence = HcType("cycling_cadence", "Kadence na kole", CyclingPedalingCadenceRecord::class)
  val Weight = HcType("weight", "Váha", WeightRecord::class)
  val BodyFat = HcType("body_fat", "Tělesný tuk", BodyFatRecord::class)

  val all: List<HcType> =
    listOf(
      Steps, Distance, Floors, Elevation, ActiveCalories, TotalCalories, HeartRate,
      RestingHeartRate, Sleep, Exercise, Speed, CyclingCadence, Weight, BodyFat,
    )

  /** Read permissions for all data types (13 distinct strings: cadence shares READ_EXERCISE). */
  val dataPermissions: Set<String> = all.map { it.readPermission }.toSet()
}

/** Health Connect permissions that are not tied to a data type. */
object ExtraPermissions {
  /** Lets widgets and background work read data while the app is not on screen. */
  val Background: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
  /** Lifts the default limit of 30 days before the first permission grant. */
  val History: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
}
