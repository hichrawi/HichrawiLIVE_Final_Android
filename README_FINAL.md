# HICHRAWI LIVE — Final Android App

Application ID: `com.hichrawi.tv`
Version: `2.2.0` / versionCode `12`

## Final behavior
- Activation is required before access to content.
- No automatic trial flow.
- Reserved test code `00000000` is controlled by the server/admin side.
- Normal subscription codes are alphanumeric codes, maximum 13 characters, without separators.
- Home has LIVE, VOD and SERIES sections.
- LIVE shows server-managed channels and packages.
- Channel logos and stream sources remain server-controlled.
- Contact section contains only Facebook, TikTok and WhatsApp, each independently enabled/disabled by settings.
- Update checking is supported through the remote configuration.
- Same applicationId is retained for future updates.

## Server settings contract
The app prefers `/api/v1/app-settings.php?device_id=...` when available and falls back to `app-config.json`.
The server settings can contain `app_name`, `subtitle`, `logo_url`, `maintenance`, `maintenance_message`, section titles, and `social` with `facebook`, `facebook_enabled`, `tiktok`, `tiktok_enabled`, `whatsapp`, `whatsapp_enabled`.

For VOD/Series, the optional endpoint is `/api/v1/content.php?device_id=...&type=vod|series`.
