# Blaze 30 Kontrol Uygulaması — Tasarım

Tarih: 2026-11-15 · Durum: onay bekliyor

## Amaç ve neden
Kozmos Blaze 30 (Blaze-30CR) amfisini kontrol eden, **güvenilir ve minimum izinli** bir Android uygulaması.
Resmi uygulama Play Protect'in kapatılmasını istiyor ve gerektiğinden fazla izin (rehber, SMS, konum) talep ediyor.
Bu proje, yalnızca gereken izinlerle çalışan bağımsız bir alternatif. Asıl ihtiyaç:
çalarken **Clean / Drive / Distortion / Metal** gibi tonlar arasında hızlı geçiş. Sonraki fazlarda amfi
simülasyonu ve kullanıcı preset'leri eklenecek.

## Hedefler (Faz 1)
- Amfiye Bluetooth LE ile bağlanma (otomatik yeniden bağlanma dahil).
- **Tek dokunuşla amfi tipi seçimi:** Clean, Drive, Acoustic, Distortion, Metal (ana ekran, büyük butonlar).
- Gain, ses seviyesi, 7 bantlı gitar EQ'su, efekt seçimi (Bypass/Flanger/Chorus/Phaser/Wah/Vibrato),
  reverb (tip/seviye/ton/süre), delay (seviye/feedback/süre).
- Sadece Bluetooth izinleri. İnternet, rehber, SMS, konum (BLE için Android'in zorunlu kıldığı yer HARİÇ),
  analitik, reklam yok.

## Hedef dışı (Faz 1)
AUX/USB müzik çalar sekmesi, kayıt, preset kaydetme, amfi simülasyonu, iOS, diğer ODAK cihaz modelleri.

## Anti-hedefler
- Resmi uygulamanın kodunu/markasını/görsellerini kopyalayıp dağıtmak: yalnızca protokol bilgisi
  (UUID, komut baytları) kullanılır, uygulama sıfırdan yazılır.
- Amfiye zarar verebilecek, doğrulanmamış komutları göndermek.
- Kullanıcıdan Play Protect'i kapatmasını istemek.

## Kısıtlar
- Protokol belgesiz; APK'dan çıkarılıp gerçek amfide doğrulanacak.
- Hedef cihaz: güncel bir Android telefon (~393 dp genişlikli ekran).
  Amfi test için kullanıcıda mevcut.
- Kuruluma kullanıcı kendi cihazında debug APK ile başlar (adb veya dosyadan); Play Store dağıtımı hedef değil.

## Yaklaşımlar
1. **Protokol: APK statik analizi (SEÇİLDİ)** — APK çalıştırılmadan decompile edilir (jadx), Blaze ekranlarından
   GATT servis/karakteristik UUID'leri ve komut baytları çıkarılır, amfide `nRF Connect` ile doğrulanır.
   Artı: orijinal uygulamayı kurmaya gerek yok, güvenli. Eksi: kod karışık olabilir.
2. Protokol: BLE trafik dinleme (yedek) — orijinal uygulamayı kurmayı gerektirdiği için yalnızca 1 başarısız olursa.
3. **Yığın: Kotlin + Jetpack Compose, yerel Android (benim kararım)** — yalnızca Android hedeflendiği ve iş
   BLE ağırlıklı olduğu için. Flutter/React Native iOS gerekirse avantajlı ama BLE katmanı yine yerel köprü
   ister; şimdilik gereksiz karmaşıklık.

## Faz planı
- **Faz 0 — Protokol keşfi (spike, atılabilir çıktı):** APK'yı ayrı klasöre aç, jadx ile Blaze'e ait sınıfları bul,
  protokolü `docs/protocol.md` olarak yaz, amfide nRF Connect ile 1-2 komutu doğrula (ör. amfi tipi değiştirme).
- **Faz 1 — Çekirdek uygulama:** BLE bağlantı katmanı (arayüz arkasında, test edilebilir sahte amfi ile),
  protokol kodlayıcı/çözücü (birim testli), ton geçiş ekranı, ayarlar ekranı.
- **Faz 2+ (sonra):** preset kaydet/yükle, ardından simülasyon; ayrı tasarımla.

## Mimari (Faz 1 taslağı)
`ble/` (taşıma) → `protocol/` (komut ↔ bayt, saf Kotlin, test edilir) → `amp/` (AmpState, repository) →
`ui/` (Compose). Protokol ve durum katmanı UI'dan bağımsız tutulur ki preset fazı üstüne eklenebilsin.
Amfi durumu uygulamada tek doğru kaynak (`AmpState`); preset = AmpState'in serileştirilmiş hali.

## Builder'a bırakılanlar
Paket adı, Gradle/AGP sürümleri, Compose tasarım detayları, hata/yeniden bağlanma ayrıntıları.

## Açık riskler
- Blaze 30'un hangi ODAK modeli koduna karşılık geldiği Faz 0'da belirlenecek (isim eşleşmesi yok).
- Komutlar bazı modellerde sağlama toplamı/oturum gerektirebilir; Faz 0 bunu netleştirir.
- Amfinin durumunu okuma (geri bildirim) desteği belirsiz; yoksa uygulama son gönderilen durumu gösterir.
