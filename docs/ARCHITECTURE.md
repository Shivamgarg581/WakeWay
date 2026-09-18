# WakeWay architecture

## Principle

The critical safety path is local:

`GPS -> distance/confidence -> alert engine -> notification/vibration/TTS`

Cloud features are optional enhancements.

## Cloud path

`Android -> Cloudflare Worker -> Supabase / Gemini / Open-Meteo / RailRadar`

## Offline

If the network fails, an active journey can continue because destination coordinates and alarm logic are stored on-device.

## Future upgrades

1. Replace hand-rolled HTTP auth UI with Supabase Kotlin 3.8.0 if desired.
2. Add encrypted local storage for sensitive cached cloud tokens.
3. Move family tracking to Supabase Realtime after fine-grained RLS policies are completed.
4. Add a proper OAuth deep-link session exchange for Google login.
5. Add route-aware arrival detection, overshoot detection and adaptive GPS intervals.
6. Add FCM token registration for cloud notifications.
7. Add a production map/tile provider and offline map caching within its license.
8. Add billing provider integration only after the free product is stable.

## Important safety requirement

AI, cloud APIs and railway APIs must never be a single point of failure for the final destination alarm.
