package cz.healthwidgy.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.healthwidgy.data.overview.Overview
import cz.healthwidgy.data.overview.OverviewRepository
import cz.healthwidgy.data.settings.Settings
import cz.healthwidgy.data.settings.SettingsRepository
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OverviewUiState(
  val overview: Overview? = null,
  val loading: Boolean = true,
  /** Set when a reload failed; the previous overview (if any) stays on screen. */
  val error: String? = null,
)

class OverviewViewModel(
  private val repository: OverviewRepository,
  settingsRepository: SettingsRepository,
) : ViewModel() {

  private val _state = MutableStateFlow(OverviewUiState())
  val state: StateFlow<OverviewUiState> = _state.asStateFlow()

  private var settings: Settings? = null
  private var granted: Set<String>? = null
  private var loadJob: Job? = null
  private var lastLoad: Instant = Instant.EPOCH

  init {
    viewModelScope.launch {
      settingsRepository.settings.distinctUntilChanged().collect {
        settings = it
        refresh(force = true)
      }
    }
  }

  /** Called with the current Health Connect grants; a change reloads at once instead of waiting for the throttle. */
  fun onAccessChanged(newGranted: Set<String>) {
    val old = granted
    granted = newGranted
    if (old != newGranted) refresh(force = true)
  }

  /** Reloads from Health Connect; without [force] at most once per [MIN_INTERVAL] (e.g. on every resume). */
  fun refresh(force: Boolean = false) {
    val currentSettings = settings ?: return
    val currentGranted = granted ?: return
    if (!force && Duration.between(lastLoad, Instant.now()) < MIN_INTERVAL) return
    if (loadJob?.isActive == true && !force) return
    loadJob?.cancel()
    lastLoad = Instant.now()
    _state.update { it.copy(loading = true, error = null) }
    loadJob =
      viewModelScope.launch {
        try {
          val overview = repository.load(currentSettings, currentGranted)
          // A partial load is retried on the next resume instead of being throttled.
          if (overview.failures.isNotEmpty()) lastLoad = Instant.EPOCH
          _state.update { old ->
            val previous = old.overview
            if (!overview.hasAnyData && overview.failures.isNotEmpty() && previous?.hasAnyData == true) {
              // Health Connect was busy or rate limited: keep the last good dashboard.
              old.copy(loading = false, error = "Health Connect teď data nevydal. Zkus obnovit za chvíli.")
            } else {
              OverviewUiState(overview = overview, loading = false)
            }
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          lastLoad = Instant.EPOCH
          _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
        }
      }
  }

  private companion object {
    val MIN_INTERVAL: Duration = Duration.ofMinutes(2)
  }
}
