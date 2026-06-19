package com.jarvis.ai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jarvis.ai.ai.GeminiAI
import com.jarvis.ai.ai.CommandProcessor
import com.jarvis.ai.databinding.ActivityMainBinding
import com.jarvis.ai.service.JarvisService
import com.jarvis.ai.utils.AppLauncher
import com.jarvis.ai.utils.ContactHelper
import com.jarvis.ai.utils.Prefs
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var geminiAI: GeminiAI
    private lateinit var commandProcessor: CommandProcessor
    private lateinit var prefs: Prefs

    private var isListening = false
    private var ttsReady = false
    private val handler = Handler(Looper.getMainLooper())

    // Animasyon için
    private var radarAngle = 0f
    private val radarRunnable = object : Runnable {
        override fun run() {
            radarAngle += 3f
            if (radarAngle >= 360f) radarAngle = 0f
            binding.radarView.rotation = radarAngle
            binding.hudRingOuter.rotation = -radarAngle * 0.5f
            binding.hudRingInner.rotation = radarAngle * 0.8f
            handler.postDelayed(this, 16) // ~60fps
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA,
            Manifest.permission.SEND_SMS
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tam ekran, ekran açık tut
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        geminiAI = GeminiAI(this)
        commandProcessor = CommandProcessor(this)

        setupUI()
        setupTTS()
        checkPermissions()
        startHUDAnimation()
        startClock()

        // İlk açılış mesajı
        handler.postDelayed({
            showStatus("Tüm sistemler aktif.")
            speak("Merhaba. Ben JARVIS. Sizi bekliyordum.")
        }, 1500)
    }

    private fun setupUI() {
        // Mikrofon butonu
        binding.btnMic.setOnClickListener {
            if (isListening) stopListening() else startListening()
        }

        // GibberLink modu
        binding.btnGibberlink.setOnClickListener {
            startActivity(Intent(this, GibberLinkActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Kamera/Yüz tanıma
        binding.btnCamera.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        // Ayarlar
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Chat geçmişini temizle
        binding.btnClear.setOnClickListener {
            binding.tvChat.text = ""
            geminiAI.clearHistory()
            showStatus("Bellek temizlendi.")
        }

        // Durum paneli tıklama
        binding.cardStatus.setOnClickListener {
            binding.tvStatus.text = "Sistem durumu: Normal | Bağlantı: Aktif | AI: Gemini"
        }
    }

    private fun setupTTS() {
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("tr", "TR")
            tts.setSpeechRate(0.95f)
            tts.setPitch(0.85f) // Derin JARVIS sesi
            ttsReady = true
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showStatus("Ses tanıma desteklenmiyor.")
            return
        }

        isListening = true
        binding.btnMic.setImageResource(android.R.drawable.ic_media_pause)
        binding.rippleView.visibility = View.VISIBLE
        showStatus("Dinliyorum...")

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                binding.micWave.visibility = View.VISIBLE
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // Ses seviyesine göre animasyon
                val scale = 1f + (rmsdB / 20f).coerceIn(0f, 0.5f)
                binding.micWave.scaleX = scale
                binding.micWave.scaleY = scale
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                binding.micWave.visibility = View.GONE
            }
            override fun onError(error: Int) {
                stopListening()
                showStatus("Ses algılanamadı. Tekrar deneyin.")
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotEmpty()) processCommand(text)
                stopListening()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""
                if (partial.isNotEmpty()) showStatus("\"$partial\"")
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_ONLY_LANGUAGE_MODEL, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer.startListening(intent)
    }

    private fun stopListening() {
        isListening = false
        binding.btnMic.setImageResource(android.R.drawable.ic_btn_speak_now)
        binding.rippleView.visibility = View.GONE
        binding.micWave.visibility = View.GONE
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.stopListening()
            speechRecognizer.destroy()
        }
    }

    private fun processCommand(text: String) {
        appendChat("Sen", text)
        showStatus("İşleniyor...")

        // Önce yerel komut kontrolü
        val localResult = commandProcessor.process(text)
        if (localResult != null) {
            appendChat("JARVIS", localResult)
            speak(localResult)
            showStatus("Hazır.")
            return
        }

        // Gemini AI'ye gönder
        lifecycleScope.launch {
            try {
                val response = geminiAI.sendMessage(text)
                appendChat("JARVIS", response)
                speak(response)
                showStatus("Hazır.")
            } catch (e: Exception) {
                val errMsg = "Üzgünüm, bir hata oluştu: ${e.message}"
                appendChat("JARVIS", errMsg)
                speak("Üzgünüm, bir hata oluştu.")
                showStatus("Hata: ${e.message}")
            }
        }
    }

    fun speak(text: String) {
        if (ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_${System.currentTimeMillis()}")
        }
    }

    fun showStatus(text: String) {
        runOnUiThread { binding.tvStatus.text = text }
    }

    private fun appendChat(sender: String, message: String) {
        runOnUiThread {
            val current = binding.tvChat.text.toString()
            val newText = if (current.isEmpty()) "[$sender]: $message"
                         else "$current\n\n[$sender]: $message"
            binding.tvChat.text = newText
            binding.scrollChat.post { binding.scrollChat.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun startHUDAnimation() {
        handler.post(radarRunnable)

        // Puls animasyonu
        binding.hudPulse.animate()
            .scaleX(1.2f).scaleY(1.2f)
            .alpha(0.3f)
            .setDuration(1000)
            .withEndAction {
                binding.hudPulse.animate()
                    .scaleX(1f).scaleY(1f)
                    .alpha(0.8f)
                    .setDuration(1000)
                    .withEndAction { startHUDAnimation() }
                    .start()
            }.start()
    }

    private fun startClock() {
        val clockRunnable = object : Runnable {
            override fun run() {
                val now = java.util.Calendar.getInstance()
                val h = String.format("%02d", now.get(java.util.Calendar.HOUR_OF_DAY))
                val m = String.format("%02d", now.get(java.util.Calendar.MINUTE))
                val s = String.format("%02d", now.get(java.util.Calendar.SECOND))
                binding.tvClock.text = "$h:$m:$s"

                val day = now.get(java.util.Calendar.DAY_OF_MONTH)
                val month = now.get(java.util.Calendar.MONTH) + 1
                val year = now.get(java.util.Calendar.YEAR)
                binding.tvDate.text = String.format("%02d.%02d.%d", day, month, year)

                handler.postDelayed(this, 1000)
            }
        }
        handler.post(clockRunnable)
    }

    private fun checkPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            startJarvisService()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            startJarvisService()
        }
    }

    private fun startJarvisService() {
        val serviceIntent = Intent(this, JarvisService::class.java)
        startForegroundService(serviceIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        if (::speechRecognizer.isInitialized) speechRecognizer.destroy()
    }
}
