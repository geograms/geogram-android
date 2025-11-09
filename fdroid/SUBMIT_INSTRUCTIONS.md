# F-Droid Submission Instructions

All files are prepared. Choose your submission method:

---

## Option 1: IzzyOnDroid (Fastest - 1-3 Days) ⭐ RECOMMENDED

**Manual steps required:**

1. Visit: https://apt.izzysoft.de/fdroid/index/info
2. Click "Submit an app"
3. Fill in the form:
   ```
   Package name: offgrid.geogram
   GitHub releases URL: https://github.com/geograms/geogram-android/releases
   APK filename pattern: geogram.apk
   ```
4. Submit and wait for email confirmation (1-3 days)

**Why this is best:** Fastest approval, uses your GitHub releases, no complex setup needed.

---

## Option 2: Official F-Droid (2-4 Weeks)

**Automated preparation complete!** Metadata file is ready at:
`fdroid/metadata/offgrid.geogram.yml`

**Manual steps required:**

1. Fork the fdroiddata repository:
   ```bash
   cd /tmp
   git clone https://gitlab.com/fdroid/fdroiddata.git
   cd fdroiddata
   git checkout -b add-geogram
   ```

2. Copy the metadata file:
   ```bash
   cp /home/brito/code/geogram/geogram-android/fdroid/metadata/offgrid.geogram.yml \
      metadata/offgrid.geogram.yml
   ```

3. Test the build (optional but recommended):
   ```bash
   fdroid build offgrid.geogram:2
   ```

4. Commit and push:
   ```bash
   git add metadata/offgrid.geogram.yml
   git commit -m "New app: Geogram - Offline-first mesh communication"
   git push origin add-geogram
   ```

5. Create merge request on GitLab:
   - Go to: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/new
   - Select branch: `add-geogram`
   - Fill in description (explain it's a mesh communication app with NOSTR)
   - Submit

6. Monitor merge request for reviewer feedback

---

## Option 3: Both! (Recommended Strategy)

Submit to **IzzyOnDroid first** (quick availability), then submit to **official F-Droid** (long-term presence).

Users will get the same APK from either source. This gives you:
- Immediate availability (IzzyOnDroid)
- Official presence (F-Droid)
- Maximum user reach

---

## What's Already Done

✅ Metadata file created (`fdroid/metadata/offgrid.geogram.yml`)
✅ Version code updated to 2 in `app/build.gradle.kts`
✅ Google Play Services removed (F-Droid compatible)
✅ LICENSE file exists (Apache-2.0)
✅ GitHub releases published with APK
✅ Documentation updated

## What You Need To Do

Choose one or both options above and follow the manual steps. The submission process requires:
- IzzyOnDroid: Web form submission (no automation possible)
- Official F-Droid: GitLab merge request (requires your GitLab account)

Both are simple and the files are ready to use!
