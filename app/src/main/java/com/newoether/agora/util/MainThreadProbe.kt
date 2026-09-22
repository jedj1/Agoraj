package com.newoether.agora.util

import android.os.Looper

/**
 * Raw evidence collector for the conversation switching cover.
 *
 * Three mechanisms feed the plain-text diagnostics rendered under the cover spinner:
 * - the switching pipeline marks its steps ([beginSession], [markPhase], [endSession]);
 * - a Looper printer tracks which main-thread callback is running and for how long, and
 *   records every callback that overruns [OVERRUN_WARN_MS] into the block history;
 * - a polling thread samples the main thread's stack at ~10Hz, so a frozen stack is still
 *   available while the main thread itself cannot recompose.
 *
 * Only [ensureInstalled] touches android.os.Looper. Every other entry point stays
 * JVM-safe because [com.newoether.agora.viewmodel.SwitchingCoordinator] drives them from
 * plain unit tests.
 */
object MainThreadProbe {
    const val MAX_STACK_FRAMES = 20
    const val MAX_BLOCK_HISTORY = 3
    const val SAMPLE_INTERVAL_MS = 100L
    const val OVERRUN_WARN_MS = 150L

    data class Snapshot(
        val overlayMs: Long,
        val sessionLabel: String,
        val phase: String,
        val phaseMs: Long,
        val busyMessage: String,
        val busyMs: Long,
        val idleMs: Long,
        val mainStack: List<String>,
        val stackAgeMs: Long,
        val blockHistory: List<String>,
    )

    @Volatile
    private var sessionStartNs = 0L

    @Volatile
    private var sessionLabel = ""

    @Volatile
    private var phase = ""

    @Volatile
    private var phaseStartNs = 0L

    // Written from the main thread inside the Looper printer only; read cross-thread.
    @Volatile
    private var looperMessage = ""

    @Volatile
    private var looperMessageStartNs = 0L

    @Volatile
    private var looperIdleSinceNs = 0L

    @Volatile
    private var mainStack: List<String> = emptyList()

    @Volatile
    private var sampleAtNs = 0L

    @Volatile
    private var workerStarted = false

    @Volatile
    private var sampling = false

    // Appended to from the sampler/main threads only through [recordBlock].
    private val blockHistory = ArrayDeque<String>()

    /** Installs the Looper printer and the sampler thread. Idempotent. */
    fun ensureInstalled() {
        if (workerStarted) return
        workerStarted = true
        val mainThread = Looper.getMainLooper().thread
        Looper.getMainLooper().setMessageLogging { line -> onLooperLog(line) }
        val worker = Thread {
            while (true) {
                if (sampling) {
                    mainStack = mainThread.stackTrace.take(MAX_STACK_FRAMES).map { "at $it" }
                    sampleAtNs = System.nanoTime()
                }
                Thread.sleep(SAMPLE_INTERVAL_MS)
            }
        }
        worker.isDaemon = true
        worker.name = "main-thread-probe"
        worker.start()
    }

    fun startSampling() {
        sampling = true
    }

    fun stopSampling() {
        sampling = false
    }

    fun beginSession(label: String) {
        val now = System.nanoTime()
        sessionStartNs = now
        sessionLabel = label
        phase = "begin"
        phaseStartNs = now
    }

    fun markPhase(name: String) {
        if (sessionStartNs == 0L) return
        phase = name
        phaseStartNs = System.nanoTime()
    }

    fun endSession() {
        sessionStartNs = 0L
        sessionLabel = ""
        phase = ""
        phaseStartNs = 0L
    }

    fun snapshot(): Snapshot {
        val now = System.nanoTime()
        val session = sessionStartNs
        val messageStart = looperMessageStartNs
        val sample = sampleAtNs
        return Snapshot(
            overlayMs = if (session != 0L) (now - session) / 1_000_000L else 0L,
            sessionLabel = sessionLabel,
            phase = phase,
            phaseMs = if (session != 0L && phaseStartNs != 0L) {
                (now - phaseStartNs) / 1_000_000L
            } else {
                0L
            },
            busyMessage = looperMessage,
            busyMs = if (messageStart != 0L) (now - messageStart) / 1_000_000L else 0L,
            idleMs = if (messageStart == 0L && looperIdleSinceNs != 0L) {
                (now - looperIdleSinceNs) / 1_000_000L
            } else {
                0L
            },
            mainStack = mainStack,
            stackAgeMs = if (sample != 0L) (now - sample) / 1_000_000L else -1L,
            blockHistory = blockHistorySnapshot(),
        )
    }

    private fun onLooperLog(line: String) {
        when {
            line.startsWith(">>>>> Dispatching to ") -> {
                looperMessage = line.substring(">>>>> Dispatching to ".length)
                looperMessageStartNs = System.nanoTime()
            }
            line.startsWith("<<<<< Finished to ") -> {
                val start = looperMessageStartNs
                val durationMs = if (start != 0L) {
                    (System.nanoTime() - start) / 1_000_000L
                } else {
                    -1L
                }
                if (durationMs >= OVERRUN_WARN_MS) recordBlock(durationMs, looperMessage)
                looperMessage = ""
                looperMessageStartNs = 0L
                looperIdleSinceNs = System.nanoTime()
            }
        }
    }

    @Synchronized
    private fun recordBlock(durationMs: Long, message: String) {
        val sampleAge = if (sampleAtNs != 0L) {
            (System.nanoTime() - sampleAtNs) / 1_000_000L
        } else {
            -1L
        }
        val stackMark = if (sampleAge in 0..(durationMs + SAMPLE_INTERVAL_MS * 2)) {
            mainStack.firstOrNull()?.removePrefix("at ") ?: "no-frames"
        } else {
            "no-sample"
        }
        val shortMessage = if (message.length > 90) message.take(90) + "..." else message
        if (blockHistory.size >= MAX_BLOCK_HISTORY) blockHistory.removeFirst()
        blockHistory.addLast("${durationMs}ms $shortMessage :: $stackMark")
    }

    @Synchronized
    private fun blockHistorySnapshot(): List<String> = ArrayList(blockHistory)
}
