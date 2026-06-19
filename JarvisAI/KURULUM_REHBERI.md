# 🤖 J.A.R.V.I.S v2.0 — Kurulum Rehberi

## 📋 Proje Yapısı

```
JarvisAI/
├── app/src/main/
│   ├── AndroidManifest.xml          ← İzinler & aktiviteler
│   ├── java/com/jarvis/ai/
│   │   ├── ui/
│   │   │   ├── MainActivity.kt      ← Ana HUD ekranı
│   │   │   ├── GibberLinkActivity.kt ← Şifreli ses protokolü
│   │   │   ├── CameraActivity.kt    ← Kamera & yüz tanıma
│   │   │   └── SettingsActivity.kt  ← API anahtarı & ayarlar
│   │   ├── ai/
│   │   │   ├── GeminiAI.kt          ← Gemini API bağlantısı
│   │   │   └── CommandProcessor.kt  ← Yerel komut işleyici
│   │   ├── service/
│   │   │   ├── JarvisService.kt     ← Arka plan servisi
│   │   │   └── BootReceiver.kt      ← Açılışta başlat
│   │   └── utils/
│   │       ├── AppLauncher.kt       ← Uygulama açma
│   │       ├── ContactHelper.kt     ← Rehber erişimi
│   │       └── Prefs.kt             ← Ayar yönetimi
│   └── res/
│       ├── layout/                  ← XML arayüzler
│       ├── drawable/                ← HUD görselleri
│       └── values/                  ← Renkler & string'ler
├── build.gradle                     ← Proje build
└── app/build.gradle                 ← Uygulama build
```

---

## ⚡ ADIM 1 — Android Studio'ya Projeyi Al

### Seçenek A: Yeni Proje Olarak Aç
1. Android Studio'yu aç
2. **File → Open** → Bu `JarvisAI` klasörünü seç
3. Gradle sync otomatik başlar, bekle (~2-3 dk)

### Seçenek B: Mevcut Projeye Dosyaları Kopyala
1. Zaten varsa `com.jarvis.ai` projen, dosyaları üzerine kopyala
2. `Sync Project with Gradle Files` butonuna tıkla

---

## ⚡ ADIM 2 — Gemini API Anahtarı Al (ÜCRETSİZ)

1. Şu adrese git: **https://aistudio.google.com/app/apikey**
2. Google hesabınla giriş yap
3. **"Create API Key"** butonuna tıkla
4. Çıkan `AIzaSy...` ile başlayan anahtarı kopyala

> ⚠️ API anahtarını kimseyle paylaşma!

---

## ⚡ ADIM 3 — API Anahtarını Uygulamaya Gir

**Yöntem 1 (Uygulama içinden):**
- Uygulamayı aç → ⚙️ Ayarlar → API anahtarını yapıştır → Kaydet

**Yöntem 2 (Kod içine göm — geliştirme için):**
`GeminiAI.kt` dosyasında şu satırı değiştir:
```kotlin
val apiKey = prefs.geminiApiKey
// ↓ değiştir ↓
val apiKey = "AIzaSy_SENIN_ANAHTARIN_BURAYA"
```

---

## ⚡ ADIM 4 — Telefonuna Yükle

1. Telefonda **Geliştirici Seçenekleri** aç:
   - Ayarlar → Telefon hakkında → "Yapı numarasına" 7 kez dokun
2. **USB hata ayıklama**'yı etkinleştir
3. Telefonu bilgisayara bağla
4. Android Studio'da ▶️ **Run** tuşuna bas
5. Telefonunu seç → Yükle

---

## 🎤 KULLANIM — Komut Örnekleri

### Sesli Komutlar
| Ne söyle | Ne yapar |
|---|---|
| `YouTube aç` | YouTube'u başlatır |
| `WhatsApp aç` | WhatsApp'ı açar |
| `Ahmet'i ara` | Rehberden Ahmet'i arar |
| `Ahmet'e whatsapp: merhaba` | WhatsApp mesajı gönderir |
| `Saat kaç?` | Saati söyler |
| `Bugün tarih?` | Tarihi söyler |
| `Pil durumu` | Pil yüzdesini söyler |
| Diğer her şey | Gemini AI yanıtlar |

---

## ⚡ GİBBERLİNK MODU — Nasıl Kullanılır?

GibberLink, **gerçek FSK (Frequency-Shift Keying)** protokolü kullanarak ses dalgaları üzerinden şifreli mesaj iletimi sağlar.

### Protokol Detayları
- **Modülasyon:** FSK Bell-202
- **Baud rate:** 300 bit/saniye  
- **0 biti:** 1200 Hz ses tonu
- **1 biti:** 2400 Hz ses tonu
- **Şifreleme:** XOR stream cipher (32 karakter anahtar)
- **Senkronizasyon:** 40-bit preamble

### İki Telefon Arası Kullanım
1. İki telefonda da JarvisAI'yi aç
2. **GibberLink** butonuna bas
3. **"Yeni Anahtar"** oluştur → **"Anahtarı Göster"** ile karşı tarafa ilet (bir kez, güvenli kanaldan)
4. Karşı taraf aynı anahtarı girer
5. Mesaj yaz → **GÖNDER** → Telefon ses çıkarır
6. Karşı telefon **DINLE** modundayken sesi algılar → Çözer → Metni gösterir

### Self-Test
- **"Test"** butonuna bas → Kendi sesini encode/decode eder → Başarılı mı kontrol eder

---

## 🔧 Sorun Giderme

| Sorun | Çözüm |
|---|---|
| "API anahtarı eksik" | Ayarlar → API anahtarını gir |
| Ses tanımıyor | Mikrofon iznini ver, Türkçe dil paketi indir |
| Uygulama açılmıyor | Android 8.0+ (API 26) gerekli |
| GibberLink çalışmıyor | Mikrofon iznini ver, ses volümünü artır |
| Kamera açılmıyor | Kamera iznini ver |

---

## 📱 Sistem Gereksinimleri

- **Android:** 8.0 (Oreo) ve üzeri
- **RAM:** Minimum 2 GB
- **İnternet:** Gemini API için gerekli
- **Mikrofon:** Sesli komutlar için
- **Test cihazı:** Redmi Note 12S ✅ (uyumlu)

---

## 🚀 Gelecek Özellikler (Eklenebilir)

- [ ] ML Kit ile gerçek yüz tanıma
- [ ] Hava durumu entegrasyonu
- [ ] Spotify/müzik kontrolü
- [ ] Akıllı ev kontrolü (Google Home)
- [ ] Çoklu dil desteği
- [ ] GibberLink QR kod anahtar paylaşımı
- [ ] Bluetooth üzerinden GibberLink

---

*J.A.R.V.I.S v2.0 — Just A Rather Very Intelligent System*
