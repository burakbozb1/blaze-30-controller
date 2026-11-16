# Amfi → uygulama senkronu — kısa tasarım

Tarih: 2026-11-15 · Durum: kullanıcı onayladı (sohbette, "Onaylıyorum. Başla")

## Amaç ve neden
Amfinin üzerinde potlarla/düğmelerle yapılan değişiklikler uygulamaya yansımıyor; uygulama yalnızca kendi gönderdiği
değerleri gösteriyor ve açılışta amfinin gerçek durumunu bilmiyor. Kullanıcı ekranın hep amfiyle aynı olmasını istiyor.
Master ses potu da bu kapsamda: gerçek trafikte pot, GUI VOL (`02 01 00`) ile AUX ses (`04 01 00`) çerçevelerini aynı değerle
(0-50) birlikte yolluyor. Yani pot = mevcut "Ses" kaydırıcısıyla aynı değer; ayrı bir master komutu gerekmiyor.

## Kanıt (gerçek trafik, 2026-11-15, `analysis/rx-capture-2026-11-15.log`, git'e girmez)
- Notify (`0xFFF2`) çerçeveleri 16 bayt: `FE 0F <tip/grup> … FF`.
- Sync sonrası tam döküm: `BB` (ses[3], amfi tipi[5], gain[7], EQ[8..14]), `BC` (efekt[3], master echo[13], master reverb[14]),
  `C0` (reverb tipi[3]/seviye[4]/ton[5]/süre[6], delay seviye[7]/feedback[8]/süre[9]), ayrıca `BD`,`BE`,`BF` (kullanılmıyor).
  Alan eşlemesi resmi uygulamanın ayrıştırıcısından okundu ve `BB`,`BC`,`C0` örnek çerçeveleriyle tutarlı.
- Canlı değişimler yazma çerçeveleriyle aynı grup/madde/alt: `02 01 00` ses, `02 03 02` gain, `02 03 00` amfi tipi,
  `02 02 00` master echo. Pot değişimlerinde bu çerçeveler saniyede onlarca kez gelir.
- `FE 0F 41 …` her saniye gelen kalp atışı (yok sayılır). Uygulamanın kendi yazdıklarını amfi yankılamıyor.
- Doğrulanmamış: canlı EQ/reverb/efekt değişim çerçeveleri (kullanıcı denemedi); bunlar bir sonraki tam dökümle tazelenir.

## Hedefler
- Bağlanınca amfinin tam durumunu `AmpState`'e al (kullanıcı hiçbir şeye dokunmadan ekran amfiyle aynı).
- Canlı çerçeveleri (ses, gain, amfi tipi, master echo/reverb, ve gelirse efekt/EQ) anında uygula.
- Uygulama arka plandan öne dönünce senkronu yeniden isteyip tam dökümü tazele.
- Kullanıcı kaydırıcıyı sürüklerken amfiden gelen değer sürüklemeyi bozmasın.

## Hedef dışı
AUX/müzik sesinin ayrı kontrolü, mikrofon/AUX/USB durumları, preset/simülasyon. `BD`/`BE`/`BF` çerçeveleri ve kalp atışı yok sayılır.

## Anti-hedefler
- Okunan değerleri asla amfiye geri yazma (döngü/yankı). Okuma salt okunur.
- Bilinmeyen/bozuk çerçeve uygulamayı çökertmesin ve durumu bozmasın.
- Amfiden gelen aralık dışı değer UI'a ham girmesin (mevcut sınırlara kıskaçla).

## Yaklaşımlar
1. **Seçilen: tam döküm + canlı çerçeve ayrıştırıcı, taşıma katmanında ham bayt, protokol katmanında ayrıştırma.**
   `AmpTransport.incoming: Flow<ByteArray>`; `ProtocolCodec.decode(frame): AmpUpdate?`; `AmpRepository` güncellemeyi `AmpState`'e uygular.
   Artı: ayrıştırma saf Kotlin, gerçek yakalanmış çerçevelerle test edilir. Eksi: doğrulanmamış çerçeveler yok sayılır.
2. Periyodik yoklama (sync'i sık sık yeniden gönder): kalp atışı varken gereksiz trafik, amfiye yük, canlılığı kötü; reddedildi.
3. Yalnızca bağlanınca tam döküm: pot değişimlerini göstermez; kullanıcının asıl isteğini karşılamaz; reddedildi.

## Benim kararlarım
- Yakalanmış gerçek çerçeveler birim testlerinde sabit örnek olarak kullanılır (ham günlük repo dışı kalır).
- Aralık dışı değerler `AmpState.apply` ile aynı kıskaçtan geçer.
- Ön plana dönüşte `Sync` yeniden gönderilir (ilk bağlantıdaki ile aynı çerçeve; amfi tam dökümle yanıtlıyor).
- Master ses için yeni komut/kaydırıcı eklenmez; pot zaten `Ses` ile aynı değeri hareket ettiriyor (telefonda doğrulanacak).

## Açık riskler
- Sync'in oturum ortasında tekrar gönderilmesinin amfide yan etkisi gözlenmedi; telefonda denenecek.
- Canlı EQ/efekt/reverb çerçeveleri gözlenmedi; yoksa yalnızca tam dökümle güncellenirler.
