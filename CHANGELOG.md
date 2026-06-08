# Changelog

Complete rewrite of [nerzhul/ncsms-android](https://github.com/nerzhul/ncsms-android) in Kotlin with WorkManager, UnifiedPush, and bidirectional SMS support.

---

## 1.2.0 — 2026-06-08
* UnifiedPush distributor selector: in-app spinner lists all installed distributors (ntfy, etc.)
* Distributor choice persisted across restarts — UP connector 2.x `saveDistributor()` now called before `registerApp()` (was silently failing without it)
* `SmsReceiver`: new `BroadcastReceiver` on `SMS_RECEIVED` — incoming replies trigger an immediate sync, replies appear in ocsms within seconds
* `SyncWorker.runNow()`: new one-shot sync method used by OutboxWorker and SmsReceiver
* After send: `OutboxWorker` triggers `SyncWorker.runNow()` so the sent SMS appears in ocsms conversation without waiting for the periodic sync
* `purgeSentQueue`: after a successful sync, calls `/api/v4/messages/sendqueue/purge-sent` so outbox items disappear exactly when they appear in the conversation — no duplicates
* `RECEIVE_SMS` permission added

## 1.1.0 — 2026-06-07
* **Send SMS from Nextcloud web UI**: `OutboxWorker` polls `/api/v4/messages/sendqueue`, sends via `SmsManager`, marks sent/failed
* **UnifiedPush integration**: `UnifiedPushReceiver` handles endpoint registration and wake-up signals
* Near-instant outbox delivery (< 2 s) via self-hosted ntfy — no Google / Firebase required
* WorkManager 15-min periodic fallback when push unavailable — no message lost
* `OcSmsClient.registerPushEndpoint()` / `unregisterPushEndpoint()`: Android notifies Nextcloud of its ntfy endpoint automatically
* Endpoint re-registered on every app startup and distributor change

## 1.0.0 — 2026-06-01
Complete rewrite from scratch. Previous Java app (nerzhul/ncsms-android) was incompatible with Android 13+, Nextcloud 25+, and had a broken native library dependency.

* Kotlin + WorkManager (Doze-safe, replaces SyncAdapter)
* `SyncWorker`: periodic SMS upload to Nextcloud (1 h / 6 h / 24 h)
* `SmsReader`: reads SMS from Android ContentProvider since last sync timestamp
* `OcSmsClient`: Nextcloud HTTP API client built on OkHttp — Basic auth, chunked upload (200 SMS/batch), SSL trust-all for home servers with self-signed certificates
* `BootReceiver`: restarts WorkManager after device reboot
* Settings UI: Nextcloud URL, username, App Password, sync interval selector, last sync info, error display
* Manual sync button with real-time progress
* `READ_SMS` + `SEND_SMS` + `POST_NOTIFICATIONS` permissions
* Android 7–14 (API 24–34), no Google Play Services dependency
* GitHub Actions CI: builds debug APK artifact on every push
