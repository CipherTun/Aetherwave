# Aetherwave

A music + podcast discovery app that's legal to build a real "download anything, worldwide" experience around, because every source it talks to explicitly allows it — now with a proper offline Library, an animated Now-Playing screen, and downloads that other music apps on the phone can see too.

Rename it, restructure it, whatever — this is a starting scaffold, not a finished product.

## What's in v0.2.0

- **Offline Library tab** — every download shows up here immediately (as "pending"), with a live progress bar while it's running, then plays straight from disk with zero network calls once complete. Remove, favorite, and a "Wi-Fi only" download switch all live here too.
- **Files other apps can see** — downloads are written to the public Music/Podcasts folders (not app-private storage) with a correctly-guessed MIME type per file (not hardcoded to `.mp3` — an Archive.org Ogg file or an `.m4a` podcast episode now keeps its real extension and codec tag), so any generic music player or file manager on the device picks them up too.
- **Animated, full-screen Now Playing** — tap the mini-player to slide up a real player screen: live seek bar, favorite toggle, a gently "breathing" album art animation while playing, animated play/pause icon.
- **Decorative equalizer bars** in the mini-player that pulse while something's playing (a looping animation, not real audio capture — see *Permissions* below for why).
- **Shimmer loading skeletons** instead of a bare spinner while search results load.
- **Favorites**, scoped to your downloaded library — heart a track/episode to pin it in the Library tab's Favorites filter.
- **Per-country podcast storefronts** — defaults to South Africa's chart, switchable via the chip row (see prior notes on Apple's iTunes/chart API).

## Why these sources

| Source | What it covers | Why downloading is legal |
|---|---|---|
| **[Jamendo](https://developer.jamendo.com)** | ~600k+ Creative Commons-licensed tracks from independent artists worldwide | Each artist opts in/out of allowing downloads (`audiodownload_allowed`). The app only shows a download button when that flag is true. |
| **[Internet Archive](https://archive.org)** | 14M+ audio items: public-domain recordings, old-time radio in dozens of languages, and the Live Music Archive (`etree`) — 250k+ concerts artists explicitly gave permission to tape and trade | Public-domain or explicitly-licensed uploads on a public read API, no key required |
| **Apple's iTunes Search/Lookup API + chart feed + raw podcast RSS** | Hundreds of thousands of podcasts across 175+ country storefronts | Podcast episodes are public RSS enclosures by design; downloading one for offline listening is literally what a podcast client does |

What this is **not**: a way to pull tracks off Spotify/Apple Music/YouTube or anything still commercially licensed. That's a different, illegal category of app and this codebase doesn't do it.

## Permissions — what's requested and why

Kept deliberately short. Every entry below is load-bearing; nothing was added just because it's a "professional" app:

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Talking to Jamendo/Archive.org/Apple, and checking connectivity |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Lets playback keep running (and show a notification) when you leave the app — required by Android 14+ for any service that plays audio in the background |
| `POST_NOTIFICATIONS` | The lock-screen/notification playback controls Media3 generates need this on Android 13+ |
| `WAKE_LOCK` *(new)* | Lets ExoPlayer hold a wake lock while actively playing, so streaming doesn't stutter once the screen locks |
| `WRITE_EXTERNAL_STORAGE` (API ≤28 only) | Only old Android versions need this for `DownloadManager` to write to the public Music folder; automatically inert on your API 29 device |

**Deliberately not requested:** `READ_MEDIA_AUDIO` / broad storage-read permission. Playing back what the app itself downloaded doesn't need it — `DownloadManager` grants the app read access to its own downloads automatically. It would only be needed for a feature this build doesn't have (scanning *all* audio already on the device, not just what Aetherwave downloaded) — worth adding later if you want that, but not before.

**Not used at all:** `RECORD_AUDIO`. A *real* audio-reactive visualizer (reading actual waveform data) needs Android's `Visualizer` API, which carries privacy-sensitive audio-capture permission requirements. The equalizer bars here are a decorative looping animation instead — visually similar, no permission cost. Worth swapping in a real one later if you want the accuracy and don't mind the permission prompt.

## Get it running

You only need **one** free credential:

- **Jamendo** — sign up at https://developer.jamendo.com/v3.0, create an app, copy the `client_id`. (You already have this.)

### Building via GitHub Actions (no local Android Studio needed)

1. Push this repo to GitHub (or ask me to push it directly once you've created an empty repo).
2. In the repo's **Settings → Secrets and variables → Actions**, add one repository secret: `JAMENDO_CLIENT_ID`.
3. Push to `main` (or run the workflow manually from the Actions tab). The `build.yml` workflow assembles a debug APK and uploads it as a build artifact you can download straight from the Actions run — no laptop required.

### Building locally (if you ever get laptop access)

```
./gradlew assembleDebug -PJAMENDO_CLIENT_ID=xxx
```

(There's no `gradlew` wrapper jar checked in — see the note in `build.yml`. Run `gradle wrapper` once if you want one locally, or just use the CI path above.)

## Architecture

- **Kotlin + Jetpack Compose (Material 3)**, single Activity, MVVM.
- `network/` — independent Retrofit clients: `JamendoApi`, `ArchiveApi`, `ItunesPodcastApi` (search/lookup), `AppleChartsApi` (per-country trending), plus `RssFeedParser` (parses a show's actual RSS feed for its episode list — not a Retrofit call, just OkHttp + Android's built-in `XmlPullParser`).
- `data/MusicRepository.kt` — fans music search out to Jamendo + Archive.org in parallel and podcast search/charts out to Apple's endpoints, normalizing everything into `Track` / `PodcastShow` / `PodcastEpisode`.
- `data/LibraryStore.kt` — the offline library + favorites, persisted as one JSON file (no Room/SQLite dependency — see the note in that file for when to graduate to one). Polls `DownloadManager` for real byte-level progress rather than guessing.
- `playback/` — a Media3 `MediaSessionService` (`PlaybackService`, now with `WAKE_MODE_NETWORK`) for real background playback + lock-screen controls, plus `PlayerManager`, a thin app-side wrapper around a `MediaController` connected to it, with position/seek support for the Now Playing screen.
- `download/Downloader.kt` — hands URLs to Android's own `DownloadManager` system service, guessing the correct file extension/MIME type per URL instead of assuming mp3, and registers every download with `LibraryStore` the moment it's enqueued.
- `ui/` — `HomeScreen` (nav host + the slide-up Now Playing sheet), `MusicTab`/`PodcastTab`/`LibraryTab`, `NowPlayingScreen` (expanded player), `Effects.kt` (shimmer + equalizer bar primitives shared across screens).

## Honest limitations — read before assuming this "just works"

I wrote this in a sandbox with no network access and no Android SDK, so **none of this has been compiled or run**. It follows current, verified API contracts and standard, well-documented Compose/Media3 patterns — but treat the first build as a debugging pass, not a finished app. Likely rough edges:

- Gradle/Compose/Media3 version numbers may need a bump — Android Studio (or the CI log) will tell you if something's stale.
- `minSdk` is 26, not 24 — `RssFeedParser` uses `java.time` to parse podcast publish dates, which needs API 26+ without adding core library desugaring. Your own device is API 29, so this doesn't affect you; it just narrows compatibility with very old devices.
- `LibraryStore` is a single JSON file rewritten on every change — fine for hundreds of downloads, not thousands. Swap for Room if the library gets huge.
- No playback queue — tapping a track plays just that one item; there's no "up next"/autoplay-through-a-list yet. The Now Playing screen deliberately has no skip-next/previous buttons rather than fake ones that wouldn't do anything.
- No per-country filtering for **music** — neither Jamendo nor Archive.org reliably expose "country of origin" as a queryable field, so that side still filters by language/tag instead. Podcasts, via Apple's storefronts, genuinely do have per-country browsing.
- Some independent podcast feeds are still plain `http://` — the manifest allows cleartext traffic so those don't silently fail, but a handful of very old or broken feeds may still not parse cleanly; `RssFeedParser` skips a malformed episode rather than crashing the whole list.
- Favorites only cover downloaded items, not arbitrary search results — heart-ing something you haven't downloaded yet isn't wired up.
- No tests yet.

## Suggested next steps

1. Get it building green in Actions, fix whatever the compiler flags.
2. A real play queue (up next / autoplay through search results or a show's episode list) — the biggest remaining gap vs. Audiomack/YouTube Music.
3. Swap the manual DI in `NetworkModule` for something like Koin once the app grows past one screen's worth of dependencies.
4. If you want the "on-device library" side too (see/import audio already on the phone, not just what Aetherwave downloaded), that's the point to add `READ_MEDIA_AUDIO` + a `MediaStore.Audio` query — deliberately left out of this pass, see *Permissions* above.
