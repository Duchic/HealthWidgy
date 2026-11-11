package cz.healthwidgy.data.hc

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.RemoteException
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Result of one Health Connect call, so a single failing type never breaks a whole screen. */
sealed interface HcResult<out T> {
  data class Ok<T>(val value: T) : HcResult<T>

  data class Failed(val kind: FailureKind, val error: Throwable) : HcResult<Nothing>
}

enum class FailureKind {
  /** Missing read permission, or a read attempted from the background without the background permission. */
  NoPermission,
  /** Rate limit hit, or Health Connect is busy (backup/restore or data sync in progress). Retry later. */
  Busy,
  Remote,
  Io,
  Unknown,
}

fun <T> HcResult<T>.getOrNull(): T? = (this as? HcResult.Ok)?.value

/** Snapshot of what the app may do with Health Connect right now. */
data class HcAccess(
  val sdkStatus: Int,
  val granted: Set<String> = emptySet(),
  val backgroundFeatureAvailable: Boolean = false,
  val historyFeatureAvailable: Boolean = false,
  val garminInstalled: Boolean = false,
) {
  val isAvailable: Boolean
    get() = sdkStatus == HealthConnectClient.SDK_AVAILABLE

  val grantedDataPermissions: Int
    get() = HcTypes.dataPermissions.count { it in granted }

  val hasAnyDataPermission: Boolean
    get() = grantedDataPermissions > 0

  val hasAllDataPermissions: Boolean
    get() = grantedDataPermissions == HcTypes.dataPermissions.size

  val hasBackground: Boolean
    get() = ExtraPermissions.Background in granted

  val hasHistory: Boolean
    get() = ExtraPermissions.History in granted

  fun isGranted(type: HcType): Boolean = type.readPermission in granted

  /** Everything worth asking for: all data types plus the extra permissions this device supports. */
  val permissionsToRequest: Set<String>
    get() = buildSet {
      addAll(HcTypes.dataPermissions)
      if (backgroundFeatureAvailable) add(ExtraPermissions.Background)
      if (historyFeatureAvailable) add(ExtraPermissions.History)
    }

  val missingPermissions: Set<String>
    get() = permissionsToRequest - granted
}

/** Thin wrapper around [HealthConnectClient]: availability, permissions and error mapping. */
class HealthConnectSource(private val context: Context) {

  private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

  fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

  suspend fun access(): HcAccess {
    val status = sdkStatus()
    val garmin = isPackageInstalled(GARMIN_CONNECT_PACKAGE)
    if (status != HealthConnectClient.SDK_AVAILABLE) return HcAccess(status, garminInstalled = garmin)
    val granted =
      try {
        client.permissionController.getGrantedPermissions()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        emptySet()
      }
    return HcAccess(
      sdkStatus = status,
      granted = granted,
      backgroundFeatureAvailable = isFeatureAvailable(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND),
      historyFeatureAvailable = isFeatureAvailable(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY),
      garminInstalled = garmin,
    )
  }

  private fun isFeatureAvailable(feature: Int): Boolean =
    try {
      client.features.getFeatureStatus(feature) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    } catch (e: Exception) {
      false
    }

  fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
    PermissionController.createRequestPermissionResultContract()

  /**
   * Runs [block] against the client and maps the documented failure modes. Cancellation is rethrown:
   * CancellationException extends IllegalStateException, which Health Connect uses for rate limits.
   */
  suspend fun <T> call(block: suspend HealthConnectClient.() -> T): HcResult<T> =
    try {
      HcResult.Ok(client.block())
    } catch (e: CancellationException) {
      throw e
    } catch (e: SecurityException) {
      HcResult.Failed(FailureKind.NoPermission, e)
    } catch (e: IllegalStateException) {
      HcResult.Failed(FailureKind.Busy, e)
    } catch (e: RemoteException) {
      HcResult.Failed(FailureKind.Remote, e)
    } catch (e: IOException) {
      HcResult.Failed(FailureKind.Io, e)
    } catch (e: Exception) {
      HcResult.Failed(FailureKind.Unknown, e)
    }

  /**
   * Health Connect settings home. The per-app page (android.health.connect.action.MANAGE_HEALTH_PERMISSIONS)
   * is guarded by GRANT_RUNTIME_PERMISSIONS and throws SecurityException for third-party apps, so the user
   * is sent here and continues via "Oprávnění aplikací → HealthWidgy".
   */
  fun healthConnectSettingsIntent(): Intent =
    Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  fun garminLaunchIntent(): Intent? =
    context.packageManager.getLaunchIntentForPackage(GARMIN_CONNECT_PACKAGE)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  private fun isPackageInstalled(pkg: String): Boolean =
    try {
      context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
      true
    } catch (e: PackageManager.NameNotFoundException) {
      false
    }
}
