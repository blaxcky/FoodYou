# Agent Notes

## Git Workflow

After each larger cohesive change, run the relevant verification, create a local commit, and
synchronize it with GitHub using the GitHub CLI (`gh`) before moving on to the next larger task.

## Local JDK 21

Android Studio is installed via Flatpak. Its JBR 21 is visible inside Android Studio as
`/app/extra/jbr`, but from the normal terminal the host path is:

```bash
/var/lib/flatpak/app/com.google.AndroidStudio/x86_64/stable/active/files/extra/jbr
```

Use it for Gradle commands because the system `java` may point to JDK 25, which breaks this
project's Gradle/Kotlin setup before compilation.

```bash
JAVA_HOME=/var/lib/flatpak/app/com.google.AndroidStudio/x86_64/stable/active/files/extra/jbr ./gradlew :app:compileDevReleaseKotlinAndroid
```

Set `GRADLE_USER_HOME` to the workspace cache for Gradle commands. This keeps Gradle wrapper locks
and dependency cache writes inside the writable repository instead of `~/.gradle`, avoiding sandbox
approval prompts for the host Gradle cache path.

```bash
GRADLE_USER_HOME=/home/markus/GitHub/FoodYou/.gradle \
JAVA_HOME=/var/lib/flatpak/app/com.google.AndroidStudio/x86_64/stable/active/files/extra/jbr \
./gradlew :app:compileDevReleaseKotlinAndroid
```

## Verification Scope

Always run the smallest meaningful verification for the change. `devRelease` is the broadest build
variant allowed by default, not a required minimum. Run release, preview, or `miniDevRelease` tasks
only when the user explicitly requests them.

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
