package cz.healthwidgy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.healthwidgy.data.hc.HcAccess
import cz.healthwidgy.data.hc.HealthConnectSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Tracks Health Connect availability and granted permissions; refreshed on every resume. */
class AccessViewModel(private val source: HealthConnectSource) : ViewModel() {
  private val _access = MutableStateFlow<HcAccess?>(null)
  val access: StateFlow<HcAccess?> = _access.asStateFlow()

  /**
   * True after a permission request that granted nothing new. Health Connect closes its dialog without
   * showing it when any requested permission was denied twice (USER_FIXED), so the UI then points the
   * user to Health Connect settings.
   */
  private val _permissionHint = MutableStateFlow(false)
  val permissionHint: StateFlow<Boolean> = _permissionHint.asStateFlow()

  private var lastRequested: Set<String> = emptySet()

  fun refresh() {
    viewModelScope.launch { _access.value = source.access() }
  }

  fun beforeRequest(permissions: Set<String>) {
    lastRequested = permissions
    _permissionHint.value = false
  }

  fun onPermissionResult(granted: Set<String>) {
    _permissionHint.value = lastRequested.isNotEmpty() && granted.intersect(lastRequested).isEmpty()
    refresh()
  }
}
