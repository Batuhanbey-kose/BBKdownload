<p align="center">
  <img src="docs/hero.svg" width="100%" alt="BBKdownload" />
</p>

<p align="center">
  <a href="https://github.com/Batuhanbey-kose/BBKdownload/releases/download/v0.1.4/BBKdownload.apk">
    <img src="docs/download.svg" width="540" alt="BBKdownload APK indir" />
  </a>
</p>

<p align="center">
  <img alt="Android CI" src="https://img.shields.io/github/actions/workflow/status/Batuhanbey-kose/BBKdownload/android.yml?branch=main&style=flat-square&label=Android%20CI" />
  <img alt="Android 10+" src="https://img.shields.io/badge/Android-10%2B-22D3EE?style=flat-square&logo=android&logoColor=white" />
  <img alt="GPL v3" src="https://img.shields.io/badge/Lisans-GPLv3-7C3AED?style=flat-square" />
</p>

<p align="center">
  <strong>Reklam yok · Üyelik yok · Takip yok</strong><br />
  Video, MP3 ve kapak dosyalarını doğrudan telefonuna kaydet.
</p>

## Telefona Nasıl Kurulur?

1. Yukarıdaki büyük **TELEFONA İNDİR** butonuna bas.
2. İnen `BBKdownload.apk` dosyasını aç ve **Yükle** seçeneğine bas.
3. Telefon izin isterse tarayıcın için **Bilinmeyen uygulamaları yükle** iznini aç.

Hepsi bu. Uygulamayı aç, bağlantıyı yapıştır ve indirme biçimini seç.

> APK Android 10 ve üzeri ARM64 telefonlar içindir. GitHub indirme sayfası açılırsa **Assets** bölümündeki `BBKdownload.apk` dosyasına dokun.

> [!NOTE]
> **Play Protect indirmeyi veya kurulumu bekletirse:** BBKdownload Google Play'de yayımlanmadığı için bazı cihazlar APK'yı `İndiriliyor...` ekranında tutabilir ya da bilinmeyen uygulama uyarısı gösterebilir. Önce sunulan **Yine de yükle** seçeneğini kullan. Bu seçenek yoksa:
>
> 1. Yalnızca yukarıdaki resmi GitHub bağlantısını kullandığından emin ol.
> 2. Play Store > profil resmi > **Play Protect** > Ayarlar bölümündeki **Uygulamaları Play Protect ile tara** seçeneğini geçici olarak kapat.
> 3. APK'yı indirip kurduktan sonra aynı seçeneği hemen yeniden aç.
> 4. Play Protect ekranındaki **Tara** düğmesiyle kontrol yap; resmi yayın APK'sında sorun bulunmamalıdır.
>
> Play Protect'i kapalı bırakma ve APK'yı başka kaynaklardan indirme.

## Ne İndirir?

| Video | MP3 | Kapak |
|---|---|---|
| Kalite seçerek MP4 kaydet | Sesi 192 kbps MP3'e çevir | Gönderi kapağını JPG kaydet |

Desteklenen bağlantılar: **YouTube, X/Twitter, TikTok, Instagram, Reddit, Facebook** ve deneysel olarak **Threads**.

Dosyalar telefonundaki `İndirilenler/BBKdownload` klasörüne gelir. Bağlantıyı başka bir uygulamadan **Paylaş > BBKdownload** diyerek de gönderebilirsin.

### 0.2.0 Güncellemesi

- **16 tema:** Mevcut temalara AMOLED, Kuzey, Kiraz ve açık renkli Kağıt, Latte, Lavanta eklendi. Oturum ekranı ve açılır menüler de seçilen temaya uyar.
- **Kolay oturum:** Bağlantı eklemeden **Oturum Aç** ile platform seçilebilir. Giriş tamamlanınca oturum otomatik kaydedilir; elle kaydetme seçeneği de vardır.
- **Daha dayanıklı indirme:** Resmi yt-dlp **2026.10.26**, APK güncellemesinde eski motorun SHA-256 doğrulamasıyla yenilenmesi, geçici ağ hatalarında en fazla üç deneme ve kalite sınırını koruyan uyumlu biçim seçimi.
- **Kuyruk yönetimi:** Bir bağlantı hata verse de diğerleri devam eder. **Başarısızları Tekrar Dene**, yalnızca hata veren bağlantıları aynı ayarlarla sıraya alır. Liste/profil bağlantısı tekrar denenirse o bağlantıdaki öğeler yeniden işlenebilir.
- **Küçük kolaylıklar:** Kalite/mod/liste ayarları hatırlanır; benzersiz bağlantı sayısı, indirilenleri açma ve gizlenebilir teknik ayrıntılar bulunur.
- **Düzeltmeler:** Kapak dönüştürmede FFmpeg hazırlanır; birleştirilmemiş ses/video parçaları İndirilenler klasörüne taşınmaz. Android 15+ sistem çubukları ve klavye boşlukları ele alınır.

Bu yenilikler `main` dalındaki **0.2.0 kaynak sürümündedir**. Yukarıdaki hazır imzalı APK bağlantısı **0.1.4** sürümüne aittir. Güncel deneme APK'sı, başarılı [Android Verify](https://github.com/Batuhanbey-kose/BBKdownload/actions/workflows/android.yml) çalışmasının **Artifacts → BBKdownload-debug-arm64** bölümünden alınabilir; deneme paketi ayrı uygulama olarak kurulur.

## Güvenli mi?

- Reklam ve kullanıcı takibi yoktur.
- Konum, kamera, mikrofon ve rehber izni istemez.
- İndirme geçmişini bir sunucuya göndermez.
- Yalnızca desteklenen platformların güvenli HTTPS bağlantılarını kabul eder.
- Gerekli platform oturumları yalnızca Android'in uygulamaya özel alanında tutulur.

## Özel Hesaplar

- Herkese açık içerikler oturum açmadan indirilebilir.
- Özel hesaptaki bir içerik yalnızca o hesabı takip eden ve içeriği görme izni bulunan kullanıcı kendi hesabıyla **Oturum Aç** bölümünden oturum açarsa işlenebilir.
- İçerik oturum açıldıktan sonra platformda görünmüyorsa BBKdownload da içeriği göremez ve indiremez.
- Uygulama özel hesap, erişim izni, DRM veya ücretli içerik korumasını aşmaz.

## Hata Bildirimi ve Öneriler

Hata raporu veya özellik önerisi için **[GitHub Issues](https://github.com/Batuhanbey-kose/BBKdownload/issues)** bölümünü kullanın. Yeni kayıt açarken uygun şablonu seçin.

> Issue içine oturum çerezi, parola, token, özel hesap bağlantısı veya kişisel bilgi eklemeyin. Güvenlik açıklarını `SECURITY.md` içindeki yöntemle özel olarak bildirin.

Hazır sürüm ve checksum: **[BBKdownload v0.1.4](https://github.com/Batuhanbey-kose/BBKdownload/releases/tag/v0.1.4)**

<details>
<summary><strong>Geliştiriciler için teknik bilgiler</strong></summary>

### Teknoloji

| Katman | Seçim |
|---|---|
| Dil | Kotlin 2.1.20 |
| Android | minSdk 29, targetSdk 35 |
| İndirme motoru | youtubedl-android 0.18.1 |
| Extractor | SHA-256 doğrulanan yt-dlp 2026.10.26 |
| Medya | Paket içi FFmpeg + QuickJS |
| Depolama | Scoped Storage + MediaStore |
| ABI | arm64-v8a |

Paketlenen `yt-dlp` SHA-256:

```text
e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3
```

### Akış

```mermaid
flowchart LR
    A[HTTPS bağlantısı] --> B{URL güvenlik filtresi}
    B -->|Onaylandı| C[Foreground DownloadService]
    B -->|Reddedildi| X[Kullanıcı uyarısı]
    C --> D[yt-dlp]
    D -->|Doğrudan medya| F[MediaStore]
    D -->|Dönüştürme/birleştirme| E[FFmpeg]
    E --> F
    F --> G[İndirilenler/BBKdownload]
```

### Derleme

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

İmzalı release için `keystore.properties.example` dosyasını temel alarak Git tarafından izlenmeyen `keystore.properties` dosyanı oluştur:

```powershell
.\gradlew.bat testDebugUnitTest lintRelease assembleRelease
```

Release çıktısı `dist/BBKdownload.apk` konumuna kopyalanır. Release anahtarı, parola, APK ve derleyici çıktıları Git tarafından yok sayılır.

### Güvenlik ve Katkı

- Güvenlik modeli: [`SECURITY.md`](SECURITY.md)
- Katkı rehberi: [`CONTRIBUTING.md`](CONTRIBUTING.md)
- Üçüncü taraflar: [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)
- Lisans: [`GNU GPL v3.0`](LICENSE.md)
- Hata ve öneriler: [`GitHub Issues`](https://github.com/Batuhanbey-kose/BBKdownload/issues)

</details>

## Kullanım Sınırları

Uygulama filigran, DRM, ücretli içerik veya özel hesap korumasını aşmaz. Yalnızca sana ait ya da indirme iznin bulunan içerikleri kullan.

<p align="center">
  <strong>BBKdownload</strong><br />
  Bağlantıyı bırak, dosyanı al.
</p>
