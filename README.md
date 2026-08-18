# Portio — IPTV Player for Android

A modern IPTV player for Android, built with Kotlin + Jetpack Compose + ExoPlayer (Media3). Portio supports **Stalker portal**, **M3U** and **Xtream Codes** sources, and is fully bilingual (**Türkçe / English**). Every push to `main` triggers a GitHub Actions build that produces and publishes the latest **Release APK**.

## Features

- **Multiple source types** — Add any number of Stalker portals, M3U playlists and Xtream Codes servers. Activate one source at a time with a quick switcher.
- **Live TV** — Genre-based channel lists, channel logos, channel numbers and fast zapping. Hide unwanted groups from Live TV and Home.
- **VOD (Movies & Series)** — Posters, cast/director info, synopsis, IMDb ratings, and season/episode structure for series.
- **Audio & Subtitle selection** — Multiple audio tracks and embedded subtitles via ExoPlayer track selection; default language preferences and external XMLTV EPG support.
- **EPG guide** — Per-channel program guide with the current program highlighted, catch-up viewing of past broadcasts, and reminders.
- **Favorites** — Persist favorite channels and VOD content.
- **Library** — Watch later, watched history, favorites and custom lists in one screen.
- **Picture-in-Picture (PiP) & background playback** — Playback continues with the screen off via a foreground media service.
- **Casting** — Chromecast support to send content to the big screen.
- **Quick zapping & pre-buffering** — The next channel is pre-buffered in a second ExoPlayer, making channel changes nearly instant.
- **Time zone correction** — Manual offset (+3, −2, etc.) between the provider's clock and yours prevents EPG shifts.
- **Rate limit & cooldown management** — Stalker portals are sensitive to burst requests. The app enforces a minimum request interval (adjustable, default 700 ms), detects server blocks and starts an automatic cooldown with a countdown, and caches category/channel lists in memory.
- **Stream reliability** — TS live-stream optimizations (access units, HD audio, non-IDR keyframes) and enlarged buffers reduce stutter; a fallback forces HLS/MPEG-TS resolution when a channel won't open.
- **Accounts & cloud sync** — Optional email/Google sign-in stores your portals, settings, favorites and watch history in Firebase and syncs them across devices.
- **Privacy & safety** — PIN lock, adult-content filtering, and a per-source content filter.
- **Integrations** — TMDB enrichment (posters, trailers, real episode names).

## Project layout

```
stalkerapp/
├── app/src/main/java/com/stalkerapp/
│   ├── data/          # Stalker API, repositories, models, storage
│   ├── playback/      # ExoPlayer management, prebuffer, background service
│   ├── ui/            # Compose screens (onboarding, live, vod, player, settings, …)
│   ├── util/          # Localization (L10n), misc helpers
│   └── widget/        # Home-screen favorites widget
├── .github/workflows/ # APK build & release pipeline
└── gradle/            # Gradle wrapper
```

## Stalker portal flow

The app follows the standard Stalker middleware flow:

1. `handshake` → session token
2. `get_profile` → MAC validation, server addresses, time zone
3. `get_genres` + `get_all_channels` → TV categories and channels
4. `get_categories` + `get_ordered_list` → VOD
5. `get_season_list` / `get_episodes` → series seasons/episodes
6. `create_link` → VOD stream URL
7. `get_epg_info` → EPG programs

All requests are sent with the `JsHttpRequest=1-xml` wrapper and the `js` response field is unwrapped automatically. Channel stream URLs are taken from the channel's `cmd` field (`ffmpeg http://…`) when available, otherwise generated from the profile's server address as `http://{server}/{channelId}`.

## Getting the APK

After pushing to `main`:

1. Open the **Actions** tab on GitHub and select the "Build APK" workflow.
2. When the run finishes, download the **portio-release** artifact (or grab the APK from the "latest" GitHub Release).
3. The APK is produced at `app/build/outputs/apk/release/` and is signed with the release keystore.

You can also trigger a build manually via `workflow_dispatch`.

## Building locally

Requirements: JDK 17 and Android SDK (compileSdk 35).

```bash
./gradlew assembleRelease
```

Local builds need the release keystore at `keystore/release.p12` and `app/google-services.json`. In CI these are supplied via GitHub Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `GOOGLE_SERVICES_JSON`) and decoded during the build — they are **not** stored in the repository.

## Legal notice

This app is intended for use only with IPTV providers you are subscribed to. Content ownership belongs to your provider.
## Planned Features

- **Harici altyazı (.srt/.vtt)** — Portal altyazısı yoksa kullanıcı dosya ekleyebilir.
- **Intro atlama (skip intro)** — TMDB + bölüm süresi ile otomatik intro tespiti.
- **AFR (Auto Frame Rate)** — TV’lerde judder azaltma tam entegre edilebilir (Afr.kt).
- **Favori kanal sıralaması** — Sürükle-bırak ile özel kanal numaralandırma.
- **Gelişmiş catch-up / timeshift** — Panel bazlı utc/lutc parametreleri, takvimden geçmiş gün seçimi.
- **Offline indirme** — Film/bölümü Wi-Fi’de indirip uçakta izleme.
- **Gelişmiş filtreler** — Yıl, IMDB puanı, dil, “yeni eklenenler”, “en çok izlenen”.
- **İçerik öneri motoru** — TMDB + izleme geçmişine göre kişiselleştirilmiş öneriler.
- **Android TV & uzaktan kumanda**
  - 10-foot UI — büyük poster grid, focus navigation.
  - TV ana ekran satırları (Android TV Home Channels).
  - Kanal önizleme — odaklanınca mini canlı önizleme.
  - Numara tuşu ile kanal — “101” yaz → kanal aç.
  - HDMI-CEC — TV kumandası ile ses/kanal kontrolü.
- **Çoklu cihaz “devam et”** — Telefonda bırak, TV’de devam et.
- **Akıllı önbellek yönetimi** — VOD katalog boyutuna göre disk kotası.
  - Delta senkron — tüm kataloğu değil, sadece değişenleri çek.
  - Kaynak sağlık monitörü — “Bu kaynak 3 gündür yanıt vermiyor” uyarısı.
  - Otomatik yedek kaynak — bir kanal düşerse alternatif URL dene.
- **VPN uyumluluk modu** — DNS/redirect sorunları için gelişmiş ağ ayarları.
  - Proxy / DNS override — Sağlayıcı engeli için DoH/DoT veya SOCKS ayarı.
  - Stalker MAC yönetimi — Birden fazla MAC profili, hızlı geçiş.
- **Sezon/bölüm indirme kuyruğu** — Tüm sezonu sırayla indir.
  - Altyazı indirme — OpenSubtitles entegrasyonu.
