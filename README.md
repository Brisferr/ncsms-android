# NcSMS Sync — Android SMS backup for Nextcloud 33

Synchronise your Android SMS messages to your Nextcloud instance using the [Phone Sync (ocsms)](https://github.com/brisferr/ocsms) app.

> **Compatible:** Android 7+ (API 24) · Android 14 ✓ · Nextcloud 25–33

---

## Features

- Automatic background sync (every 1 h / 6 h / 24 h)
- Manual sync with real-time status
- Secure authentication via **Nextcloud App Password**
- Accepts self-signed SSL certificates (home servers)
- Works on Android 14 without requiring default SMS app role

---

## Installation

### Download the APK

1. Go to **[Releases](../../releases)** or **[Actions](../../actions)**
2. Download the latest `NcSMS-debug.zip` artifact
3. Unzip → install `app-debug.apk` on your phone
4. Enable *Install from unknown sources* if prompted

### Build from source

Requirements: Android Studio or JDK 17 + Android SDK

```bash
git clone https://github.com/brisferr/ncsms-android.git
cd ncsms-android
gradle wrapper --gradle-version=8.6
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

The repository also includes a **GitHub Actions** workflow that builds the APK automatically on every push — no local toolchain needed.

---

## Configuration

1. Open the app on your phone
2. **URL Nextcloud** — e.g. `https://cloud.example.com`
3. **Username** — your Nextcloud login
4. **Password** — create an **App Password** (strongly recommended):  
   Nextcloud → *Settings → Security → App passwords → Create new app password*
4. Choose a sync interval and tap **Save**
5. Tap **Sync now** for the first sync

---

## Server-side app

You need the patched **Phone Sync (ocsms)** Nextcloud app compatible with NC 25–33:  
👉 <https://github.com/brisferr/ocsms>

---

## Permissions

| Permission | Reason |
|---|---|
| `READ_SMS` | Read SMS messages from your phone |
| `INTERNET` | Upload messages to Nextcloud |
| `RECEIVE_BOOT_COMPLETED` | Restart background sync after reboot |
| `POST_NOTIFICATIONS` | Android 13+ notification permission |

---

## Differences from the original ncsms-android

This is a **complete rewrite** of [nerzhul/ncsms-android](https://github.com/nerzhul/ncsms-android) in Kotlin, targeting modern Android (API 24+, tested on Android 14):

- Kotlin instead of Java
- WorkManager instead of SyncAdapter
- Handles self-signed SSL certificates
- No dependency on a pre-compiled Go library (ncsmsgo.aar)
- Compatible with Android 14 SMS permission model

---

## License

AGPL-3.0 — same as the original ocsms project.
