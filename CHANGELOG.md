# Changelog

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
