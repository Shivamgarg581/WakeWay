# WakeWay setup from zero cost

## 1. Android

Install a current Android Studio stable release.

Open this folder as a Gradle project.

The project targets Android 16 / API 36 because Google Play requires new apps and updates to target API 36+ from August 31, 2026.

Create `local.properties` from `local.properties.example`.

## 2. Supabase

Create a free Supabase project.

Run `backend/supabase/schema.sql` in SQL Editor.

Enable Email auth.

Optional later: enable Google OAuth and set the redirect to:

`wakeway://auth/callback`

Free plan limits change over time; check the provider dashboard before production.

## 3. Cloudflare Worker

From `backend/`:

```bash
npm install
wrangler login
wrangler secret put SUPABASE_URL
wrangler secret put SUPABASE_PUBLISHABLE_KEY
wrangler secret put SUPABASE_SERVICE_ROLE_KEY
wrangler secret put GEMINI_API_KEY
wrangler secret put RAILRADAR_API_KEY
npm run deploy
```

Copy the worker URL into Android `local.properties`.

## 4. AI

The Worker calls Gemini. Keep the API key only as a Worker secret.

## 5. Weather

The Worker calls Open-Meteo. No key is required for its free/non-commercial endpoint; check current terms for your eventual commercial use.

## 6. Maps

The prototype includes a Leaflet + OpenStreetMap map renderer in a WebView. For production, use a compliant OSM-derived tile provider and follow the tile provider's current usage/caching/attribution policy. Do not treat public OSM infrastructure as an unlimited free CDN.

## 7. Live train

The train screen calls the Worker. If `RAILRADAR_API_KEY` is missing, the UI stays functional and says that live train data is unavailable. The destination alarm itself remains device-local.

## 8. Notifications/location

On first journey, grant location and notification permissions.

For best reliability, users may need to allow the app to run its active foreground location service and exclude it from battery restrictions on some Android OEMs.

## 9. Google Play

The project targets API 36. Before production, finish:
- privacy policy
- Data safety form
- background/foreground location justification
- notification permission flow
- device/OEM testing
- release signing
- closed testing
- store listing
