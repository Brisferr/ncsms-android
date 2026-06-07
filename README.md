# NcSMS — Android SMS sync & send for Nextcloud

Synchronise your Android SMS with Nextcloud and **send SMS from the Nextcloud web UI** through your phone.

> **Compatible:** Android 7+ (API 24) · Android 14 ✓ · Nextcloud 25–33  
> **Server-side app required:** [brisferr/ocsms](https://github.com/brisferr/ocsms)

---

## Features

| Feature | Description |
|---|---|
| **SMS archive sync** | Automatically uploads SMS to Nextcloud (every 1 h / 6 h / 24 h) |
| **Send from web** | Nextcloud queues a message → app sends it via the phone's SIM |
| **UnifiedPush** | Near-instant delivery (< 2 s) via self-hosted ntfy — no Google required |
| **WorkManager fallback** | 15-min polling when push is unavailable — no message lost |
| **Manual sync** | One-tap sync with real-time progress in the UI |
| **Self-signed SSL** | Accepts home-server certificates automatically |

---

## How it works

```
Nextcloud web UI
      │  queues SMS
      ▼
ocsms_sendmessage_queue
      │  PushNotifier POST
      ▼
ntfy (self-hosted)
      │  UnifiedPush wake-up
      ▼
UnifiedPushReceiver.onMessage()
      │  triggers immediately
      ▼
OutboxWorker
      │  polls /api/v4/messages/sendqueue
      │  sends via SmsManager
      ▼
POST /api/v4/messages/sendqueue/{id}/sent

SyncWorker (1 h)  ──upload archive──►  Nextcloud
OutboxWorker (15 min)  ──fallback──►  send queued SMS
```

---

## Installation

### Download APK

1. Go to **[Releases](../../releases)** or **[Actions](../../actions)**
2. Download the latest `NcSMS-debug.zip` artifact
3. Unzip → install `app-debug.apk`
4. Enable *Install from unknown sources* if prompted

### Build from source

Requirements: JDK 17 + Android SDK (or Android Studio)

```bash
git clone https://github.com/brisferr/ncsms-android.git
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
4. Choose a sync interval and tap **Save**
5. When prompted, choose **ntfy** as the UnifiedPush distributor
6. Tap **Sync now** for the first upload

---

## UnifiedPush setup

UnifiedPush allows Nextcloud to wake up the app instantly when you compose a message in the browser. Without it, the app polls every 15 minutes as a fallback — both work, push is just faster.

1. Install the **[ntfy app](https://f-droid.org/en/packages/io.heckel.ntfy/)** from F-Droid (same instance you may already use for SchildiChat / Element)
2. Open NcSMS → save settings → a dialog will ask you to choose a UnifiedPush distributor → select ntfy
3. The endpoint is registered with Nextcloud automatically — no manual configuration needed

---

## Permissions

| Permission | Reason |
|---|---|
| `READ_SMS` | Read SMS messages from the device for upload |
| `SEND_SMS` | Send outbound SMS queued from Nextcloud |
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
| No push | UnifiedPush (ntfy) |
| Broken self-signed SSL | Trust-all for home servers |
| Dependency on ncsmsgo.aar | No native library |
| Android 5 target | Android 7–14 (API 24–34) |

---

## License

AGPL-3.0 — same as the original ocsms project.
