# WakeWay

WakeWay is a destination alarm app that monitors your journey and wakes you before you reach your stop.

**Mobile-first build:** this repository includes a GitHub Actions cloud-build workflow so you can build/test the Android app from an Android phone.

> This is a working foundation, not a claim that every third-party production service is already configured. Provider credentials, signing, and Play Console compliance still need to be set up before public production release.

See:
- `BUILD_FROM_PHONE.md`
- `FEATURE_MATRIX.md`
- `docs/SETUP.md`
- `docs/ARCHITECTURE.md`


**Sleep. We'll wake you.**

A free-first Android destination alarm for train, metro, bus, taxi, auto, car and walking journeys.

## What's included in this starter

- Android 16/API 36 target
- Jetpack Compose UI
- Home, journey setup, active journey, history, settings
- Foreground location journey service
- On-device distance detection
- Progressive notification/vibration/TTS alerts
- Map screen prototype using Leaflet + OpenStreetMap
- Weather endpoint
- Train endpoint adapter
- Gemini AI endpoint adapter
- Supabase auth/database backend adapter
- Family invite API adapter
- Chat API adapter
- Subscription schema
- Cloudflare Worker backend
- Supabase SQL schema
- Privacy website
- Setup and architecture documentation

## Important

This is a **buildable starter architecture**, not a claim that third-party provider credentials, quotas, licenses, OAuth setup, App Store/Play configuration, or production moderation are automatically completed. Those require your own provider accounts/settings.

The core destination alarm is deliberately designed to remain local and should not depend on AI, cloud, weather or live railway APIs.

## Current build baseline

- Android Studio Quail 4 / 2026.1.4 (current stable channel when this project was created)
- Android API 36 target
- Kotlin 2.4.20
- Compose BOM 2026.08.00
- AndroidX Activity 1.13.0
- Local history/settings via SharedPreferences in this first build
- Cloudflare Worker backend
- Supabase backend schema

## First run

1. Open in Android Studio.
2. Copy `local.properties.example` to `local.properties`.
3. Put your Worker URL and public Supabase client values there.
4. Sync Gradle.
5. Run on your Android phone.
6. Grant location and notification permissions.
7. Create a destination with correct coordinates.
8. Start a journey and lock the phone.
9. Test outdoors first.

## Current limitations to finish before public release

- Replace prototype manual coordinate entry with production place search + map picker.
- Harden the foreground-service lifecycle and OEM battery behaviour.
- Add proper OAuth callback/session persistence.
- Add encrypted token storage.
- Add proper family RLS/realtime policies before exposing live locations.
- Add train route/overshoot logic and better transit stop detection.
- Use a production-compliant map/tile provider.
- Finish Play Console privacy/data-safety declarations and testing.
- Add release signing and full QA across Android OEMs.
