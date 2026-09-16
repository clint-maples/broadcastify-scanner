package com.clintmaples.broadcastifyscanner

import com.clintmaples.broadcastifyscanner.player.SpectrumAudioProcessor
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class SpectrumFftTest {
    @Test
    fun fft_detectsSineEnergy() {
        val n = 64
        val real = FloatArray(n) { i -> sin(2.0 * PI * 4.0 * i / n).toFloat() }
        val imag = FloatArray(n)
        SpectrumAudioProcessor.fft(real, imag)
        var peakBin = 0
        var peak = 0f
        for (i in 0 until n / 2) {
            val mag = sqrt(real[i] * real[i] + imag[i] * imag[i])
            if (mag > peak) {
                peak = mag
                peakBin = i
            }
        }
        assertTrue("expected energy near bin 4, got $peakBin", abs(peakBin - 4) <= 1)
        assertTrue(peak > 10f)
    }
}
