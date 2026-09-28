# Aetherwave

A music + podcast discovery app that's legal to build a real "download anything, worldwide" experience around, because every source it talks to explicitly allows it.

Rename it, restructure it, whatever — this is a starting scaffold, not a finished product.

## Read this first: what this app can and can't be

You asked for something that looks and feels like Audiomack/Boomplay/YouTube Music, and specifically flagged that search results skew old and don't reflect South African artists. Two different problems, worth separating:

**The visual/UX gap — fixable, and this update goes hard at it.** Sectioned home screen with a greeting header, horizontal "rail" cards, a real play queue with shuffle/repeat/skip, playback speed, a sleep timer, animated Now Playing screen — all in this build. That part is just design and engineering work, no external blocker.

**The catalog gap — not fixable within "legal."** Boomplay, Audiomack's mainstream catalog, and YouTube Music's library are commercially licensed — real deals with Sony/Universal/Warner and African labels, paid for per stream. There is no free or open API that hands out that catalog; the only way to reach it is the streaming services' own apps, or scraping/reverse-engineering them, which is exactly the kind of thing I won't help build regardless of how it's framed. So Aetherwave will never have 2025/2026 Amapiano or Afrobeats chart hits — not because of a bug, but because that music isn't available through any channel I can legally wire up.

What this update **does** do about your two specific complaints:

- **"Old music, 2019 stuff"** — the previous build only ever showed Jamendo's *popularity* ranking, which rewards tracks that have quietly accumulated plays for years. There's now a separate **New Releases** rail using Jamendo's `releasedate_desc` sort — genuinely the newest uploads. Caveat: "newest on Jamendo" still isn't "newest globally released," because Jamendo's own catalog is small, independent, and slow-moving compared to a commercial service.
- **"Searching South Africa shows other countries"** — Jamendo has a real endpoint (`artists/locations`) where artists self-declare a home country. There's now an **"Artists based in [country]"** rail on the Music home screen, defaulting to South Africa, genuinely filtered by that field — not a keyword guess. The honest catch: very few Jamendo artists have set a South African location (it's a mostly European/North American indie catalog), so this rail may often be sparse. It's real filtering, just against a small pool. Archive.org and Openverse have no equivalent field, so a text search still searches everywhere regardless of the country picker.

## What's new in v0.3.0

- **A third music source: Openverse** (api.openverse.org) — a search index across Wikimedia Commons, ccMixter, Free Music Archive and others. More variety per search, keyless, no signup.
- **Auto-downloads the highest quality available**: Jamendo downloads now request `audiodlformat=flac` (their top tier); Archive.org downloads now prefer the item's lossless master (`Flac`/`24bit Flac`/`WAVE`/`AIFF`) over the compressed copy used for streaming. Streaming still uses a smaller lossy format on purpose — fast to start, doesn't burn mobile data — the *download* is what jumps to best-available quality.
- **A real play queue** — tapping any track in a list (search results, a rail, an episode list, your downloaded Library) queues that whole list. Shuffle, repeat (off/all/one), skip next/previous, playback speed (0.75x–2x), and a sleep timer, all on the Now Playing screen.
- **Sectioned Music home**: greeting header, Recently Played, New Releases, Artists based in a country you pick, Popular — replacing the old flat trending list.

## Why these sources

| Source | What it covers | Why downloading is legal |
|---|---|---|
| **[Jamendo](https://developer.jamendo.com)** | ~600k+ Creative Commons-licensed tracks from independent artists worldwide | Each artist opts in/out of allowing downloads (`audiodownload_allowed`) |
| **[Internet Archive](https://archive.org)** | 14M+ audio items: public-domain recordings, old-time radio in dozens of languages, the Live Music Archive (`etree`) | Public-domain or explicitly-licensed uploads, no key required |
| **[Openverse](https://api.openverse.org)** *(new)* | Search index across Wikimedia Commons, ccMixter, Free Music Archive and more | Only surfaces cc0/pdm/by/by-sa licensed results |
| **Apple's iTunes Search/Lookup API + chart feed + raw podcast RSS** | Hundreds of thousands of podcasts across 175+ country storefronts | Podcast episodes are public RSS enclosures by design |

What this is **not**: a way to pull tracks off Spotify/Apple Music/YouTube/Boomplay or anything still commercially licensed — see the section above.

## Permissions — what's requested and why

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Talking to the four sources above |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Background playback + notification, required by Android 14+ |
| `POST_NOTIFICATIONS` | Media3's lock-screen/notification controls need this on Android 13+ |
| `WAKE_LOCK` | Keeps playback from stuttering once the screen locks |
| `WRITE_EXTERNAL_STORAGE` (API ≤28 only) | Inert on your API 29 device; only old Android needs it for `DownloadManager` |

**Deliberately not requested:** `READ_MEDIA_AUDIO`/broad storage-read (not needed to play back what the app itself downloaded), `RECORD_AUDIO` (the mini-player's equalizer bars are a decorative looping animation, not real audio capture).

## Get it running

Still just **one** free credential:

- **Jamendo** — https://developer.jamendo.com/v3.0 → create an app → copy the `client_id`.

### Building via GitHub Actions (no local Android Studio needed)

1. Push this repo to GitHub (or ask me to push it directly once you've created an empty repo).
2. Repo **Settings → Secrets and variables → Actions** → add `JAMENDO_CLIENT_ID`.
3. Push to `main`, or run the workflow manually from the Actions tab — it assembles a debug APK and uploads it as a downloadable build artifact.

### Building locally

```
./gradlew assembleDebug -PJAMENDO_CLIENT_ID=xxx
```

## Architecture

- **Kotlin + Jetpack Compose (Material 3)**, single Activity, MVVM.
- `network/` — `JamendoApi` (search/trending/newest/location-based artist lookup), `ArchiveApi`, `OpenverseApi`, `ItunesPodcastApi`, `AppleChartsApi`, `RssFeedParser`.
- `data/MusicRepository.kt` — fans music search across Jamendo + Archive.org + Openverse in parallel; resolves two Archive.org file URLs per result (stream quality vs. download quality); resolves Jamendo's location endpoint into a country-filtered track list.
- `data/LibraryStore.kt` — offline library + favorites, one JSON file (see that file for when to graduate to Room).
- `playback/PlayerManager.kt` — now owns a real queue (`ExoPlayer.setMediaItems`), shuffle/repeat via the standard `Player` API, playback speed, and a coroutine-based sleep timer, on top of the existing Media3 `MediaSessionService` background playback.
- `download/Downloader.kt` — per-file MIME/extension detection, quality-aware URLs from the repository layer, registers every download with `LibraryStore`.
- `ui/` — `HomeScreen` (nav host, sectioned Music tab, slide-up Now Playing sheet), `NowPlayingScreen` (queue transport controls), `LibraryTab`, `Effects.kt`.

## Honest limitations — read before assuming this "just works"

Written in a sandbox with no network/Android SDK access, so **none of this has been compiled or run**. Verified API contracts, standard Compose/Media3 patterns — but treat the first build as a debugging pass. Specifics:

- Gradle/Compose/Media3 versions may need a bump — Android Studio or the CI log will flag anything stale.
- `minSdk` is 26 (not 24) — `RssFeedParser` uses `java.time`. Your device is API 29, unaffected.
- `LibraryStore` is one JSON file rewritten on every change — fine for hundreds of downloads, not thousands.
- Jamendo's `releasedate_desc`/`audiodlformat=flac`/`artists/locations` parameters are documented on developer.jamendo.com but untested end-to-end by me — if a response shape doesn't match what `JamendoApi.kt` expects, that's the first place to look.
- Openverse results vary a lot in quality/relevance since it's aggregating several very different sources — expect some noise mixed with genuinely good finds.
- The "Artists based in [country]" rail depends entirely on how many Jamendo artists bothered to set a location — see the honest framing above.
- Favorites only cover downloaded items, not arbitrary search results.
- No tests yet.

## Suggested next steps

1. Get it building green in Actions.
2. A real system equalizer (`android.media.audiofx.Equalizer` on the ExoPlayer session) and adaptive Now-Playing color theming from album art (Spotify/YT Music's signature look) — both scoped out of this pass to keep it shippable, both realistic next additions.
3. Swap the manual DI in `NetworkModule` for Koin once the app grows past one screen's worth of dependencies.
4. If genre-based African-music discovery matters more than volume, try seeding Music search with specific tags (amapiano, afrobeats, gqom, kwaito, highlife) rather than relying on the country rail alone — worth testing which actually returns results once you can hit the live API.
