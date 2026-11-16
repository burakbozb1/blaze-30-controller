# Blaze 30 Controller — proje bağlamı (Claude için)

Kozmos Blaze 30 gitar amfisini Bluetooth LE ile kontrol eden, **bağımsız ve minimum izinli** uygulama. İki platform:
**Android** (`app/`, Kotlin + Jetpack Compose) ve **Windows** (`windows/`, Electron + TypeScript, Web Bluetooth).
Repo **public**: <https://github.com/burakbozb1/blaze-30-controller> (MIT). Satıcının resmi (ODAK) uygulamasıyla ilgisi yok.
Sahibi Türkçe konuşur: **yanıtlar Türkçe**, kısa ve sonuç odaklı; maliyete duyarlı.

Bu dosya yeni bir geliştirme işine başlarken keşfi baştan yapmamak içindir. Ayrıntılar `docs/` altında; önce ilgili belgeyi oku.

## Durum (2026-11-16)
- Android 0.2.0 (versionCode 3), Windows 0.3.0. GitHub release `v0.3.0`: `Blaze-30-Controller.apk` + `Blaze-30-Controller-Windows.exe`. Son eklenen: başlıkta **MÜZİK (AUX) sesi** potu.
- Her ikisi: ton (5 pedal), gain, ana ses, 7 bant EQ + SIFIRLA, efekt, delay/reverb (güç düğmesi = master seviye 0), amfi→uygulama senkronu,
  mikrofonlu akort, soldan açılan hakkında paneli (LinkedIn düğmesi). Android gerçek amfide doğrulandı; **Windows gerçek amfide kullanıcı tarafından denendi, çalışıyor.**
- Yapılacaklar adayları: `docs/backlog.md` (preset, amfi simülasyonu, akort transpoze, küçük inceleme bulguları).

## Değişmez kurallar
- **İzinler (Android):** yalnızca `BLUETOOTH_SCAN` (neverForLocation), `BLUETOOTH_CONNECT`, `RECORD_AUDIO`. **INTERNET yok.** `ManifestPermissionsTest` denetler. Windows'ta da ağ isteği yok (CSP `default-src 'self'`, UI testi doğrular).
- Mikrofon sesi cihazdan çıkmaz; mikrofon yalnızca AKORT sekmesi açıkken çalışır.
- **Fabrika sıfırlama çerçevesi (`FE0D01010000000000FF`) hiçbir koşulda gönderilemez** (kodlayıcıda temsil edilemez; test var). Doğrulanmamış komutu ürün koduna koyma.
- Amfi aynı anda **tek BLE bağlantısı** kabul eder (telefon ve PC aynı anda bağlanamaz).
- Her yeni bağlantıda **Sync çerçevesi** (`FE0DAB413442000000FF`) şart; amfi cevap olarak durum dökümü (`BB`,`BC`,`C0`) yollar. Yeniden bağlanınca uygulamanın eski değerleri **geri yazılmaz** (amfideki pot değişikliklerini ezmemek için).
- Repo **public ve tek commit geçmişi** ister (aşağıya bak). Kişisel/cihaz bilgisi, anahtar, şifre, IP/MAC, seri no **asla** repoya girmez.

## Protokol (özet; tamamı `docs/protocol.md`)
Servis `0000fff0-…`, yazma `fff3` (default write type), bildirim `fff2` (16 bayt `FE 0F … FF`). Yazma çerçevesi 10 bayt `FE 0D G I S V 00 00 00 FF`.
Aralıklar: ses 0–50, gain 0–30, master delay/reverb 0–50, efekt sayfası potları 0–20, EQ 0–20 (düz = 10). Cihaz adı `BLAZE-30 APP`.
Ana ses potu GUI VOL = uygulamadaki "ANA SES" (`02 01 00`). Bluetooth/müzik (AUX) sesi = başlıktaki "MÜZİK" potu (`FE0D040100VV…`, canlı bildirim `04 01 00 V`, açılış dökümü `BF[3]`; 2026-11-16'da doğrulandı). Fiziksel master pot ikisini birlikte oynatır.

## Mimari
Katmanlar (Android ve Windows çekirdeği aynı yapıda; **Windows çekirdeği Kotlin çekirdeğinin birebir portu**, aynı test vektörleri):
`transport` (ham bayt bağlantısı) → `protocol` (AmpCommand + encode/decode) → `amp` (AmpState + AmpRepository: sıralı kuyruk, otomatik yeniden bağlanma, salt-okunur yansıtma, `refresh()`) → UI ViewModel/AppController (durum makinesi: tarama pencereleri, yeniden dene) → ekranlar.
- Android: `app/src/main/java/dev/blaze/controller/{ble,protocol,amp,tuner,ui}`; ana ekran `ui/screens/MainScreen.kt`, bileşenler `ui/components/` (Canvas ile çizilen potlar, pedaller), tema `ui/theme/Pedalboard.kt`. Testler `app/src/test` (birim, ~118) ve `app/src/androidTest` (Compose, cihazda, ~58).
- Windows: `windows/src/core` (saf TS, Vitest), `src/platform` (WebBluetoothTransport, MicAudioSource), `src/ui` (DOM + CSS, `screens.ts`, `components.ts`), `src/shell/guards.ts` (güvenlik kararları), `electron/` (main/preload, sertleştirilmiş), `tests-ui/` (Playwright + Electron, `?fake=1` sahte amfi). ~140 birim + 14 UI testi.
- Görünüm: onaylı "pedalboard" tasarımı (kırmızı/siyah fırçalanmış zemin). Referans: `docs/superpowers/specs/ui-redesign-mockups/5-final-all-screens.html`. Geniş Windows penceresinde AMP sekmesi 3 eşit tam genişlik satır, dar pencerede dikey düzen; yatay Android'de sekmeler başlık satırında.
- Belgeler: `docs/superpowers/specs/*-design.md` (kararlar), `docs/superpowers/plans/*.md` (uygulama planları), `docs/backlog.md`.

## Komutlar
- Android (Windows'ta, proje yolunda `ü` var → `subst X: "<proje klasörü>"` sonra `cd /x` veya `X:\`): `./gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug`. `tools/` (SDK, Gradle, jadx) git dışı; `local.properties` → `sdk.dir=X\:/tools/android-sdk`.
- Cihaz testleri: `:app:connectedDebugAndroidTest`. **Bitince Gradle test uygulamasını telefondan siler → sonra `:app:installDebug` ile yeniden kur** (kurmayı testten ÖNCE yapma).
- Android release APK: `BLAZE_RELEASE_PROPS` (repo dışı `.properties`: storeFile/storePassword/keyAlias/keyPassword) sonra `assembleRelease`. İmza anahtarı repo dışında bir klasörde (kullanıcı hafızasına bak); kaybolursa güncellemeler eski kurulumun üstüne kurulamaz.
- Windows: `cd windows && npm test` (birim), `npm run typecheck`, `npm run test:ui` (önce build yapar), `npm run dist` (taşınabilir exe → `windows/release/Blaze-30-Controller-Windows.exe`, imzasız).

## Tuzaklar (zaman kaybettiren şeyler)
- **`ELECTRON_RUN_AS_NODE=1`** IDE kabuğunda ayarlı olabilir → Electron düz Node gibi açılır ("app undefined"). Başlatmadan önce kaldır (Playwright harness'i kaldırır).
- Bash araç çağrılarında heredoc içinde `'` veya `` ` `` bozuk çalışabilir → dosyaları Write/Edit aracıyla yaz, Python için `<<'PYEOF'` kullan.
- Adb: telefon **kablosuz hata ayıklama** ile bağlanır (eşleştirme kodu/port her seferinde değişir, kullanıcıdan istenir); bazı cihazlarda `adb shell input tap` uygulamaya ulaşmaz → arayüz kontrolü Compose testleri ve kullanıcının elle denemesiyle. `MSYS_NO_PATHCONV=1` kullan. Ekran görüntüsü için `ScreensShotTest`/`GalleryShotTest`.
- Web Bluetooth ilk `requestDevice` için kullanıcı hareketi ister: Electron ana süreç ilk taramayı `executeJavaScript(…, true)` ile başlatır; seçici 30 sn açık tutulur ve sonraki tarama pencereleri aynı bekleyen isteği paylaşır (`WebBluetoothTransport.pending`).
- Bu PC'de Bluetooth donanımı yok → Windows tarafı sahte taşıyıcıyla test edilir; gerçek amfi testi kullanıcının laptopunda yapılır.
- İki PC arası dosya (bulut yerine yerel ağ tercih edilir): istenirse `python -m http.server 8080 --bind <LAN IP> --directory <klasör>` ile geçici web paylaşımı aç, iş bitince kapat; klasörü repo dışında tut.

## Çalışma biçimi (kullanıcı tercihleri)
- Yeni özellik: `superpowers:brainstorming` → kısa tasarım belgesi (kullanıcı onayı) → `writing-plans` → **`executing-plans` (native/inline)**; subagent yalnızca **iş sonunda tek taze inceleme** (opus) için. Her görev TDD (önce başarısız test), `task-start`/`task-done` betikleri ve `.superpowers/sdd/<plan>/progress.md` defteri (git dışı).
- Commit mesajı sonuna: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Commit/push yalnızca istenince. **Push öncesi tara ve rapor sun, onay al.**
- **Public geçmiş tek commit:** onaylı pushta tüm çalışma tek commit'e squash edilip `--force-with-lease` ile gönderilir (tam geçmiş yerelde `dev-history-full` dalında kalır, asla push edilmez); eski sürümlerin etiketleri de silinir ki eski commit'ler erişilebilir kalmasın. Yazar adı **"Burak Boz"** (yerel git `user.name` buna ayarlı; "BOZ" değil).
- Tarama: telefon modeli/seri, amfi MAC, yerel yollar, IP/ağ adları, e-posta (yalnızca commit yazarında kabul), şifre/token/anahtar, `.apk/.exe/.jks` izlenmiyor mu.
- Yayın: APK (imzalı) + exe → GitHub release (`gh release create`), README'deki SHA-256 ve indirme bağlantıları (`releases/latest/download/<dosya>`) güncel tutulur.
- Yeni arayüz işi: görsel doğrulama için ekran görüntüsü al ve bak; kullanıcı ekran görüntüsüyle geri bildirim verir.

## Kullanıcıya ait (repoda tutulmayan) bilgiler
Kişisel ayrıntılar (cihaz, hesap, yerel yollar, anahtar konumu) Claude'un kullanıcı hafızasındadır; bu dosyaya yazılmaz.
