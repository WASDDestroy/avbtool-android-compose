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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
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
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.wasddestroy.avbtoolandroid.ui.theme.AVBToolAndroidTheme
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
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(R.string.nav_home, Icons.Filled.Home),
    CONSOLE(R.string.nav_console, Icons.Filled.Terminal),
    SETTINGS(R.string.nav_settings, Icons.Filled.Settings),
}

enum class LanguageMode(
    @StringRes val labelRes: Int,
    val tag: String?,
) {
    FOLLOW_SYSTEM(R.string.settings_language_follow_system, null),
    ENGLISH(R.string.settings_language_english, "en"),
    CHINESE(R.string.settings_language_chinese, "zh-CN"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AVBToolAndroidApp() {
    var dynamicThemeColor by rememberSaveable { mutableStateOf(true) }
    var themeModeName by rememberSaveable { mutableStateOf(ThemeMode.FOLLOW_SYSTEM.name) }
    var amoledBlack by rememberSaveable { mutableStateOf(false) }
    var predictiveBackGesture by rememberSaveable { mutableStateOf(true) }
    var languageModeName by rememberSaveable { mutableStateOf(LanguageMode.FOLLOW_SYSTEM.name) }
    val context = LocalContext.current

    val themeMode = runCatching { ThemeMode.valueOf(themeModeName) }.getOrDefault(ThemeMode.FOLLOW_SYSTEM)
    val languageMode = runCatching { LanguageMode.valueOf(languageModeName) }.getOrDefault(LanguageMode.FOLLOW_SYSTEM)
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }

    var commandId by rememberSaveable { mutableStateOf<String?>(null) }
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

    BackHandler(enabled = commandId != null) {
        closeCommand()
    }

    PredictiveBackHandler(enabled = commandId != null && predictiveBackGesture) { progress ->
        try {
            progress.collect { event ->
                commandBackProgress.snapTo(event.progress)
            }
            closeCommand()
        } catch (e: CancellationException) {
            scope.launch {
                commandBackProgress.animateTo(0f, tween(200))
            }
            throw e
        }
    }

    LaunchedEffect(commandId) {
        if (commandId != null) {
            commandBackProgress.snapTo(1f)
            commandBackProgress.animateTo(0f, tween(300))
        } else {
            commandBackProgress.snapTo(0f)
        }
    }

    AVBToolAndroidTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicThemeColor,
        amoledBlack = amoledBlack,
    ) {
        Box(Modifier.fillMaxSize()) {
            RootScreen(
                isTopDestination = commandId == null,
                onOpenCommand = { id -> commandId = id },
                dynamicThemeColor = dynamicThemeColor,
                themeMode = themeMode,
                amoledBlack = amoledBlack,
                predictiveBackGesture = predictiveBackGesture,
                languageMode = languageMode,
                onDynamicThemeColorChange = { dynamicThemeColor = it },
                onThemeModeChange = { themeModeName = it.name },
                onAmoledBlackChange = { amoledBlack = it },
                onPredictiveBackGestureChange = { predictiveBackGesture = it },
                onLanguageModeChange = { mode ->
                    languageModeName = mode.name
                    applyAppLanguage(context, mode.tag)
                },
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootScreen(
    isTopDestination: Boolean,
    onOpenCommand: (String) -> Unit,
    dynamicThemeColor: Boolean,
    themeMode: ThemeMode,
    amoledBlack: Boolean,
    predictiveBackGesture: Boolean,
    languageMode: LanguageMode,
    onDynamicThemeColorChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAmoledBlackChange: (Boolean) -> Unit,
    onPredictiveBackGestureChange: (Boolean) -> Unit,
    onLanguageModeChange: (LanguageMode) -> Unit,
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val pagerState = rememberPagerState(pageCount = { AppDestinations.entries.size })
    var terminalSelecting by remember { mutableStateOf(false) }
    val rootScope = rememberCoroutineScope()
    var rootBackGestureInProgress by remember { mutableStateOf(false) }

    // Predictive back from Console or Settings returns to Home instead of exiting the app.
    // Drive the pager directly from the gesture so Home is revealed during the
    // swipe, not only after it completes. The enabled flag is scoped to the
    // root destination so the command-screen pop gesture is not intercepted by
    // this handler.
    BackHandler(
        enabled = !predictiveBackGesture && isTopDestination && currentDestination != AppDestinations.HOME,
    ) {
        currentDestination = AppDestinations.HOME
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
            rootBackGestureInProgress = false
        } catch (e: CancellationException) {
            // Gesture cancelled: snap back to the page the gesture started
            // from. Keep the settled-page observer disabled until the snap-back
            // finishes so an intermediate pager currentPage cannot disable the
            // predictive back handler or start a conflicting animation.
            rootScope.launch {
                pagerState.animateScrollToPage(destination.ordinal)
                rootBackGestureInProgress = false
            }
            throw e
        }
    }

    LaunchedEffect(currentDestination) {
        pagerState.animateScrollToPage(currentDestination.ordinal)
    }

    LaunchedEffect(pagerState.settledPage) {
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
                    onClick = { currentDestination = destination },
                )
            }
        },
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                userScrollEnabled = currentDestination != AppDestinations.CONSOLE && !terminalSelecting,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) { page ->
                when (AppDestinations.entries[page]) {
                    AppDestinations.HOME -> HomeScreen(
                        modifier = Modifier.fillMaxSize(),
                        onOpenCommand = onOpenCommand,
                    )
                    AppDestinations.CONSOLE -> ConsoleScreen(
                        modifier = Modifier.fillMaxSize(),
                        isActive = currentDestination == AppDestinations.CONSOLE,
                        onSelectionModeChanged = { terminalSelecting = it },
                    )
                    AppDestinations.SETTINGS -> SettingsScreen(
                        modifier = Modifier.fillMaxSize(),
                        dynamicThemeColor = dynamicThemeColor,
                        themeMode = themeMode,
                        amoledBlack = amoledBlack,
                        predictiveBackGesture = predictiveBackGesture,
                        languageMode = languageMode,
                        onDynamicThemeColorChange = onDynamicThemeColorChange,
                        onThemeModeChange = onThemeModeChange,
                        onAmoledBlackChange = onAmoledBlackChange,
                        onPredictiveBackGestureChange = onPredictiveBackGestureChange,
                        onLanguageModeChange = onLanguageModeChange,
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
