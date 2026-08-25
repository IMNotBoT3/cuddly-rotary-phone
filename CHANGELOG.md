# Changelog

## 7.1.0

- Fixed the critical Wi-Fi keyboard line-delimiter regression from 7.0.
- Commands now end with a real LF byte again.
- Fixed F8 mirror mode toggling ON but sending no completed Android commands.
- Removed an accidentally duplicated `KeyboardConnection` class block.
- Retained automatic reconnect, keepalive, heartbeat, screenshot ACK, and Enter fixes.


## 7.0.0

- Reworked Same-Wi-Fi keyboard transport for automatic reconnection.
- Added TCP keepalive on Windows and Android.
- Added Windows keepalive tuning where supported.
- Added `TCP_NODELAY` on both sides.
- Added a 12-second no-op heartbeat (`K:PING`).
- Added reconnect retries with backoff after transient socket failures.
- `WinError 10053` and brief Wi-Fi drops no longer immediately terminate the bridge.
- Android TCP server sockets use address reuse before bind.
- Screenshot channel remains separate and acknowledged.
- Enter and Shift+Enter fixes from v6 are retained.
- F8 remains Laptop-only ↔ Mirror-to-phone.


## 6.0.0

- Fixed Android Enter handling using `performEditorAction`.
- Enter now honors Send/Done/Go/Search/Next/Previous editor actions.
- Added Send-action fallback before raw Enter.
- Changed Shift+Enter to commit a real newline.
- WiFiSync starts in Laptop-only mode.
- Clarified F8 as Laptop-only ↔ Mirror-to-phone.
- Added IPv4 validation.
- Added screenshot receipt acknowledgement.
- Windows reports screenshot success only after Android confirms the PNG.
- Added Android lint validation to the release workflow.
- Retained visible tray/status reporting.
- Retained Same-Wi-Fi screenshot and keyboard transfer.
- Retained optional Bluetooth LE keyboard forwarding.