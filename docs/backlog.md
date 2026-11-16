# Yapılacaklar listesi (backlog)

Son güncelleme: 2026-11-15. Sıra kesin değildir; her madde başlarken ayrı bir tasarım/plan alır.

## Planlanan özellikler
- **Transpoze** — akort ekranı bitti (mikrofonlu, kromatik); transpoze ve A4 referansı ayarı sonraya bırakıldı.
- ~~Arayüz tasarımı yenilemesi~~ — yapıldı (pedalboard teması, 2026-11-15); telefonda kullanıcı onayı ve ince ayar bekliyor.
- **Preset kaydet/yükle** — preset = `AmpState`'in kaydedilmiş hali (amfi durumu artık uygulamada birebir bulunuyor).
- **Amfi simülasyonu** — preset'in üzerine kurulur.

## Ertelenen küçük bulgular (son inceleme)
- Android 12/12L'de ardışık bildirim kaybı olasılığı (`BleAmpTransport`, eski `onCharacteristicChanged`).
- Senkron cevapsız kalırsa yeniden denenmiyor; yarım döküm onarılmıyor.
- Yavaş gelen döküm, kullanıcının yeni yazdığı değeri eskisine çevirebilir.
- `refresh()` ekran dönünce de çalışıyor (fazladan senkron).
- `docs/protocol.md` içindeki eski "Notify frames" bölümü yeni bölümle çelişiyor.
- Sahte test amfisi gerçek taşma davranışını (`DROP_OLDEST`) simüle etmiyor.
- İptal edilen tarama "Bluetooth kapalı" durumunu ezebilir; 30 sn'de 5 tarama sınırı; ilk bağlantı hatası metni; tarama günlüğü yalnızca eşleşen cihazları yazmalı; fabrika-reset testi tüm komutları kapsamalı.
- Doğrulanmamış canlı çerçeveler: EQ / efekt / reverb tipi değişimleri (yalnızca tam dökümle tazelenir).

## Ertelenen küçük bulgular (akort incelemesi)
- Perde algılama ana iş parçacığında çalışıyor (yaklaşık 1,6 M işlem, saniyede ~11 kez); yavaş cihazlarda `withContext(Dispatchers.Default)` ile taşınmalı.
- Aralık dışı saf bir ton (3 kHz bip/ıslık) YIN tarafından bir alt katı olarak (~1 kHz) bildirilebilir; `tau < tauMin` için dip kontrolü eklenip `null` dönülmeli.
- Şebeke uğultusu yalnızca saf 50 Hz sinüsle test edildi; 60 Hz ve 50+100 Hz karışımı eşik üstündeyse nota gösterebilir.
- `MicAudioSource`: UNPROCESSED ile başlatılamayan `AudioRecord` nesnesi `release()` edilmiyor; `PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED` kontrol edilmeli.
- Ekran dönünce yeni `TunerController` oluşuyor, ~93 ms boyunca iki mikrofon açılabilir (denetleyici ViewModel'de tutulup çalıştırmalar sıraya alınmalı).
- Hata durumunda uygulama içinde "tekrar dene" yok (ekrandan çıkıp girmek gerekiyor).
- `micNeedsSettings` / `micAsked` kayıtlı durumda değil; izin penceresi dışarı dokunularak kapatılınca yanlışlıkla "ayarlardan ver" gösterebilir (Bluetooth akışında da aynı kalıp var).
- Akort ekranına geri dönüldüğünde son okuma/hata bir kare görünebilir (`run()` iptalinde `ui` sıfırlanmalı).
- Plandaki `dumpsys audio` ile mikrofon bırakıldı kontrolü ölçülmedi (kullanıcı bildirdi); Görev 1 commit'i (`20bdc58`) derlenmiyor (ledger'a yazıldı).

## Ertelenen küçük bulgular (arayüz yenilemesi)
- `adb shell input tap` bazı cihazlarda uygulamaya dokunuş iletmiyor; otomatik uçtan uca arayüz testi için başka yol gerekir (mikrofon bırakma ve ekran döndürme kontrolü elle yapıldı).
- Delay/Reverb "açık" durumu yalnızca oturum içi hatırlanır (uygulama kapanınca varsayılan 20).
- Değer penceresinde −/+ basılı tutunca tekrar yok.
- Pot/fader bırakıldıktan hemen sonra gecikmiş ara yazma gelirse kısa geri sıçrama.
- Mikrofon izni reddedilince ekran dönünce izin penceresi yeniden çıkabilir (`micAsked` kayıtlı değil).
- Bağlantı kartı altındaki soluk kontrollere dokunuşu/TalkBack'i engellemiyor ve kaydırılamıyor.
- Erişilebilirlik ayrıntıları: pot etiketi açıklamadan sonra okunuyor, SIFIRLA butonunda rol yok; `EqFader` içinde eski `span` yakalaması; `KnobFace` çizimde küçük tahsisler.
- Sekme/çip yazıları en büyük yazı ölçeklerinde (≥1,5×) kırpılabilir (pedal etiketleri sabit boyutlu yapıldı).
- Güç düğmesi: ANA potu 0'a sürüklendikten sonra düğmeye basınca geri gelen seviye sürüklemede geçilen son sıfırdan büyük değer (1–3 olabilir); "sürükleme öncesi seviye" olarak değiştirmek kullanıcı kararı.

## Windows sürümü (2026-11-15)
- Gerçek amfi + Bluetooth ile elle test kullanıcıda (geliştirme PC'sinde Bluetooth yok): bağlanma, sync, pedallar, potlar, akort mikrofonu, dokunmatik.
- Windows'ta imzasız `.exe` SmartScreen uyarısı verir; kod imzalama sertifikası (ücretli) ileride.
- Electron'un Web Bluetooth seçicisi gerçek donanımda doğrulanmadı; olmazsa yerleşik seçici penceresine dönülür.
- Adaptör açık/kapalı olayı (`availabilitychanged`) dinlenebilir; şimdilik yalnızca açılışta ve elle "Tekrar ara" ile kontrol edilir.
