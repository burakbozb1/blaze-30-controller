# Arayüz yenilemesi — "Pedalboard" tasarımı

Tarih: 2026-11-15 · Durum: kullanıcı görsel yönü onayladı (taslak turları sohbette); yazılı belge onay bekliyor.
Görsel kaynaklar: `docs/superpowers/specs/ui-redesign-mockups/` (tarayıcıda açılan statik HTML; **numara büyüdükçe önceliklidir**:
`4` güç düğmesi, `3` pedal/fırçalanmış zemin/EQ sıfırlama, `1` sekme yapısı ve AKORT sekmesi, `2` yalnızca Gain açılır penceresi için).

## Amaç ve neden
Mevcut arayüz işlevsel ama: Compose açık teması koyu pencere üstünde çalıştığı için başlık/durum yazıları siyah çıkıp okunmuyor (hata),
beş ton aynı düz buton, kimlik yok, akort/EQ butonları aşağıda gömülü. Kullanıcı uygulamayı **evde çalışırken** kullanıyor, gitarı
**kırmızı-siyah**; fiziksel bir **pedalboard/amfi paneli** hissi istiyor: gerçekçi pedal düğmeleri, parlak yaylı potlar, LED, fırçalanmış
siyah zemin. Konsept görseli yalnızca fikir; düğmeler gerçek telefonda küçük kalmamalı, düzen **dikey** olmalı.

## Hedefler
1. **Sabit üst alan** (tüm sekmelerde): marka "KOZMOS BLAZE 30", altında yeşil LED + "BAĞLI", sağda **ANA SES** potu.
2. **Sekme çubuğu**: `AMP | EQ | AKORT` (aktif sekme kırmızı metin + kırmızı parlayan alt çizgi).
3. **Sabit alt pedal sırası** (tüm sekmelerde, kaymaz): CLEAN · ACOUSTIC · DRIVE · DISTORTION · METAL. Tek dokunuşla amfi tipi.
   Aktif pedal: kalın kırmızı parlayan halka + kırmızı parlak etiket. Dokunma alanı sütunun tamamı (≥ 64 dp genişlik, ≥ 96 dp yükseklik).
4. **AMP sekmesi** (kaydırmadan tek ekran): GAIN potu (büyük) + efekt seçimi (OFF/FLANGER/CHORUS/PHASER/WAH/VIBRATO); DELAY paneli (güç düğmesi
   + 4 pot); REVERB paneli (güç düğmesi + PLATE/ROOM/SPRING + 4 pot).
5. **EQ sekmesi**: 7 büyük dikey fader (100…6.4k), üstünde değer, altında tam genişlik **SIFIRLA** butonu (7 bandın hepsi merkeze = 10).
6. **AKORT sekmesi**: mevcut mikrofonlu akort (sabit boyutlu aktif/pasif panel) yeni görsel dille; ayrı "Akort" ana ekran butonu kalkar.
7. **Pot etkileşimi**: pot üzerinde dikey sürükle (yukarı = artır) → ince ayar; **çift dokunuş** → onaylanan açılır pencere (başlık, büyük değer,
   kırmızı kaydırıcı, −/+ ile ±1, TAMAM). Aynı pencere her pot için (Gain, Ana Ses, Delay/Reverb potları).
8. **Delay ve Reverb tek dokunuşla aç/kapa**: güç düğmesi; kapalıyken paneldeki potlar soluk (hâlâ ayarlanabilir).
9. **Görsel dil**: fırçalanmış siyah zemin (yatay fırça dokusu), yarı saydam koyu paneller, kırmızı vurgu, gerçekçi pedal/pot/LED/güç düğmesi
   (hepsi çizim; bitmap dosyası yok). Güç düğmesinde **AÇIK/KAPALI yazısı yok**. Pot yayı potu eş merkezli tam sarar (yay, kapak, gösterge çizgisi aynı değerden).
10. Sadece koyu tema (açık tema yok), kontrast ≥ 4.5:1, TalkBack ile pot/fader ayarlanabilir.

## Hedef dışı
Transpoze, A4 ayarı, preset, amfi simülasyonu, yatay/tablet düzeni, açık tema, protokol/Bluetooth/sync/akort algoritması değişikliği (bunlara dokunulmaz).
EQ fader'larında çift-dokunuş penceresi (yalnızca potlarda).

## Anti-hedefler
- Mevcut işlevlerden hiçbiri kaybolmaz (ton, gain, ses, EQ, efekt, reverb tipi/seviye/ton/süre, delay seviye/feedback/süre, master delay/reverb, akort, izin ve bağlantı akışları).
- Amfi → uygulama senkronu bozulmaz: ekrandaki her değer `AmpState`'ten gelir, sürükleme sırasında amfiden gelen değer parmağı bozmaz (mevcut `SliderModel` mantığı korunur).
- Görsel süs doğruluğun önüne geçmez: değer metni her zaman okunur kalır; animasyon yok denecek kadar az.

## Kısıtlar
- Kotlin + Jetpack Compose, minSdk 31, hedef ~393 dp genişlikli telefon. İzin kümesi değişmez (BT_SCAN, BT_CONNECT, RECORD_AUDIO).
- Yeni bitmap/yazı tipi dosyası eklenmez; doku, pot, pedal, LED, simge `Canvas`/`Brush` ile çizilir (fırça dokusu bir kez üretilip önbelleğe alınır).
- Mevcut `HomeViewModel` ve `AmpRepository` korunur; yalnızca küçük eklemeler.

## Yaklaşımlar
1. **Seçilen: sıfırdan yeni bileşen kütüphanesi (`Knob`, `PedalSwitch`, `PowerButton`, `Led`, `TabBar`, `ValuePopup`, `EqFader`, `ChipRow`) + üç sekme ekranı + sabit iskelet.**
   Mevcut ekranlar (`HomeScreen`, `SoundScreen`) yeni ekranlarla değiştirilir; eski mantık (`SliderModel`, `SliderThrottle`, `TunerController`) yeniden kullanılır.
2. Mevcut ekranları adım adım boyamak (tema + stil): hızlı ama pot/pedal/popup/sekme yapısı bunu karşılamıyor; reddedildi.
3. Hazır bitmap görseller (referans kırpmaları): düşük çözünürlük, her ekranda bulanık, boyutlandırma zor; reddedildi.

## Davranış ayrıntıları (benim kararlarım — kullanıcı itiraz edebilir)
- **Pot sürükleme**: tam aralık ≈ 220 dp dikey harekete eşit; yukarı = artır; sürükleme sırasında mevcut hız sınırı (≤ 1 komut/60 ms) + parmak kalkınca son değer garanti (`SliderThrottle`).
- **Güç düğmesi**: delay/reverb amfide ayrı bir açma bayrağına sahip değil. **Kapat** = ana seviyeyi (master delay / master reverb, 0–50) 0 yapmak ve önceki değeri hatırlamak;
  **aç** = hatırlanan değeri geri yazmak (hatırlanan 0 ise varsayılan 20). "Açık" = ana seviye > 0; amfide seviye elle 0 yapılırsa düğme kapalı görünür. Hatırlama yalnızca oturum içi.
- **EQ SIFIRLA**: 7 `SetEqBand(i, 10)`; fader'lar amfi durumuna göre hareket eder.
- **Bağlantı durumu**: LED yeşil+"BAĞLI" bağlıyken; sarı "ARANIYOR…/BAĞLANILIYOR…/YENİDEN BAĞLANILIYOR…"; kırmızı "BAĞLI DEĞİL". Bağlı değilken tüm kontroller soluk ve pasif;
  izin/Bluetooth/bulunamadı/cihaz seçici/yeniden bağlan akışları içerik alanında kart olarak çıkar (mevcut metin ve buton mantığı korunur).
- **AKORT sekmesi**: mikrofon yalnızca bu sekme seçiliyken ve uygulama ön plandayken açık; izin akışı sekme içinde (mevcut mantık).
- **Pedal sırası** AKORT sekmesinde de görünür (akort ederken Clean'e geçebilmek için).
- **Dokunma geri bildirimi**: pedal ve güç düğmesine hafif titreşim (haptic); yazılı ayar yok.
- **Etiket dili**: Türkçe (BAĞLI, ANA SES, SEVİYE, SÜRE, TON, AKORT, SIFIRLA, TAMAM); ton/efekt/reverb tip adları İngilizce kalır (amfi üzerinde de öyle).
- **Pot değer aralıkları**: Ana Ses 0–50, Gain 0–30, Master Delay/Reverb 0–50, diğer delay/reverb potları 0–20, EQ 0–20 (mevcut `Ranges`).

## Doğrulama yaklaşımı
- Mantık birim testi (JVM): pot sürükleme→değer eşlemesi ve sınırlar, yay açısı, güç aç/kapa durum makinesi (hatırlama, 0 durumu, amfi tarafı değişimi), EQ sıfırlama komut listesi, sekme/durum eşlemesi.
- Telefonda Compose testleri: bileşenlerin semantiği (rol, değer, durum açıklaması), pot çift dokunuşu pencereyi açar, pedal dokunuşu ton komutu üretir, sekmeler arası geçiş.
- **Görsel doğruluğu yalnızca kullanıcı telefonda onaylar**; her görsel adımda ekran görüntüsü alınır ve taslakla karşılaştırılır.

## Açık riskler
- Fırçalanmış doku ve parlama (blur/gölge) düşük seviyeli cihazlarda yavaşlatabilir; doku bir kez üretilir, parlama `Canvas` katmanı kullanır; test telefonunda ölçülür.
- "DISTORTION" etiketi pedal sütununda (~74 dp) sığmazsa yazı boyutu 9–10 sp arasında ayarlanır, kısaltma yapılmaz.
- Çift dokunuş ile sürükleme çakışabilir; sürükleme eşiği (~8 dp) aşılmadan çift dokunuş sayılır.

## Ek kararlar (okuyucu soruları sonrası)
Sohbette zaten cevaplananlar:
- **Açılır pencere canlıdır.** Kullanıcı "normal uygulamadaki ses açma/EQ gibi sağa sola çekerek" dedi: kaydırıcı ve −/+ değeri anında (mevcut hız sınırıyla) amfiye yazar; arkadaki pot da onu izler.
  TAMAM, geri tuşu ve pencere dışına dokunma yalnızca pencereyi kapatır, değer olduğu gibi kalır (iptal/geri alma yok).
- **Master delay/reverb 0 gerçekten sessizleştirir**: kullanıcı doğruladı ("master'ı kapattığımda diğerlerinin sesi gelmez"; master reverb de aynı ailede çalışıyor).
- **Testler**: telefon (kablosuz adb) ortamda hazır; Compose cihaz testleri plana dahil, her görsel adımda ekran görüntüsü alınır.

Benim kararlarım:
- **Bağlı değilken**: sekme çubuğu ve AKORT sekmesi çalışır (akort yalnızca mikrofon ister, Bluetooth gerektirmez). AMP ve EQ sekmeleri soluk/pasif kontrollerini gösterir,
  üstlerinde durum kartı (izin / Bluetooth kapalı / aranıyor / bulunamadı / cihaz seçici / yeniden bağlan) durur. Pedal sırası da soluk ve pasif.
- **Sığma**: AMP sekmesi ağırlıklı (weight) düzenle mevcut yüksekliğe uyar; 393 dp genişlikli telefonda kaydırma gerekmez. Küçük ekran veya büyük yazı ölçeğinde orta alan dikey kayabilir
  (pedal sırası ve üst alan sabit kalır); yazı ölçeği 1,0 dışında da bozulmadan çalışmalı, pot/pedal boyutları dp ile sabit.
- **Güç düğmesi ↔ ANA pot**: aynı denetimin iki yüzü. "Hatırlanan değer" = en son sıfırdan büyük ana seviye (elle, güç düğmesiyle veya amfiden gelen fark etmez); soluk görünüm `ana seviye == 0`'a bağlı.
- **Çift dokunuş**: Compose varsayılan çift-dokunuş süresi; ilk dokunuş bir şey yapmaz; dikey sürükleme eşiği dokunma toleransı kadar; AMP sekmesi kaydırmadığı için çakışma yok
  (kaydırma yedek durumunda pot üzerindeki dikey sürükleme potu ayarlar, kaydırmayı değil).
- **Pencere −/+**: ±1, basılı tutunca tekrar yok (kaydırıcı hızlı ayar için var).
- **Sekme ve mikrofon**: seçili sekme `rememberSaveable` ile döndürmede korunur, süreç ölümünde AMP'a döner. Mikrofon `AKORT sekmesi seçili && uygulama STARTED` iken açık;
  izin penceresi gibi yalnızca duraklatan katmanlar mikrofonu kapatmaz, uygulama arka plana gidince kapanır.
