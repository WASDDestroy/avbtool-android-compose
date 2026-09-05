package me.wasddestroy.avbtoolandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.wasddestroy.avbtoolandroid.ui.theme.AVBToolAndroidTheme
import me.wasddestroy.avbtoolandroid.partition.PartitionReaderScreen
import me.wasddestroy.avbtoolandroid.ui.theme.ThemeMode
import java.util.concurrent.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        runCatching { PythonRuntime.start(applicationContext) }
        setContent {
            AVBToolAndroidApp()
        }
    }
}

enum class AppDestinations(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(R.string.nav_home, Icons.Filled.Home),
    PROFILE(R.string.nav_profile, Icons.Filled.FolderOpen),
    SETTINGS(R.string.nav_settings, Icons.Filled.Settings),
}

enum class LanguageMode(
    @param:StringRes val labelRes: Int,
    val tag: String?,
) {
    FOLLOW_SYSTEM(R.string.settings_language_follow_system, null),
    ENGLISH(R.string.settings_language_english, "en"),
    CHINESE(R.string.settings_language_chinese, "zh-CN"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AVBToolAndroidApp() {
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory)
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val darkTheme = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }

    LaunchedEffect(settings.languageMode) {
        applyAppLanguage(context, settings.languageMode.tag)
    }

    var commandId by rememberSaveable { mutableStateOf<String?>(null) }
    var consoleOpen by rememberSaveable { mutableStateOf(false) }
    var partitionReaderOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val commandBackProgress = remember { Animatable(0f) }

    fun closeCommand() {
        if (commandId == null) return
        scope.launch {
            commandBackProgress.animateTo(1f, tween(200))
            commandId = null
            commandBackProgress.snapTo(0f)
        }
    }

    fun closeOverlays() {
        if (commandId != null) {
            closeCommand()
        } else if (consoleOpen) {
            consoleOpen = false
        } else {
            partitionReaderOpen = false
        }
    }

    BackHandler(enabled = commandId != null || consoleOpen || partitionReaderOpen) {
        closeOverlays()
    }

    PredictiveBackHandler(
        enabled = commandId == null && (consoleOpen || partitionReaderOpen) && settings.predictiveBackGesture,
    ) { progress ->
        try {
            progress.collect { event ->
                commandBackProgress.snapTo(event.progress)
            }
            consoleOpen = false
            partitionReaderOpen = false
        } catch (e: CancellationException) {
            scope.launch {
                commandBackProgress.animateTo(0f, tween(200))
            }
            throw e
        }
    }

    LaunchedEffect(commandId, consoleOpen, partitionReaderOpen) {
        if (commandId != null || consoleOpen || partitionReaderOpen) {
            commandBackProgress.snapTo(1f)
            commandBackProgress.animateTo(0f, tween(300))
        } else {
            commandBackProgress.snapTo(0f)
        }
    }

    AVBToolAndroidTheme(
        darkTheme = darkTheme,
        dynamicColor = settings.dynamicThemeColor,
        amoledBlack = settings.amoledBlack,
        colorSpecVersion = settings.colorSpecVersion,
        colorVariant = settings.colorVariant,
    ) {
        Box(Modifier.fillMaxSize()) {
            RootScreen(
                isTopDestination = commandId == null && !consoleOpen && !partitionReaderOpen,
                onOpenCommand = { id -> commandId = id },
                onOpenConsole = { consoleOpen = true },
                onOpenPartitionReader = { partitionReaderOpen = true },
                predictiveBackGesture = settings.predictiveBackGesture,
                settingsViewModel = settingsViewModel,
            )

            val command = commandId?.let { AvbCommands.byId(it) }
            if (command != null) {
                CommandScreen(
                    command = command,
                    onBack = ::closeCommand,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = commandBackProgress.value * size.width
                        },
                )
            } else if (commandId != null) {
                LaunchedEffect(Unit) { commandId = null }
            } else if (consoleOpen) {
                ConsoleScreen(
                    onBack = { consoleOpen = false },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = commandBackProgress.value * size.width
                        },
                )
            } else if (partitionReaderOpen) {
                PartitionReaderScreen(
                    onBack = { partitionReaderOpen = false },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = commandBackProgress.value * size.width
                        },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootScreen(
    isTopDestination: Boolean,
    onOpenCommand: (String) -> Unit,
    onOpenConsole: () -> Unit,
    onOpenPartitionReader: () -> Unit,
    predictiveBackGesture: Boolean,
    settingsViewModel: SettingsViewModel,
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val pagerState = rememberPagerState(pageCount = { AppDestinations.entries.size })
    val rootScope = rememberCoroutineScope()
    var rootBackGestureInProgress by remember { mutableStateOf(false) }
    val profileViewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.factory)

    // Predictive back from Profile or Settings returns to Home instead of exiting
    // the app. Drive the pager directly from the gesture so Home is revealed
    // during the swipe, not only after it completes. The enabled flag is scoped
    // to the root destination so the command/console-screen pop gesture is not
    // intercepted by this handler.
    BackHandler(
        enabled = !predictiveBackGesture && isTopDestination && currentDestination != AppDestinations.HOME,
    ) {
        currentDestination = AppDestinations.HOME
        rootScope.launch { pagerState.animateScrollToPage(AppDestinations.HOME.ordinal) }
    }

    PredictiveBackHandler(
        enabled = predictiveBackGesture && isTopDestination && currentDestination != AppDestinations.HOME,
    ) { progress ->
        val destination = currentDestination
        val startPage = destination.ordinal
        var lastProgress = 0f
        rootBackGestureInProgress = true
        try {
            progress.collect { event ->
                val pageSize = pagerState.layoutInfo.pageSize.toFloat()
                if (pageSize > 0f) {
                    val delta = -(event.progress - lastProgress) * startPage * pageSize
                    if (delta != 0f) {
                        pagerState.scroll { scrollBy(delta) }
                    }
                }
                lastProgress = event.progress
            }
            // Gesture committed: settle on Home.
            currentDestination = AppDestinations.HOME
            rootScope.launch { pagerState.animateScrollToPage(AppDestinations.HOME.ordinal) }
            rootBackGestureInProgress = false
        } catch (e: CancellationException) {
            // Gesture canceled: snap back to the page the gesture started
            // from. Keep the settled-page observer disabled until the snap-back
            // finishes so an intermediate pager currentPage cannot disable the
            // predictive back handler or start a conflicting animation.
            rootScope.launch {
                try {
                    pagerState.animateScrollToPage(destination.ordinal)
                } finally {
                    rootBackGestureInProgress = false
                }
            }
            throw e
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        // Single source of truth: the pager feeds currentDestination. Nav-bar
        // taps go through the pager directly, never through this state, so no
        // write-back loop can reroute the scroll.
        if (!rootBackGestureInProgress) {
            currentDestination = AppDestinations.entries[pagerState.settledPage]
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = stringResource(destination.labelRes),
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                    selected = destination == currentDestination,
                    onClick = {
                        rootScope.launch {
                            pagerState.animateScrollToPage(destination.ordinal)
                        }
                    },
                )
            }
        },
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) { page ->
                when (AppDestinations.entries[page]) {
                    AppDestinations.HOME -> HomeScreen(
                        modifier = Modifier.fillMaxSize(),
                        onOpenCommand = onOpenCommand,
                    )
                    AppDestinations.PROFILE -> ProfileScreen(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = profileViewModel,
                    )
                    AppDestinations.SETTINGS -> SettingsScreen(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = settingsViewModel,
                        onOpenConsole = onOpenConsole,
                        onOpenPartitionReader = onOpenPartitionReader,
                    )
                }
            }
        }
    }
}


private fun applyAppLanguage(context: Context, tag: String?) {
    if (Build.VERSION.SDK_INT >= 33) {
        val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
        localeManager.applicationLocales =
            if (tag.isNullOrBlank()) LocaleList.getEmptyLocaleList()
            else LocaleList.forLanguageTags(tag)
    } else {
        @Suppress("DEPRECATION")
        val config = Configuration(context.resources.configuration)
        config.setLocale(if (tag.isNullOrBlank()) java.util.Locale.getDefault() else java.util.Locale.forLanguageTag(tag))
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        (context as? Activity)?.recreate()
    }
}
