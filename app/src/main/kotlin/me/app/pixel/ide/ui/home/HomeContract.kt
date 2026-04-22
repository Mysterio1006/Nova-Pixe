package me.app.pixel.ide.ui.home

data class HomeState(
    val isFirstTime: Boolean = true,
    val isLoading: Boolean = false
)

sealed class HomeIntent {
    object CreateNewProject : HomeIntent()
    object OpenLocalProject : HomeIntent()
    object OpenSettings : HomeIntent()
    // 新增：打开专门的 AI 指令/对话控制台
    object OpenAiTerminal : HomeIntent()
}

sealed class HomeEffect {
    object NavigateToEditor : HomeEffect()
    object NavigateToFilePicker : HomeEffect()
    object NavigateToSettings : HomeEffect()
    // 新增：路由到 AI 终端
    object NavigateToAiTerminal : HomeEffect()
}