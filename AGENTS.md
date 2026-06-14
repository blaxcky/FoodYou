# Agent Notes

## Git Workflow

After each larger cohesive change, run the relevant verification and create a commit for that
change before moving on to the next larger task.

## Local JDK 21

Android Studio is installed via Flatpak. Its JBR 21 is visible inside Android Studio as
`/app/extra/jbr`, but from the normal terminal the host path is:

```bash
/var/lib/flatpak/app/com.google.AndroidStudio/x86_64/stable/active/files/extra/jbr
```

Use it for Gradle commands because the system `java` may point to JDK 25, which breaks this
project's Gradle/Kotlin setup before compilation.

```bash
JAVA_HOME=/var/lib/flatpak/app/com.google.AndroidStudio/x86_64/stable/active/files/extra/jbr ./gradlew :app:compileDebugKotlinAndroid
```

Set `GRADLE_USER_HOME` to the workspace cache for Gradle commands. This keeps Gradle wrapper locks
and dependency cache writes inside the writable repository instead of `~/.gradle`, avoiding sandbox
approval prompts for the host Gradle cache path.

```bash
GRADLE_USER_HOME=/home/markus/GitHub/FoodYou/.gradle \
JAVA_HOME=/var/lib/flatpak/app/com.google.AndroidStudio/x86_64/stable/active/files/extra/jbr \
./gradlew :app:compileDebugKotlinAndroid
```
