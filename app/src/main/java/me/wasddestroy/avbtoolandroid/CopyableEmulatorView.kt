package me.wasddestroy.avbtoolandroid

import android.annotation.SuppressLint
import android.content.Context
import android.util.DisplayMetrics
import android.view.MotionEvent
import jackpal.androidterm.emulatorview.EmulatorView
import jackpal.androidterm.emulatorview.TermSession

/**
 * EmulatorView with long-press-to-select enabled.
 *
 * The stock EmulatorView long-press shows an empty context menu and never
 * enters selection mode. Here we toggle selection mode instead. Once in
 * selection mode, dragging selects terminal text and releasing copies it to
 * the clipboard.
 */
@SuppressLint("ViewConstructor")
class CopyableEmulatorView(
    context: Context,
    session: TermSession,
    metrics: DisplayMetrics
) : EmulatorView(context, session, metrics) {

    var onSelectionModeChanged: ((Boolean) -> Unit)? = null

    override fun toggleSelectingText() {
        super.toggleSelectingText()
        onSelectionModeChanged?.invoke(selectingText)
    }

    override fun onLongPress(e: MotionEvent) {
        if (!selectingText) {
            toggleSelectingText()
            // The stock selection handler only initializes its anchor on
            // ACTION_DOWN, but a long-press gesture skips that phase. Feed it
            // a synthetic DOWN at the long-press point so the subsequent
            // MOVE/UP events have valid selection coordinates.
            val down = MotionEvent.obtain(
                e.downTime, e.eventTime, MotionEvent.ACTION_DOWN, e.x, e.y, e.metaState
            )
            try {
                super.onTouchEvent(down)
            } finally {
                down.recycle()
            }
        }
    }
}
