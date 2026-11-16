# Müzik (AUX) ses seviyesi kontrolü — kısa tasarım

## Amaç ve neden
Kullanıcı telefondan Bluetooth ile amfiye backing track (ör. YouTube) çalıyor. Uygulamadaki "ANA SES" yalnızca gitar ses seviyesini
(amfi ekranında "GUI VOL", `02 01 00`) değiştiriyor; backing track'in sesi amfide ayrı bir **AUX ses seviyesi** (`04 01 00`) ve
bu uygulamada kontrolü yok. İstenen: "ANA SES"in yanına ikinci bir pot ile backing track sesini de uygulamadan ayarlamak.
Başarı ölçütü: pot çevrilince backing track'in sesi değişir; amfideki pot değişince pot ekranda güncellenir (senkron).

## Kapsam
- Dahil: AUX ses potu (0–50) başlıkta ANA SES'in yanında; Android ve Windows birlikte (iki platform eşit kalır); amfi→uygulama yansıtma; aynı kurallar (bağlı değilken soluk/pasif, çift dokun penceresi, dikey sürükleme).
- Dışında: AUX oynatma kontrolleri (play/ileri/geri), USB/mikrofon grupları, ayrı "müzik" sekmesi.
- Anti-hedef: doğrulanmamış komutla yayın yapmak. Komut amfide doğrulanmadan sürüm çıkmaz.

## Bilinenler (kaynak: satıcı uygulaması analizi `analysis/`, `docs/protocol.md`)
- Yazma: `FE 0D 04 01 00 VV 00 00 00 FF`, VV 0–50 (satıcı kodu `NetData` satır 292; AUX potu 0–50). **Amfide henüz doğrulanmadı.**
- Canlı bildirim: `FE 0F 04 01 00 VV …` (fiziksel master pot hem GUI VOL hem AUX'u hareket ettirir; kayıtta gözlendi; şu an yok sayılıyor).
- Açılışta durum dökümünde AUX değeri **belirsiz**: `BF` dökümünün `[3]` baytı aday (örnek `…BF 0C 00 01 00 0A…`, BB'deki ses de 0x0C). Doğrulanmadan kullanılmaz.

## Yaklaşım ve karar
Seçenekler: (A) başlıkta ikinci pot; (B) AMP sekmesinde ayrı "MÜZİK" paneli; (C) ayrı sekme. **Kullanıcı A'yı seçti** (çalarken sık ayarlıyor).
Mantık: `AmpCommand.SetAuxVolume` + `AmpState.auxVolume` + decode (`04 01 00`) + ViewModel/AppController eylemi + başlıkta pot "MÜZİK"
(erişilebilir ad "Müzik sesi"; test etiketi `knob-Müzik sesi`). Android ve Windows çekirdeği aynı testlerle (aynı vektörler).

## Doğrulama (yayından önce şart)
1. Android geliştirme sürümünde (ham RX günlüğü açık, yalnızca geliştirme) telefon+amfi ile: potu çevir → backing track sesi değişiyor mu; amfideki master pot → `04 01 00` canlı çerçeveleri pota yansıyor mu.
2. Yeniden bağlanma/Sync sonrası dökümde AUX nerede? (`BF[3]` mi?) Gözlemle; doğrulanırsa açılış senkronuna eklenir, aksi halde pot bir sonraki amfi/uygulama değişikliğine kadar son bilinen değeri gösterir (varsayılan 10) ve bu belgelenir.
3. Sonuçlar `docs/protocol.md`'ye `verified` olarak işlenir.

## Yapıcıya bırakılan
Pot adı yazısı ("MÜZİK"), başlık boşluk ayarı (dar telefon ve geniş pencere), varsayılan değer 10.

## Riskler
- Amfi komutu kabul etmeyebilir ya da başka kanalı etkileyebilir → doğrulamada görülür, yayın yapılmaz.
- Dar ekranda başlık kalabalığı → iki pot 62 dp, LED/logo satırı daralır; test ve ekran görüntüsüyle kontrol.
