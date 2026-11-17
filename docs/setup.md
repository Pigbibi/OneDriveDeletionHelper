# Setup and Connection Guide

English | [简体中文](setup.zh-CN.md)

## Sign in after installation

1. Download the latest `PhotoKeep-<version>.apk` (currently `0.3.1`) from [Releases](https://github.com/Pigbibi/PhotoKeep/releases) and install it on Android 11 or later.
2. Open the app and tap **Sign in with OneDrive**. The official Microsoft sign-in page opens.
3. Sign in with your Microsoft account, review and approve the file access permissions requested by PhotoKeep, then return to the app.
4. Allow access to all photos and videos, then choose the phone directories and OneDrive directories.
5. Connect to Wi-Fi, build the initial correspondence, and verify with test photos first.

**Regular users do not need to register a Microsoft app, enter a Client ID, or create a client secret.** The release APK embeds the project's public application ID; each user signs in and grants consent independently, and photos or tokens are never sent to the maintainer.

Personal Microsoft accounts work with the default connection. Work or school accounts are governed by your organization's consent policy and may require administrator approval. Because this project has not completed Microsoft publisher verification, self-service consent is not guaranteed across all organizations.

## Upgrading from v0.1.0

Install the new APK directly over the existing version without uninstalling first. Existing custom Client IDs, accounts, directories, and photo correspondences are preserved. Installations without an existing custom configuration automatically switch to the new built-in connection.

To switch from an older custom connection back to the project default connection, confirm **Settings -> Advanced connection settings -> Restore default connection**. Switching disables background checks and automatic cleanup and clears the previous cloud association; sign in again and re-select directories to rebuild the correspondence. Local and cloud photos are not deleted.

Use **Advanced connection settings -> Custom Client ID** only if you build from source or need a dedicated Microsoft app registration; see the [developer registration guide](microsoft-app.md). Users still on v0.1.0 can upgrade or configure their own app in the legacy first-time connection settings as described there.

## Configuring photo directories

- Keep automatic backup enabled in the official OneDrive app and confirm it covers your camera, Pictures, screenshots, and other needed sources; see [Microsoft's Pictures folder notes](https://support.microsoft.com/en-us/onedrive/seeing-pictures-that-are-not-my-photos-in-onedrive).
- Grant PhotoKeep access to **all** photos and videos along with original media metadata. Partial photo access pauses checks.
- Select phone directories and then add the corresponding OneDrive photo directories; multiple cloud folders and subfolders are supported. Selecting the entire OneDrive root is disallowed to prevent unrelated files from entering scope.
- Existing year/month folders in OneDrive are preserved. PhotoKeep does not create year/month folders or alter OneDrive's upload organization settings.
- Initial correspondence building and pre-cleanup checks run only over Wi-Fi. Keep power and network connectivity stable when reading large videos.

## Verifying with test photos

1. Take or prepare 4 unimportant test photos and wait for the official OneDrive app to back them up.
2. Select the test directory in PhotoKeep and tap **Build initial correspondence**. No cloud deletions should appear after the check finishes.
3. Delete one photo in your phone gallery and run a check again. The preview should mark it as **System trash** or **Not found on phone**. Items not found on the phone can only be confirmed manually.
4. Select the test photo in the cleanup preview and confirm cleanup. Wait for content verification to finish, then confirm the matching file moved to the OneDrive recycle bin.
5. Move a second photo to another system-visible directory on the phone and confirm the cloud file is kept.
6. Rename the third photo on the phone and confirm the cloud file is kept.
7. Make a local duplicate of the fourth photo, delete the original local copy, and confirm the cloud file is kept.
8. Revoke photo permissions and run a check again; cleanup should pause.
9. Based on these results, decide whether to enable periodic checks and automatic cleanup. This project has not been end-to-end verified against every real device and OneDrive account on your behalf.

## Boundaries of automatic cleanup

Automatic cleanup must be enabled explicitly and applies only to correlated files explicitly marked as trashed by Android. It requires two valid observations at least one hour apart, 24 hours elapsed since the first observation, matching content, directories still in scope, and full photo access.

Each run processes at most 10 files and pauses if candidates exceed roughly 20% of correlated files; when fewer than 5 files are correlated, at most 1 file is processed.

Some galleries or Google Photos remove local files directly without leaving an Android-readable trash state. Those deletions still require manual confirmation, and **not all Google Photos deletions propagate automatically**. Changes made only in Google Photos cloud storage are invisible to this app.

Allow notifications in Android app settings and check whether background activity or battery optimization restricts the app. Some OEM skins expose options such as autostart, background activity, or unrestricted battery, whose names vary by device and OS version. Periodic checks rely on Android system scheduling, which does not guarantee minute-exact timing.

## Troubleshooting and updates

- Prompted to sign in again: verify network access and tap **Sign in with OneDrive** again. For work or school accounts requesting admin approval, contact your organization; custom-connection users should recheck their app registration. Never paste tokens or raw sign-in errors into an issue.
- Waiting for Wi-Fi: both manual and background checks require an unmetered network; some Wi-Fi networks marked as metered will wait.
- Content mismatch: the cloud file is kept, especially for OEM motion photos, HEIC/RAW files, or files edited in the cloud. Do not force deletion.
- Deletion status pending confirmation: automatic retries stop. Inspect the file and recycle bin in OneDrive, then choose **Keep and stop tracking** in the preview.
- Changing accounts or directories: disables automatic cleanup and requires rebuilding the correspondence.
- Install updates over the existing app without uninstalling first. Switching devices, uninstalling, or clearing app data removes deletion history from the previous phone.
