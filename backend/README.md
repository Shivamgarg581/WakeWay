# WakeWay Worker

## Free-first services

- Cloudflare Workers: API proxy, caching, rate limiting.
- Supabase: PostgreSQL + Auth + optional Realtime.
- Gemini: optional AI provider.
- Open-Meteo: weather.
- Nominatim/OSM: prototype place search/map ecosystem; follow current usage policies.
- RailRadar: optional Indian rail data; use its current API terms and quota.
- FCM/Firebase: optional notifications/analytics/crash reporting.

## Secrets

Set secrets from the worker directory:

```bash
wrangler secret put SUPABASE_URL
wrangler secret put SUPABASE_PUBLISHABLE_KEY
wrangler secret put SUPABASE_SERVICE_ROLE_KEY
wrangler secret put GEMINI_API_KEY
wrangler secret put RAILRADAR_API_KEY
```

Do not put secrets in `wrangler.toml` or the Android APK.

## Local dev

```bash
npm install
npm run dev
```

## Deploy

```bash
npm run deploy
```

Then put the deployed worker URL into Android `local.properties`:

```properties
WAKEWAY_BACKEND_URL=https://YOUR-WORKER.workers.dev
SUPABASE_URL=https://YOUR-PROJECT.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_PUBLIC_KEY
```

The Android client only needs public Supabase client configuration. The service-role key stays on the Worker.
