# Aetherwave

A modern Flutter music and podcast application focused on discovery, multi-source playback, permitted offline downloads, local music, playlists, personalization and optional Supabase cloud features.

## Production baseline

- Flutter stable 3.47.0
- Dart SDK compatible with Flutter 3.47
- Material 3 UI
- `just_audio` 0.10.6 for playback
- Supabase Flutter 2.17.2
- Cached Network Image 4.0.2
- go_router 18.0.1 available for declarative/deep-link routing work
- Android target API 36 for 2026 Google Play submissions

Flutter 3.47 is the current stable release documented by Flutter. Google Play's 2026 requirement for new apps and updates is Android 16 / API 36 or higher. The workflow therefore builds against target API 36.  

## Providers

Aetherwave can aggregate permitted content from configured providers such as Jamendo, Audius, Apple/iTunes previews and Podcast Index. Downloading is only enabled when a provider exposes a permitted downloadable URL; preview-only sources remain preview-only.

## Required GitHub Actions secrets

- `JAMENDO_CLIENT_ID`
- `AUDIUS_API_KEY`
- `AUDIOMACK_CONSUMER_KEY`
- `AUDIOMACK_CONSUMER_SECRET`
- `PODCASTINDEX_API_KEY`
- `PODCASTINDEX_API_SECRET`
- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- `OPENVERSE_API_TOKEN` (optional; the Openverse API may be used without it, but a token can provide more reliable access)

## Supabase

Run `supabase/schema.sql` in the Supabase SQL editor when enabling accounts, cloud sync, sharing, comments, notifications and creator storage.

## Build

GitHub Actions generates the native Android/iOS platform projects, applies platform configuration, resolves dependencies, analyzes the project, then produces Android APK/AAB and an unsigned iOS release artifact.

## Unified music provider layer

Aetherwave searches multiple catalog and audio providers behind one internal provider registry. Provider names are intentionally not exposed in the app UI. Search results are normalized, ranked, and deduplicated before they reach the player/library.

Current integrations include Audiomack, Audius, Jamendo, Free To Use, ccMixter, Apple/iTunes catalog previews, Deezer previews, Openverse openly licensed audio, and rights-filtered Internet Archive audio. Apple/iTunes and Deezer are catalog/preview sources; they are not used as full-track download sources. Downloads are only surfaced when the returned source explicitly exposes a permitted downloadable file.

Openverse results are restricted to CC0/public-domain audio for Aetherwave downloads. Internet Archive downloads are restricted to items whose declared license indicates public-domain/CC0-compatible rights. Other Creative Commons content may be playable only when returned by the provider under its applicable terms.

Audiomack playback uses its official OAuth API and requests the short-lived streaming URL immediately before playback. Audiomack downloads are not exposed as ordinary file downloads because the official API documents playback rather than an application download endpoint. Audius downloads use its documented download endpoint only when the returned track is marked downloadable. Jamendo downloads require audiodownload_allowed. Free To Use downloads use the provider's returned audio file URL. ccMixter downloads are restricted to licenses that permit redistribution. The app does not extract or rip audio from services that do not authorize downloading.
