package com.clintmaples.broadcastifyscanner.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Taps PCM before mute/volume so the green→yellow meter still moves when muted.
 * Graph: decode → this processor (analyze, then apply gain) → AudioTrack.
 */
class SpectrumAudioProcessor(
    private val barCount: Int = SPECTRUM_BARS,
) : BaseAudioProcessor() {

    @Volatile
    var outputGain: Float = 1f

    @Volatile
    var listener: Listener? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val window = FloatArray(FFT_SIZE)
    private var windowFill = 0
    private val fftReal = FloatArray(FFT_SIZE)
    private val fftImag = FloatArray(FFT_SIZE)
    private val magnitudes = FloatArray(FFT_SIZE / 2)
    private val levels = FloatArray(barCount)
    private val published = FloatArray(barCount)
    private var lastPostAt = 0L
    private var sampleRateHz = 44_100

    fun interface Listener {
        fun onSpectrum(levels: FloatArray)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRateHz = inputAudioFormat.sampleRate
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val readOnly = inputBuffer.asReadOnlyBuffer()
        analyze(readOnly)

        val gain = outputGain
        val output = replaceOutputBuffer(remaining)
        if (gain >= 0.999f) {
            output.put(inputBuffer)
        } else if (gain <= 0.001f) {
            // Keep the sink clock moving with silence so we stay live while muted.
            val order = inputBuffer.order()
            output.order(order)
            if (order == ByteOrder.LITTLE_ENDIAN || order == ByteOrder.BIG_ENDIAN) {
                repeat(remaining) { output.put(0) }
            } else {
                output.put(inputBuffer)
            }
            inputBuffer.position(inputBuffer.limit())
        } else {
            scaleInto(inputBuffer, output, gain)
        }
        output.flip()
    }

    override fun onFlush() {
        windowFill = 0
    }

    override fun onReset() {
        windowFill = 0
        levels.fill(0f)
    }

    private fun analyze(buffer: ByteBuffer) {
        val format = inputAudioFormat
        val channels = format.channelCount.coerceAtLeast(1)
        val encoding = format.encoding
        buffer.order(inputAudioFormatToOrder(buffer))

        if (encoding == C.ENCODING_PCM_FLOAT) {
            while (buffer.remaining() >= 4 * channels) {
                var mix = 0f
                repeat(channels) { mix += buffer.float }
                pushSample(mix / channels)
            }
        } else {
            while (buffer.remaining() >= 2 * channels) {
                var mix = 0f
                repeat(channels) { mix += buffer.short / 32768f }
                pushSample(mix / channels)
            }
        }
    }

    private fun inputAudioFormatToOrder(buffer: ByteBuffer): ByteOrder {
        return if (buffer.order() == ByteOrder.BIG_ENDIAN) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
    }

    private fun pushSample(sample: Float) {
        window[windowFill++] = sample
        if (windowFill >= FFT_SIZE) {
            computeSpectrum()
            windowFill = 0
        }
    }

    private fun computeSpectrum() {
        for (i in 0 until FFT_SIZE) {
            val w = 0.5f * (1f - cos(2.0 * PI * i / (FFT_SIZE - 1)).toFloat())
            fftReal[i] = window[i] * w
            fftImag[i] = 0f
        }
        fft(fftReal, fftImag)
        val nyquist = magnitudes.size
        for (i in 0 until nyquist) {
            val re = fftReal[i]
            val im = fftImag[i]
            magnitudes[i] = sqrt(re * re + im * im)
        }

        // Skip DC; focus on speech/radio energy (same idea as the web analyser).
        val usable = ((nyquist - 1) * 0.85f).toInt().coerceAtLeast(1)
        for (i in 0 until barCount) {
            val start = 1 + (i * usable) / barCount
            val end = 1 + ((i + 1) * usable) / barCount
            var sum = 0f
            var n = 0
            for (j in start until end.coerceAtMost(nyquist)) {
                sum += magnitudes[j]
                n++
            }
            val raw = if (n == 0) 0f else (sum / n)
            val norm = min(1f, (raw / MAG_REF).toDouble().pow(0.85).toFloat() * 1.15f)
            levels[i] = levels[i] * 0.45f + norm * 0.55f
        }
        publish()
    }

    private fun publish() {
        val now = SystemClock.uptimeMillis()
        if (now - lastPostAt < 33L) return
        lastPostAt = now
        System.arraycopy(levels, 0, published, 0, barCount)
        val snapshot = published.copyOf()
        mainHandler.post { listener?.onSpectrum(snapshot) }
    }

    private fun scaleInto(input: ByteBuffer, output: ByteBuffer, gain: Float) {
        output.order(input.order())
        val format = inputAudioFormat
        val encoding = format.encoding
        if (encoding == C.ENCODING_PCM_FLOAT) {
            while (input.remaining() >= 4) {
                output.putFloat(input.float * gain)
            }
        } else {
            while (input.remaining() >= 2) {
                val s = (input.short * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                output.putShort(s.toShort())
            }
        }
        if (input.hasRemaining()) {
            output.put(input)
        }
    }

    companion object {
        const val SPECTRUM_BARS = 19
        private const val FFT_SIZE = 64
        private const val MAG_REF = 8f

        /** In-place radix-2 Cooley–Tukey FFT. */
        fun fft(real: FloatArray, imag: FloatArray) {
            val n = real.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) {
                    j = j xor bit
                    bit = bit shr 1
                }
                j = j xor bit
                if (i < j) {
                    val tr = real[i]; real[i] = real[j]; real[j] = tr
                    val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
                }
            }
            var len = 2
            while (len <= n) {
                val ang = (-2.0 * PI / len)
                val wlenRe = cos(ang).toFloat()
                val wlenIm = sin(ang).toFloat()
                for (i in 0 until n step len) {
                    var wr = 1f
                    var wi = 0f
                    val half = len / 2
                    for (k in 0 until half) {
                        val ur = real[i + k]
                        val ui = imag[i + k]
                        val vr = real[i + k + half] * wr - imag[i + k + half] * wi
                        val vi = real[i + k + half] * wi + imag[i + k + half] * wr
                        real[i + k] = ur + vr
                        imag[i + k] = ui + vi
                        real[i + k + half] = ur - vr
                        imag[i + k + half] = ui - vi
                        val nwr = wr * wlenRe - wi * wlenIm
                        wi = wr * wlenIm + wi * wlenRe
                        wr = nwr
                    }
                }
                len = len shl 1
            }
        }
    }
}
