package com.byagowi.persiancalendar.ui.map

import androidx.lifecycle.ViewModel
import com.byagowi.persiancalendar.utils.DAY_IN_MILLIS
import com.byagowi.persiancalendar.utils.ONE_HOUR_IN_MILLIS
import io.github.persiancalendar.praytimes.Coordinates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge

class MapViewModel : ViewModel() {

    // State
    private val _time = MutableStateFlow(System.currentTimeMillis())
    val time: StateFlow<Long> get() = _time

    private val _displayNightMask = MutableStateFlow(true)
    val displayNightMask: StateFlow<Boolean> get() = _displayNightMask

    private val _displayLocation = MutableStateFlow(true)
    val displayLocation: StateFlow<Boolean> get() = _displayLocation

    private val _displayGrid = MutableStateFlow(false)
    val displayGrid: StateFlow<Boolean> get() = _displayGrid

    private val _isDirectPathMode = MutableStateFlow(false)
    val isDirectPathMode: StateFlow<Boolean> get() = _isDirectPathMode

    private val _directPathDestination = MutableStateFlow<Coordinates?>(null)
    val directPathDestination: StateFlow<Coordinates?> get() = _directPathDestination

    // Events
    val updateEvent: Flow<*> = merge(
        _time, _displayNightMask, _displayLocation, _displayGrid, _isDirectPathMode,
        _directPathDestination
    ).debounce(10) // just to filter initial immediate emits

    // Commands
    fun subtractOneHour() {
        _time.value -= ONE_HOUR_IN_MILLIS
    }

    fun addOneHour() {
        _time.value += ONE_HOUR_IN_MILLIS
    }

    fun subtractOneDay() {
        _time.value -= DAY_IN_MILLIS
    }

    fun addOneDay() {
        _time.value += DAY_IN_MILLIS
    }

    fun toggleNightMask() {
        _displayNightMask.value = !displayNightMask.value
    }

    fun toggleDisplayLocation() {
        _displayLocation.value = !displayLocation.value
    }

    fun turnOnDisplayLocation() {
        _displayLocation.value = true
    }

    fun toggleDisplayGrid() {
        _displayGrid.value = !displayGrid.value
    }

    fun toggleDirectPathMode() {
        _isDirectPathMode.value = !isDirectPathMode.value
    }

    fun changeDirectPathDestination(coordinates: Coordinates?) {
        _directPathDestination.value = coordinates
    }
}
