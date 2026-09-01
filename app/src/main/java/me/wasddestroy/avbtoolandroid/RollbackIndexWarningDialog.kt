package me.wasddestroy.avbtoolandroid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.wasddestroy.avbtoolandroid.ui.components.CountdownDialogConfirmButton
import me.wasddestroy.avbtoolandroid.ui.components.DialogDismissButton

/**
 * High-priority warning shown when a rollback_index fails classification:
 * before signing (command screen, profile batch sign) and before a profile
 * import is committed. [onContinue] is null when every finding is invalid —
 * the action cannot succeed at all, so only going back is offered. Otherwise
 * the confirm button carries a countdown, because once the device accepts
 * the signed image the RPMB minimum rises permanently.
 */
@Composable
fun RollbackIndexWarningDialog(
    findings: List<RollbackIndexFinding>,
    onDismiss: () -> Unit,
    onContinue: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rollback_warning_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.rollback_warning_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
                findings.forEach { finding ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = finding.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = finding.verdict.messageText(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (onContinue != null) {
                val countdownPattern = stringResource(R.string.rollback_warning_continue_countdown)
                CountdownDialogConfirmButton(
                    text = stringResource(R.string.rollback_warning_continue),
                    countdownText = { remaining -> String.format(countdownPattern, remaining) },
                ) {
                    onContinue()
                }
            }
        },
        dismissButton = {
            DialogDismissButton(onClick = onDismiss) {
                Text(stringResource(R.string.command_cancel))
            }
        },
    )
}

@Composable
private fun RollbackIndexVerdict.messageText(): String = when (this) {
    is RollbackIndexVerdict.Unrecognized ->
        stringResource(R.string.rollback_warning_unrecognized, value.toString())
    is RollbackIndexVerdict.FutureDate -> {
        val now = System.currentTimeMillis() / 1000
        when (val epoch = epochSecond) {
            null -> stringResource(R.string.rollback_warning_future_unrepresentable, value.toString())
            else -> stringResource(
                R.string.rollback_warning_future,
                value.toString(),
                RollbackIndexGuard.formatEpochDate(epoch),
                RollbackIndexGuard.formatEpochDate(now),
            )
        }
    }
    is RollbackIndexVerdict.Invalid ->
        stringResource(R.string.rollback_warning_invalid, raw)
    RollbackIndexVerdict.Ok -> ""
}
