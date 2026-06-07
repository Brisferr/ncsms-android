# NcSMS — Android SMS sync & send for Nextcloud

Synchronise your Android SMS with Nextcloud and **send SMS from the Nextcloud web UI** through your phone.

> **Compatible:** Android 7+ (API 24) · Android 14 ✓ · Nextcloud 25–33  
> **Server-side app required:** [brisferr/ocsms](https://github.com/Brisferr/ocsms)

---

## Features

| Feature | Description |
|---|---|
| **SMS archive sync** | Uploads SMS to Nextcloud automatically (every 1 h / 6 h / 24 h) |
| **Send from web** | Nextcloud queues a message → app sends it via the phone's SIM |
| **Instant send** | UnifiedPush via self-hosted ntfy wakes the app in < 2 s |
| **Instant replies** | `SMS_RECEIVED` broadcast triggers immediate sync — replies appear in ocsms within seconds |
| **WorkManager fallback** | 15-min polling when push is unavailable — no message is ever lost |
| **Distributor selector** | In-app spinner to choose your UnifiedPush distributor (ntfy, etc.) |
| **Manual sync** | One-tap sync with real-time progress in the UI |
| **Self-signed SSL** | Accepts home-server certificates automatically |

---

## How it works

```
Nextcloud web UI
      │  queues SMS
      ▼
oc_ocsms_sendmessage_queue
      │  PushNotifier POST
      ▼
ntfy (self-hosted)              ◄── SMS_RECEIVED broadcast
      │  UnifiedPush wake-up          │
      ▼                         SmsReceiver
UnifiedPushReceiver.onMessage()       │
      │                         SyncWorker.runNow()
      ▼                               │
OutboxWorker                    (replies visible in ocsms
      │  polls sendqueue          within seconds)
      │  sends via SmsManager
      ▼
markSent → SyncWorker.runNow()  ← sent SMS visible in ocsms
      │
purgeSentQueue                  ← no duplicate in outbox view
```

---

## Installation

### Download APK

1. Go to **[Actions](../../actions)** → latest successful run → **NcSMS-debug** artifact
2. Unzip → install `app-debug.apk`
3. Enable *Install from unknown sources* if prompted

### Build from source

Requirements: JDK 17 + Android SDK (or Android Studio)

```bash
git clone https://github.com/Brisferr/ncsms-android.git
cd ncsms-android
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## Configuration

1. Open the app
2. Enter your Nextcloud **URL** (e.g. `https://cloud.example.com`)
3. Enter your **username** and an **App Password**:  
   Nextcloud → *Settings → Security → App passwords → Create new*
4. In the **UnifiedPush distributor** spinner, select **ntfy** (or your preferred distributor)
5. Choose a sync interval and tap **Save**
6. Grant SMS permissions when prompted
7. Tap **Sync now** for the first upload

---

## UnifiedPush setup (recommended)

UnifiedPush enables Nextcloud to wake the app instantly when you send a message from the browser. Without it the app polls every 15 minutes — both work, push is faster.

### Requirements

- A self-hosted **[ntfy](https://ntfy.sh)** server (see [ocsms README](https://github.com/Brisferr/ocsms) for a docker compose example)
- The **[ntfy Android app](https://f-droid.org/en/packages/io.heckel.ntfy/)** (F-Droid or Play Store)

### Steps

1. Install ntfy on your server and create a user account
2. Open the ntfy Android app → add your server (`https://push.example.com`) → log in
3. Open NcSMS → in the **UnifiedPush distributor** spinner → select **ntfy** → tap **Save**
4. The app registers its endpoint with Nextcloud automatically

No Google account, no Firebase, no Google Play Services required.

---

## Permissions

| Permission | Reason |
|---|---|
| `READ_SMS` | Read SMS messages from the device for upload |
| `SEND_SMS` | Send outbound SMS queued from Nextcloud |
| `RECEIVE_SMS` | Detect incoming SMS to trigger immediate sync (replies visible in seconds) |
| `INTERNET` | Communicate with Nextcloud and ntfy |
| `ACCESS_NETWORK_STATE` | Check connectivity before syncing |
| `RECEIVE_BOOT_COMPLETED` | Restart background workers after reboot |
| `POST_NOTIFICATIONS` | Android 13+ notification permission |

---

## Differences from the original ncsms-android

This is a **complete rewrite** of [nerzhul/ncsms-android](https://github.com/nerzhul/ncsms-android):

| Old | New |
|---|---|
| Java | Kotlin |
| SyncAdapter | WorkManager (Doze-safe) |
| No send support | OutboxWorker + SmsManager |
| No push | UnifiedPush with in-app distributor selector |
| No reply detection | `SmsReceiver` on `SMS_RECEIVED` → instant sync |
| Broken self-signed SSL | Trust-all for home servers |
| Dependency on ncsmsgo.aar | No native library |
| Android 5 target | Android 7–14 (API 24–34) |

---

## Architecture

```
be.ncsms
├── MainActivity.kt          Settings UI + UP distributor selector
├── OcSmsClient.kt           Nextcloud HTTP API client (OkHttp)
├── SyncWorker.kt            Periodic + on-demand SMS upload to Nextcloud
├── OutboxWorker.kt          Polls + sends queued SMS; triggers sync after send
├── SmsReader.kt             Reads SMS from Android ContentProvider
├── SmsReceiver.kt           BroadcastReceiver: SMS_RECEIVED → SyncWorker.runNow()
├── UnifiedPushReceiver.kt   UP callbacks: endpoint registration + wake-up
└── BootReceiver.kt          Restarts WorkManager after reboot
```

---

## License

AGPL-3.0 — same as the original ocsms project.
