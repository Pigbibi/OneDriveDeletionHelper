# Third-party notices

PhotoKeep's original source and original vector artwork are licensed under the MIT License, Copyright (c) 2026 Pigbibi. Third-party components retain their respective licenses; PhotoKeep's MIT license does not replace them.

Direct runtime components:

- [Microsoft Authentication Library for Android](https://github.com/AzureAD/microsoft-authentication-library-for-android), Microsoft Corporation — MIT License. Used for Microsoft browser authentication and token management, instead of implementing an authentication protocol and credential store ourselves.
- [AndroidX WorkManager and AndroidX dependencies](https://android.googlesource.com/platform/frameworks/support/) — Apache License 2.0. Used for Android background scheduling, constraints and foreground progress.
- [OkHttp](https://github.com/square/okhttp), Square, Inc. and contributors — Apache License 2.0. Used for DELETE with transport retries and redirects explicitly disabled.
- Transitive runtime dependencies include Microsoft Identity Common (MIT), Microsoft Surface Duo DisplayMask (MIT), Kotlin and Okio (Apache License 2.0), and Gson (Apache License 2.0). Their upstream notices and licenses remain applicable.

Build and test tools include Gradle (Apache License 2.0), Android Gradle Plugin (Apache License 2.0), JUnit 4 (Eclipse Public License 1.0), Hamcrest (BSD 3-Clause), and Robolectric (MIT). Test tools are not intentionally bundled as runtime features.

The Gradle wrapper scripts retain their original copyright and license headers. Dependency license resources are retained where packaged; only duplicate non-license dependency/index resources are excluded. The application is not endorsed by Microsoft, Google, or Xiaomi. OneDrive and other product names belong to their respective owners.
