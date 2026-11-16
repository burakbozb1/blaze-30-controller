# Blaze 30 Controller

Kozmos Blaze 30 gitar amfisini **Bluetooth LE** ile kontrol eden bağımsız, minimum izinli uygulama. **Android** ve **Windows** için.

> **Resmi olmayan, bağımsız bir projedir; Kozmos veya amfinin üreticisiyle bağlantısı yoktur.** "Kozmos" ve "Blaze" ilgili sahiplerinin adlarıdır, yalnızca uyumlu cihazı belirtmek için kullanılır. Resmi uygulamanın koduyla ilgisi yoktur; yalnızca amfinin Bluetooth komut protokolü (`docs/protocol.md`) amfi üzerinde doğrulanarak kullanılmıştır.

## ⬇️ İndir ve kur

### 📱 Android
**[Blaze-30-Controller.apk dosyasını indir](https://github.com/burakbozb1/blaze-30-controller/releases/latest/download/Blaze-30-Controller.apk)** (Android 12+, ~20 MB) · [tüm sürümler](https://github.com/burakbozb1/blaze-30-controller/releases)

1. APK'yı telefonuna indir ve aç. Tarayıcın "bu kaynaktan kuruluma izin ver" derse izin ver (yalnızca bu bir kez).
2. Kurulumdan sonra uygulama **Bluetooth** ve **mikrofon** (yalnızca akort için) izni ister. Başka izin yoktur, internet erişimi yoktur.
3. Amfiyi aç, uygulamayı başlat; amfi otomatik bulunur.

APK Google Play dışından dağıtılır; kurmadan önce doğrulamak istersen imza parmak izi (SHA-256):
`f8095c24a34121c149bbf794ea5168ffa1b46a3b3616f5760bc162b578c2d2fb`
(Kurulu uygulamayı sonraki sürümle güncellemek için aynı imzayla yayımlanan APK'yı kullan.)

### 🖥️ Windows
**[Blaze-30-Controller-Windows.exe dosyasını indir](https://github.com/burakbozb1/blaze-30-controller/releases/latest/download/Blaze-30-Controller-Windows.exe)** (Windows 10/11, ~110 MB, kurulum gerektirmez)

1. İndirdiğin `.exe` dosyasına çift tıkla. Windows "SmartScreen" uyarısı verirse **Ek bilgi → Yine de çalıştır** de (uygulama kod imzalı değildir; aşağıdaki SHA-256 ile dosyayı doğrulayabilirsin).
2. Bilgisayarda **Bluetooth 4.0+ (LE)** olmalı. Masaüstü bilgisayarda yoksa bir USB Bluetooth 5.0 adaptörü yeterlidir.
3. Amfi aynı anda **yalnızca bir cihaza** bağlanır: telefondaki uygulamayı kapat, sonra Windows uygulamasını aç; amfi otomatik bulunur. Dokunmatik ekran ve fare ikisi de çalışır.
4. Uygulama yalnızca Bluetooth ve (akort için) mikrofon kullanır; ağ erişimi yoktur.

SHA-256: `98d16b9e0f284397c53677deb1b93385a9d605231f674c98f3a35a74ee9e9172`

Doğrulamak için PowerShell: `Get-FileHash .\Blaze-30-Controller-Windows.exe -Algorithm SHA256`

## Özellikler (Android ve Windows aynı)
Ton geçişi (Clean, Acoustic, Drive, Distortion, Metal), gain, ana ses ve **müzik (Bluetooth/AUX) sesi** (backing track seviyesi), 7 bantlı EQ (SIFIRLA ile), efekt, delay ve reverb (güç düğmeleriyle aç/kapat), **amfideki potlarda yapılan değişikliklerin uygulamaya yansıması**, ve mikrofonlu **akort**. Preset ve amfi simülasyonu sonraki fazlar (`docs/backlog.md`).

Arayüz: üstte marka, durum LED'i, ANA SES (gitar) ve MÜZİK (Bluetooth/AUX) potları; **AMP | EQ | AKORT** sekmeleri; altta tüm sekmelerde sabit pedal sırası. Pota çift dokunuş/çift tıklama ile ince ayar penceresi açılır, dikey sürükleme ile ayar yapılır. Soldan açılan panel (veya logoya dokunma) uygulamayı tanıtır. Tüm görseller kodla çizilir.

## Gizlilik ve izinler
- **Android:** `BLUETOOTH_SCAN` (konum için kullanılmaz, `neverForLocation`), `BLUETOOTH_CONNECT` ve yalnızca Akort için `RECORD_AUDIO`. İnternet, rehber, SMS, konum, depolama, analitik, reklam yoktur. İzin kümesi bir birim testiyle denetlenir (`ManifestPermissionsTest`).
- **Windows:** yalnızca Bluetooth ve (Akort sekmesi açıkken) mikrofon. Ağ isteği yapmaz (arayüz testleri bunu doğrular), sayfa içeriği `default-src 'self'` ile sınırlıdır, yalnızca geliştiricinin LinkedIn sayfası tarayıcıda açılabilir.
- Mikrofon sesi cihazda işlenir; kaydedilmez, saklanmaz, gönderilmez. Mikrofon yalnızca Akort ekranı açıkken çalışır.
- Amfinin fabrika sıfırlama komutu uygulamada hiçbir koşulda gönderilemez.

## Geliştirme

### Android (`app/`)
Proje yolunda `ü` gibi ASCII dışı karakter varsa Android derleme araçları takma sürücü ister:

    subst X: "<proje klasörü>"
    cd /d X:\
    gradlew.bat :app:testDebugUnitTest :app:assembleDebug

`tools/` (SDK, Gradle, jadx) git'e girmez; `local.properties` içinde `sdk.dir=X\:/tools/android-sdk` olmalı. Çıktı: `app/build/outputs/apk/debug/app-debug.apk`. Telefona kurmak için USB/kablosuz hata ayıklama açıp `adb install -r app-debug.apk`.

İmzalı APK: `BLAZE_RELEASE_PROPS` ortam değişkeni, `storeFile/storePassword/keyAlias/keyPassword` içeren (repo dışındaki) bir `.properties` dosyasını göstermelidir; sonra `./gradlew assembleRelease`. Anahtar dosyası repoya girmez.

### Windows (`windows/`)
TypeScript + Electron (Web Bluetooth). Çekirdek (`windows/src/core`) Android Kotlin çekirdeğinin birebir portudur; testleri aynı vektörleri kullanır.

    cd windows
    npm install
    npm test            # birim testleri (Vitest)
    npm run test:ui     # arayüz testleri (Playwright + Electron, sahte amfi ile)
    npm run dist        # taşınabilir exe -> windows/release/

Proje yolunda ASCII dışı karakter varsa `subst X:` ile çalıştırın. Bazı IDE'ler `ELECTRON_RUN_AS_NODE` değişkenini ayarlar; Electron'u başlatmadan önce kaldırın. Gerçek amfi testi Bluetooth donanımı gerektirir (testler sahte bağlantıyla çalışır).

## Lisans
MIT, bkz. `LICENSE`.
