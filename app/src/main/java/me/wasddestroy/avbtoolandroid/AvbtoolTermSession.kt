package me.wasddestroy.avbtoolandroid

import android.content.Context

import jackpal.androidterm.emulatorview.TermSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.OutputStream

class AvbtoolTermSession(
    private val runner: AvbTaskRunner,
    private val scope: CoroutineScope,
    private val context: Context,
) : TermSession() {

    private val line = StringBuilder()
    private val history = mutableListOf<String>()
    private var historyIndex = -1
    private var savedLine: String? = null
    private var escapeActive = false
    private val escapeBuffer = StringBuilder()
    private var bannerShown = false
    private var emulatorReady = false
    private var cursorPos = 0

    init {
        // TermSession normally bridges a PTY process. We run Python in-process,
        // so provide dummy streams so the legacy reader/writer threads exit/idle
        // without NPEs. Our overridden write() handles keyboard input directly.
        termIn = ByteArrayInputStream(ByteArray(0))
        termOut = object : OutputStream() {
            override fun write(b: Int) = Unit
            override fun write(b: ByteArray, off: Int, len: Int) = Unit
        }
    }

    fun insertText(text: String) {
        if (!emulatorReady) return
        line.append(text)
        cursorPos = line.length
        appendToEmulator(text.toByteArray(), 0, text.toByteArray().size)
        notifyUpdate()
    }

    fun appendOutput(text: String) {
        val normalized = text.replace("\n", "\r\n")
        val bytes = normalized.toByteArray(Charsets.UTF_8)
        if (bytes.isNotEmpty()) {
            appendToEmulator(bytes, 0, bytes.size)
            notifyUpdate()
        }
    }

    fun writePrompt() {
        appendOutput("\r\n> ")
    }

    fun showBanner() {
        if (!bannerShown) {
            bannerShown = true
            appendOutput("AVBTool console. Type avbtool commands, e.g. info_image --image <path>\r\n")
            writePrompt()
        }
    }

    fun clearScreen() {
        if (!emulatorReady) return
        appendToEmulator("[2J[H".toByteArray(), 0, 7)
        line.setLength(0)
        cursorPos = 0
        savedLine = null
        notifyUpdate()
        writePrompt()
    }

    override fun initializeEmulator(columns: Int, rows: Int) {
        super.initializeEmulator(columns, rows)
        emulatorReady = true
        showBanner()
    }

    override fun write(data: ByteArray, offset: Int, count: Int) {
        if (!emulatorReady) return
        for (i in offset until offset + count) {
            val b = data[i].toInt() and 0xff
            if (escapeActive) {
                handleEscape(b)
                continue
            }
            when (b) {
                0x03 -> cancelLine()                 // Ctrl+C
                0x0d, 0x0a -> executeLine()          // Enter
                0x7f, 0x08 -> backspace()            // Backspace / DEL
                0x1b -> { escapeActive = true; escapeBuffer.setLength(0) }
                else -> {
                    if (b >= 0x20) {
                        val ch = b.toChar()
                        if (cursorPos < line.length) {
                            // Insert at cursor position
                            line.insert(cursorPos, ch)
                            // Re-render from cursor position
                            val remaining = line.substring(cursorPos)
                            appendToEmulator(remaining.toByteArray(), 0, remaining.length)
                            // Move cursor back to correct position
                            val backspaces = remaining.length - 1
                            if (backspaces > 0) {
                                appendToEmulator(("\u001b[D".repeat(backspaces)).toByteArray(), 0, backspaces * 3)
                            }
                        } else {
                            // Append at end
                            line.append(ch)
                            appendToEmulator(byteArrayOf(ch.code.toByte()), 0, 1)
                        }
                        cursorPos++
                        notifyUpdate()
                    }
                }
            }
        }
    }

    private fun handleEscape(b: Int) {
        escapeBuffer.append(b.toChar())
        val s = escapeBuffer.toString()
        when {
            s.endsWith("A") -> navigateHistory(-1)
            s.endsWith("B") -> navigateHistory(1)
            s.endsWith("C") -> moveCursor(1)
            s.endsWith("D") -> moveCursor(-1)
            s.endsWith("H") -> moveCursor(Int.MIN_VALUE)
            s.endsWith("F") -> moveCursor(Int.MAX_VALUE)
        }
        if (b.toChar() in 'A'..'Z' || b.toChar() in 'a'..'z' || b.toChar() == '~') {
            escapeActive = false
            escapeBuffer.setLength(0)
        }
    }

    private fun backspace() {
        if (line.isNotEmpty() && cursorPos > 0) {
            line.deleteCharAt(cursorPos - 1)
            cursorPos--
            // Move cursor left, erase to end of line, re-render remaining text
            appendToEmulator("\b".toByteArray(), 0, 1)
            appendToEmulator("\u001b[K".toByteArray(), 0, 3)
            val remaining = line.substring(cursorPos)
            if (remaining.isNotEmpty()) {
                appendToEmulator(remaining.toByteArray(), 0, remaining.length)
                // Move cursor back to correct position
                val backspaces = remaining.length
                if (backspaces > 0) {
                    appendToEmulator(("\u001b[D".repeat(backspaces)).toByteArray(), 0, backspaces * 3)
                }
            }
            notifyUpdate()
        }
    }

    private fun cancelLine() {
        appendOutput("^C\r\n")
        line.setLength(0)
        cursorPos = 0
        savedLine = null
        writePrompt()
    }

    private fun moveCursor(delta: Int) {
        when {
            delta == Int.MIN_VALUE -> {
                // Home: move to beginning
                if (cursorPos > 0) {
                    appendToEmulator("\r".toByteArray(), 0, 1)
                    cursorPos = 0
                    notifyUpdate()
                }
            }
            delta == Int.MAX_VALUE -> {
                // End: move to end
                if (cursorPos < line.length) {
                    val remaining = line.substring(cursorPos)
                    appendToEmulator(remaining.toByteArray(), 0, remaining.length)
                    cursorPos = line.length
                    notifyUpdate()
                }
            }
            delta < 0 -> {
                // Left arrow: move cursor left
                if (cursorPos > 0) {
                    appendToEmulator("\u001b[D".toByteArray(), 0, 3)
                    cursorPos--
                    notifyUpdate()
                }
            }
            delta > 0 -> {
                // Right arrow: move cursor right
                if (cursorPos < line.length) {
                    appendToEmulator("\u001b[C".toByteArray(), 0, 3)
                    cursorPos++
                    notifyUpdate()
                }
            }
        }
    }

    private fun navigateHistory(delta: Int) {
        if (history.isEmpty()) return
        if (savedLine == null) savedLine = line.toString()
        historyIndex = (historyIndex + delta).coerceIn(-1, history.size - 1)
        val newLine = if (historyIndex < 0) savedLine.orEmpty() else history[historyIndex]
        line.setLength(0)
        line.append(newLine)
        cursorPos = line.length
        appendToEmulator("\r\u001b[K".toByteArray(), 0, 4)
        appendToEmulator(newLine.toByteArray(), 0, newLine.length)
        notifyUpdate()
    }

    private fun executeLine() {
        val command = line.toString().trim()
        line.setLength(0)
        cursorPos = 0
        appendOutput("\r\n")
        if (command == "clear") {
            clearScreen()
            return
        }
        if (command.isNotEmpty()) {
            history.add(command)
            historyIndex = history.size
            savedLine = null

            val tip = consoleTip(context, command)
            if (tip != null) appendOutput(tip + "\r\n")

            val argv = parseConsoleCommand(command)
            if (argv == null) {
                appendOutput("? cannot parse: $command\r\n")
            } else {
                scope.launch {
                    val result = runner.run(argv)
                    appendOutput(result.stdout)
                    if (result.stderr.isNotBlank()) {
                        appendOutput("[stderr]\r\n" + result.stderr.trimEnd() + "\r\n")
                    }
                    writePrompt()
                }
                return
            }
        }
        writePrompt()
    }
}
