# WakeWay backend connection

The repository now contains an expanded Cloudflare Worker API and an Android client that can run in local-first mode when the Worker URL is absent.

## GitHub repository secrets

Add these repository secrets only when you have the corresponding provider account:

- `CLOUDFLARE_ACCOUNT_ID`
- `CLOUDFLARE_API_TOKEN`
- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY`
- `SUPABASE_SERVICE_ROLE_KEY`
- `WAKEWAY_BACKEND_URL`
- `GEMINI_API_KEY`
- `GEMINI_MODEL` (optional; defaults to `gemini-2.5-flash`)
- `RAILRADAR_API_KEY` (optional)

Cloudflare's GitHub CI flow uses an API token and account ID; do not commit the token to the repository.

## Supabase

Run `backend/supabase/schema.sql` once in the Supabase SQL editor for a new project. It creates profiles, places, journeys, family sharing, friends, blocks/reports, conversations, chat messages and subscriptions.

## Deployment

After the secrets exist, pushing changes under `backend/` triggers `.github/workflows/deploy-backend.yml`.

The Android build workflow reads `WAKEWAY_BACKEND_URL`, `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` from GitHub secrets at build time. The service-role key never belongs in the Android project.

## APIs

Core API groups now include health/config, auth, profiles, saved places, synced journeys, place search, weather, live trains, train station search, trains-between-stations, train route geometry, seat availability, coach position, station live board, AI, family sharing, friends/requests, blocking/reporting, chat and subscription status.

Weather uses Open-Meteo. Place search uses Open-Meteo geocoding. Live rail endpoints use the current RailRadar v1 paths when a RailRadar key is configured.