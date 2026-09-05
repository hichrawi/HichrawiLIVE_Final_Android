# HICHRAWI LIVE — Final Android Production Project

Application ID: `com.hichrawi.tv`
Version: `2.0.0` / versionCode `10`

## Production flow
1. Short HICHRAWI LIVE welcome screen.
2. Device registration with the existing Hichrawi API.
3. Subscription-code activation only.
4. Active subscription opens the dynamic channel list.
5. Channels, names, order and logo URLs are supplied by the existing API/Admin.
6. Playback requests a short-lived playback URL from the API.
7. The player re-checks subscription state periodically while the app is open.

There is no trial flow in this Android project.

## Backend
The app is configured for:
`https://api.158.179.208.77.sslip.io`

API endpoints used:
- `/api/v1/register-device.php`
- `/api/v1/activate.php`
- `/api/v1/license.php`
- `/api/v1/channels.php`
- `/api/v1/playback-token.php`

## Important
The existing Admin/API remains the backend source of truth. No new Admin is included here.
Future releases should keep the same application ID and signing key and increase `versionCode` so Android can update the installed app without uninstalling it.

## Backend bootstrap
The APK first uses the fallback API and can optionally read:
`https://hichrawi-tv-app.web.app/app-config.json`
with JSON such as `{"api_base":"https://api.example.com"}`.
This allows the VPS/API hostname to be changed later without publishing a new APK, as long as the Firebase Hosting project remains available.
