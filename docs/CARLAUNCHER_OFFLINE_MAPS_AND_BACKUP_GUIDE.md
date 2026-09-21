# CarLauncher Çevrimdışı Harita Mimarisi, Sürüm Yönetimi ve Yedekleme Kılavuzu

Bu belge; CoMaps / Organic Maps CarLauncher modülünde çevrimdışı haritaların (.mwm) nasıl çalıştığını, motorun veri sürümü (data version) kuralını, yedekleme/içe aktarma mimarisini ve gelecekte yaşanabilecek olası sorunların önüne geçmek için uygulanan çözümleri açıklar.

---

## 1. Temel Mantık: C++ Motorunun Harita Arama Kuralı

Organic Maps harita işleme çekirdeği (C++ motoru) harita dosyalarını rastgele aramaz. Çok katı bir klasör ve sürüm hiyerarşisi vardır:

1. **`assets/countries.txt` Dosyası ve Sürüm Kodu (`"v": YYMMDD`):**
   - APK derlenirken `assets/countries.txt` içerisine harita veritabanının sürüm tarihi yazılır (Örneğin: `"v": 260830` -> Seri: 2026.06.28 / 260830).
   - Motor açılırken bu dosyayı okur veya bellekten `Framework.getDataVersion()` ile beklediği veri sürümünü belirler.

2. **Hedef Klasör Zorunluluğu (`files/YYMMDD/`):**
   - C++ motoru haritaları **yalnızca** `files/<SURUM>/` (örneğin `/data/user/0/app.organicmaps.carlauncher/files/260830/`) klasörü içerisinde arar.
   - **Kritik Kural:** Harita dosyaları (`.mwm`) doğrudan `files/` kök dizinine atılırsa, C++ motoru bu dosyaları **görmez ve yok sayar**. Bu durum harita dosyası fiziksel olarak cihazda olmasına rağmen "Harita dosyaları eksik" uyarısına ve harita motorunun açılmamasına neden olur.
   - Bu nedenle `LauncherBackupManager.getMapsTargetDir(Context)` metodu beklenen versiyonu (`260830`) dinamik olarak `assets/countries.txt` veya `Framework.getDataVersion()` üzerinden okur ve hedef klasörü `files/260830/` olarak otomatik oluşturur.

3. **Zorunlu Temel Haritalar (`World.mwm` ve `WorldCoasts.mwm`):**
   - Motorun dünya koordinatlarını ve kıyı şeritlerini çizebilmesi için `World.mwm` ve `WorldCoasts.mwm` dosyalarının bulunması zorunludur.
   - Sadece yerel bir bölge haritası (örneğin `Turkey_Marmara.mwm`) yüklenip `World.mwm` yüklenmezse motor çalışamaz.

---

## 2. Eksik Harita veya Sürüm Uyuşmazlığında Launcher Davranışı

### Önceki Sorun
Uygulama ilk kurulduğunda veya harita dosyaları bulunmadığında, Organic Maps çekirdeği tam ekran kısıtlayıcı bir "Harita İndir" ekranı açıyor veya modal kilitlenme oluşturuyordu. Bu durumda:
- Launcher'ın ayarlar penceresi,
- Müzik çalar büyük paneli ve kontrolleri,
- Hız, saat ve widget'lar,
- Alt uygulama dock'u (AppDock)
erişilemez hale geliyordu.

### Mevcut Çözüm (İzole Sol Alan Modeli)
1. Harita dosyaları eksik olsa veya sürüm uyuşmasa dahi Launcher'ın tüm servisleri (Müzik, Hız, Saat, Ayarlar, Dock) **eksiksiz ve tam fonksiyonel** çalışır.
2. Sadece sol taraftaki harita alanında (`mapContainer`) şık, modern bir bilgilendirme ve işlem kartı görüntülenir.
3. Bu kart üzerinde:
   - **Gerekli Harita Sürümü Rozeti:** APK'nın beklediği veri versiyonu (`Gerekli Harita Sürümü: 260830`) açıkça kullanıcıya gösterilir.
   - **İnternetten Harita İndir Butonu:** Tıklandığında CoMaps'in kendi resmi indirme sayfası (`CarLauncherDownloadResourcesActivity`) açılır; internet varsa doğrudan arama yapılıp indirilebilir.
   - **Yedek Paketinden Yükle (.zip):** USB veya depolamadan tek bir sıkıştırmasız `.zip` paketi seçildiğinde, içindeki tüm haritalar, yer imleri ve ayarlar otomatik çıkarılıp ilgili versiyon klasörüne kopyalanır.
   - **Klasörden Yükle (Klasör Seç):** Kullanıcının dosyaları tek tek seçmesi gerekmez; USB bellekten haritaların bulunduğu klasör seçilir ve arka planda tüm `.mwm` dosyaları aktarılır.
   - **Harita Dosyaları Seç (.mwm):** Klasik tek tek veya çoklu `.mwm` dosya seçici.
   - **Ayarlar Butonu:** CarLauncher'ın detaylı Ayarlar ekranını açar.

---

## 3. Yedekleme Mimarisi ve Paketleme (.zip)

### CoMaps Orijinal Yedekleme vs. LauncherBackupManager
- **Orijinal CoMaps:** CoMaps'in kendi "Yedekle" fonksiyonu sadece yer imlerini (Bookmarks / `.kml` veya `.kmz`) yedekler; harita dosyalarını veya launcher tercihlerini yedeklemez.
- **LauncherBackupManager:** Tarafımızca geliştirilen bu mimari;
  1. Harita dosyalarını (`maps/*.mwm`),
  2. Yer imlerini (`bookmarks/`),
  3. Organic Maps native ayarlarını (`settings.ini`),
  4. CarLauncher widget ve arayüz ayarlarını (`carlauncher_settings.json`)
  tek çatı altında toplar.

### Sıkıştırmasız Hızlı Paketleme (`Deflater.NO_COMPRESSION`)
- `.mwm` harita dosyaları doğası gereği zaten yüksek oranda sıkıştırılmış ikili (binary) verilerdir.
- Yedekleme sırasında ZIP sıkıştırması (Deflate) uygulamak araç multimedya cihazlarında (Android teyplerde) aşırı CPU ısınmasına ve dakikalarca süren beklemelere yol açar.
- Bu nedenle `LauncherBackupManager`, `.zip` arşivini oluştururken ve açarken sıkıştırmasız yöntem (Store modu) kullanır. Bu sayede gigabaytlarca harita dosyası saniyeler içinde kopyalanır.

---

## 4. JNI (C++ Motoru) ve Çökme Önleme Kuralları

1. **JNI Bellek Güvenliği (`arePlatformAndCoreInitialized`):**
   - Harita motoru henüz belleğe yüklenmeden önce `Framework.nativeGetBytesToDownload()` veya `Framework.getDataVersion()` gibi native C++ fonksiyonları çağrılırsa `g_framework` işaretçisi null olduğundan `SIGSEGV` (nullptr dereference) çökmesi yaşanır.
   - **Kural:** Herhangi bir native framework metodunu çağırmadan önce mutlaka:
     ```java
     boolean coreReady = MwmApplication.getOrganicMaps().arePlatformAndCoreInitialized();
     if (coreReady) {
         // Native C++ cagrisi yapilabilir
     }
     ```
     kontrolü yapılmalıdır.

2. **Harita İçe Aktarma Sonrası Motorun Yenilenmesi (`reloadEngines`):**
   - Yeni harita dosyaları diske yazıldıktan sonra C++ motorunun haritaları tanıyabilmesi için `Framework.nativeReloadWorldMaps()` ve `MwmApplication.getOrganicMaps().initStorage()` çağrıları yapılır.
   - Eksik olan `World.mwm` ve `WorldCoasts.mwm` tamamlandığında `CarLauncherActivity.recreate()` tetiklenerek harita motoru temiz bir şekilde başlatılır.

---

## 5. Harita Performansı ve Araç Teybi Optimizasyonu

### Tüm Türkiye Haritaları Neden Yavaşlatır?
1. **Bölünmüş Harita İndeksleri (`mmap`):** Türkiye haritası Organic Maps'te tek parça değildir; 7-8 ana bölgeye bölünmüştür (toplam ~1.5 GB). Motor açılışta ve navigasyon esnasında bu dosyaların tümünü Linux'un `mmap` mekanizmasıyla RAM'e bağlar. 8 ayrı dosyanın birbirine bağlanan yüzbinlerce kavşak ve yol düğümü aynı anda bellekte tutulur.
2. **Drape Grafik Yükü (Zoom Out):** Geniş açıdan bakıldığında motor ekrandaki milyonlarca poligonu ve yol çizgisini aynı anda render etmeye çalışır.
3. **Araç Teypleri için Durum:** Snapdragon 7+ Gen 2 gibi çok güçlü bir telefon işlemcisinde bile bu yük hissediliyorsa; arabada eMMC 5.1 yavaş depolama ve 1-2 GB RAM'li teyplerde tüm Türkiye dosyaları cihazı ciddi oranda hantallaştırır.

### Arabada Maksimum Hız İçin İpuçları:
- **Bölgesel Kullanım:** Araçta çoğunlukla bulunduğunuz bölgeyi (örneğin sadece `Turkey_Marmara.mwm` + temel `World.mwm` ve `WorldCoasts.mwm`) tutmak. Bu durumda harita motoru sadece ~150 MB veri işler ve 60 FPS akıcı çalışır. Farklı bir bölgeye seyahat edileceğinde diğer harita kolayca aktarılabilir.
- **3D Binaları Kapatmak:** Ayarlardan 3D binalar kapatıldığında grafik çizim çağrıları %60 oranında hafifler.

---

## 6. Menüden Harita & Yedek Yöneticisine Erişim

Haritalar tam olsa bile dilediğiniz an:
- Beklenen harita veri sürümünü (`260830`) görmek,
- Resmi sunucudan harita arayıp indirmek,
- USB bellekten tek paket (`.zip`) veya klasör seçerek geri yüklemek,
- Mevcut tüm ayar ve haritaları tek parça `.zip` olarak USB'ye yedeklemek için:

**CarLauncher Ayarları -> Haritalar ve Yedekleme -> "Harita ve Yedek Yöneticisi"** seçeneğine tıklayarak bu merkezi yönetim ekranını dilediğiniz an açabilirsiniz.

---

## 7. Pratik Sorun Giderme (Sık Karşılaşılan Durumlar)

| Durum | Sebebi | Çözüm |
| :--- | :--- | :--- |
| Harita dosyaları USB'den kopyalandı ama harita hâlâ açılmıyor | Dosyalar versiyon klasörü (`files/260830/`) yerine kök `files/` dizinine atılmış | "Klasörden Yükle" veya "Yedek Paketinden Yükle" butonunu kullanın; sistem dosyaları otomatik olarak doğru versiyon klasörüne taşıyacaktır. |
| Türkiye haritası yüklendi ama ekran siyah/boş kalıyor | `World.mwm` ve `WorldCoasts.mwm` eksik | Temel dünya haritası olmadan bölgesel haritalar render edilemez. World dosyalarını da yükleyin. |
| Telefonumda çalışan haritayı araca yükledim, açılmadı | İki cihazdaki APK'ların derleme tarihleri ve veri sürümleri farklı | Harita kartındaki `Gerekli Harita Sürümü: XXXXXX` rozetine bakın. İki cihazda da aynı APK sürümünün kurulu olduğundan emin olun veya aynı versiyona ait harita kullanın. |
| Telefonda yeni APK açılır açılmaz kapandı | 32-bit (armeabi-v7a) APK modern 64-bit telefona kurulmuş | Telefonlara `CarLauncher-arm64.apk`, araç teybine `CarLauncher-32bit.apk` kurulmalıdır. |
