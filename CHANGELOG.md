# Değişiklikler

## 0.2.0

- Resmi yt-dlp 2026.10.26 paketlendi. Önceki kurulumlardaki motor her yeni işte SHA-256 ile kontrol edilerek atomik olarak yenilenir; bozuk çıkarımlar da aynı yolla onarılır.
- Kapak/JPG dönüştürmesinde eksik FFmpeg başlatma adımı düzeltildi.
- Geçici ağ hatalarında toplam üç denemeyle sınırlı, iptal edilebilir beklemeli tekrar deneme eklendi. Oturum, erişim reddi ve istek sınırı hataları otomatik tekrarlanmaz.
- Uygun video biçimi bulunamadığında kalite sınırını koruyan alternatif biçim denenir. TikTok kalite sınırını aşabilen geri dönüş kaldırıldı.
- Bir bağlantı hata verdiğinde kalan kuyruk devam eder; başarısız bağlantılar özgün ayarlarla tekrar denenebilir.
- Birleştirilmemiş video/ses akışlarının ve geçici dosyaların tamamlanmış medya olarak yayımlanması engellendi. İptalde kaydedilmiş dosya sayısı korunur.
- Bağlantı gerektirmeyen platform seçimi, giriş sonrası otomatik oturum kaydı, sayfa hatasında yeniden açma ve ekran döndürmede giriş sayfasını koruma eklendi.
- Eksik veya farklı alan adlarına dağılmış X oturumları geçerli sayılmaz; oturum kaydı atomik yazılır, silme işlemi WebView çerez temizliğinin tamamlanmasını bekler.
- AMOLED, Kuzey, Kiraz, Kağıt, Latte ve Lavanta ile tema sayısı 16 oldu. Oturum ekranı, sistem çubukları ve açılır listeler tema seçimine uyar.
- İndirme ayarlarını hatırlama, benzersiz bağlantı sayacı, indirilenleri açma ve teknik ayrıntıları göster/gizle eklendi.
- Android 15+ kenardan kenara görünümde sistem çubukları ve klavye için boşluklar düzenlendi.

## 0.1.4

- İlk genel APK yayınıdır.
- Android 10+ ARM64 cihazlar için imzalı release paketi hazırlandı.
- Video, MP3 ve kapak indirme modları eklendi.
- YouTube, TikTok, Instagram, X/Twitter, Reddit, Facebook ve deneysel Threads desteği eklendi.
- Android paylaşım menüsü entegrasyonu ve kalıcı tema seçimi eklendi.
- Açık içerikler için oturumsuz indirme, kısıtlı içerikler için isteğe bağlı uygulama içi oturum akışı eklendi.
- TikTok videolarında doğrudan birleşik MP4 formatı tercih edildi.
- FFmpeg yalnızca gerekli dönüştürme ve birleştirme işlemlerinde başlatılacak şekilde düzenlendi.
- Python çalışma ortamının güvenilir biçimde hazırlanması sağlandı.
- ZIP çıkarma katmanındaki release optimizasyon uyumsuzluğu giderildi.
- Reflection kullanan bağımlılıklar için koruma kuralları eklendi.
