package me.wasddestroy.avbtoolandroid

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.wasddestroy.avbtoolandroid.ui.theme.ThemeMode

data class SettingsUiState(
    val dynamicThemeColor: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val amoledBlack: Boolean = false,
    val predictiveBackGesture: Boolean = true,
    val languageMode: LanguageMode = LanguageMode.FOLLOW_SYSTEM,
    val showFunctionKeyboard: Boolean = true,
)

/**
 * 设置项仅存在于内存中，与重构前的 rememberSaveable 行为一致：
 * 进程被杀死后回到默认值。本次重构有意不引入 DataStore 持久化。
 */
class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setDynamicThemeColor(value: Boolean) = _uiState.update { it.copy(dynamicThemeColor = value) }

    fun setThemeMode(value: ThemeMode) = _uiState.update { it.copy(themeMode = value) }

    fun setAmoledBlack(value: Boolean) = _uiState.update { it.copy(amoledBlack = value) }

    fun setPredictiveBackGesture(value: Boolean) = _uiState.update { it.copy(predictiveBackGesture = value) }

    fun setLanguageMode(value: LanguageMode) = _uiState.update { it.copy(languageMode = value) }

    fun setShowFunctionKeyboard(value: Boolean) = _uiState.update { it.copy(showFunctionKeyboard = value) }
}
