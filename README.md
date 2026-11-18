# Enerji İmparatorluğu

Java + LibGDX ile yazılmış izometrik 3D enerji şirketi tycoon / ekonomi simülasyonu.
Kurulum yok: tek bir JAR dosyası, çift tıkla veya `java -jar` ile çalışır. Harici asset yoktur; arazi, binalar ve hatlar kodla üretilir.

## Çalıştırma
Gereksinim: Java 17 veya üzeri (JRE yeterli).

```bash
java -jar dist/EnerjiImparatorlugu.jar
```
Windows'ta `run.bat`, Linux/macOS'ta `./run.sh` de kullanılabilir. JAR; Windows, Linux ve macOS (Intel/Apple Silicon) için gerekli OpenGL yerel kütüphanelerini içinde taşır.

## Oyun
- Santral kur (kömür, doğalgaz, güneş, rüzgar, hidro, nükleer), şehirlere iletim hattı çek, elektrik sat.
- Her saat talep en ucuz santralden başlayarak karşılanır (merit order); fiyat son devreye giren santralin maliyetidir. Arz yetersizse fiyat sıçrar ve ceza kesilir.
- Yakıt fiyatları dalgalanır (gaz şokları), karbon vergisi her yıl artar, hava (rüzgar/bulut) saatlik değişir, santraller arızalanır.
- Finans: borçlan/öde, faiz kaldıraca göre artar; nakit çok eksiye düşerse iflas. 0–3 yapay zekâ rakip de santral kurar.

### Ekipman ve tedarikçiler (kurgusal markalar)
Bir santral türüne tıklayınca model seçim penceresi açılır. Her türde 3–4 model var:
| Tür | Seçenekler |
|---|---|
| Güneş | SunPeak PERC (ucuz, hızlı yaşlanır), Helion TOPCon, Lumina çift yüzlü HJT (sıcakta kayıp az), Kuzey Güneş tandem (2033) |
| Rüzgar | Rüzgarsan IEC-I (güçlü rüzgar), Aeolus dengeli, Nordwind düşük rüzgar (büyük rotor), Boreal 7.2 MW (2028) |
| Doğalgaz | Atlas OCGT (ucuz, düşük verim), Atlas CCGT, Meridian H-sınıfı |
| Kömür | Ferrum subkritik / süperkritik, Taurus ultra-süperkritik (soğutma kuleli) |
| Hidro | Hydrova Kaplan (alçak düşü), Francis, Alpina Pelton (dağ gölü) |
| Nükleer | Atomtek Gen III+, Polaris SMR (2030, kısa inşaat) |

Model; üretimi, verimi/emisyonu, yıllık yaşlanmayı, fiyatı ve inşaat süresini belirler. Tedarikçi itibarı arıza sıklığını ve teslim gecikmesi riskini, garanti süresi ücretsiz onarımı belirler. Tedarikçi fiyatları aylık dalgalanır (Piyasa penceresi).
Kendi santralinizi seçince **yükseltmeler** çıkar: güneşte tracker ve robotik temizlik, rüzgarda repowering, kömür/gazda karbon yakalama, biyokütle ve hidrojen karışımı, hidroda türbin yenileme, nükleerde ömür uzatma.

### Haritalar ve senaryolar
- **Harita tipleri:** Kıta, Ada, Takımadalar, Dağlık İç Bölge (göller, nehirler), Nehir Vadisi, Kıyı Çölü (güçlü güneş, az su).
- **Ayarlar:** boyut 48/64/96, şehir sayısı, rakip sayısı (0–3), zorluk, harita tohumu; yeni oyun ekranında canlı önizleme.
- **Senaryolar:** Serbest Oyun ($4 milyar), Yeşil Dönüşüm (2035), Kömürden Çıkış (2032), Enerji Krizi (2030), Ada Şebekesi (2033). Hedefler sağ alttaki panelde izlenir. Süre dolarsa oyun biter.

### Grafik
Şehirlerde Kenney gökdelen/apartmanları (3 renk teması, uzaktan sade modeller), kömür/gaz/nükleer santrallerde Kenney fabrika, baca, soğutma kulesi ve tankları; gerçek zamanlı gölgeler (Menü → Gölge: Kapalı/Orta/Yüksek; ayar saklanır), köşe gölgelemesi (AO), gün boyu dönen güneş, gece pencere ışıkları ve ikaz lambaları, ağaçlar, büyüdükçe yayılan banliyöler, kafes çelik direkler, ekipmana göre değişen santral modelleri.

### Kontroller
| Girdi | İşlev |
|---|---|
| Sol tık | Seç / yerleştir / hat çek |
| Sağ tık sürükle, WASD/oklar | Kaydır |
| Sağ tık (sürüklemeden), Esc | Aracı iptal et |
| Tekerlek | Yakınlaştır |
| Q / E | Kamerayı 90° döndür |
| Boşluk, 1/2/3 | Duraklat, hız |
| L | Harita katmanı değiştir |
| F / M / C / H | Finans, Piyasa, Şirketler, Yardım |
| F5 / F9 | Kaydet / yükle (`~/.energytycoon/`) |

## Geliştirme
```bash
./gradlew test       # sim testleri: dispatch, ekipman etkileri, tüm harita tipleri, senaryolar, kayıt v1/v2
./gradlew fatJar     # build/libs/energytycoon.jar
./gradlew run        # doğrudan çalıştır
```

Yapı (`com.energytycoon`): `sim` (LibGDX'ten bağımsız simülasyon çekirdeği; `Balance` denge sabitleri, `EquipmentCatalog` modeller/tedarikçiler, `Scenarios` senaryolar), `world` (harita üretimi), `render` (izometrik kamera, arazi, prosedürel modeller), `ui` (Scene2D HUD ve grafikler), `save` (JSON kayıt).

Doğrulama kancası (`DevHook`): `ET_SHOT=/yol/resim.png ET_SCENARIO=game|setup ET_PRESET=HIGHLANDS ET_HOUR=21 ET_OPEN=equip:WIND java -jar ...` ekran görüntüsü alıp çıkar (normal oyunda etkisizdir).

## Üçüncü taraf
- Doğa modelleri: **Kenney Nature Kit** (kenney.nl, CC0) — ağaçlar, çalılar, çim kümeleri, kayalar; arazi parçalarına pişirilir, renkleri arazi paletine eşlenir.
- **Innerscene Low-poly Nature** karoları (innerscene.com, CC0) — çim ve su renk paleti, su yüzeyindeki dalgacık şeritleri.
- Kaynak GLB dosyaları ve lisansları `assets-src/nature/`; oyunun kullandığı OBJ halleri `src/main/resources/models/nature/`.
  Yeniden dönüştürmek için: `python3 tools/glb2obj.py assets-src/nature/Kenney_Nature_Kit_GLTF/tree_oak.glb src/main/resources/models/nature`
- Yazı tipi: **Manrope V5 by Mikhail Sharanda** (kişisel/ticari kullanım ve gömme serbest; dosyalar değiştirilmeden dağıtılır, lisans: `src/main/resources/fonts/ManropeV5-LICENSE.txt`, https://www.sharanda.com/manrope).
- Manrope'ta bulunmayan semboller (▲▼█░✓✗★☆↔) için yedek: DejaVu Sans (lisans: `src/main/resources/fonts/DejaVu-LICENSE.txt`).
- Zaman kontrol düğmeleri (duraklat/devam, hız kademeleri): proje sahibinin sağladığı paket, `src/main/resources/ui/time_*.png`.
- Arayüz görselleri (ana menü arka planı, panel çerçeveleri, butonlar, ikonlar, HUD öğeleri): proje sahibinin sağladığı "Energy Tycoon menu assets" paketi; kesilmiş/ölçeklenmiş halleri `src/main/resources/ui/`.
- Kenney City Kit Commercial 2.1 ve Industrial 2.0 (kenney.nl, CC0): şehir binaları, santral binaları, bacalar, soğutma kuleleri, tanklar. Lisans dosyaları `src/main/resources/models/kenney/*/License.txt`.
- LibGDX / LWJGL3 (Apache 2.0), FreeType.
