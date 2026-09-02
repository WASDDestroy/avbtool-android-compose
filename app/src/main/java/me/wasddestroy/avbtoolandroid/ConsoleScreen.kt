package me.wasddestroy.avbtoolandroid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

internal fun hasStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 30) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun storagePermissionIntent(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= 30) {
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            ("package:" + context.packageName).toUri())
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            ("package:" + context.packageName).toUri())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: ConsoleViewModel = viewModel(factory = ConsoleViewModel.factory(context))
    val settingsViewModel: SettingsViewModel = viewModel()
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val session = viewModel.session
    val bridge = viewModel.bridge
    var terminalView by remember { mutableStateOf<CopyableEmulatorView?>(null) }

    fun showIme(view: CopyableEmulatorView) {
        view.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        @Suppress("DEPRECATION")
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideIme(view: CopyableEmulatorView) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    val storageGranted by viewModel.storageGranted.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refreshStoragePermission(context)
    }
    val manageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshStoragePermission(context)
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        bridge.openRead(uri)?.let { fd ->
            session.insertText(bridge.pseudoPath(fd))
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_console)) },
                navigationIcon = {
                    IconButton(onClick = {
                        terminalView?.let { view ->
                            if (view.selectingText) {
                                view.toggleSelectingText()
                            }
                            hideIme(view)
                        }
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.command_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)
        val navBottom = WindowInsets.navigationBars.getBottom(density)
        val imeOnlyBottom = (imeBottom - navBottom).coerceAtLeast(0)
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .padding(bottom = with(density) { imeOnlyBottom.toDp() }),
        ) {
            if (!storageGranted) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.console_storage_banner),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (Build.VERSION.SDK_INT >= 30) {
                                    manageLauncher.launch(storagePermissionIntent(context))
                                } else {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_EXTERNAL_STORAGE,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        )
                                    )
                                }
                            }) { Text(stringResource(R.string.console_grant_storage)) }
                            TextButton(onClick = { openDocument.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.console_use_saf)) }
                        }
                    }
                }
            }

            AndroidView(
                factory = { viewContext ->
                    CopyableEmulatorView(viewContext, session, viewContext.resources.displayMetrics).apply {
                        setTextSize(12)
                        setBackKeyCharacter(0x7f)
                        terminalView = this
                        val touchSlop = ViewConfiguration.get(viewContext).scaledTouchSlop
                        var downX = 0f
                        var downY = 0f
                        var moved = false
                        setOnTouchListener { v, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    downX = event.x
                                    downY = event.y
                                    moved = false
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    if (kotlin.math.abs(event.x - downX) > touchSlop ||
                                        kotlin.math.abs(event.y - downY) > touchSlop
                                    ) {
                                        moved = true
                                    }
                                }
                                MotionEvent.ACTION_UP -> {
                                    if (!moved && !(v as CopyableEmulatorView).selectingText) {
                                        v.performClick()
                                        showIme(v)
                                    }
                                }
                            }
                            false
                        }
                    }
                },
                update = { view ->
                    if (view.termSession !== session) {
                        view.attachSession(view.context, session)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            if (settings.showFunctionKeyboard) {
                SoftKeyBar(session = session)
            }
        }
    }
}

@Composable
private fun SoftKeyBar(session: AvbtoolTermSession) {
    val topKeys = listOf(
        "Ctrl" to "",
        "Tab" to "	",
        "Esc" to "",
        "Home" to "[H",
        "End" to "[F",
    )
    val bottomKeys = listOf(
        "PgUp" to "[5~",
        "PgDn" to "[6~",
        "←" to "[D",
        "↑" to "[A",
        "↓" to "[B",
        "→" to "[C",
    )
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                topKeys.forEach { (label, seq) ->
                    SoftKeyButton(session, label, seq)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                bottomKeys.forEach { (label, seq) ->
                    SoftKeyButton(session, label, seq)
                }
            }
        }
    }
}

@Composable
private fun SoftKeyButton(session: AvbtoolTermSession, label: String, seq: String) {
    TextButton(
        onClick = {
            val bytes = seq.toByteArray()
            session.write(bytes, 0, bytes.size)
        },
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        modifier = Modifier.height(36.dp),
    ) {
        Text(label, fontSize = 12.sp)
    }
}

fun consoleTip(context: Context, line: String): String? {
    val parts = line.trim().split(" ").filter { it.isNotBlank() }
    if (parts.isEmpty()) return null
    val first = parts.first()
    val second = parts.getOrNull(1)
    val executableTyped = first == "avbtool.py" || first.endsWith(".py") ||
        ((first == "python" || first == "python3") && second != null &&
         (second == "avbtool.py" || second.endsWith(".py")))
    return if (executableTyped) context.getString(R.string.console_tip) else null
}

fun parseConsoleCommand(line: String): List<String>? {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return null
    var parts = trimmed.split(" ").filter { it.isNotBlank() }
    val first = parts.first()
    val second = parts.getOrNull(1)
    if (first == "avbtool.py" || first.endsWith(".py")) {
        parts = parts.drop(1)
    } else if ((first == "python" || first == "python3") && second != null &&
               (second == "avbtool.py" || second.endsWith(".py"))) {
        parts = parts.drop(2)
    }
    if (parts.firstOrNull() == "avbtool") parts = parts.drop(1)
    return listOf("avbtool") + parts
}
