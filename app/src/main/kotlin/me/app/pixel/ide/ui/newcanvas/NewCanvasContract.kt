package me.app.pixel.ide.ui.newcanvas

enum class CanvasBg {
    TRANSPARENT, WHITE, BLACK
}

data class NewCanvasState(
    val projectName: String = "未命名像素画",
    val width: String = "64",
    val height: String = "64",
    val isRatioLocked: Boolean = true,
    val background: CanvasBg = CanvasBg.TRANSPARENT
)

sealed class NewCanvasIntent {
    data class UpdateProjectName(val name: String) : NewCanvasIntent()
    data class UpdateWidth(val width: String) : NewCanvasIntent()
    data class UpdateHeight(val height: String) : NewCanvasIntent()
    object ToggleRatioLock : NewCanvasIntent()
    data class ApplyPreset(val size: Int) : NewCanvasIntent()
    data class SelectBackground(val bg: CanvasBg) : NewCanvasIntent()
    object CreateCanvas : NewCanvasIntent()
    object NavigateBack : NewCanvasIntent()
}

sealed class NewCanvasEffect {
    // 成功后携带参数进入画布页面
    data class NavigateToEditor(val name: String, val width: Int, val height: Int, val bg: CanvasBg) : NewCanvasEffect()
    object NavigateBack : NewCanvasEffect()
    data class ShowError(val message: String) : NewCanvasEffect()
}