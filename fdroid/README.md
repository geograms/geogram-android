# F-Droid Publishing Guide

Geogram Android is now **fully compatible with F-Droid** after removing all Google Play Services dependencies in v0.2.0.

## Option 1: Submit to Official F-Droid Repository (Recommended)

This is the best option for maximum reach and user trust.

### Prerequisites

1. Ensure your app is published on GitHub with proper tags
2. Have a clear LICENSE file (Apache-2.0, GPL, etc.)
3. Ensure the app builds reproducibly

### Step 1: Fork fdroiddata Repository

```bash
git clone https://gitlab.com/fdroid/fdroiddata.git
cd fdroiddata
git checkout -b add-geogram
```

### Step 2: Create Metadata File

Create `metadata/offgrid.geogram.yml`:

```yaml
Categories:
  - Internet
  - Security
  - Navigation
License: Apache-2.0
WebSite: https://github.com/geograms/geogram-android
SourceCode: https://github.com/geograms/geogram-android
IssueTracker: https://github.com/geograms/geogram-android/issues
Changelog: https://github.com/geograms/geogram-android/releases

AutoName: Geogram
Summary: Offline-first mesh communication app

Description: |-
  Geogram is an offline-first communication ecosystem for resilient, decentralized
  messaging. It uses NOSTR protocol for end-to-end encrypted messaging, BLE beacons
  for proximity-based discovery, and integrates with radio communications (APRS/FM).

  Features:
  * NOSTR-based encrypted messaging
  * BLE beacon proximity detection
  * Offline message caching and sync
  * Native Android APIs (no Google dependencies)
  * APRS integration for radio communications

RepoType: git
Repo: https://github.com/geograms/geogram-android.git

Builds:
  - versionName: 0.2.0
    versionCode: 2
    commit: v0.2.0
    subdir: app
    gradle:
      - yes
```

### Step 3: Test Local Build

Before submitting, test that F-Droid can build your app:

```bash
fdroid build offgrid.geogram:2
```

If successful, you'll see "Build succeeded" at the end.

### Step 4: Submit Merge Request

1. Commit your changes:
   ```bash
   git add metadata/offgrid.geogram.yml
   git commit -m "New app: Geogram"
   git push origin add-geogram
   ```

2. Create merge request on GitLab:
   - Go to https://gitlab.com/fdroid/fdroiddata/-/merge_requests/new
   - Select your branch: `add-geogram`
   - Fill in the template (they have a checklist)
   - Submit

3. Monitor the merge request for feedback from F-Droid reviewers

### Step 5: Wait for Review

- Initial response: Usually within 1-2 weeks
- Full review and merge: Can take 2-4 weeks
- Once merged, your app appears in F-Droid within 24-48 hours

### Common Review Feedback

Be prepared to address:
- **Reproducible builds**: They verify the build produces the same APK
- **Non-free dependencies**: All dependencies must be open source
- **Build issues**: Gradle version, SDK requirements, etc.
- **Metadata accuracy**: Description, license, categories

---

## Option 2: IzzyOnDroid Repository (Faster Alternative)

IzzyOnDroid is a trusted third-party F-Droid repository with easier submission.

### Advantages
- Much faster approval (usually 1-3 days)
- Uses pre-built APKs from GitHub releases
- Good for getting started quickly

### How to Submit

1. Go to https://apt.izzysoft.de/fdroid/index/info
2. Click "Submit an app"
3. Provide:
   - Package name: `offgrid.geogram`
   - GitHub releases URL: `https://github.com/geograms/geogram-android/releases`
   - APK name pattern: `geogram.apk`

4. Wait for Izzy to review (usually 1-3 days)

### Users Add IzzyOnDroid

1. Open F-Droid app
2. Settings → Repositories
3. Tap `+` add repository
4. Enter: `https://apt.izzysoft.de/fdroid/repo`
5. Search for "Geogram"

---

## Option 3: Host Your Own F-Droid Repository

Only use this if you want complete control or need to test before official submission.

### Step 1: Install fdroidserver

```bash
sudo apt-get install fdroidserver
```

### Step 2: Initialize Repository

```bash
mkdir -p fdroid/repo
cd fdroid
fdroid init
```

### Step 3: Configure Repository

Edit `fdroid/config.yml`:

```yaml
repo_url: "https://geograms.github.io/geogram-android/fdroid/repo"
repo_name: "Geogram Official Repository"
repo_description: "F-Droid repository for Geogram Android"
archive_older: 3
```

### Step 4: Add APK and Publish

```bash
# Copy APK from releases
cp ../app/build/outputs/apk/debug/app-debug.apk repo/offgrid.geogram_2.apk

# Update repository index
fdroid update --create-metadata

# Sign and publish
fdroid publish
```

### Step 5: Host on GitHub Pages

```bash
# From fdroid directory
git init
git add .
git commit -m "Initialize F-Droid repository"
git push
```

Enable GitHub Pages in repository settings, then users can add:
`https://geograms.github.io/geogram-android/fdroid/repo`

---

## Recommendation

**Start with IzzyOnDroid** (Option 2) for immediate availability, then submit to official F-Droid (Option 1) for long-term presence. This gives you:

1. Quick availability (IzzyOnDroid: 1-3 days)
2. Official presence (F-Droid: 2-4 weeks)
3. Maximum user reach

Both repositories can coexist - users will get the same APK from either source.

---

## Version Code Guidelines

F-Droid requires monotonically increasing version codes:

- v0.1.0 → versionCode: 1
- v0.2.0 → versionCode: 2
- v0.3.0 → versionCode: 3

Never reuse or decrease version codes.
