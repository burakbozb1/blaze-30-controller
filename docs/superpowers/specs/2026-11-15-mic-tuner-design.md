# Mikrofonlu akort cihazı — kısa tasarım

Tarih: 2026-11-15 · Durum: kullanıcı onayladı (sohbette, "Onaylıyorum.")

## Amaç ve neden
Kullanıcı uygulamada bir akort özelliği istiyor. Amfinin kendi akort/perde bilgisini vermediği donanımda doğrulandı
(akort komutuna yanıt yok, kullanılmayan `FFF1` kanalı boş, klasik Bluetooth'ta yalnızca ses alıcısı; bkz. `docs/protocol.md`).
Gitar girişindeki sinyal telefona ulaşmıyor; kablo istenmiyor. Bu yüzden akort **telefon mikrofonuyla** yapılır.

## Hedefler
- Ana ekrandan açılan "Akort" ekranı: en yakın nota adı ve oktav (örn. `E2`), cent sapması (±50), frekans (Hz).
- Kromatik algılama: tel seçmeye gerek yok; Drop D, yarım ton aşağı vb. akortlar kendiliğinden çalışır.
- ±5 cent içinde "akortlu" (yeşil). Standart akorta yakınsa en yakın tel ipucu (6..1).
- Çevre gürültüsünü akort sanmama: sessizlik/gürültüde "bir tel çal" yazar, rastgele nota göstermez.
- Alçak E (82,41 Hz) dahil doğru oktav; tek karelik hatalarda ekran zıplamaz (medyan yumuşatma).
- Mikrofon yalnızca Akort ekranı görünürken ve uygulama ön plandayken açık; çıkınca/arka plana gidince kapanır.

## Hedef dışı
Transpoze, referans frekansı (A4) ayarı (A4 = 440 Hz sabit), akort seçimi/preset, ses kaydı, amfiyle etkileşim (Bluetooth'tan bağımsız).

## Anti-hedefler
- Ses telefondan çıkmaz, kaydedilmez, saklanmaz (uygulamada internet izni yok, ve eklenmez).
- Başka hiçbir izin eklenmez (yalnızca `RECORD_AUDIO`; Bluetooth izinleri aynen kalır).
- Mikrofon kapalıyken/izin yokken çökme veya sessiz başarısızlık yok: kullanıcıya net durum gösterilir.

## Kısıtlar
- Android 12+ (minSdk 31), hedef cihaz güncel bir telefon. Örnekleme 44,1 kHz, mono, 16 bit; 4096 örneklik kareler (~93 ms).
- Perde algılama saf Kotlin (YIN + parabolik ara değerleme), birim testi yapay sinyallerle.
- Pratik not (ekranda gösterilir): Clean tonda, düşük sesle, telefon amfiye yakınken en iyi sonuç; distortion'da algılama zorlaşabilir.

## Yaklaşımlar
1. **Seçilen: telefon mikrofonu + YIN.** Amfi bağlantısından bağımsız, kromatik, alternatif akortlara hazır. Eksi: ortam gürültüsü/distortion.
2. Amfinin kendi akort modu: ruled out (donanımda çalışmıyor).
3. Kablo/USB ses: kullanıcı istemedi.

## Benim kararlarım
- Algılama aralığı 55–1100 Hz; sessizlik eşiği tam ölçeğin ~%1'i (RMS); YIN eşiği 0,15.
- Ekran son okumayı 5 kare (≈0,5 s) tutar, sonra "bir tel çal". Medyan penceresi 5 okuma.
- Nota adları diyez ile (C#, D#, F#, G#, A#). Tel ipucu yalnızca standart tele ±2,5 yarım ton yakınken.
- Mikrofon yaşam döngüsü: ekran açık ve ön plan (STARTED) iken dinler; ekran/uygulama durunca `AudioRecord` serbest bırakılır.

## Açık riskler
- Gerçek gitar + oda + amfi hoparlöründe doğruluk yalnızca telefonda denenerek anlaşılır; eşikler gerekirse ayarlanır.
- Mikrofonu başka bir uygulama tutuyorsa başlatma başarısız olabilir: "Mikrofon kullanılamıyor" durumu gösterilir.
