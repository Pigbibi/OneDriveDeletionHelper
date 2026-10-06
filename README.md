# PhotoKeep (拾光清理)

[简体中文](README.zh-CN.md)

<img src="docs/branding/icon.svg" width="88" height="88" alt="PhotoKeep icon: a photo print and a checked cloud on a deep-blue background" />

**Keep OneDrive's automatic backup, and let cleanup on your phone catch up with the cloud.**

PhotoKeep is a standalone, open-source Android app that connects your phone directly to Microsoft Graph. You keep backing up originals and videos with the official OneDrive app; PhotoKeep builds the correspondence, checks deletions, and moves confirmed cloud files to the recycle bin. No self-hosted server is required.

[Download v0.2.0 APK](https://github.com/Pigbibi/OneDriveDeletionHelper/releases/tag/v0.2.0) · [Setup and Microsoft connection](docs/SETUP.zh-CN.md) (Chinese) · [Privacy](docs/PRIVACY.md) (Chinese) · [MIT license](LICENSE)

> **This is a pre-release.** The published APK embeds the project's public Microsoft application ID, so you just tap **Sign in with OneDrive** and authorize — regular users don't need to register an app. Work/school accounts may require administrator approval. Real-device deletion behavior for the system gallery and Google Photos, and full end-to-end verification against a OneDrive account, are still pending — try it with test photos first.

<p>
  <img src="docs/screenshots/overview.png" width="240" alt="Overview: connect OneDrive and the real empty-state statistics" />
  <img src="docs/screenshots/preview.png" width="240" alt="Cleanup preview: all cloud photos kept when no correspondence exists yet" />
  <img src="docs/screenshots/settings.png" width="240" alt="Settings: Microsoft connection, multiple photo directories and check frequency" />
</p>

Screenshots show the real app running in an Android 15 emulator, without real photos or accounts.

## What it does

- Built-in Microsoft OAuth sign-in; regular users authorize directly, and a custom Client ID is kept only in advanced settings.
- Original vector icon, adapted for Android circular, rounded-square, and Android 13+ themed launcher icons.
- Select multiple photo directories on internal storage, such as `DCIM/Camera/`, `Pictures/`, and screenshots; subdirectories are included.
- Select multiple OneDrive photo directories, read recursively, and handle pagination. Phone and cloud directory layouts don't have to match, so existing year/month folders are preserved.
- Check content hashes of local photos and videos; cloud files are kept when they moved, were renamed, or still have an identical local copy.
- Manual check, cleanup preview, per-item confirmation, keep-selected, and recent history.
- Background checks every 6 hours, 12 hours, or daily over Wi-Fi; the system may postpone them.
- Optional automatic cleanup: off by default, and only for files the **system explicitly marks as trashed**, with two consecutive valid checks and 24 hours elapsed.
- Before deleting, stream the cloud original to verify SHA-256 and the full byte count; re-confirm the file version, directory scope, and phone media-library state.
- Uses only ordinary recycle-bin deletion with `If-Match`; automatic retries of delete requests are disabled. Uncertain results keep the record and pause automatic cleanup.

## Boundaries

| Situation | Current behavior |
|---|---|
| Phone file is in the system trash, correspondence is clear | Eligible for automatic cleanup |
| File disappeared from the phone directly | Manual confirmation only; cannot distinguish delete, move-to-hidden, or space-freeing |
| Google Photos deletes only cloud photos | The app cannot know; it does not read the Google Photos library |
| Google Photos deletes the local file | Enters the trash candidate set or manual review based on Android visibility |
| Existing OneDrive historical duplicates | No bulk dedup or cleanup; files without a correspondence are kept |
| Phone photo deleted before a correspondence existed | Not traceable; the cloud file is never auto-deleted |
| Same name and size but different content | Content check fails; kept |
| Cloud year/month directories | Current structure kept; the app does not create or reorganize them |
| Logical groups in the gallery (people, favorites, shared albums) | Not replicated |
| SD card, app-private directories, vaults, files not indexed by the system | Not supported in this release |
| Motion photos, HEIC/RAW, and other special formats | Verified by raw bytes; kept if the API returns different content — not all vendor formats are guaranteed to match |

The first scan only builds **candidate correspondences**: name and size narrow the field, they don't authorize deletion. Full cloud content verification happens right before cleanup, so the first setup doesn't download the whole album. That step uses download bandwidth and long videos can take a while, but it never stores a copy or re-uploads photos.

## Install and get started

1. Download `PhotoKeep-0.2.0.apk` from Releases and install it on Android 11 or later. Allow installing from that source.
2. Tap **Sign in with OneDrive** and complete sign-in on Microsoft's official page; see the [connection guide](docs/SETUP.zh-CN.md) (Chinese).
3. In the app, grant full-photos permission and choose the phone and OneDrive directories.
4. Connect to Wi-Fi and build the first correspondence. Start with a few test photos to check delete, move, rename, and keep-copy behavior.
5. Confirm the results in the cleanup preview, then enable periodic checks and automatic cleanup as needed.

**Update by installing over the existing app — don't uninstall first.** Correspondence is stored only on the phone; uninstalling or clearing app data loses the records and prevents tracing past deletions. The published APK uses its own signing key, so a self-built APK usually cannot install over the official release.

## Privacy and permissions

There is no developer server, advertising, or developer data reporting. The app's private records don't participate in cloud backup or device migration, to avoid mistaking another phone's missing files for deletions. Microsoft sign-in is handled by MSAL; passwords, access tokens, and download links are never written into app records.

Microsoft Graph's `Files.ReadWrite` permission itself includes read and write capability — it is not a "delete-only" permission. This project's code only queries, verifies content, and moves files to the recycle bin. Raw media metadata is read to verify full original-file bytes, not to extract location coordinates. See [Privacy](docs/PRIVACY.md) (Chinese) for details.

## Develop and build

Uses Java 17 and the Android SDK (`platforms;android-37.0`, `build-tools;36.0.0`), with Gradle Wrapper 9.6.1, Android Gradle Plugin 9.3.1, minimum Android API 30, and target API 35.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Android Studio can open the project root directly. The SDK location uses `ANDROID_HOME` or an untracked local `local.properties`.

Release builds and signing are described in [Releasing](docs/RELEASING.md) (Chinese). When building yourself, the APK signature differs from the published one, so follow the [developer guide](docs/MICROSOFT-APP.md) (Chinese) to configure your own Microsoft app; regular users should install the published build. All account authorization, cloud-content download, and real deletion testing must use your own test directory; automated tests don't touch a real Microsoft account.

Core code: `core/` handles matching, deletion policy, and pre-delete verification; `data/` handles media reading, Microsoft auth, Graph, and local storage; `sync/` handles the check flow and scheduling. The UI uses native Android Views; see [DESIGN.md](docs/DESIGN.md) (Chinese) for design conventions.

## Open source

MIT License, Copyright (c) 2026 **Pigbibi**. Third-party components keep their own licenses in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). This is an independent project with no affiliation with Microsoft, Google, or device vendors.
