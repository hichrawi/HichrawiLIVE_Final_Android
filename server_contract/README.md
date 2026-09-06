# HICHRAWI LIVE server contract

The Android client keeps all live channels and playback behind the existing VPS API.
For the new Home/Contact controls it optionally reads:

- `GET /api/v1/app-settings.php?device_id=DEVICE_ID`
- `GET /api/v1/content.php?device_id=DEVICE_ID&type=vod`
- `GET /api/v1/content.php?device_id=DEVICE_ID&type=series`

If these optional endpoints are not present, the app remains usable: LIVE and the existing subscription/playback API continue to work, while VOD/Series show an empty-state message and contact settings fall back to remote configuration.

Expected app-settings response:

```json
{
  "ok": true,
  "settings": {
    "app_name": "HICHRAWI LIVE",
    "subtitle": "Live • VOD • Series",
    "logo_url": "",
    "maintenance": false,
    "maintenance_message": "",
    "home_live_title": "LIVE",
    "home_vod_title": "VOD",
    "home_series_title": "SERIES",
    "social": {
      "facebook": "",
      "facebook_enabled": false,
      "tiktok": "",
      "tiktok_enabled": false,
      "whatsapp": "",
      "whatsapp_enabled": false
    }
  }
}
```
