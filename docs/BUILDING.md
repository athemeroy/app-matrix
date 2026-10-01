# Build and install

Both products are standalone Android applications. They share source through
`:shared:core`; neither app needs the other installed. No account, API key,
Telegram server, or network service is needed by either app.

## Pinned toolchain

- JDK: Eclipse Temurin 21.0.12.1+1 for the Linux CI build; Java source level 17
- Gradle: 8.11.1, official wrapper scripts/JAR and distribution SHA-256 checked
- Android Gradle Plugin: 8.9.3
- Android platform: API 35, revision 2 (API stubs SHA-256 checked); build-tools: 35.0.0
- Minimum Android version: Android 8.0 / API 26
- Unit tests: JUnit 4.13.2 (test-only dependency)

A full JDK is required, not a JRE. The first build needs Internet access to
retrieve Gradle and build dependencies from Google Maven and Maven Central.

Install Android Studio, or use the official command-line SDK. Read and accept
[Google's Android SDK agreement](https://developer.android.com/studio/terms)
before using its packages. On Linux x86_64, the optional helpers install into
directories you choose, without modifying the operating system:

```sh
export JAVA_HOME="$HOME/.local/app-matrix-jdk"
./tools/setup-jdk.sh "$JAVA_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/.local/app-matrix-android-sdk"
./tools/setup-android-sdk.sh --accept-sdk-license
```

Alternatively, point `JAVA_HOME` to your JDK 21 and `ANDROID_HOME` to your existing
SDK containing `platforms;android-35` and `build-tools;35.0.0`. Android Studio can
also read an uncommitted `local.properties` containing `sdk.dir=/your/sdk/path`.
On Windows use `gradlew.bat` in place of `./gradlew`; install the matching SDK
packages through Android Studio. The helper shell scripts target Linux with Bash and GNU utilities; the download
helpers specifically target Linux x86_64. Use the Gradle tasks directly on other
hosts (their native build has not been validated here).

## Independent builds

```sh
# Pocket Poster only (plus the shared library)
./gradlew :apps:poster:assembleDebug
# Field Journal only (plus the shared library)
./gradlew :apps:journal:assembleDebug
# Build, lint, and test either product, also producing an unsigned release APK
./tools/build-app.sh poster
./tools/build-app.sh journal
```

Install on an Android 8.0+ device with USB debugging enabled by its owner:

```sh
adb install -r apps/poster/build/outputs/apk/debug/poster-debug.apk
adb shell am start -n dev.appmatrix.poster/.MainActivity
adb install -r apps/journal/build/outputs/apk/debug/journal-debug.apk
adb shell am start -n dev.appmatrix.journal/.MainActivity
```

Android requires explicit permission to install APKs downloaded outside an app
store. Review the source and hashes first. Debug APKs are for evaluation; each
builder uses a local debug key. Different CI runs may use different keys, so an
APK from a later run may not update an earlier installation. Back up your data
before uninstalling: uninstalling removes that app's private data. The release
APK is unsigned and must be signed by its distributor before installation.
Production signing keys are deliberately absent from this repository.

## Native integration tests

Use an explicitly selected emulator or test device. The runners create synthetic,
app-private fixtures and launch the real activities; no AndroidX test services
are required. They are supplemental to manual UI, system picker, and restart
acceptance testing.

```sh
adb devices
./tools/run-device-tests.sh emulator-5554 all
# Or run just one independent app:
./tools/run-device-tests.sh emulator-5554 poster
```

The script compiles and installs the app/test APKs and validates the runner's
success code plus a positive assertion count. An `adb` exit status of zero alone
is not treated as a passing test. Reports are in `artifacts/runtime/`.

## CI and distribution

`.github/workflows/android.yml` builds each app in an independent Ubuntu 24.04
job. It verifies the official wrapper, uses the pinned JDK, checks the hosted
runner's preinstalled API 35/build-tools 35.0.0, builds debug and unsigned release,
and runs lint and unit tests. APK checks also verify the application IDs, API
floor, debug signatures, absence of declared permissions, and bundled licenses. Build jobs fail clearly if the preinstalled compile SDK is missing. The separate
native-test job explicitly provisions the verified SDK tools and AOSP API 35
revision-2 image with emulator 37.1.11. It requires the Android SDK agreement
described above; it never accepts unrelated SDK agreements.

The native job first checks existing `/dev/kvm` access, then boots with hardware
acceleration, runs both framework integration suites, and records launcher
screenshots and diagnostic logs. It never changes host device permissions or
group membership. A runner without authorized KVM access fails the preflight.

Each successful build job uploads APKs, SHA-256 checksums, build metadata, and a complete
source archive generated from the same Git commit. `tools/package-artifacts.sh`
requires a clean project checkout and reruns the build/checks so stale APKs cannot
silently be packaged with different source. Publish the matching source archive alongside every distributed APK,
retain the license/notice files, and provide the exact commit URL. CI artifacts
expire after 30 days; copy the entire bundle to a permanent release before
advertising it as a public download.

Build dependencies are SHA-256 pinned in `gradle/verification-metadata.xml`;
Gradle rejects unrecognized or changed dependency bytes. Review official upstream
artifacts before deliberately updating that file. Build/test configuration is pinned. Bit-for-bit reproducibility across different
machines is not yet claimed: debug signing certificates and host details can
differ. An actual passing build does not prove interactive runtime correctness;
see the acceptance test plan and recorded verification status for device tests.

## Official origins

- [AGP compatibility](https://developer.android.com/build/releases/agp-8-9-0-release-notes)
- [Google Maven AGP versions](https://dl.google.com/android/maven2/com/android/tools/build/gradle/maven-metadata.xml)
- [Gradle 8.11.1 checksums](https://gradle.org/release-checksums/)
- [Android command-line SDK and checksum](https://developer.android.com/studio#command-tools)
- [Temurin 21.0.12.1+1 source and binary release](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1)

The Gradle wrapper scripts/JAR are generated without source modification from the
official Gradle 8.11.1 distribution; distribution URL, timeout and SHA-256 are
configured in `gradle/wrapper/gradle-wrapper.properties`. Its Apache-2.0 license
and notices are retained in `third_party/gradle/`. SDK/JDK installations remain
external build tools and are not bundled in the app APKs. The project-specific
build scripts and CI workflow are new App Matrix code.
