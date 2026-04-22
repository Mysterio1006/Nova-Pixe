package me.app.pixel.ide.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeState())
    val uiState: StateFlow<HomeState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<HomeEffect>()
    val uiEffect = _uiEffect.asSharedFlow()

    fun processIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.CreateNewProject -> {
                emitEffect(HomeEffect.NavigateToEditor)
            }
            is HomeIntent.OpenLocalProject -> {
                emitEffect(HomeEffect.NavigateToFilePicker)
            }
            is HomeIntent.OpenSettings -> {
                emitEffect(HomeEffect.NavigateToSettings)
            }
            is HomeIntent.OpenAiTerminal -> {
                emitEffect(HomeEffect.NavigateToAiTerminal)
            }
        }
    }

    private fun emitEffect(effect: HomeEffect) {
        viewModelScope.launch {
            _uiEffect.emit(effect)
        }
    }
}