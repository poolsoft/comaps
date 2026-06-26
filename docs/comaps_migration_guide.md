# CoMaps (Organic Maps) CarLauncher Göç Rehberi & Geliştirici Kılavuzu

Bu belge, OsmAnd CarLauncher projesinden Organic Maps (CoMaps) projesine yapılan CarLauncher modülü göçünün (migration) detaylarını, mevcut teknik durumu ve projeyi IDE üzerinde doğrudan açtığınızda kullanabileceğiniz hazır **AI Geliştirici Promptu**nu içerir.

---

## 🚗 Proje Genel Bakışı ve Mimari Göçü

OsmAnd projesinde yer alan CarLauncher modülü, tamamen CoMaps (Organic Maps) projesine taşınmıştır. İki uygulamanın SDK ve motor yapıları farklı olduğu için aşağıdaki adaptasyonlar yapılmıştır:

| Özellik | OsmAnd Altyapısı | CoMaps (Organic Maps) Uyarlaması | Durum |
| :--- | :--- | :--- | :--- |
| **Konum & Hız** | `TelemetryManager` / `LocationListener` | `LocationHelper` & `LocationListener` | ✅ Tamamlandı |
| **Navigasyon Yönü** | `RoutingHelper` & `TurnType` | `RoutingController` & `CarDirection` | ✅ Tamamlandı |
| **Mesafe/ETA Format** | `OsmAndFormatter` | `app.organicmaps.util.Utils` yerel formatlayıcıları | ✅ Tamamlandı |
| **Yönlendirme Metni** | Native C++ motoru (JNI/TTS) | Java katmanında switch-case + `strings_car.xml` | ✅ Tamamlandı |
| **OBD-II Verileri** | `VehicleMetricsPlugin` / OBD kütüphanesi | `ObdState` (Placeholder/Dummy) | ⏳ Dummy / Sonra Bakılacak |
| **Müzik & Donanım** | Yerel Android Media kütüphaneleri | Android `MediaSession` & Özel donanım adaptörleri | ✅ Tamamlandı |
| **Dil Entegrasyonu** | OsmAnd string kaynakları | `strings_car.xml` (TR ve EN kaynakları hazırlandı) | ✅ Tamamlandı |

---

## 📋 Mevcut Dosya Yapısı (CoMaps)

CarLauncher ile ilgili tüm kaynaklar ve kodlar `CoMaps_Repo/android/app/src/carlauncher` klasörü altındadır:
* **Java Kodları**: `android/app/src/carlauncher/java/app/organicmaps/carlauncher/`
  * `telemetry/TelemetryManager.java`: Navigasyon ve konum verilerini Organic Maps SDK'sından çekip formatlar.
  * `ui/CarFloatingButtonManager.java`: Yüzen yardımcı buton ve popup menü mantığı.
  * `ui/CarLauncherSettingsFragment.java`: Arayüz ayarları.
  * `widgets/`: Hız, Pusula, Saat ve Hava Durumu widget'ları.
  * `widgets/view/`: Analog Hız Göstergesi (`AnalogSpeedometerView`), modern analog saat görünümleri.
* **Arayüz Tanımları**: `android/app/src/carlauncher/res/xml/carlauncher_prefs.xml`
* **Dil Kaynakları**:
  * `android/app/src/main/res/values/strings_car.xml` (İngilizce ve varsayılan)
  * `android/app/src/main/res/values-tr/strings_car.xml` (Türkçe)

---

## 💡 AI Geliştirici Ajanı İçin Hazır Başlangıç Promptu
> [!TIP]
> Aşağıdaki prompt metnini kopyalayarak yeni bir AI ajanı başlattığınızda veya projeyi yeni bir workspace olarak tanıttığınızda doğrudan ilk mesaj olarak gönderebilirsiniz.

```markdown
Sen bir Android geliştirme uzmanı ve Organic Maps (CoMaps) CarLauncher projesinden sorumlu bir yapay zeka kodlama asistanısın.

### Proje Amacı:
OsmAnd projesinden göç ettirilerek Organic Maps (CoMaps) projesine kazandırılan `carlauncher` modülünün geliştirilmesine ve stabilizasyonuna devam etmek.

### Proje Yapısı:
1. CarLauncher kaynak kodları `android/app/src/carlauncher/java/app/organicmaps/carlauncher/` dizinindedir.
2. Dil kaynakları `android/app/src/main/res/values/strings_car.xml` (EN) ve `values-tr/strings_car.xml` (TR) dosyalarında toplanmıştır.
3. Ayarlar ekranı preference yapısı `android/app/src/carlauncher/res/xml/carlauncher_prefs.xml` dosyasındadır ve tüm statik yazılar `@string/...` üzerinden dil kaynaklarına bağlıdır.

### Telemetry ve Navigasyon Durumu:
- `TelemetryManager.java` sınıfı, Organic Maps konum ve navigasyon SDK'sını dinler.
- `pollNavigation()` metodu, `RoutingController.get().getCachedRoutingInfo()` üzerinden anlık mesafe, ETA süreleri ve dönüş yönlerini (`CarDirection`) alır.
- `getTurnInstruction()` metodu, switch-case yapısı ve `strings_car.xml` kaynakları aracılığıyla "Sola dönün", "Hedefinize ulaştınız" gibi Türkçe/İngilizce metin yönlendirmelerini oluşturup ekranda caddenin ismiyle birleştirir.
- OBD (Araç motor verileri) verileri şu an dummy/placeholder durumundadır.

### Senden İstenenler ve Kurallar:
1. Kodlarda Türkçe yaz ama değişken, sınıf ve metot adlarında Türkçe karakter kullanma (String kaynakları hariç).
2. Yeni bir özelliğe veya büyük kod değişikliklerine başlamadan önce her zaman detaylı bir "Uygulama Planı" (Implementation Plan) hazırlayıp onayımı bekle.
3. Yapılan tüm değişiklikleri git diff ile doğrula ve her görev bitiminde projeyi git reposuna commit edip pushla.
4. Çözünürlük uyumluluklarını, araç multimedya cihazlarındaki double-din yatay ekran ölçeklemelerini ve donanım tetikleyicilerini (`CarHardwareManager.java`) göz önünde bulundur.

Hazır olduğunda bana projenin mevcut durumuyla ilgili kısa bir analiz sunarak başlayabilirsin.
```
