# Blaze 30 Controller — Windows sürümü (tasarım)

## Amaç ve neden
Android uygulamasının aynısını Windows'ta da kullanabilmek. Kullanıcı bazen masaüstü bilgisayarla, bazen dokunmatik
laptopla çalışıyor; amfiyi telefon yerine bilgisayardan yönetmek istiyor. Başarı ölçütü: laptopta ve (USB Bluetooth
adaptörlü) masaüstünde, hem fare hem dokunmatikle, Android'dekiyle aynı kontroller ve aynı görünüm.

## Kapsam
**Dahil (Android ile eşdeğer):** amfi bulma/bağlanma (sınırlı tarama + manuel yeniden dene), ton (5 pedal), gain, ana ses,
7 bantlı EQ + SIFIRLA, efekt, delay/reverb (panel + güç düğmeleri), amfi→uygulama senkronu, mikrofonlu akort (AKORT),
çift dokun/çift tıkla değer penceresi, soldan açılan hakkında paneli (LinkedIn düğmesi), onaylı kırmızı/siyah "pedalboard" görünümü.

**Dışında:** preset, amfi simülasyonu, akort transpoze, AUX sesi (Android'de de yok); mobil/Mac/Linux; Microsoft Store.

**Anti-hedef:** Android'deki gizlilik sözünü bozmak. Ses ve veri bilgisayardan çıkmaz; uygulama ağ isteği yapmaz
(yalnızca LinkedIn düğmesi tarayıcıyı açar). Fabrika sıfırlama çerçevesi (`FE0D01010000000000FF`) asla gönderilmez.

## Kısıtlar
- Amfi aynı anda tek BLE bağlantısı kabul eder: telefon ve bilgisayar aynı anda bağlanamaz (kullanıcıya belirtilir).
- Her yeni bağlantıda Sync çerçevesi (`FE0DAB413442000000FF`) gönderilir; protokol `docs/protocol.md`'deki gibi.
- Bu geliştirme makinesinde Bluetooth donanımı yok → gerçek amfi testi kullanıcının laptopunda yapılır; ben her şeyi sahte
  taşıyıcıyla (fake transport) test ederim.
- Repo public: kişisel/cihaz bilgisi, anahtar, şifre repoya girmez.

## Yaklaşımlar
| | Artı | Eksi |
|---|---|---|
| A. Kotlin Compose Desktop | Android kodunu yeniden kullanır | Windows BLE kütüphanesi yok → C# köprü; dokunmatik zayıf |
| **B. Electron + TypeScript (seçildi)** | Web Bluetooth Windows'ta hazır; dokunmatik/fare doğal; onaylı HTML tasarım yeniden kullanılır; tek `.exe` | ~100 MB paket; protokol/akort TS'e yeniden yazılır |
| C. WinUI 3 / .NET | Yerel görünüm, BLE hazır | Arayüz ve akort sıfırdan; paketleme/imzalama zahmetli |

Kullanıcı "nasıl"ı bana bıraktı (sonuç odaklı konuştu): **B benim kararım**, gerekçe yukarıda.

## Tasarım (B)
- **Konum:** aynı repoda `windows/` klasörü. Saf TypeScript çekirdek + ince Electron kabuğu.
- **Çekirdek (`windows/src/core/`, Electron'dan bağımsız, birim testli):**
  `protocol` (kodlama/çözme, `docs/protocol.md` ve Android codec testlerindeki gerçek çerçevelerle aynı vektörler),
  `AmpState`, `AmpRepository` (sıralı kuyruk, otomatik yeniden bağlanma, okuma-salt yansıtma, Sync), `PowerMemory`,
  `SliderModel/Throttle` (60 ms), `tuner` (YIN pitch + NoteMath, Android ile aynı eşikler, en az 3 tespit).
- **Taşıyıcı arayüzü `AmpTransport`:** `WebBluetoothTransport` (gerçek) ve `FakeTransport` (test). Servis `fff0`, yazma `fff3`, bildirim `fff2`.
- **Arayüz (`windows/src/ui/`):** HTML/CSS/TS, onaylı `5-final-all-screens.html` görünümü; pot ve fader'lar Pointer Events ile
  (fare + dokunmatik aynı kod); dikey sürükle = ince ayar, çift dokun/tıkla = değer penceresi, TAMAM. Pencere yeniden boyutlanır;
  geniş pencerede (yatay) sekmeler başlık satırında, dar pencerede altında (Android yatay düzeni gibi). Dokunma hedefleri ≥ 44 px.
  Alt çubukta 5 pedal sabit. Soldan kaydırma/logoya tıklama → hakkında paneli. Dokunmatikte tarayıcı kaydırma/yakınlaştırma kapalı.
- **Electron kabuğu (`windows/electron/`):** `select-bluetooth-device` olayında adı `BLAZE-30` ile başlayan cihazı otomatik seçer
  (yoksa iptal eder; çoklu bulunursa liste), mikrofon izni yalnızca uygulamanın kendi penceresine; `contextIsolation`, `nodeIntegration` kapalı,
  ağ erişimi yok (CSP `default-src 'self'`), harici bağlantılar `shell.openExternal` ile yalnızca LinkedIn URL'sine.
- **Akort:** `getUserMedia` (yankı/gürültü bastırma KAPALI), AudioWorklet/ScriptProcessor ile 4096 örnek kare, yalnızca AKORT sekmesi açıkken
  mikrofon açık; sekmeden çıkınca akış durdurulur.
- **Paketleme:** `electron-builder` ile taşınabilir `Blaze-30-Controller-Windows.exe` (kurulum gerektirmez) → GitHub Release'e
  (Android APK'nın yanına, aynı sürüm numarası ailesi). İmzasız olduğundan SmartScreen uyarısı çıkabilir; README bunu açıklar ve SHA-256 verir.

## Test stratejisi
- Birim testler (vitest): protokol vektörleri, repository (kuyruk, yeniden bağlanma, sync, yansıtma), PowerMemory, slider, tuner (sentetik sinüs dalgaları, bilinen notalar).
- Arayüz testleri (Playwright + Electron): pot/fader sürükleme, çift tıklama penceresi, pedal, sekmeler, dar/geniş düzen, ekran görüntüleri; sahte taşıyıcıyla.
- Kullanıcı elle: laptopta gerçek amfi + gitar + dokunmatik (Bluetooth burada yok).

## Yapıcıya bırakılanlar
Klasör içi dosya adları, CSS ayrıntıları, Electron/Vite sürümleri, ikon dönüşümü (`Logo.png` → `.ico`), AudioWorklet vs ScriptProcessor seçimi.

## Riskler
- Windows Web Bluetooth'ta cihaz seçici/yeniden bağlanma davranışı gerçek donanımsız doğrulanamaz → kullanıcı testi; geri dönüş planı: yerleşik seçici penceresi.
- Bazı Bluetooth adaptörleri (özellikle ucuz dongle) BLE'yi zayıf destekler; README'de "Bluetooth 4.0+/5.0 LE destekli adaptör" önerilir.
