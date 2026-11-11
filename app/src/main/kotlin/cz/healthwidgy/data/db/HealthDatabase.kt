package cz.healthwidgy.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/*
 * Local cache of what Health Connect holds. Everything here can be rebuilt from Health Connect at any time,
 * so schema changes simply drop and refill the database. `source` is the SourceMode name the row was
 * computed for (GARMIN_ONLY / ALL_SOURCES), because totals differ per source filter.
 */

/** One value per local day and metric (see [Metrics] for ids). Sums use [value]; heart rate uses min/max/avg. */
@Entity(tableName = "daily_metric", primaryKeys = ["date", "metric", "source"])
data class DailyMetricEntity(
  /** ISO local date, e.g. 2026-11-10. */
  val date: String,
  val metric: String,
  val source: String,
  val value: Double?,
  val min: Double? = null,
  val max: Double? = null,
  val avg: Double? = null,
  val updatedAt: Long,
)

/** One value per hour for the last days (steps per hour, heart rate band per hour). */
@Entity(tableName = "hourly_metric", primaryKeys = ["hourStart", "metric", "source"])
data class HourlyMetricEntity(
  /** Epoch millis of the hour start. */
  val hourStart: Long,
  /** ISO local date of the hour. */
  val date: String,
  val metric: String,
  val source: String,
  val value: Double?,
  val min: Double? = null,
  val max: Double? = null,
  val avg: Double? = null,
)

@Entity(tableName = "sleep_session", primaryKeys = ["id", "source"])
data class SleepSessionEntity(
  val id: String,
  val source: String,
  val start: Long,
  val end: Long,
  /** ISO local date the session ended on; a night belongs to the morning it ended. */
  val wakeDate: String,
  val asleepMinutes: Long,
  val deepMinutes: Long,
  val lightMinutes: Long,
  val remMinutes: Long,
  val awakeMinutes: Long,
  val unspecifiedMinutes: Long,
  /** Stages as "type,startMillis,endMillis;…" (empty when the session has no stages). */
  val stages: String,
)

@Entity(tableName = "exercise_session", primaryKeys = ["id", "source"])
data class ExerciseSessionEntity(
  val id: String,
  val source: String,
  val exerciseType: Int,
  val title: String?,
  val start: Long,
  val end: Long,
  val date: String,
  /** Null until computed (older sessions are computed when first opened). */
  val statsComputed: Boolean,
  val distanceMeters: Double?,
  val activeKcal: Double?,
  val avgHeartRate: Long?,
  val maxHeartRate: Long?,
  val elevationGainMeters: Double?,
)

/** Weight or body fat reading from any app (single readings: no double counting across sources). */
@Entity(tableName = "body_measurement", primaryKeys = ["id", "kind"])
data class BodyMeasurementEntity(
  val id: String,
  /** "weight" (kg) or "body_fat" (%). */
  val kind: String,
  val time: Long,
  val date: String,
  val value: Double,
  val origin: String,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
  @PrimaryKey val source: String,
  /** First day the cache covers for this source. */
  val coveredFrom: String?,
  val lastSyncAt: Long?,
  /** Whether the history permission was granted when the cache was backfilled. */
  val backfilledWithHistory: Boolean,
  /** Comma-separated HcType ids whose history the last backfill loaded without errors. */
  val backfilledTypes: String = "",
)

@Dao
interface HealthDao {
  // --- daily ---
  @Query("SELECT * FROM daily_metric WHERE metric = :metric AND source = :source AND date >= :from ORDER BY date")
  fun dailyFlow(metric: String, source: String, from: String): Flow<List<DailyMetricEntity>>

  @Query("DELETE FROM daily_metric WHERE source = :source AND metric = :metric AND date >= :from AND date <= :to")
  suspend fun deleteDaily(source: String, metric: String, from: String, to: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertDaily(rows: List<DailyMetricEntity>)

  // --- hourly ---
  @Query("SELECT * FROM hourly_metric WHERE metric = :metric AND source = :source AND date = :date ORDER BY hourStart")
  fun hourlyFlow(metric: String, source: String, date: String): Flow<List<HourlyMetricEntity>>

  @Query("DELETE FROM hourly_metric WHERE source = :source AND metric = :metric AND hourStart >= :fromMillis")
  suspend fun deleteHourly(source: String, metric: String, fromMillis: Long)

  @Query("DELETE FROM hourly_metric WHERE hourStart < :beforeMillis")
  suspend fun pruneHourly(beforeMillis: Long)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertHourly(rows: List<HourlyMetricEntity>)

  // --- sleep ---
  @Query("SELECT * FROM sleep_session WHERE source = :source AND wakeDate >= :from ORDER BY end DESC")
  fun sleepFlow(source: String, from: String): Flow<List<SleepSessionEntity>>

  @Query("DELETE FROM sleep_session WHERE source = :source AND end >= :fromMillis")
  suspend fun deleteSleepFrom(source: String, fromMillis: Long)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSleep(rows: List<SleepSessionEntity>)

  // --- exercise ---
  @Query("SELECT * FROM exercise_session WHERE source = :source ORDER BY start DESC")
  fun exerciseFlow(source: String): Flow<List<ExerciseSessionEntity>>

  @Query("SELECT * FROM exercise_session WHERE source = :source AND id = :id")
  fun exerciseByIdFlow(source: String, id: String): Flow<ExerciseSessionEntity?>

  @Query("SELECT * FROM exercise_session WHERE source = :source AND id = :id")
  suspend fun exerciseById(source: String, id: String): ExerciseSessionEntity?

  @Query("SELECT * FROM exercise_session WHERE source = :source AND start >= :fromMillis")
  suspend fun exercisesFrom(source: String, fromMillis: Long): List<ExerciseSessionEntity>

  @Query("DELETE FROM exercise_session WHERE source = :source AND start >= :fromMillis")
  suspend fun deleteExercisesFrom(source: String, fromMillis: Long)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertExercises(rows: List<ExerciseSessionEntity>)

  // --- body ---
  @Query("SELECT * FROM body_measurement WHERE kind = :kind AND time >= :fromMillis ORDER BY time")
  fun bodyFlow(kind: String, fromMillis: Long): Flow<List<BodyMeasurementEntity>>

  @Query("DELETE FROM body_measurement WHERE kind = :kind AND time >= :fromMillis")
  suspend fun deleteBodyFrom(kind: String, fromMillis: Long)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertBody(rows: List<BodyMeasurementEntity>)

  // --- sync state ---
  @Query("SELECT * FROM sync_state WHERE source = :source")
  suspend fun syncState(source: String): SyncStateEntity?

  @Query("SELECT * FROM sync_state WHERE source = :source")
  fun syncStateFlow(source: String): Flow<SyncStateEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSyncState(state: SyncStateEntity)

  @Query("DELETE FROM sync_state")
  suspend fun clearSyncState()
}

@Database(
  entities =
    [
      DailyMetricEntity::class,
      HourlyMetricEntity::class,
      SleepSessionEntity::class,
      ExerciseSessionEntity::class,
      BodyMeasurementEntity::class,
      SyncStateEntity::class,
    ],
  version = 2,
  exportSchema = false,
)
abstract class HealthDatabase : RoomDatabase() {
  abstract fun dao(): HealthDao

  companion object {
    fun create(context: Context): HealthDatabase =
      Room.databaseBuilder(context, HealthDatabase::class.java, "health-cache.db")
        // A cache of Health Connect: on schema changes drop it and sync again.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
  }
}

/** Runs [block] in one transaction (used by the sync engine to replace a window atomically). */
interface TransactionRunner {
  suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactionRunner(private val db: HealthDatabase) : TransactionRunner {
  override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}
