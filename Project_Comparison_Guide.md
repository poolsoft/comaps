# 🔍 Proje Konfigürasyonları Karşılaştırma Rehberi

## Sorun
CoMaps'in kendi APK'sında UTF-8 sorunu olmazken, sizin projenizde (CoMaps'i modül olarak kullandığınız) sorun yaşıyorsunuz. Bu durumda iki proje arasında önemli konfigürasyon farklılıkları var.

## 📋 Kıyaslama Kontrol Listesi

### 1. AAR Dosyası İçeriği Karşılaştırması

**Yapılacak:** CoMaps AAR'ı ile kendi projenizdeki AAR'ı karşılaştırın

```bash
# Geçici klasör oluşturun
mkdir aar_comparison && cd aar_comparison

# CoMaps AAR'ını açın (çalışan)
mkdir comaps_aar && cd comaps_aar
jar -xf /path/to/comaps/app.apk
find . -name "*.aar" -exec jar -xf {} \;
cd ..

# Kendi projenizdeki AAR'ı açın
mkdir your_aar && cd your_aar
# Kendi projenizdeki AAR dosyasını bulun ve açın
jar -xf /path/to/your/libs/sdk-debug.aar
cd ..

# AAR içeriklerini karşılaştırın
echo "=== COMAPS AAR İÇERİĞİ ===" > aar_comparison.txt
find comaps_aar -name "*.json" | sort >> aar_comparison.txt

echo "=== SİZİN AAR İÇERİĞİNİZ ===" >> aar_comparison.txt
find your_aar -name "*.json" | sort >> aar_comparison.txt

# JSON dosyalarını karşılaştırın
diff comaps_aar/assets/countries-strings/ your_aar/assets/countries-strings/ > json_differences.txt
```

**Kontrol Edilecekler:**
- [ ] `liborganicmaps.so` dosyası mevcut mu?
- [ ] AAR dosya boyutları aynı mı?
- [ ] Aynı sayıda AAR dosyası var mı?

### 2. AndroidManifest.xml Karşılaştırması

**Yapılacak:** İki projenin manifest dosyalarını karşılaştırın

```bash
# CoMaps projesi manifest'i
echo "=== COMAPS MANIFEST ===" > manifest_comparison.txt
cat /path/to/comaps/android/app/src/main/AndroidManifest.xml >> manifest_comparison.txt

echo -e "\n=== SİZİN PROJENİZ MANIFEST ===" >> manifest_comparison.txt
cat /path/to/your/project/src/main/AndroidManifest.xml >> manifest_comparison.txt

# Farkları bulun
diff /path/to/comaps/android/app/src/main/AndroidManifest.xml /path/to/your/project/src/main/AndroidManifest.xml > manifest_differences.txt
```

**Kontrol Edilecek Önemli İzinler:**
- [ ] `android:usesCleartextTraffic="true"`
- [ ] `<uses-native-library android:name="liborganicmaps.so" android:required="true" />`
- [ ] `android:requestLegacyExternalStorage="true"`
- [ ] `<uses-library android:name="android.test.runner" android:required="false" />`

### 3. Gradle Build Dosyası Karşılaştırması

**Yapılacak:** İki projenin build konfigürasyonlarını karşılaştırın

```bash
# CoMaps ve sizin projenizin build.gradle'lerini karşılaştırın
echo "=== COMAPS BUILD.GRADLE ===" > gradle_comparison.txt
cat /path/to/comaps/android/app/build.gradle >> gradle_comparison.txt

echo -e "\n=== SİZİN PROJENİZ BUILD.GRADLE ===" >> gradle_comparison.txt
cat /path/to/your/project/app/build.gradle >> gradle_comparison.txt

# Farkları bulun
diff /path/to/comaps/android/app/build.gradle /path/to/your/project/app/build.gradle > gradle_differences.txt

# Özellikle şu bölümleri kontrol edin:
grep -A5 -B5 "compileOptions" /path/to/comaps/android/app/build.gradle
grep -A5 -B5 "compileOptions" /path/to/your/project/app/build.gradle

grep -A10 -B2 "lint" /path/to/comaps/android/app/build.gradle
grep -A10 -B2 "lint" /path/to/your/project/app/build.gradle
```

**Kontrol Edilecek Önemli Ayarlar:**
- [ ] `encoding 'UTF-8'` ayarı
- [ ] `disable 'ByteOrderMark'` ayarı
- [ ] `minifyEnabled` ayarları
- [ ] `coreLibraryDesugaringEnabled` ayarı

### 4. Proguard/R8 Kuralları Karşılaştırması

**Yapılacak:** İki projenin proguard kurallarını karşılaştırın

```bash
# CoMaps ve sizin projenizin proguard kurallarını karşılaştırın
echo "=== COMAPS PROGUARD ===" > proguard_comparison.txt
cat /path/to/comaps/android/app/proguard-rules.pro >> proguard_comparison.txt

echo -e "\n=== SİZİN PROJENİZ PROGUARD ===" >> proguard_comparison.txt
cat /path/to/your/project/app/proguard-rules.pro >> proguard_comparison.txt

# Farkları bulun
diff /path/to/comaps/android/app/proguard-rules.pro /path/to/your/project/app/proguard-rules.pro > proguard_differences.txt

# JSON ve native kod kurallarını kontrol edin
grep -i "json\|native\|utf" /path/to/comaps/android/app/proguard-rules.pro
grep -i "json\|native\|utf" /path/to/your/project/app/proguard-rules.pro
```

**Kontrol Edilecek Önemli Kurallar:**
- [ ] `-keep class org.json.**`
- [ ] `-keep class java.nio.charset.**`
- [ ] `-keepclasseswithmembernames class * { native <methods>; }`
- [ ] `-keep class app.organicmaps.**`

### 5. Assets Klasörü Karşılaştırması

**Yapılacak:** İki projenin assets içeriğini karşılaştırın

```bash
# CoMaps ve sizin projenizin assets klasörlerini karşılaştırın
echo "=== COMAPS ASSETS ===" > assets_comparison.txt
find /path/to/comaps/android/app/src/main/assets -type f 2>/dev/null | sort >> assets_comparison.txt

echo -e "\n=== SİZİN PROJENİZ ASSETS ===" >> assets_comparison.txt
find /path/to/your/project/app/src/main/assets -type f 2>/dev/null | sort >> assets_comparison.txt

# JSON dosyalarını özellikle karşılaştırın
diff /path/to/comaps/android/app/src/main/assets/countries-strings/ /path/to/your/project/app/src/main/assets/countries-strings/ > json_differences.txt 2>/dev/null || echo "Assets klasörleri bulunamadı veya farklı"
```

**Kontrol Edilecekler:**
- [ ] `countries-strings/tr.json/localize.json` mevcut mu?
- [ ] `countries-strings/en.json/localize.json` mevcut mu?
- [ ] JSON dosya boyutları aynı mı?

### 6. SDK Modülü Yapılandırması Karşılaştırması

**Yapılacak:** SDK modülü konfigürasyonlarını karşılaştırın

```bash
# SDK build.gradle dosyalarını karşılaştırın
echo "=== COMAPS SDK BUILD.GRADLE ===" > sdk_comparison.txt
cat /path/to/comaps/android/sdk/build.gradle >> sdk_comparison.txt

echo -e "\n=== SİZİN PROJENİZ SDK BUILD.GRADLE ===" >> sdk_comparison.txt
cat /path/to/your/project/comaps/build.gradle >> sdk_comparison.txt

# Farkları bulun
diff /path/to/comaps/android/sdk/build.gradle /path/to/your/project/comaps/build.gradle > sdk_differences.txt
```

**Kontrol Edilecek Önemli Ayarlar:**
- [ ] `jniDebuggable true` ayarı
- [ ] `ndk abiFilters` ayarları
- [ ] `externalNativeBuild` cmake ayarları
- [ ] `lint` ayarları

## 🔧 BULUNAN FARKLARI UYGULAMA

### Eğer Çalışan APK'da Eksik Olan Ayarlar Varsa:

**AndroidManifest.xml'e Ekleyin:**
```xml
<!-- Çalışan APK'da varsa sizin manifest'inize ekleyin -->
<uses-library android:name="android.test.runner" android:required="false" />
<uses-native-library android:name="liborganicmaps.so" android:required="true" />
```

**Build.gradle'a Ekleyin:**
```gradle
compileOptions {
    encoding 'UTF-8'
    // ... diğer ayarlar
}

lint {
    disable 'ByteOrderMark'
    // ... diğer ayarlar
}
```

**Proguard-rules.pro'ya Ekleyin:**
```proguard
# Çalışan APK'da varsa ekleyin
-keep class org.json.** { *; }
-keep class java.nio.charset.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
```

## 📊 SONUÇ ANALİZİ

### Karşılaştırma Sonuçlarını İnceledikten Sonra:

1. **Eksik İzinleri Belirleyin:**
   - Manifest'te hangi izinler eksik?
   - Build ayarlarında hangi optimizasyonlar eksik?

2. **Farklı AAR İçeriğini Belirleyin:**
   - Hangi AAR dosyaları farklı?
   - JSON dosyaları aynı mı?

3. **Build Konfigürasyonlarını Eşitleyin:**
   - Çalışan APK'nın ayarlarını kendi projenize uygulayın

4. **Test Edin:**
   - Her değişiklikten sonra uygulamayı test edin
   - Logları karşılaştırın

## ⚡ HIZLI KONTROL KOMUTLARI

```bash
# Tüm karşılaştırmaları tek seferde yapın (AAR odaklı)
./compare_aar_configs.sh /path/to/comaps/app.apk /path/to/your/libs/sdk-debug.aar

# Kıyaslama scripti içeriği:
#!/bin/bash
COMAPS_APK=$1
YOUR_AAR=$2

echo "=== COMAPS AAR İÇERİK KARŞILAŞTIRMASI ===" > aar_comparison_results.txt

# CoMaps AAR'ını açın
mkdir temp_comaps && cd temp_comaps
jar -xf $COMAPS_APK
find . -name "*.aar" -exec jar -xf {} \;
cd ..

# Sizin AAR'ınızı açın
mkdir temp_your && cd temp_your
jar -xf $YOUR_AAR
cd ..

echo "=== COMAPS AAR İÇERİĞİ ===" >> aar_comparison_results.txt
find temp_comaps -name "*.json" | head -10 >> aar_comparison_results.txt

echo "=== SİZİN AAR İÇERİĞİNİZ ===" >> aar_comparison_results.txt
find temp_your -name "*.json" | head -10 >> aar_comparison_results.txt

# Manifest karşılaştırması
echo "=== MANIFEST KARŞILAŞTIRMASI ===" >> aar_comparison_results.txt
diff temp_comaps/AndroidManifest.xml temp_your/AndroidManifest.xml >> aar_comparison_results.txt

echo "Karşılaştırma tamamlandı. Sonuçlar: aar_comparison_results.txt"
```

## 🎯 EN ÖNEMLİ KONTROL NOKTALARI

### **Şu Sırayla Kontrol Edin:**

1. **AndroidManifest.xml izinleri** (en kritik)
2. **Build.gradle encoding ayarları**
3. **Proguard kuralları**
4. **AAR içindeki JSON dosyaları**
5. **SDK modülü konfigürasyonu**

### **Hızlı Test:**
```bash
# Sadece manifest ve build.gradle karşılaştırması
diff /path/to/comaps/android/app/src/main/AndroidManifest.xml /path/to/your/project/src/main/AndroidManifest.xml
diff /path/to/comaps/android/app/build.gradle /path/to/your/project/app/build.gradle
```

Bu rehber ile iki proje arasındaki tüm farklılıkları tespit edip, sorunun kaynağını bulabilirsiniz! 🚀
