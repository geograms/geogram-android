# F-Droid Publishing Guide

Geogram Android uses Google Play Services (Nearby API, Location), which are **proprietary dependencies not allowed** in the official F-Droid repository.

## Solution: Host Your Own F-Droid Repository

This approach allows you to distribute Geogram via F-Droid without removing Google dependencies.

### Prerequisites

1. Install `fdroidserver`:
   ```bash
   sudo apt-get install fdroidserver
   ```

2. Create a signing key (one-time setup):
   ```bash
   keytool -genkey -v -keystore fdroid-key.jks -alias geogram -keyalg RSA -keysize 2048 -validity 10000
   ```
   **IMPORTANT**: Keep `fdroid-key.jks` secure and backed up!

### Step 1: Initialize F-Droid Repository

```bash
mkdir -p fdroid/repo
cd fdroid
fdroid init
```

This creates:
- `config.yml` - Repository configuration
- `keystore.p12` - Repository signing key

### Step 2: Configure Repository

Edit `fdroid/config.yml`:

```yaml
repo_url: "https://geograms.github.io/fdroid/repo"
repo_name: "Geogram F-Droid Repository"
repo_description: "Official F-Droid repository for Geogram Android"
repo_icon: "icon.png"
archive_older: 3
```

### Step 3: Add APK to Repository

```bash
# Copy latest release APK
cp ../app/build/outputs/apk/debug/app-debug.apk repo/geogram-0.1.0.apk

# Update repository index
fdroid update --create-metadata

# Sign repository
fdroid publish
```

### Step 4: Host Repository on GitHub Pages

1. Create a new repository: `geograms/fdroid`

2. Push the repository:
   ```bash
   git init
   git add .
   git commit -m "Initialize Geogram F-Droid repository"
   git branch -M main
   git remote add origin https://github.com/geograms/fdroid.git
   git push -u origin main
   ```

3. Enable GitHub Pages:
   - Go to repository Settings → Pages
   - Source: Deploy from branch `main`
   - Folder: `/ (root)`

4. Your F-Droid repository will be available at:
   `https://geograms.github.io/fdroid/repo`

### Step 5: Users Add Your Repository

Users install F-Droid app, then:

1. Open F-Droid
2. Settings → Repositories
3. Tap `+` to add repository
4. Enter URL: `https://geograms.github.io/fdroid/repo`
5. Find and install Geogram

### Automated Updates

Add this to your `release.sh`:

```bash
# After building APK
if [ -d "../fdroid" ]; then
    echo "Updating F-Droid repository..."
    cp geogram.apk ../fdroid/repo/geogram-${VERSION}.apk
    cd ../fdroid
    fdroid update
    fdroid publish
    git add .
    git commit -m "Update Geogram to v${VERSION}"
    git push
    cd ../geogram-android
fi
```

---

## Alternative: Submit to Official F-Droid (Requires Major Refactoring)

To submit to official F-Droid, you must:

1. **Remove all Google Play Services dependencies**:
   - Remove `play.services.nearby` (BLE Direct)
   - Remove `play.services.location`
   - Implement alternative BLE solutions using Android SDK directly

2. **Create build variants**:
   ```kotlin
   flavorDimensions += "distribution"
   productFlavors {
       create("fdroid") {
           dimension = "distribution"
           // No Google dependencies
       }
       create("standard") {
           dimension = "distribution"
           // Includes Google dependencies
       }
   }
   ```

3. **Fork fdroiddata repository**:
   ```bash
   git clone https://gitlab.com/fdroid/fdroiddata.git
   ```

4. **Create metadata file** `metadata/offgrid.geogram.yml`:
   ```yaml
   Categories:
     - Internet
     - Connectivity
   License: Apache-2.0
   WebSite: https://github.com/geograms/geogram-android
   SourceCode: https://github.com/geograms/geogram-android
   IssueTracker: https://github.com/geograms/geogram-android/issues

   AutoName: Geogram

   RepoType: git
   Repo: https://github.com/geograms/geogram-android.git

   Builds:
     - versionName: 0.1.0
       versionCode: 1
       commit: v0.1.0
       gradle:
         - fdroid
   ```

5. **Submit merge request** to fdroiddata

6. **Wait for review** (can take weeks/months)

---

## Recommendation

**Start with your own F-Droid repository** (Option 1). It's much faster and doesn't require code changes. Later, you can create an F-Droid flavor if you want official F-Droid inclusion.
