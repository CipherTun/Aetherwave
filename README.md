# Aetherwave

A Flutter music discovery and offline-listening app for Android and iOS.

## Included in this build

- Multi-source search: Apple iTunes previews, Apple country charts, Jamendo, Audius and Deezer previews.
- Personalized Home shelves built from likes, recent listening, followed artists and selected genres.
- Artist and album pages, artist radio, queues, shuffle/repeat and playback speed.
- Full-screen player with progress seeking, background/lock-screen playback, lyrics and sleep timer.
- Local device audio import.
- Offline downloads only when a source exposes a permitted download URL (currently Jamendo downloads).
- Download progress and local offline playback.
- Smart Downloads for permitted liked/recent tracks.
- Search categories for songs, artists, albums and Podcast Index feeds/episodes.
- Country chart selector.
- Library sections for likes, playlists, downloads, local music, artists, albums and history.
- Supabase accounts and cross-device library sync.
- Shared playlists.
- Optional Supabase social layer: comments, user follows and notifications.
- Optional Creator Studio: upload original audio into a Supabase Storage bucket and publish a creator release record.
- Light/dark/system theme.

## Deliberate boundaries

Aetherwave does not bypass streaming services' restrictions or turn preview-only catalogs into downloadable files. It only downloads when the provider exposes a permitted download URL. Video extraction and copyrighted catalog ripping are not implemented.

The app does not claim to have a proprietary audio DSP/equalizer or music-identification backend without the required provider/API. Those require dedicated native/provider integrations rather than fake UI controls.

## Build secrets

Required/optional GitHub Actions secrets:

- `JAMENDO_CLIENT_ID`
- `AUDIUS_API_KEY` (optional)
- `PODCASTINDEX_API_KEY` (optional)
- `PODCASTINDEX_API_SECRET` (optional)
- `SUPABASE_URL` (optional)
- `SUPABASE_ANON_KEY` (optional)

Run `supabase/schema.sql` once in the Supabase SQL Editor to enable accounts, sync, shared playlists, comments, notifications and Creator Studio uploads.
