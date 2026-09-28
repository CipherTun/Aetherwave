# Aetherwave (com.aether.wave)
Flutter app (iOS + Android). Sources: Apple iTunes Search + Apple RSS charts (per country, no key), Jamendo (Creative Commons, downloads), Audius (full streams), Deezer (charts/search previews). Countries load live from restcountries.com; default = device region.
Features: multi-source search, country charts, playlists, likes, history, queue, shuffle/repeat, speed, sleep timer, lyrics (lrclib.net), offline downloads with progress, light/dark, background + lock-screen playback.
GitHub secrets: JAMENDO_CLIENT_ID (devportal.jamendo.com), AUDIUS_API_KEY (optional, api.audius.co/plans). Push -> Actions builds APK/AAB/iOS.
iTunes and Deezer give 30s previews only; only Jamendo tracks whose artist allows it can be downloaded.
Release signing (Android keystore, Apple certificates) is yours to add.

Backend (optional, Supabase): run supabase/schema.sql, then add secrets SUPABASE_URL and SUPABASE_ANON_KEY. Enables accounts, library sync across devices, shared playlists. Without them the app runs in guest mode.
