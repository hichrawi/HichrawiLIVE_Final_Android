# HICHRAWI LIVE — Final Android Client

Final Android client for the HICHRAWI LIVE subscription platform.

## Final product behavior
- No automatic trial.
- Subscription activation screen before access.
- Test code `00000000` is handled by the server and can be disabled there.
- Normal subscription codes are generated/validated by the server; the client accepts up to 13 alphanumeric characters with no separators.
- Home layout: LIVE / VOD / SERIES.
- LIVE: channels, packages, search and favorites.
- VOD / SERIES: loaded dynamically when the optional content API is available.
- Contact: Facebook, TikTok, WhatsApp only; each can be independently enabled/disabled by server settings.
- Channel logos and playback sources remain server controlled.
- Subscription is rechecked while the app is open.
- Remote update check supports optional or forced updates.
- Application ID remains `com.hichrawi.tv`.

## Existing API used by LIVE
- `/api/v1/register-device.php`
- `/api/v1/activate.php`
- `/api/v1/license.php`
- `/api/v1/channels.php`
- `/api/v1/playback-token.php`

## Optional dynamic API
- `/api/v1/app-settings.php`
- `/api/v1/packages.php`
- `/api/v1/content.php`

If optional endpoints are unavailable, LIVE remains usable through the existing API. VOD/Series and remote social/settings controls require their corresponding server endpoint or the remote configuration file.
