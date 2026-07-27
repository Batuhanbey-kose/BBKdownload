# Güvenlik

## Gizlilik modeli

Uygulama reklam, analitik, çökme raporlama veya uzaktan loglama SDK'sı içermez. İndirme ilerleme satırları yalnızca işlem sırasında bellekte tutulur ve kalıcı bir log dosyasına yazılmaz. URL kullanıcı bilgileri ve sorgu parametreleri arayüz loglarında gösterilmez.

Platform oturum çerezleri yalnızca Android uygulama sandbox'ındaki `files/sessions` dizininde saklanır. Android yedekleme ve cihaz aktarımı kapalıdır; bu dizin ayrıca yedekleme kurallarında açıkça dışlanmıştır.

Uygulama yalnızca desteklenen platformlara ait HTTPS bağlantılarını kabul eder. Cleartext trafik, WebView dosya erişimi, karma içerik ve güvenilmeyen oturum yönlendirmeleri engellenir.

## Hassas dosyalar

Aşağıdaki dosyaları GitHub'a, GitHub Releases'a veya hata raporlarına eklemeyin:

- `keystore.properties`
- `private/`
- `*.jks` ve `*.keystore`
- Gerçek oturum çerezleri veya cihazdan alınmış uygulama verileri
- Derleyici tanılama ve mapping çıktıları

Bu yollar `.gitignore` içinde yer alır. Release anahtarını kaybetmek, aynı paket adına güncelleme yayımlamayı engeller; güvenli ve ayrı bir konumda yedekleyin.

## Açık bildirme

Bir güvenlik açığı bulursanız herkese açık issue içinde token, cookie, kişisel URL veya cihaz çıktısı paylaşmayın. Depoda GitHub Private Vulnerability Reporting etkinse özel güvenlik bildirimi kullanın.
