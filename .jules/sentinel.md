## 2025-02-17 - Hardcoded Insecure Protocols
**Vulnerability:** The application hardcoded `http://` and `ws://` protocols in `ComfyWebSocket` and `MainViewModel`, preventing the use of secure connections (HTTPS/WSS) even if the server supported it.
**Learning:** In local-first applications (like ComfyUI controllers), developers often assume local network environments are "safe" and neglect SSL/TLS support, but this exposes users to risks if they tunnel their instance or use it on untrusted networks.
**Prevention:** Always use dynamic protocol selection based on user configuration or discovery. Use `OkHttpClient` which supports both, but ensure the URL scheme is variable.

## 2025-02-17 - Sensitive Data in Cloud Backups
**Vulnerability:** The default Android Auto Backup configuration (`allowBackup="true"`) included the application's database and DataStore preferences, which contain internal IP addresses, ports, and prompt history.
**Learning:** Even for "local" apps, the default backup rules can inadvertently leak internal network topology and usage history to cloud providers. Users expect local-first apps to keep data local.
**Prevention:** Explicitly exclude sensitive files (databases, preferences) from `cloud-backup` in `data_extraction_rules.xml` and `backup_rules.xml`.
## 2025-02-18 - Cloud Backup of Sensitive Configuration
**Vulnerability:** The application was configured to back up all data (including Shared Preferences containing Host IP/Port) to Google Cloud, which might expose private network topology or future sensitive credentials.
**Learning:** Default Android backup rules (`include domain="sharedpref"`) are often too permissive for apps that store local network details or connection secrets in standard preferences.
**Prevention:** Explicitly exclude sensitive domains (like `sharedpref`) from `cloud-backup` in `data_extraction_rules.xml` and `backup_rules.xml`, while retaining them for `device-transfer` to support local migration.

## 2025-02-18 - Hardcoded Protocols in Secondary UI Logic
**Vulnerability:** Preloading logic in `GalleryScreen` and list items in `HistoryScreen` used hardcoded `http://` URLs, bypassing the secure connection setting even when `GalleryItem` was fixed.
**Learning:** Security fixes often target the primary usage path but miss secondary paths (like preloading or history views) where logic is duplicated.
**Prevention:** Centralize URL construction logic in a shared extension function (e.g., `GeneratedMediaListing.constructUrl`) and verify all usages with grep.

## 2025-02-18 - Unconditional Production Logging of Sensitive API Data
**Vulnerability:** `ComfyApplication` initialized a shared `OkHttpClient` with a logging interceptor that logged full request URLs (including query params) and headers to `Logcat` in all builds (including Release).
**Learning:** Developers often add global logging interceptors for debugging early in development but forget to wrap them in `DEBUGGABLE` checks, assuming `Log.d` is stripped or harmless, whereas modern tools can capture these logs in production environments.
**Prevention:** Always wrap `logging-interceptor` or any `addInterceptor` that logs data with a check for `(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0`.
