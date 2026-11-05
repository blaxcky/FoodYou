# Agent Notes

## Git Workflow

After each larger cohesive change, run the relevant verification, create a local commit, and
synchronize it with GitHub using the GitHub CLI (`gh`) before moving on to the next larger task.

## Local JDK 21 and Android SDK

This project uses JDK 21 for Gradle. Use the Eclipse Temurin 21 tarball unpacked in the user's home
directory; `jdk-21` is a symlink to the current version (`jdk-21.0.12.1+1`):

```bash
/home/markus/.local/opt/jdk-21
```

No JDK 21 is installed system-wide: `/usr/lib/jvm` only has JDK 25, and the `temurin-21-jdk` DNF
package is no longer installed. Gradle 8.13 cannot run on JDK 25, and the JDK 17 under
`~/.gradle/jdks` cannot compile the Java 21 target.

Do not use the Android Studio Flatpak JBR either: it has been updated to JDK 25 and makes Gradle fail
immediately with a bare version message such as `25.0.3`. The system `java` may also point to JDK 25,
so always set `JAVA_HOME` explicitly.

The repository has no `local.properties`, so also set `ANDROID_HOME` to the Android SDK at
`/home/markus/Android/Sdk`; otherwise Gradle fails with "SDK location not found".

Set `GRADLE_USER_HOME` to the workspace cache for Gradle commands. This keeps Gradle wrapper locks
and dependency cache writes inside the writable repository instead of `~/.gradle`, avoiding sandbox
approval prompts for the host Gradle cache path.

```bash
GRADLE_USER_HOME=/home/markus/Github/FoodYou/.gradle \
JAVA_HOME=/home/markus/.local/opt/jdk-21 \
ANDROID_HOME=/home/markus/Android/Sdk \
./gradlew :app:compileDevReleaseKotlinAndroid
```

The JDK and SDK live outside the workspace, so the filesystem sandbox may hide them: if they appear
to be missing, run the command outside the sandbox before concluding that they are absent.

For Android Studio, use `GRADLE_LOCAL_JAVA_HOME` as the Gradle JDK and set
`java.home=/home/markus/.local/opt/jdk-21` in the ignored `.gradle/config.properties` file.

## Verification Scope

Always run the smallest meaningful verification for the change. `devRelease` is the broadest build
variant allowed by default, not a required minimum. Run release or preview tasks only when the user
explicitly requests them.

`devRelease` is shrunk with R8 (not obfuscated) and split per ABI: `assembleDevRelease` writes
`app-arm64-v8a-devRelease.apk`, `app-x86_64-devRelease.apk`, and `app-armeabi-v7a-devRelease.apk`,
and `installDevRelease` picks the split matching the device. Because R8 runs, verify features that
rely on reflection or JNI on the emulator after dependency or keep-rule changes. The instrumented-test
init scripts in `dev/` turn minification off, so instrumented tests run against an unminified app.

- For documentation, comments, and agent-instruction changes, run only `git diff --check`.
- For production-code changes, run the directly affected tests. If no relevant test exists, run at
  most `:app:compileDevReleaseKotlinAndroid`.
- To run one unit-test class, use
  `:app:testDevReleaseUnitTest --tests <fully.qualified.TestClass>`.
- For APK, manifest, or packaging verification, or an explicitly requested integration check, run
  at most `:app:assembleDevRelease`.

Do not duplicate verification: after a successful test or `assembleDevRelease` task has already
compiled the affected sources, do not compile those same sources separately. Do not automatically
run broad or expensive tasks such as `build`, `check`, an unfiltered `test`, full lint, or builds of
all variants. Broader or more expensive verification is permitted when the user explicitly asks
for it.

### Koin Definitions Under R8

`devRelease` lets R8 merge an interface that has a single implementation into that implementation,
so `AiController::class` and `AndroidAiController::class` become the same Koin key at runtime. Never
add a definition that resolves another type of the same object (`single<B> { get<A>() as Impl }`):
after merging it resolves itself and the app dies in a `StackOverflowError` crash loop on every
process start, including widget updates. Register one definition and attach extra types with
`bind`/`binds`. Unit tests run unshrunk and will not catch this; verify Koin wiring changes with
`./dev/android-emulator.sh install` and a clean `logcat`.

### Visual UI Changes (Roborazzi)

[Roborazzi](https://github.com/takahirom/roborazzi) renders Android and Compose UI on the JVM,
captures screenshots, and compares them with reference images to detect unintended visual changes.
Run it only for an actual visual UI change and only for the affected screen, using the relevant
screenshot-test class:

```bash
./gradlew :app:testDevReleaseUnitTest --tests <fully.qualified.ScreenshotTestClass> \
  -Proborazzi.test.verify=true
```

Do not run the complete screenshot suite for a local, isolated change. Update reference images only
when the visual change is intentional and updating them is part of the task; never blindly replace
a reference image after a failed comparison.

## Formatting

This project does not currently configure a Gradle formatter task such as `ktfmtFormat`.
Do not run `./gradlew ktfmtFormat`; it will fail with "Task 'ktfmtFormat' not found".

For formatting verification, run `git diff --check` plus only the verification task, if any, selected
under Verification Scope.

## Local Android Emulator

This host has a persistent, KVM-accelerated Pixel 7 AVD named `FoodYou_API_36`. It uses the stable
Android 16 / API 36 Google APIs x86_64 image. Manage it only through the repository helper so that a
physical device or unrelated emulator is never selected accidentally:

```bash
./dev/android-emulator.sh start
./dev/android-emulator.sh install
./dev/android-emulator.sh inspect
./dev/android-emulator.sh stop
```

The emulator runs headlessly on demand and preserves its app data and Quick Boot state. Use
`cold-start` if a saved emulator snapshot is unhealthy. When Codex invokes the helper, all commands
need host access because the filesystem sandbox exposes neither `/dev/kvm` nor the ADB server socket.
The helper waits for Android to finish booting, pins Gradle deployment to the matching emulator
serial, and uses Temurin JDK 21, the Android SDK, and the workspace Gradle cache. Its headless start
uses the AMD host GPU with Vulkan disabled; the software renderer crashes on this Fedora/Btrfs host.
The AVD runs in the transient `foodyou-emulator.service` user unit so it remains available between
separate Codex commands without starting automatically at login.

`inspect` writes the current UI layout plus normal and annotated screenshots to
`captures/android-emulator/`, which is ignored by Git. Prefer layout inspection first and use the
screenshots for visual details or UI that is absent from the accessibility hierarchy.
