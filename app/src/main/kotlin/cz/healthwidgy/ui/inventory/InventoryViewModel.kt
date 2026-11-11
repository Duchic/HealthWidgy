package cz.healthwidgy.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.healthwidgy.data.hc.HealthConnectSource
import cz.healthwidgy.data.inventory.InventoryReport
import cz.healthwidgy.data.inventory.InventoryRepository
import cz.healthwidgy.data.inventory.TypeInventory
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InventoryUiState(
  val running: Boolean = false,
  val types: List<TypeInventory> = emptyList(),
  val report: InventoryReport? = null,
  val exportFile: File? = null,
  val error: String? = null,
)

class InventoryViewModel(
  private val repository: InventoryRepository,
  private val source: HealthConnectSource,
) : ViewModel() {

  private val _state = MutableStateFlow(InventoryUiState())
  val state: StateFlow<InventoryUiState> = _state.asStateFlow()
  private var job: Job? = null

  fun run() {
    if (_state.value.running) return
    _state.value = InventoryUiState(running = true)
    job =
      viewModelScope.launch {
        try {
          val access = source.access()
          val report = repository.build(access) { type -> _state.update { it.copy(types = it.types + type) } }
          _state.update { it.copy(running = false, report = report, types = report.types, exportFile = repository.exportFile) }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          _state.update { it.copy(running = false, error = e.message ?: e.toString()) }
        }
      }
  }

  /**
   * Stops a running inventory when the app leaves the screen: without the background permission Health
   * Connect refuses reads from the background, which would save a report full of false permission errors.
   * The export file is written only at the end, so a cancelled run leaves no partial JSON.
   */
  fun cancelIfRunning() {
    if (!_state.value.running) return
    job?.cancel()
    _state.update { it.copy(running = false, types = emptyList(), error = "Inventura byla přerušena, protože aplikace přešla do pozadí. Spusť ji znovu.") }
  }
}
