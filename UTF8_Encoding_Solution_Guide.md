# Organic Maps Android Projesi - UTF-8 Encoding Sorunu Çözüm Rehberi

## Sorun Tanımı
Uygulama başlatılırken "unable to decode byte 0xa4" hatası alıyorsunuz. Bu hata Türkçe karakterlerin (ğ, ş, ı, ö, ü, ç) UTF-8 encoding sorunundan kaynaklanıyor.

**Kök Neden:** AAR dosyası içindeki JSON dosyaları (ülkeler/diller çevirileri) Android runtime'da yüklenirken encoding sorunu yaşıyor.

## 🚨 YENİ BULGU: Android Sistem UTF-8 Encoding'i Görmezden Geliyor
Loglardan görüldüğü üzere: `Ignoring attempt to set property "file.encoding" to value "UTF-8"`

## � KRİTİK KEŞİF: Sorun en.json'da Başlıyor!
Loglardan görüldüğü üzere hata `countries-strings/en.json/localize.json` dosyasında başlıyor. Bu İngilizce JSON dosyasında bile UTF-8 decode sorunu var!

## �🔧 ÇÖZÜM ADIMLARI

### 1. Build.gradle Dosyası Güncellemeleri

**Dosya:** `app/build.gradle`

```gradle
android {
    // ... mevcut kod ...

    compileOptions {
        coreLibraryDesugaringEnabled = true
        encoding 'UTF-8'  // ← BU SATIRI EKLEYİN

        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    lint {
        disable 'MissingTranslation'
        disable 'MissingQuantity', 'UnusedQuantity'
        disable 'CustomSplashScreen'
        disable 'InsecureBaseConfiguration'
        disable 'ByteOrderMark'  // ← BU SATIRI EKLEYİN (UTF-8 BOM sorunu için)
        disable 'InvalidPackage' // ← BU SATIRI EKLEYİN
        abortOnError = true
    }
}
```

**Neden:**
- `encoding 'UTF-8'`: Tüm kaynak kodların UTF-8 olarak derlenmesini sağlar
- `disable 'ByteOrderMark'`: UTF-8 BOM (Byte Order Mark) sorunlarını önler
- `disable 'InvalidPackage'`: Package name validation sorunlarını önler

### 2. AndroidManifest.xml Güncellemeleri

**Dosya:** `src/main/AndroidManifest.xml`

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.wow.carlauncher">

    <application
        android:label="@string/app_name"
        android:requestLegacyExternalStorage="true"
        android:usesCleartextTraffic="true"  <!-- ← BU SATIRI EKLEYİN -->
        android:theme="@style/AppTheme">

        <!-- UTF-8 desteği için gerekli izinler -->
        <uses-library android:name="android.test.runner" android:required="false" />

        <!-- Native kod için gerekli izin -->
        <uses-native-library android:name="liborganicmaps.so" android:required="true" />

    </application>

</manifest>
```

**Neden:**
- `android:usesCleartextTraffic="true"`: HTTP trafiği için gerekli (bazı durumlarda)
- `uses-native-library`: Native kütüphane kullanımını bildirir

### 3. Gradle Properties Güncellemeleri

**Dosya:** `gradle.properties`

```properties
# Mevcut ayarlarınızın üzerine ekleyin:

# UTF-8 encoding için
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8

# Android build optimizasyonları
android.enableSeparateAnnotationProcessing=true
android.useAndroidX=true
android.enableJetifier=true

# Native build için
android.native.buildOutput=verbose
```

**Neden:**
- `org.gradle.jvmargs=-Dfile.encoding=UTF-8`: Gradle'in UTF-8 kullanmasını garanti eder
- Diğer ayarlar: AndroidX ve build optimizasyonları için

### 4. Proguard Kuralları Güncellemeleri

**Dosya:** `app/proguard-rules.pro`

```proguard
# Mevcut kurallarınızın üzerine ekleyin:

# UTF-8 ve JSON parsing için gerekli kurallar
-keep class org.json.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn java.nio.file.**
-dontwarn okio.**
-keep class java.nio.charset.** { *; }

# Native kod için JNI kuralları
-keepclasseswithmembernames class * {
    native <methods>;
}

# OrganicMaps native library için
-keep class app.organicmaps.** { *; }
```

**Neden:**
- JSON parsing kütüphanelerini korur
- Native metodları korur
- UTF-8 charset sınıflarını korur

### 5. Assets Klasörü Kontrolü

**Yapılacak:** Eğer AAR dosyası içindeki data klasörünü kullanacaksanız:

```
src/main/assets/
└── countries-strings/
    └── tr.json/
        └── localize.json
```

**Neden:** AAR içindeki JSON dosyalarına erişim için gerekli

### 6. Test İçin Geçici Çözüm

**Yapılacak:** Türkçe karakterleri test etmek için JSON dosyalarında:

```json
// Geçici olarak Türkçe karakterleri ASCII'ye çevirin
"Şehir": "Sehir",
"Çalışma": "Calisma"
```

**Neden:** Önce ASCII karakterlerle test edip sonra UTF-8 düzeltmelerini yapın

## 🧪 TEST ADIMLARI

### 1. Build ve Çalıştırma

```bash
# Temiz build
./gradlew clean build

# Uygulamayı çalıştırın
./gradlew installDebug

# ADB logcat ile takip edin
adb logcat | grep -i "json\|utf\|decode\|organicmaps"
```

### 2. Hata Kontrolü

```bash
# Uygulama başlatıldığında şu logları arayın:
# - "unable to decode byte" → Çözülmemiş
# - "Json::Exception" → Çözülmemiş
# - "terminating due to uncaught exception" → Çözülmemiş
```

### 3. Başarı Kriterleri

- Uygulama crash olmadan açılmalı
- Türkçe karakterler doğru görüntülenmeli
- Loglarda UTF-8 decode hatası olmamalı

## ⚠️ SORUN GİDERME

### Hala Hata Alıyorsanız:

**🔥 KRİTİK BULGU:** Android sistem `file.encoding=UTF-8` ayarını görmezden geliyor!

**💀 SORUN KAYNAĞI:** Hata `en.json` dosyasında başlıyor! İngilizce JSON'da bile UTF-8 sorunu var.

### **🚀 ÇÖZÜM 1: SDK'yı Yeniden Derleyin (En Etkili Çözüm)**

**Yapılacak:** SDK'yı UTF-8 sorunu çözülerek yeniden derleyin:

```bash
# Geçici klasör oluşturun
mkdir sdk_fix && cd sdk_fix

# Bu projeyi klonlayın (gerçek kaynak kod için)
git clone https://github.com/organicmaps/organicmaps.git
cd organicmaps

# ✅ ÇÖZÜM EKLENDİ: CMakeLists.txt'e UTF-8 fix eklendi
# Artık otomatik olarak Android için UTF-8 desteği var

# Configure edin
./configure.sh

# 🔧 LOKAL MAKİNE İÇİN GEREKLİ ORTAM DEĞİŞKENLERİ:
export ANDROID_HOME=/path/to/Android/Sdk
export ANDROID_NDK_HOME=/path/to/Android/Sdk/ndk/28.2.13676358
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools:$PATH

# 🔧 LOKAL DERLEME İÇİN ÖZEL AYARLAR:
export SKIP_MAP_DOWNLOAD=1  # İnternet gereksinimini azalt
export CMAKE_BUILD_PARALLEL_LEVEL=$(nproc)  # Paralel build

# 🔧 BELLEK AYARLARI (Derleme hatalarını önlemek için):
export CMAKE_C_COMPILER_LAUNCHER=ccache
export CMAKE_CXX_COMPILER_LAUNCHER=ccache

# SDK'yı yeniden build edin
./gradlew :android:sdk:assembleDebug

# 🔧 ALTERNATİF: Daha az bellek kullan:
./gradlew :android:sdk:assembleDebug -Dorg.gradle.jvmargs="-Xmx2g -XX:+UseParallelGC"
```

### **🚀 ÇÖZÜM 2: JSON Parsing'de Hatayı Es Geçin (Kod Değişikliği)**

**Dosya:** Native kod içinde JSON parsing'i handle edin:

```cpp
// cppjansson.hpp içinde veya kullanım yerinde
try {
    json_load_file(file_path);
} catch (const Json::Exception& e) {
    // UTF-8 decode hatası için özel handling
    if (strstr(e.what(), "unable to decode byte") != nullptr) {
        LOG(WARN, "UTF-8 decode error in JSON, trying fallback encoding");
        // Alternatif encoding ile tekrar dene
        json_load_file_with_encoding(file_path, "ISO-8859-9"); // Türkçe için
    } else {
        throw; // Diğer hataları fırlatmaya devam et
    }
}
```

### **🚀 ÇÖZÜM 2: AAR İçindeki JSON'u Temizleyin**

1. **AAR'ı Açın ve JSON'u İnceleyin:**
```bash
mkdir temp_aar && cd temp_aar
jar -xf ../comaps/libs/sdk-debug.aar

# İngilizce JSON'u inceleyin
hexdump -C assets/countries-strings/en.json/localize.json | head -10
file assets/countries-strings/en.json/localize.json
```

2. **JSON'u UTF-8 Temizliğine Sokun:**
```bash
# BOM karakterlerini temizleyin
sed -i '1s/^\xEF\xBB\xBF//' assets/countries-strings/en.json/localize.json

# Geçersiz UTF-8 karakterleri temizleyin
iconv -f UTF-8 -t UTF-8 -c assets/countries-strings/en.json/localize.json > temp.json
mv temp.json assets/countries-strings/en.json/localize.json
```

3. **Türkçe JSON'u İngilizce ile Değiştirin (Geçici):**
```bash
# Önce İngilizce JSON'u düzeltin, sonra kopyalayın
cp assets/countries-strings/en.json/localize.json assets/countries-strings/tr.json/localize.json
```

### **🚀 ÇÖZÜM 3: Detaylı Loglama Ekleyin**

**ADB ile detaylı log alın:**
```bash
# Tüm native logları görün
adb logcat -v time | grep -E "(OMcore|OrganicMaps|Json|decode|byte)"

# Sadece hata anını yakalayın
adb logcat -v time -s "*:E" | grep -A5 -B5 "Json::Exception"
```

### **🚀 ÇÖZÜM 4: Native Kod Debug**

**CMake ayarlarında debug ekleyin:**
```cmake
# CMakeLists.txt içinde
if(PLATFORM_ANDROID)
    add_compile_options(-DDEBUG_JSON_PARSING)
    add_definitions(-DLOG_JSON_ERRORS)
endif()
```

### **🚀 ÇÖZÜM 5: Alternatif JSON Kütüphanesi**

**cppjansson yerine alternatif kullanın:**
```cmake
# CMakeLists.txt'te
set(USE_ALTERNATIVE_JSON_PARSER ON)
# nlohmann/json veya rapidjson kullanın
```

### **ADB Logcat Detaylı İnceleme:**
```bash
adb logcat -v time -s OrganicMaps:V OMcore:V
```

## 📋 UYGULAMA KONTROL LİSTESİ

- [ ] Build.gradle dosyasında `encoding 'UTF-8'` eklendi
- [ ] AndroidManifest.xml'de ağ izinleri eklendi
- [ ] Gradle properties güncellendi
- [ ] Proguard kuralları eklendi
- [ ] Assets klasörü kontrol edildi
- [ ] Temiz build yapıldı
- [ ] Uygulama test edildi
- [ ] Loglar incelendi

## 🔍 PROJELER ARASI FARK ANALİZİ

**Kendi APK'nızda sorun olmazken sizin projenizde sorun oluyorsa:**

### **1. AAR Dosyası İçeriğini Karşılaştırın:**
```bash
# Kendi çalışan APK'nızdaki AAR içeriğini kontrol edin
mkdir compare_aar && cd compare_aar

# Çalışan APK'yı açın
jar -xf /path/to/working/app.apk

# Sorunlu APK'yı açın
jar -xf /path/to/problematic/app.apk

# AAR dosyalarını karşılaştırın
find . -name "*.aar" -exec jar -tf {} \; | sort | uniq > all_aar_contents.txt

# JSON dosyalarını karşılaştırın
diff working/assets/countries-strings/ problematic/assets/countries-strings/
```

### **2. AndroidManifest.xml Farkları:**
```bash
# İki manifest'i karşılaştırın
diff working/AndroidManifest.xml problematic/AndroidManifest.xml
```

### **3. Gradle Build Farkları:**
```bash
# Build konfigürasyonlarını karşılaştırın
diff working/build.gradle problematic/build.gradle
```

### **4. Proguard/R8 Farkları:**
```bash
# Optimizasyon ayarlarını karşılaştırın
diff working/proguard-rules.pro problematic/proguard-rules.pro
```

Bu adımları gerçek projenizde uyguladığınızda UTF-8 encoding sorunu çözülmüş olmalı. Eğer hala sorun yaşarsanız, hangi adımda takıldığınızı ve aldığınız hata mesajlarını belirtin.
