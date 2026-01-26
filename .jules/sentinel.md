## 2025-02-17 - Hardcoded Insecure Protocols
**Vulnerability:** The application hardcoded `http://` and `ws://` protocols in `ComfyWebSocket` and `MainViewModel`, preventing the use of secure connections (HTTPS/WSS) even if the server supported it.
**Learning:** In local-first applications (like ComfyUI controllers), developers often assume local network environments are "safe" and neglect SSL/TLS support, but this exposes users to risks if they tunnel their instance or use it on untrusted networks.
**Prevention:** Always use dynamic protocol selection based on user configuration or discovery. Use `OkHttpClient` which supports both, but ensure the URL scheme is variable.
