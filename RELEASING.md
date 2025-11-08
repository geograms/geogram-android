# Release Process

This document describes how to create a new release of Geogram Android.

## Prerequisites

1. **GitHub CLI** must be installed and authenticated:
   ```bash
   gh auth login
   ```

2. **Java 17** must be available at `/usr/lib/jvm/java-17-openjdk-amd64`

3. All changes must be committed and pushed to the `main` branch

## Creating a Release

### 1. Update Version

Edit `app/build.gradle.kts` and update the version:

```kotlin
versionCode = 2  // Increment by 1
versionName = "0.2.0"  // Follow semantic versioning
```

Semantic versioning format: `MAJOR.MINOR.PATCH`
- **MAJOR**: Incompatible API changes
- **MINOR**: New functionality (backwards compatible)
- **PATCH**: Bug fixes (backwards compatible)

### 2. Commit Version Change

```bash
git add app/build.gradle.kts
git commit -m "Bump version to 0.2.0"
git push
```

### 3. Run Release Script

```bash
./release.sh
```

The script will automatically:
- ✅ Extract version from `build.gradle.kts`
- ✅ Clean previous builds
- ✅ Build debug APK with Gradle
- ✅ Copy APK to `geogram.apk`
- ✅ Generate release notes from git commits
- ✅ Create GitHub release with tag `v{VERSION}`
- ✅ Upload `geogram.apk` to the release
- ✅ Clean up temporary files

### 4. Verify Release

Visit the release URL printed by the script and verify:
- APK is downloadable as `geogram.apk`
- Version number is correct
- Release notes are accurate

## Manual Release (if script fails)

If the automated script fails, you can create a release manually:

```bash
# Build APK
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew clean assembleDebug

# Copy to clean filename
cp app/build/outputs/apk/debug/app-debug.apk geogram.apk

# Create release
GIT_CONFIG_NOSYSTEM=1 gh release create v0.2.0 \
  geogram.apk \
  --title "v0.2.0" \
  --notes "Release notes here..."

# Clean up
rm geogram.apk
```

## Troubleshooting

### "Tag already exists"
The version in `build.gradle.kts` hasn't been updated. Increment the version and try again.

### "GitHub CLI is not authenticated"
Run `gh auth login` to authenticate.

### "APK build failed"
Check the Gradle output for errors. Ensure all dependencies are available and code compiles successfully.

### Git config permission errors
The script uses `GIT_CONFIG_NOSYSTEM=1` to work around system git config permission issues. If you still encounter problems, check git configuration permissions.
