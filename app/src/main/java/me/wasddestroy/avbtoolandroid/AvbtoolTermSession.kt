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

    init {
        // TermSession normally bridges a PTY process. We run Python in-process,
        // so provide dummy streams so the legacy reader/writer threads exit/idle
        // without NPEs. Our overridden write() handles keyboard input directly.
        setTermIn(ByteArrayInputStream(ByteArray(0)))
        setTermOut(object : OutputStream() {
            override fun write(b: Int) = Unit
            override fun write(b: ByteArray, off: Int, len: Int) = Unit
        })
    }

    fun insertText(text: String) {
        if (!emulatorReady) return
        line.append(text)
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
                        line.append(ch)
                        appendToEmulator(byteArrayOf(ch.code.toByte()), 0, 1)
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
        if (line.isNotEmpty()) {
            line.deleteCharAt(line.length - 1)
            appendToEmulator("\b \b".toByteArray(), 0, 3)
            notifyUpdate()
        }
    }

    private fun cancelLine() {
        appendOutput("^C\r\n")
        line.setLength(0)
        savedLine = null
        writePrompt()
    }

    private fun moveCursor(delta: Int) {
        // Minimal cursor movement support. Home/End clear and re-echo the line.
        if (delta == Int.MIN_VALUE || delta == Int.MAX_VALUE) {
            appendToEmulator("\r".toByteArray(), 0, 1)
            appendToEmulator("\u001b[K".toByteArray(), 0, 2)
            appendToEmulator(line.toString().toByteArray(), 0, line.length)
            notifyUpdate()
        }
    }

    private fun navigateHistory(delta: Int) {
        if (history.isEmpty()) return
        if (savedLine == null) savedLine = line.toString()
        historyIndex = (historyIndex + delta).coerceIn(-1, history.size - 1)
        val newLine = if (historyIndex < 0) savedLine.orEmpty() else history[historyIndex]
        line.setLength(0)
        line.append(newLine)
        appendToEmulator("\r\u001b[K".toByteArray(), 0, 4)
        appendToEmulator(newLine.toByteArray(), 0, newLine.length)
        notifyUpdate()
    }

    private fun executeLine() {
        val command = line.toString().trim()
        line.setLength(0)
        appendOutput("\r\n")
        if (command.isNotEmpty()) {
            history.add(command)
            historyIndex = history.size
            savedLine = null

            val tip = consoleTip(context, command)
            if (tip != null) appendOutput(tip + "\r\n")

            val argv = parseConsoleCommand(command)
            if (argv == null) {
                appendOutput("? cannot parse: " + command + "\r\n")
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
