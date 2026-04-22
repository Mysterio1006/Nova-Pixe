package me.app.pixel.ide.ui.newcanvas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NewCanvasViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(NewCanvasState())
    val uiState: StateFlow<NewCanvasState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<NewCanvasEffect>()
    val uiEffect = _uiEffect.asSharedFlow()

    fun processIntent(intent: NewCanvasIntent) {
        when (intent) {
            is NewCanvasIntent.UpdateProjectName -> {
                _uiState.update { it.copy(projectName = intent.name) }
            }
            is NewCanvasIntent.UpdateWidth -> {
                val newWidth = intent.width.filter { it.isDigit() }
                _uiState.update { state ->
                    if (state.isRatioLocked) {
                        state.copy(width = newWidth, height = newWidth) // 像素画通常锁定 1:1
                    } else {
                        state.copy(width = newWidth)
                    }
                }
            }
            is NewCanvasIntent.UpdateHeight -> {
                val newHeight = intent.height.filter { it.isDigit() }
                _uiState.update { state ->
                    if (state.isRatioLocked) {
                        state.copy(width = newHeight, height = newHeight)
                    } else {
                        state.copy(height = newHeight)
                    }
                }
            }
            is NewCanvasIntent.ToggleRatioLock -> {
                _uiState.update { it.copy(isRatioLocked = !it.isRatioLocked) }
            }
            is NewCanvasIntent.ApplyPreset -> {
                val sizeStr = intent.size.toString()
                _uiState.update { it.copy(width = sizeStr, height = sizeStr) }
            }
            is NewCanvasIntent.SelectBackground -> {
                _uiState.update { it.copy(background = intent.bg) }
            }
            is NewCanvasIntent.CreateCanvas -> {
                val w = _uiState.value.width.toIntOrNull() ?: 0
                val h = _uiState.value.height.toIntOrNull() ?: 0
                if (w in 1..4096 && h in 1..4096) {
                    emitEffect(NewCanvasEffect.NavigateToEditor(
                        name = _uiState.value.projectName.ifBlank { "未命名" },
                        width = w,
                        height = h,
                        bg = _uiState.value.background
                    ))
                } else {
                    emitEffect(NewCanvasEffect.ShowError("宽高必须在 1 到 4096 之间"))
                }
            }
            is NewCanvasIntent.NavigateBack -> {
                emitEffect(NewCanvasEffect.NavigateBack)
            }
        }
    }

    private fun emitEffect(effect: NewCanvasEffect) {
        viewModelScope.launch {
            _uiEffect.emit(effect)
        }
    }
}