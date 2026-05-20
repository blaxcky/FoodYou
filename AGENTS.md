# Agent Notes

## Git Workflow

After each larger cohesive change, run the relevant verification and create a commit for that
change before moving on to the next larger task.

## Local JDK 21

Android Studio is installed via Flatpak. Its JBR 21 is visible inside Android Studio as
`/app/extra/jbr`, but from the normal terminal the host path is:

```bash
/var/lib/flatpak/app/com.google.AndroidStudio/x86_64/stable/c98eca696f8cd3b09f8a9f158ccd7c0c55f1e59bb36297235759ab3afe678ba9/files/extra/jbr
```

Use it for Gradle commands because the system `java` may point to JDK 25, which breaks this
project's Gradle/Kotlin setup before compilation.

```bash
JAVA_HOME=/var/lib/flatpak/app/com.google.AndroidStudio/x86_64/stable/c98eca696f8cd3b09f8a9f158ccd7c0c55f1e59bb36297235759ab3afe678ba9/files/extra/jbr ./gradlew :app:compileDebugKotlinAndroid
```
