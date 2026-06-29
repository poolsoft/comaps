# 🚗 CoMaps CarLauncher Proje Analizi

## Genel Bakış

CarLauncher, CoMaps (Organic Maps) uygulamasının **Android** tarafında çalışan, araç içi kullanım için özelleştirilmiş bir **launcher** (ana ekran) uygulamasıdır. CoMaps'in temel Activity'sini (`MwmActivity`) extend ederek onun harita motorunu ve routing altyapısını kullanır, üzerine kendi widget tabanlı arayüzünü inşa eder.

**Dizin:** `android/app/src/carlauncher/`

---

## 📁 Dosya Yapısı

```
android/app/src/carlauncher/
├── AndroidManifest.xml
├── assets/fonts/
├── res/
│   ├── drawable/                   # SVG ikonlar
│   ├── layout/                     # 39 XML layout
│   │   ├── activity_car_launcher.xml
│   │   ├── activity_neon_dashboard.xml
│   │   ├── fragment_widget_panel.xml
│   │   ├── fragment_app_dock_*.xml
│   │   ├── fragment_music_player.xml
│   │   └── ...
│   ├── values/strings.xml          # İngilizce metinler
│   ├── values-tr/strings.xml       # Türkçe çeviriler
│   └── xml/carlauncher_prefs.xml   # Ayarlar
└── java/app/organicmaps/carlauncher/
    ├── CarLauncherActivity.java     # ★ ANA ACTIVITY
    ├── CarLauncherInterface.java    # Interface
    ├── CarLauncherSettings.java     # Ayarlar
    ├── CarCrashLogger.java          # Crash log
    ├── MediaNotificationListener.java
    ├── backup/         → LauncherBackupManager.java
    ├── dock/           → 8 dosya (AppDock, uygulama kısayolları)
    ├── hardware/       → CarHardwareManager.java
    ├── music/          → 13 dosya (müzik çalar, Bluetooth, Radyo)
    ├── obd/            → 8 dosya (OBD-II araç tanılama)
    ├── overlay/        → OverlayWindowManager.java
    ├── radio/          → RadioManager.java
    ├── telemetry/      → TelemetryManager.java
    ├── ui/             → 26 dosya (dashboard, panel, widget kontrol)
    ├── voice/          → VoiceCommandService.java, VoiceVisualizerView.java
    └── widgets/        → 17 dosya (widget sistemi, görünümler)
```

---

## 🏗 Mimari Katmanlar

```
┌──────────────────────────────────────────────────────────────┐
│                    CarLauncherActivity                        │
│            (extends MwmActivity - CoMaps ana Activity)        │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────┐  ┌────────────────────────────┐    │
│  │     Widget Panel    │  │        App Dock             │    │
│  │  (WidgetPanelFrag.) │  │   (AppDockFragment)         │    │
│  │                     │  │                              │    │
│  │  ┌───────────────┐  │  │  ┌──────────────────────┐   │    │
│  │  │ SpeedWidget   │  │  │  │ AppShortcut (dock)    │   │    │
│  │  │ NavigationW.  │  │  │  │ AppDrawer (çekmece)   │   │    │
│  │  │ ClockWidget   │  │  │  └──────────────────────┘   │    │
│  │  │ MusicWidget   │  │  │                              │    │
│  │  │ WeatherWidget │  │  │  ┌──────────────────────┐   │    │
│  │  │ DirectionW.   │  │  │  │ FloatingButton (GPS) │   │    │
│  │  └───────────────┘  │  │  └──────────────────────┘   │    │
│  └─────────────────────┘  └────────────────────────────┘    │
│                                                              │
│  ┌──────────────────────────────────────────────────┐        │
│  │          NeonDashboardActivity                    │        │
│  │  (Fütüristik hız göstergesi + navigasyon + OBD)   │        │
│  └──────────────────────────────────────────────────┘        │
│                                                              │
│  ┌──────────────────────────────────────────────────┐        │
│  │          TelemetryManager (Observer Pattern)      │        │
│  │  LocationState | NavigationState | ObdState       │        │
│  │  → Tüm widget'lara veri sağlar                    │        │
│  └──────────────────────────────────────────────────┘        │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│                    CoMaps Core (DEĞİŞTİRİLMEDİ)              │
│  MwmActivity | LocationHelper | RoutingController | ...     │
└──────────────────────────────────────────────────────────────┘
```


## 🎯 Temel Özellikler

### 1️⃣ Ana Launcher (HOME Ekranı)
- `CarLauncherActivity` → `MwmActivity`'yi extend eder
- Android'de **default HOME** olarak çalışır (IntentFilter: HOME + LAUNCHER)
- 3 layout modu: Normal / No Widgets / Full Screen
- Gece karartma katmanı (Night Dim)

### 2️⃣ Widget Sistemi

| Widget | Sınıf | Açıklama |
|--------|-------|----------|
| **Hız Göstergesi** | `SpeedWidget` | Dijital/Analog hız, limit uyarısı |
| **Navigasyon** | `NavigationWidget` | Dönüş, mesafe, talimat, ETA |
| **Dijital Saat (M3)** | `Material3ClockWidget` | Material 3, Cross Boxed font |
| **Klasik Saat** | `ClockWidget` | Dijital/Analog + hava durumu |
| **Müzik Çalar** | `MusicWidget` | Kontroller + görselleştirici |
| **Hava Durumu** | `WeatherWidget` | OpenWeatherMap API |
| **Pusula** | `DirectionWidget` | Yön (K/KD/D/GD/G/GB/B/KB) |
| **App Shortcut** | `AppShortcutWidget` | Uygulama başlatma |
| **System Widget** | `SystemAppWidget` | Android AppWidget |

### 3️⃣ Navigasyon Sistemi
- `TelemetryManager` (LocationListener) → GPS, Navigasyon, OBD
- `CarDirection` enum: TURN_RIGHT, TURN_LEFT, U_TURN, ROUNDABOUT...
- Hız limiti aşım uyarısı (kırmızı/turuncu)
- Stale GPS koruması

### 4️⃣ OBD-II Araç Tanılama
RPM, sıcaklık, voltaj, motor yükü - Bluetooth/USB üzerinden

### 5️⃣ Müzik (Çoklu Adaptör)
Internal, Bluetooth, HCN, XY Auto, Android Media Session

### 6️⃣ Neon Dashboard
Fütüristik hız göstergesi + navigasyon + OBD

---

## 🔌 CoMaps Entegrasyonu

| CarLauncher | CoMaps Core |
|------------|-------------|
| `extends MwmActivity` | Ana Activity |
| `LocationHelper` | GPS verisi |
| `RoutingController` | Rota bilgisi |
| `@layout/activity_map` | Harita görünümü |

---

## ⚙️ Ayarlar

Görünüm → Müzik → Hava Durumu → Auto Launch → Dock → Asistan → Yedekleme → Hakkında

---

## 🔧 Yapılan Değişiklikler

### ✅ Hardcode String'ler Resource'a Taşındı
Widget isimleri, navigasyon etiketleri, hava durumu açıklamaları, yön kodları, dialog metinleri `strings.xml`'e taşındı.

### ✅ Status Bar Düzeltmesi
- `onResume()` override edildi → arka plandan dönüşte çalışır
- `onWindowFocusChanged()` eklendi → odak değişimlerinde çalışır
- Settings'te değişiklik → zaten çalışıyordu

### ✅ Dokümantasyon
- Bu analiz dosyası oluşturuldu (`Carlauncher_Analiz.md`)

---

## 📝 Notlar

- Widget isimleri (`WidgetRegistry`) context olmadığı için hala hardcode olabilir
- Bazı Türkçe karakter içeren string'ler hala Java kodunda olabilir (UTF-8 kodlama sorunu)
- Status bar ayarı MwmActivity tarafından sıfırlanabilir, test edilmeli
