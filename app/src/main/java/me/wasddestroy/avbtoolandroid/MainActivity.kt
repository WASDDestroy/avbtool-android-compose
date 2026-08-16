package me.wasddestroy.avbtoolandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.unit.dp
import me.wasddestroy.avbtoolandroid.ui.theme.AVBToolAndroidTheme
import java.util.concurrent.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        runCatching { PythonRuntime.start(applicationContext) }
        setContent {
            AVBToolAndroidTheme {
                AVBToolAndroidApp()
            }
        }
    }
}

enum class AppDestinations(
    @androidx.annotation.StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(R.string.nav_home, Icons.Filled.Home),
    CONSOLE(R.string.nav_console, Icons.Filled.Terminal),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AVBToolAndroidApp() {
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

    PredictiveBackHandler(enabled = commandId != null) { progress ->
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

    Box(Modifier.fillMaxSize()) {
        RootScreen(
            isTopDestination = commandId == null,
            onOpenCommand = { id -> commandId = id },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootScreen(
    isTopDestination: Boolean,
    onOpenCommand: (String) -> Unit,
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val pagerState = rememberPagerState(pageCount = { AppDestinations.entries.size })
    var terminalSelecting by remember { mutableStateOf(false) }
    val rootScope = rememberCoroutineScope()
    var rootBackGestureInProgress by remember { mutableStateOf(false) }

    // Predictive back from Console returns to Home instead of exiting the app.
    // Drive the pager directly from the gesture so Home is revealed during the
    // swipe, not only after it completes. The enabled flag is scoped to the
    // root destination so the command-screen pop gesture is not intercepted by
    // this handler.
    PredictiveBackHandler(
        enabled = isTopDestination && currentDestination != AppDestinations.HOME,
    ) { progress ->
        val destination = currentDestination
        var lastProgress = 0f
        rootBackGestureInProgress = true
        try {
            progress.collect { event ->
                val pageSize = pagerState.layoutInfo.pageSize.toFloat()
                if (pageSize > 0f) {
                    val delta = -(event.progress - lastProgress) * pageSize
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
                userScrollEnabled = !terminalSelecting,
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
                }
            }
        }
    }
}
