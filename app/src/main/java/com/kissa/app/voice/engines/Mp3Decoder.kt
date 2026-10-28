package com.kissa.app.voice.engines

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.kissa.app.voice.WavUtil
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Decodes an MP3 file (Microsoft neural voice output) to a 16-bit mono WAV with the platform MediaCodec, so the
 * normal playback path gets an amplitude envelope for lip-sync. Returns false if the device can't decode it
 * (the caller then plays the MP3 directly with time-based mouth movement).
 */
object Mp3Decoder {
    fun toWav(mp3: File, wav: File): Boolean = runCatching {
        val ex = MediaExtractor(); ex.setDataSource(mp3.absolutePath)
        val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return false
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0); codec.start()
        val out = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var inputDone = false; var outputDone = false
        val deadline = System.currentTimeMillis() + 8000
        while (!outputDone && System.currentTimeMillis() < deadline) {
            if (!inputDone) {
                val i = codec.dequeueInputBuffer(5000)
                if (i >= 0) {
                    val buf = codec.getInputBuffer(i)!!
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                    else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 5000)
            when {
                o >= 0 -> {
                    val buf = codec.getOutputBuffer(o)!!
                    val chunk = ByteArray(info.size); buf.position(info.offset); buf.get(chunk)
                    out.write(chunk)
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
                o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
        }
        runCatching { codec.stop() }; codec.release(); ex.release()
        var pcm = out.toByteArray()
        if (channels == 2) { // downmix to mono
            val mono = ByteArray(pcm.size / 2)
            var j = 0; var k = 0
            while (k + 3 < pcm.size) {
                val l = ((pcm[k + 1].toInt() shl 8) or (pcm[k].toInt() and 0xff)).toShort().toInt()
                val r = ((pcm[k + 3].toInt() shl 8) or (pcm[k + 2].toInt() and 0xff)).toShort().toInt()
                val m = (l + r) / 2
                mono[j] = (m and 0xff).toByte(); mono[j + 1] = ((m shr 8) and 0xff).toByte(); j += 2; k += 4
            }
            pcm = mono
        }
        if (pcm.size < 1000) return false
        val tmp = File(wav.parentFile, wav.name + ".part")
        WavUtil.write(tmp, pcm, rate)
        tmp.renameTo(wav)
    }.getOrDefault(false)
}
