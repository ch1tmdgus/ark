<p align="center">
  <b>English</b> · <a href="README.ko.md">한국어</a> · <a href="README.ja.md">日本語</a>
</p>

# ARK

**ARK** is a root-based backup and restore app for Android. It archives an app's APK (including split APKs) and its data together, and saves the result on your device, in any folder you choose, or on your own WebDAV cloud.

- Package name: `app.n4c.ark`
- Min SDK: 26 (Android 8.0), target SDK 34
- UI: Jetpack Compose + Material 3
- Languages: English, 한국어, 日本語 (follows the system language)

> **Root is required.** ARK reads other apps' private data in `/data/data`, which is only possible with superuser access.

## Features

- **Flexible backups** – choose APK only, data only, or both.
- **Three destinations**
  - This device (`/sdcard/ARK`)
  - Any folder you pick (Storage Access Framework)
  - WebDAV cloud (shown only after you connect a cloud in Settings)
- **Background operation** – runs as a foreground service, shows progress (%) and a one-line log, and reports the result as a notification, so you can leave the app.
- **Backup metadata** – every backup records the source device, Android version, app version, and the date and time. It is shown before you restore.
- **Easy restore** – pick a source (this device, cloud, or any file) and tap a backup. Restoring reinstalls the APK and puts the data back.
- **First-run setup** – a short welcome, a root and permission check, and you are ready. It is shown only once.

## Requirements

- A rooted device (Magisk, KernelSU, APatch, …)
- For long backups, notification permission and unrestricted battery usage are recommended
- Android Studio (Koala or newer) with JDK 17 to build from source

## Build

```bash
git clone https://github.com/ch1tmdgus/ark.git
cd ark
```

1. Open the folder in Android Studio and let Gradle sync finish.
2. Connect a rooted phone with USB debugging enabled and press **Run**, or use **Build ▸ Build APK(s)**.
3. On first launch, allow superuser access for ARK in your root manager.

Dependencies are resolved from Google, Maven Central and JitPack (for libsu).

## Usage

1. **Back up** – tap an app, choose what to back up, then choose where to save it.
2. **Restore** – tap the **Restore** button, choose the source, select a backup, and confirm.
3. **Cloud** – open **Settings** (gear icon), enter your WebDAV URL, user name and (app) password, and tap **Connect**.

WebDAV URL examples:

| Server | URL |
| --- | --- |
| Nextcloud | `https://example.com/remote.php/dav/files/USERNAME` |
| Synology WebDAV Server | `https://NAS_ADDRESS:5006` (HTTP: `5005`) |

Backups are stored in an `ARK` folder on the server. Only HTTP Basic authentication is supported. If your service uses two-factor authentication, create an app password.

## Backup format

Each backup is a pair of files with the same base name:

```
<package>_<yyyyMMdd_HHmmss>.ark    # tar archive
├── meta.json                      # device, Android version, app version, date, contents
├── apk/                           # base.apk and split APKs   (APK / APK + Data)
├── data.tar                       # /data/data/<package>      (Data / APK + Data)
└── ext.tar                        # /sdcard/Android/data/<package>, if present

<package>_<yyyyMMdd_HHmmss>.json   # copy of meta.json, used to list backups quickly
```

## How it works

ARK talks to the system through a root shell ([libsu](https://github.com/topjohnwu/libsu)) running in the global mount namespace, so every app's `/data/data` folder is visible. It archives data with `tar`, installs APKs with `pm install-create / install-write / install-commit`, and after restoring data it fixes ownership with `chown` and the SELinux contexts with `restorecon`. WebDAV is implemented with [OkHttp](https://square.github.io/okhttp/).

## Limitations

- Apps that bind their data to the device (Android Keystore, hardware-backed credentials, some banking apps) may not work after a restore.
- Runtime permissions are not part of the backup. You may need to grant them again.
- Backups are **not encrypted**. They can contain login sessions and other sensitive data, so protect the files you store in the cloud.
- Restoring **overwrites** the current data of the app.

## Disclaimer

Backing up and restoring app data with root access can cause data loss or break apps. Use ARK at your own risk, and test with a non-critical app first.

## Project structure

```
app/src/main/java/app/n4c/ark/
├── MainActivity.kt      # entry point, theme, onboarding / home switch
├── Onboarding.kt        # first-run welcome and permission pages
├── Screens.kt           # home screen and dialogs
├── Sheets.kt            # backup, restore and settings bottom sheets
├── Components.kt        # shared UI components
├── Theme.kt             # Material 3 color schemes
├── BackupEngine.kt      # root backup / restore logic
├── ArkService.kt        # foreground service and shared task state
├── WebDavClient.kt      # WebDAV client
├── Prefs.kt, Models.kt, Utils.kt
```

## Credits

- [libsu](https://github.com/topjohnwu/libsu) by topjohnwu
- [OkHttp](https://github.com/square/okhttp) by Square
