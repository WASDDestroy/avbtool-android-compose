package me.wasddestroy.avbtoolandroid

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.wasddestroy.avbtoolandroid.ui.theme.ColorSpecVersion
import me.wasddestroy.avbtoolandroid.ui.theme.ColorVariant
import me.wasddestroy.avbtoolandroid.ui.theme.ThemeMode

data class SettingsUiState(
    val dynamicThemeColor: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val amoledBlack: Boolean = false,
    val predictiveBackGesture: Boolean = true,
    val languageMode: LanguageMode = LanguageMode.FOLLOW_SYSTEM,
    val showFunctionKeyboard: Boolean = true,
    val colorSpecVersion: ColorSpecVersion = ColorSpecVersion.SPEC_2021,
    val colorVariant: ColorVariant = ColorVariant.TONAL_SPOT,
    /** Dangerous: import profile archives without manifest checksum checks. */
    val skipProfileArchiveVerification: Boolean = false,
)

class SettingsViewModel(private val store: SettingsStore) : ViewModel() {

    private val _uiState = MutableStateFlow(store.read())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setDynamicThemeColor(value: Boolean) = update { it.copy(dynamicThemeColor = value) }
    fun setThemeMode(value: ThemeMode) = update { it.copy(themeMode = value) }
    fun setAmoledBlack(value: Boolean) = update { it.copy(amoledBlack = value) }
    fun setPredictiveBackGesture(value: Boolean) = update { it.copy(predictiveBackGesture = value) }
    fun setLanguageMode(value: LanguageMode) = update { it.copy(languageMode = value) }
    fun setShowFunctionKeyboard(value: Boolean) = update { it.copy(showFunctionKeyboard = value) }
    fun setColorSpecVersion(value: ColorSpecVersion) = update { it.copy(colorSpecVersion = value) }
    fun setColorVariant(value: ColorVariant) = update { it.copy(colorVariant = value) }
    fun setSkipProfileArchiveVerification(value: Boolean) = update { it.copy(skipProfileArchiveVerification = value) }

    private fun update(transform: (SettingsUiState) -> SettingsUiState) {
        _uiState.update { current ->
            transform(current).also { store.write(it) }
        }
    }

    companion object {
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                val sp = app.getSharedPreferences("application_configs", Context.MODE_PRIVATE)
                SettingsViewModel(SettingsStore(sp))
            }
        }
    }
}
