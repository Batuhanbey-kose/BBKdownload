# BBKdownload'a Katkı

Katkılar küçük, odaklı ve doğrulanabilir olmalıdır.

## Geliştirme akışı

1. Repoyu fork edin ve kısa isimli bir özellik dalı açın.
2. Değişikliği mevcut Kotlin ve Android kalıplarını koruyarak uygulayın.
3. Davranış değişiklikleri için birim testi ekleyin.
4. Aşağıdaki doğrulamayı çalıştırın:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

5. Pull request açıklamasında davranış değişikliğini, güvenlik etkisini ve test sonucunu belirtin.

## Güvenlik

Gerçek platform çerezlerini, kişisel bağlantıları, release anahtarlarını, `keystore.properties` dosyasını veya R8 mapping çıktısını issue ve pull request'lere eklemeyin. Güvenlik açıkları için `SECURITY.md` içindeki özel bildirim yöntemini kullanın.
