# HICHRAWI LIVE — Final Android App

Final Android/Android TV app project for HICHRAWI LIVE.

## Final requirements
- Application ID remains `com.hichrawi.tv`.
- No automatic trial flow.
- Special test code: `00000000` (must be enabled/disabled server-side).
- Normal subscription codes: exactly 13 characters, uppercase letters + digits, no separators.
- Activation, subscription checks and playback authorization are server-side.
- Channel sources and logos remain editable from Admin without rebuilding the APK.
- Channels and Packages UI is designed for phone and Android TV/remote navigation.
- Channel logos are loaded dynamically from Admin.
- New HICHRAWI logo is used by the final app instead of the previous logo.
- In-app update check is supported through `app-config.json` fields: `latest_version_code`, `apk_url`, `force_update`, `update_message`.
- Keep the same signing key for future updates.

## Update procedure
Increase `versionCode` for every APK release. Publish the signed APK at a stable HTTPS URL and update the remote config. Android may require user confirmation to install an APK outside Google Play; Play-distributed builds should use Google Play's update mechanism.
