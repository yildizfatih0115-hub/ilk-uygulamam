package com.jarvis.ai.ai

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.jarvis.ai.utils.Prefs

class GeminiAI(private val context: Context) {

    private val prefs = Prefs(context)
    private val chatHistory = mutableListOf<Pair<String, String>>() // user, assistant

    private val systemPrompt = """
        Sen J.A.R.V.I.S — Just A Rather Very Intelligent System.
        Tony Stark'ın kişisel yapay zeka asistanısın.
        
        Kişilik özelliklerin:
        - Son derece zeki, analitik ve verimli
        - Kibar ama kısa ve öz konuşursun
        - İngilizce terimler kullanabilirsin ama Türkçe konuşursun
        - Hafif bir İngiliz aksanı hissi verirsin
        - "Efendim", "Tabii ki efendim" gibi ifadeler kullanırsın
        - Teknik konularda detaylı ama anlaşılır açıklarsın
        - Zaman zaman ince bir mizah yaparsın
        
        Yeteneklerin:
        - Uygulama açma/kapatma
        - Arama yapma
        - WhatsApp mesajı gönderme
        - Hava durumu sorgulama
        - Genel sohbet ve sorular
        - GibberLink şifreli iletişim modu
        
        JARVIS gibi konuş. Kısa, net, profesyonel.
    """.trimIndent()

    private fun buildModel(): GenerativeModel {
        val apiKey = prefs.geminiApiKey
        return GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 500
            }
        )
    }

    suspend fun sendMessage(userMessage: String): String {
        return try {
            val model = buildModel()

            // Konuşma geçmişini mesaj formatına dönüştür
            val contents = buildList {
                // Sistem promptu ilk kullanıcı mesajı olarak ekle
                add(content("user") { text(systemPrompt) })
                add(content("model") { text("Anlıyorum efendim. Tüm sistemler hazır.") })

                // Geçmiş
                for ((user, assistant) in chatHistory.takeLast(10)) {
                    add(content("user") { text(user) })
                    add(content("model") { text(assistant) })
                }
            }

            val chat = model.startChat(history = contents)
            val response = chat.sendMessage(userMessage)
            val responseText = response.text ?: "Anlayamadım efendim."

            // Geçmişe ekle
            chatHistory.add(Pair(userMessage, responseText))

            responseText
        } catch (e: Exception) {
            when {
                e.message?.contains("API_KEY") == true ->
                    "API anahtarı eksik efendim. Lütfen ayarlardan Gemini API anahtarınızı girin."
                e.message?.contains("network") == true || e.message?.contains("timeout") == true ->
                    "İnternet bağlantısı kurulamıyor efendim."
                else ->
                    "Bir hata oluştu efendim: ${e.message?.take(100)}"
            }
        }
    }

    fun clearHistory() {
        chatHistory.clear()
    }
}
