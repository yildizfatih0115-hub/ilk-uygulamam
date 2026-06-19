package com.jarvis.ai.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.ai.databinding.ActivitySettingsBinding
import com.jarvis.ai.utils.Prefs

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        loadSettings()
        setupUI()
    }

    private fun loadSettings() {
        binding.etApiKey.setText(prefs.geminiApiKey)
        binding.etJarvisName.setText(prefs.jarvisName)
        binding.switchHotword.isChecked = prefs.hotwordEnabled
        binding.switchTts.isChecked = prefs.ttsEnabled
        binding.switchAutoStart.isChecked = prefs.autoStart
        binding.switchGameMode.isChecked = prefs.gameMode
    }

    private fun setupUI() {
        binding.btnSave.setOnClickListener {
            val apiKey = binding.etApiKey.text.toString().trim()
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "Gemini API anahtarı gerekli!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.geminiApiKey = apiKey
            prefs.jarvisName = binding.etJarvisName.text.toString().ifEmpty { "JARVIS" }
            prefs.hotwordEnabled = binding.switchHotword.isChecked
            prefs.ttsEnabled = binding.switchTts.isChecked
            prefs.autoStart = binding.switchAutoStart.isChecked
            prefs.gameMode = binding.switchGameMode.isChecked

            Toast.makeText(this, "Ayarlar kaydedildi efendim.", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnGetApiKey.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://aistudio.google.com/app/apikey"))
            startActivity(intent)
        }

        binding.btnBack.setOnClickListener { finish() }
    }
}
