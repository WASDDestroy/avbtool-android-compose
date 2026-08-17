package me.wasddestroy.avbtoolandroid

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConsoleViewModel(
    runner: AvbTaskRunner,
    val bridge: SafFileBridge,
    appContext: Context,
) : ViewModel() {

    // 会话绑定 viewModelScope，因此正在执行的 console 命令不会因 Composable
    // 离开组合而被取消，终端内容也会在配置变更后保留。
    val session = AvbtoolTermSession(runner, viewModelScope, appContext)

    private val _storageGranted = MutableStateFlow(hasStoragePermission(appContext))
    val storageGranted: StateFlow<Boolean> = _storageGranted.asStateFlow()

    fun refreshStoragePermission(context: Context) {
        _storageGranted.value = hasStoragePermission(context)
    }

    override fun onCleared() {
        super.onCleared()
        session.finish()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    ConsoleViewModel(
                        runner = AvbTaskRunner(appContext),
                        bridge = SafFileBridge(appContext),
                        appContext = appContext,
                    )
                }
            }
        }
    }
}
