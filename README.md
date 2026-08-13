# Stalker Portal Player

Android için Kotlin + ExoPlayer (Media3) tabanlı Stalker portal IPTV oynatıcısı. Bu depo, GitHub Actions üzerinden her `main` push'unda otomatik olarak **Release APK** üretir.

## Özellikler

- **Çoklu Profil / Portal Desteği** — Birden fazla Stalker portalı kaydedin, her portal için ayrı MAC adresi üretilir/hatırlanır. Bazı portallar için kullanıcı adı/şifre de desteklenir.
- **Canlı TV (Live TV)** — Kategori bazlı (Spor, Haber, Ulusal vb.) kanal listeleme, kanal logoları, kanal numarası ve hızlı kanal değiştirme (zapping).
- **VOD (Film & Dizi)** — Poster, oyuncu/yönetmen bilgisi, konu, IMDb puanı ve dizi için sezon/bölüm yapısı.
- **Ses & Altyazı Seçimi** — Çoklu ses dili (Audio Tracks) ve gömülü altyazı seçimi (ExoPlayer `TrackSelection`).
- **Favoriler** — Kanal ve VOD içeriklerini favorilere ekleme (kalıcı).
- **Picture-in-Picture (PiP)** ve **Arka Plan Oynatma** — Ön plan medya servisi ile ekran kapansa da oynatma sürer.
- **Akıllı Arama & Filtreleme** — Kanal ve VOD listelerinde anında filtreleme.
- **Hızlı Geçiş (Quick Zapping) & Ön Bellekleme (Pre-buffering)** — Sıradaki kanal arka planda ikinci bir ExoPlayer ile hazırlanır; kanal değişimi saniyenin altına iner.
- **Zaman Dilimi (Timezone) Düzeltme** — Sağlayıcı sunucusunun saati ile kullanıcının yerel saati arasındaki fark için manuel ofset (+3, -2 vb.) → EPG kaymalarını önler.
- **Rate Limit & Cooldown Yönetimi** — Stalker portalları ardışık isteklere duyarlıdır; Handshake/Load istekleri çok sık gönderilirse sunucu cihazı 5 dakikalığına engeller. Bu uygulama:
  - İstekler arası minimum bekleme süresi (ayarlanabilir, varsayılan 700 ms) uygular.
  - Sunucu engeli algılandığında otomatik cooldown başlatır ve geri sayımı gösterir.
  - Kategori/kanal listelerini bellekte önbellekler (cache) → tekrarlanan istekleri azaltır.
- **ExoPlayer TS Optimizasyonu** — `.ts` canlı yayınlarında takılmayı azaltmak için `DefaultExtractorsFactory` üzerinde TS `FLAG_DETECT_ACCESS_UNITS`, `FLAG_ENABLE_HD_AUDIO`, `FLAG_ALLOW_NON_IDR_KEYFRAMES` bayrakları ve büyütülmüş buffer ayarları kullanılır.
- **EPG** — Kanal bazlı program rehberi (şu an oynayan program vurgulanır).

## Yapı

```
stalkerapp/
├── app/src/main/java/com/stalkerapp/
│   ├── data/          # Stalker API, repository, modeller, depolama
│   ├── playback/      # ExoPlayer yönetimi, prebuffer, arka plan servisi
│   └── ui/            # Compose ekranları (login, live, vod, player, …)
├── .github/workflows/ # APK build
└── gradle/            # Gradle wrapper
```

## Stalker Portal Akışı

Uygulama standart Stalker middleware akışını izler:

1. `handshake` → oturum token'ı
2. `get_profile` → MAC doğrulama, sunucu adresleri, saat dilimi
3. `get_genres` + `get_all_channels` → TV kategorileri ve kanallar
4. `get_categories` + `get_ordered_list` → VOD
5. `get_season_list` / `get_episodes` → dizi sezon/bölümleri
6. `create_link` → VOD akış URL'si
7. `get_epg_info` → EPG programları

Tüm istekler `JsHttpRequest=1-xml` sarmalayıcısıyla gönderilir ve `js` yanıt alanı otomatik açılır. Kanal akış URL'si öncelikle kanalın `cmd` alanından (`ffmpeg http://...`) çıkarılır; yoksa `profile.server_info` içindeki sunucu adresinden `http://{server}/{channelId}` olarak üretilir. Not: Bazı portallarda VOD/kanal URL biçimi sağlayıcıya özeldir; `cmd` alanı sağlanmıyorsa sunucu adresine göre fallback üretilir.

## APK Alma

`main` dalına push yaptıktan sonra:

1. GitHub'da **Actions** sekmesine girin → "Build APK" işini seçin.
2. İş bitince **stalkerapp-release** artifact'ini indirin.
3. APK `app/build/outputs/apk/release/` altında `stalkerapp-release.apk` olarak üretilir (debug anahtarıyla imzalıdır).

Ayrıca `workflow_dispatch` ile istediğiniz zaman manuel de tetikleyebilirsiniz.

## Kurulum / Derleme

Gereksinimler: JDK 17, Android SDK (compileSdk 35).

```bash
./gradlew assembleRelease
```

## Yasal Uyarı

Bu uygulama yalnızca kendi aboneliğiniz olan IPTV sağlayıcıları ile kullanılmalıdır. İçerik sahipliği sağlayıcınıza aittir.
