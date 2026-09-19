# CoMaps Auto V2 Proje Kuralları

1. **Upstream / Çekirdek Dokunulmazlığı:**
   - Upstream Organic Maps kaynaklarına (`android/sdk/`, `android/app/src/main/`, `libs/`, `drape/` ve diğer çekirdek dizinler) KESİNLİKLE DOKUNULMAZ.
   - Tüm yeni geliştirmeler, hata düzeltmeleri, UI değişiklikleri ve özelleştirmeler İSTİSNASIZ YALNIZCA `android/app/src/carlauncher/` dizini altında yapılmalıdır.
   - Upstream güncellemelerinde (pull / rebase) çakışma (conflict) çıkmaması zorunludur.

2. **Dil ve Karakter Kuralı:**
   - Kullanıcıyla iletişimde daima Türkçe konuşulur, planlar ve dökümanlar Türkçe yazılır.
   - Kod içi tanımlarda (değişken, fonksiyon, sınıf adları ve kod içi yorumlar) Türkçe karakter (ç, ğ, ı, ö, ş, ü) KESİNLİKLE KULLANILMAZ (Kullanıcıya gösterilen XML/UI Stringleri hariç).

3. **Kullanıcı Onayı ve Açıklama:**
   - Büyük değişikliklerde kullanıcı "kodla" demeden kod yazılmaz.
   - "Açıklar mısın" dendiğinde sadece açıklama yapılır, kodlamaya girişilmez.
   - Yapılan her kod değişikliğinden sonra syntax kontrolleri yapılır.
