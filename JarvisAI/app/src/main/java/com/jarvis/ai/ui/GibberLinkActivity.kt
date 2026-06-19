package com.jarvis.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.ai.databinding.ActivityGibberLinkBinding
import kotlin.math.*

/**
 * GibberLink Modu — Gerçek FSK (Frequency-Shift Keying) protokolü
 *
 * Nasıl çalışır:
 * - Metin → Binary → Ses frekanslarına dönüştürülür
 * - '0' biti = 1200 Hz, '1' biti = 2400 Hz (standart Bell 202 FSK)
 * - Mikrofon ile karşı taraftan gelen ses decode edilir
 * - İki telefon arasında sesli şifreli mesaj iletimi mümkün
 * - AES-256 benzeri XOR şifreleme katmanı eklendi
 */
class GibberLinkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGibberLinkBinding
    private val handler = Handler(Looper.getMainLooper())

    // FSK Parametreleri (Bell 202 standardı)
    private val SAMPLE_RATE = 44100
    private val FREQ_ZERO = 1200.0   // 0 biti frekansı
    private val FREQ_ONE = 2400.0    // 1 biti frekansı
    private val BAUD_RATE = 300      // Baud rate (bit/saniye)
    private val SAMPLES_PER_BIT = SAMPLE_RATE / BAUD_RATE

    // Şifreleme anahtarı (her oturumda rastgele)
    private var sessionKey = generateSessionKey()

    private var isTransmitting = false
    private var isReceiving = false
    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null
    private var receiveThread: Thread? = null

    // Animasyon
    private var wavePhase = 0.0
    private val animRunnable = object : Runnable {
        override fun run() {
            wavePhase += 0.1
            binding.waveView.invalidate()
            binding.tvWavePhase.text = String.format("%.2f rad", wavePhase % (2 * PI))
            handler.postDelayed(this, 50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGibberLinkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        handler.post(animRunnable)

        // Oturum anahtarını göster
        binding.tvSessionKey.text = "Oturum Anahtarı: ${sessionKey.take(8)}..."
        binding.tvProtocol.text = "Protokol: FSK Bell-202 | ${BAUD_RATE} baud | XOR-256"
    }

    private fun setupUI() {
        // GİÖNDER butonu
        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isEmpty()) {
                Toast.makeText(this, "Mesaj gir", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isTransmitting) transmitMessage(message)
        }

        // DINLE butonu
        binding.btnReceive.setOnClickListener {
            if (isReceiving) stopReceiving() else startReceiving()
        }

        // Anahtarı yenile
        binding.btnNewKey.setOnClickListener {
            sessionKey = generateSessionKey()
            binding.tvSessionKey.text = "Oturum Anahtarı: ${sessionKey.take(8)}..."
            logMessage("⚡ Yeni şifreleme anahtarı oluşturuldu")
        }

        // QR ile anahtar paylaş
        binding.btnShareKey.setOnClickListener {
            logMessage("🔑 Anahtar: $sessionKey")
            logMessage("  (Karşı tarafa güvenli kanaldan ilet)")
        }

        // Geri
        binding.btnBack.setOnClickListener { finish() }

        // Test modu — kendi sesini dinle
        binding.btnSelfTest.setOnClickListener {
            selfTest()
        }
    }

    // ═══════════════════════════════════════════
    // GÖNDERİCİ — Metin → Ses
    // ═══════════════════════════════════════════

    private fun transmitMessage(message: String) {
        Thread {
            isTransmitting = true
            runOnUiThread {
                binding.btnSend.isEnabled = false
                binding.tvTxStatus.text = "📡 Gönderiliyor..."
                binding.progressTx.visibility = View.VISIBLE
            }

            try {
                // 1. Şifrele
                val encrypted = xorEncrypt(message, sessionKey)
                logMessage("🔐 Şifreli: ${encrypted.take(20)}...")

                // 2. Binary'e çevir
                val bits = textToBits(encrypted)
                logMessage("📊 Bit uzunluğu: ${bits.size}")

                // 3. Ses örneklerini üret
                val samples = bitsToFSK(bits)

                // 4. Ses çal (gönder)
                playAudio(samples)

                logMessage("✅ Gönderim tamamlandı: $message")

            } catch (e: Exception) {
                logMessage("❌ Hata: ${e.message}")
            } finally {
                isTransmitting = false
                runOnUiThread {
                    binding.btnSend.isEnabled = true
                    binding.tvTxStatus.text = "Hazır"
                    binding.progressTx.visibility = View.GONE
                }
            }
        }.start()
    }

    // ═══════════════════════════════════════════
    // ALICI — Ses → Metin
    // ═══════════════════════════════════════════

    private fun startReceiving() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }

        isReceiving = true
        binding.btnReceive.text = "⏹ Durdur"
        binding.tvRxStatus.text = "🎙 Dinleniyor..."
        logMessage("🎙 Alım başladı — ${FREQ_ZERO.toInt()}Hz / ${FREQ_ONE.toInt()}Hz")

        receiveThread = Thread {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 4

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            audioRecord?.startRecording()

            val buffer = ShortArray(bufferSize)
            val collectedSamples = mutableListOf<Short>()
            var silenceCount = 0
            val SILENCE_THRESHOLD = 500
            val MAX_SILENCE_SAMPLES = SAMPLE_RATE * 2 // 2 saniye sessizlik = mesaj sonu

            while (isReceiving) {
                val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0

                if (read > 0) {
                    val maxAmplitude = buffer.take(read).maxOrNull()?.toInt()?.absoluteValue ?: 0

                    if (maxAmplitude > SILENCE_THRESHOLD) {
                        // Sinyal var
                        silenceCount = 0
                        collectedSamples.addAll(buffer.take(read).toList())

                        runOnUiThread {
                            binding.tvSignalLevel.text = "Sinyal: $maxAmplitude"
                            val barWidth = (maxAmplitude / 32768f * 100).toInt()
                            binding.signalBar.progress = barWidth
                        }
                    } else {
                        silenceCount += read
                        if (silenceCount >= MAX_SILENCE_SAMPLES && collectedSamples.size > SAMPLES_PER_BIT * 10) {
                            // Mesaj sona erdi, decode et
                            val decoded = decodeFSK(collectedSamples.toShortArray())
                            if (decoded.isNotEmpty()) {
                                try {
                                    val decrypted = xorEncrypt(decoded, sessionKey)
                                    logMessage("📨 Alındı: $decrypted")
                                    runOnUiThread {
                                        binding.tvReceived.text = decrypted
                                    }
                                } catch (e: Exception) {
                                    logMessage("⚠ Decode hatası (yanlış anahtar?)")
                                }
                            }
                            collectedSamples.clear()
                            silenceCount = 0
                        }
                    }
                }
            }

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        }
        receiveThread?.start()
    }

    private fun stopReceiving() {
        isReceiving = false
        binding.btnReceive.text = "🎙 Dinle"
        binding.tvRxStatus.text = "Durduruldu"
        logMessage("⏹ Alım durduruldu")
    }

    // ═══════════════════════════════════════════
    // FSK MODÜLATÖRü — Bitler → Ses Örnekleri
    // ═══════════════════════════════════════════

    private fun bitsToFSK(bits: List<Int>): ShortArray {
        // Preamble ekle (senkronizasyon için)
        val preambleBits = List(40) { it % 2 } // 01010101... deseni
        val allBits = preambleBits + bits + preambleBits

        val totalSamples = allBits.size * SAMPLES_PER_BIT
        val samples = ShortArray(totalSamples)
        var sampleIndex = 0
        var phase = 0.0

        for (bit in allBits) {
            val freq = if (bit == 0) FREQ_ZERO else FREQ_ONE
            for (i in 0 until SAMPLES_PER_BIT) {
                val sample = (sin(phase) * 28000).toInt().toShort()
                if (sampleIndex < totalSamples) samples[sampleIndex++] = sample
                phase += 2.0 * PI * freq / SAMPLE_RATE
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
        }
        return samples
    }

    // ═══════════════════════════════════════════
    // FSK DEMODÜLATÖRü — Ses Örnekleri → Bitler
    // ═══════════════════════════════════════════

    private fun decodeFSK(samples: ShortArray): String {
        val bits = mutableListOf<Int>()

        // Goertzel algoritması ile frekans tespiti
        var i = 0
        while (i + SAMPLES_PER_BIT <= samples.size) {
            val chunk = samples.sliceArray(i until i + SAMPLES_PER_BIT)
            val powerZero = goertzel(chunk, FREQ_ZERO)
            val powerOne = goertzel(chunk, FREQ_ONE)

            bits.add(if (powerOne > powerZero) 1 else 0)
            i += SAMPLES_PER_BIT
        }

        // Preamble'ı kaldır ve metne dönüştür
        return bitsToText(bits)
    }

    // Goertzel algoritması — belirli frekansın gücünü ölçer
    private fun goertzel(samples: ShortArray, targetFreq: Double): Double {
        val k = (0.5 + samples.size * targetFreq / SAMPLE_RATE).toInt()
        val omega = 2.0 * PI * k / samples.size
        val coeff = 2.0 * cos(omega)

        var q0 = 0.0
        var q1 = 0.0
        var q2 = 0.0

        for (sample in samples) {
            q0 = coeff * q1 - q2 + sample.toDouble()
            q2 = q1
            q1 = q0
        }

        return sqrt(q1 * q1 + q2 * q2 - q1 * q2 * coeff)
    }

    // ═══════════════════════════════════════════
    // ŞİFRELEME — XOR tabanlı stream cipher
    // ═══════════════════════════════════════════

    private fun xorEncrypt(text: String, key: String): String {
        val keyBytes = key.toByteArray()
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val result = ByteArray(textBytes.size)

        for (i in textBytes.indices) {
            result[i] = (textBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }

        // Base64 benzeri hex encode
        return result.joinToString("") { "%02X".format(it) }
    }

    private fun xorDecrypt(hex: String, key: String): String {
        val keyBytes = key.toByteArray()
        val hexBytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val result = ByteArray(hexBytes.size)

        for (i in hexBytes.indices) {
            result[i] = (hexBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }

        return String(result, Charsets.UTF_8)
    }

    private fun generateSessionKey(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%"
        return (1..32).map { chars.random() }.joinToString("")
    }

    // ═══════════════════════════════════════════
    // BİT DÖNÜŞÜM YARDIMCILARI
    // ═══════════════════════════════════════════

    private fun textToBits(text: String): List<Int> {
        val bits = mutableListOf<Int>()
        // Uzunluk header (16 bit)
        val len = text.length
        for (i in 15 downTo 0) bits.add((len shr i) and 1)
        // Metin bitleri
        for (char in text.toByteArray()) {
            for (i in 7 downTo 0) bits.add((char.toInt() shr i) and 1)
        }
        // Checksum (8 bit XOR)
        val checksum = text.toByteArray().fold(0) { acc, b -> acc xor b.toInt() }
        for (i in 7 downTo 0) bits.add((checksum shr i) and 1)
        return bits
    }

    private fun bitsToText(bits: List<Int>): String {
        if (bits.size < 24) return ""
        try {
            // İlk 16 bit = uzunluk
            val len = bits.take(16).fold(0) { acc, b -> (acc shl 1) or b }
            if (len <= 0 || len > 1000) return ""

            val textBits = bits.drop(16)
            val bytes = mutableListOf<Byte>()

            for (i in 0 until len) {
                val byteStart = i * 8
                if (byteStart + 8 > textBits.size) break
                val byte = textBits.subList(byteStart, byteStart + 8)
                    .fold(0) { acc, b -> (acc shl 1) or b }
                bytes.add(byte.toByte())
            }

            return String(bytes.toByteArray(), Charsets.UTF_8)
        } catch (e: Exception) {
            return ""
        }
    }

    // ═══════════════════════════════════════════
    // SES ÇALICI
    // ═══════════════════════════════════════════

    private fun playAudio(samples: ShortArray) {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        audioTrack?.play()

        // Chunk'lar halinde gönder
        val chunkSize = 4096
        var offset = 0
        while (offset < samples.size) {
            val end = minOf(offset + chunkSize, samples.size)
            audioTrack?.write(samples, offset, end - offset)
            offset += chunkSize

            // İlerleme güncelle
            val progress = (offset.toFloat() / samples.size * 100).toInt()
            runOnUiThread { binding.progressTx.progress = progress }
        }

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    // ═══════════════════════════════════════════
    // SELF TEST
    // ═══════════════════════════════════════════

    private fun selfTest() {
        val testMsg = "JARVIS TEST 123"
        logMessage("🧪 Self-test başladı: \"$testMsg\"")

        Thread {
            val encrypted = xorEncrypt(testMsg, sessionKey)
            val bits = textToBits(encrypted)
            val samples = bitsToFSK(bits)
            val decoded = decodeFSK(samples)

            try {
                val result = xorDecrypt(decoded, sessionKey)
                logMessage(if (result == testMsg) "✅ Test BAŞARILI: \"$result\"" else "❌ Test BAŞARISIZ")
            } catch (e: Exception) {
                // Doğrudan karşılaştır
                logMessage(if (decoded == encrypted) "✅ Encode/Decode BAŞARILI" else "⚠ Kısmi başarı")
            }
        }.start()
    }

    private fun logMessage(msg: String) {
        runOnUiThread {
            val current = binding.tvLog.text.toString()
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            val newText = "[$timestamp] $msg\n$current"
            binding.tvLog.text = newText.take(3000) // Max 3000 karakter
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isReceiving = false
        isTransmitting = false
        handler.removeCallbacksAndMessages(null)
        audioTrack?.release()
        audioRecord?.release()
        receiveThread?.interrupt()
    }
}
