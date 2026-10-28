package com.kissa.app.voice

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object WavUtil {
    fun header(pcmBytes: Int, sampleRate: Int, channels: Int = 1, bits: Int = 16): ByteArray {
        val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        val byteRate = sampleRate * channels * bits / 8
        b.put("RIFF".toByteArray()); b.putInt(36 + pcmBytes); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(channels.toShort())
        b.putInt(sampleRate); b.putInt(byteRate); b.putShort((channels * bits / 8).toShort()); b.putShort(bits.toShort())
        b.put("data".toByteArray()); b.putInt(pcmBytes)
        return b.array()
    }

    fun write(file: File, pcm: ByteArray, sampleRate: Int) {
        file.outputStream().use { it.write(header(pcm.size, sampleRate)); it.write(pcm) }
    }

    /** RMS amplitude envelope (0..1) per 50 ms window, used for lip-sync of recorded voice. */
    fun envelope(file: File, windowMs: Int = 50): FloatArray? {
        if (!file.name.endsWith(".wav")) return null
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 44) return null
            val hdr = ByteArray(44); raf.readFully(hdr)
            val bb = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
            val rate = bb.getInt(24).takeIf { it in 8000..48000 } ?: 16000
            val pcm = ByteArray((raf.length() - 44).toInt()); raf.readFully(pcm)
            return envelopeOf(pcm, rate, windowMs)
        }
    }

    fun envelopeOf(pcm: ByteArray, rate: Int, windowMs: Int = 50): FloatArray {
        val samplesPerWin = rate * windowMs / 1000
        val total = pcm.size / 2
        val n = (total + samplesPerWin - 1) / samplesPerWin
        val out = FloatArray(n)
        var peak = 1e-6f
        for (w in 0 until n) {
            var sum = 0.0; var cnt = 0
            val s0 = w * samplesPerWin
            for (s in s0 until minOf(total, s0 + samplesPerWin)) {
                val v = ((pcm[2 * s + 1].toInt() shl 8) or (pcm[2 * s].toInt() and 0xff)).toShort() / 32768.0
                sum += v * v; cnt++
            }
            out[w] = if (cnt > 0) sqrt(sum / cnt).toFloat() else 0f
            if (out[w] > peak) peak = out[w]
        }
        for (i in out.indices) out[i] = (out[i] / peak).coerceIn(0f, 1f)
        return out
    }
}
