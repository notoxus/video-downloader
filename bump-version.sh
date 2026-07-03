#!/usr/bin/env bash
# Bumps the app's public release version everywhere it's hardcoded, in one shot.
# Usage: ./bump-version.sh 1.0.5
#
# Updates:
#   - pom.xml                 (assembly finalName -> VideoDownloader-v1.0.5)
#   - UpdateChecker.java       (CURRENT_VERSION -> "v1.0.5")
#   - AppGUI.java              (window title -> "Video Downloader - v1.0.5")
#   - Installation.md          (all VideoDownloader-v*-<platform> filenames)
#   - android/app/build.gradle.kts            (versionName + versionCode+1)
#   - companion-android/app/build.gradle.kts  (versionName + versionCode+1)
#
# Does NOT touch pom.xml's <version>0.0.1-SNAPSHOT</version> (Maven's own
# artifact version, unrelated to the public release tag) or historical
# "vX.Y.Z+" mentions in READMEs.

set -euo pipefail

if [ $# -ne 1 ]; then
    echo "Usage: $0 <new-version>   e.g. $0 1.0.5"
    exit 1
fi

NEW="$1"
if ! [[ "$NEW" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Error: version must look like 1.0.5 (no leading 'v')"
    exit 1
fi

cd "$(dirname "${BASH_SOURCE[0]}")"

echo "Bumping to v$NEW ..."

# --- Desktop ---
sed -i -E "s/(<finalName>VideoDownloader-v)[0-9]+\.[0-9]+\.[0-9]+(<\/finalName>)/\1$NEW\2/" pom.xml

sed -i -E "s/(CURRENT_VERSION = \")v[0-9]+\.[0-9]+\.[0-9]+(\")/\1v$NEW\2/" \
    src/main/java/com/videodownloader/controller/UpdateChecker.java

sed -i -E "s/(setTitle\(\"Video Downloader - )v[0-9]+\.[0-9]+\.[0-9]+(\"\))/\1v$NEW\2/" \
    src/main/java/com/videodownloader/view/AppGUI.java

sed -i -E "s/VideoDownloader-v[0-9]+\.[0-9]+\.[0-9]+/VideoDownloader-v$NEW/g" Installation.md

# --- Android (standalone + companion): versionName + versionCode+1 each ---
bump_gradle() {
    local file="$1"
    local old_code
    old_code=$(grep -oE "versionCode = [0-9]+" "$file" | grep -oE "[0-9]+")
    local new_code=$((old_code + 1))
    sed -i -E "s/versionCode = [0-9]+/versionCode = $new_code/" "$file"
    sed -i -E "s/versionName = \"[^\"]+\"/versionName = \"$NEW\"/" "$file"
    echo "  $file: versionCode $old_code -> $new_code, versionName -> $NEW"
}
bump_gradle android/app/build.gradle.kts
bump_gradle companion-android/app/build.gradle.kts

echo ""
echo "Done. Review before committing:"
echo "  git diff --stat"
