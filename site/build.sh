#!/usr/bin/env bash
# Builds dewey.owen64.me into site/dist: the page, brand files, screenshots,
# and the latest extension ZIP and Android APK pulled from GitHub Releases
# (works with a private repo, since gh uses your own login).
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(dirname "$here")"
dist="$here/dist"

rm -rf "$dist"
mkdir -p "$dist/brand" "$dist/img/app" "$dist/download"

cp "$here"/src/{style.css,site.js,404.html,privacy.html,terms.html} "$dist/"
cp "$root"/brand/logo/dewey-{horizontal,horizontal-dark,mark}.svg "$dist/brand/"
cp "$root/brand/mascot/dewey-hello.svg" "$dist/brand/"
cp "$root/brand/icons/chrome/icon.svg" "$root/brand/icons/chrome/icon32.png" "$dist/brand/"
cp "$root"/docs/images/{hero,grades,materials,home,archive,ios-preview}.png "$dist/img/"
cp "$here"/src/img/app/*.jpg "$dist/img/app/"

# Newest tag with a prefix: gh lists releases newest first.
latest_tag() {
  gh release list --repo obfuscated-tm/dewey --limit 50 --json tagName -q '.[].tagName' \
    | grep "^$1" | head -1
}

ext_tag="$(latest_tag ext-v)"
android_tag="$(latest_tag android-v)"

gh release download "$ext_tag" --repo obfuscated-tm/dewey \
  --pattern 'dewey-extension.zip' --dir "$dist/download"
gh release download "$android_tag" --repo obfuscated-tm/dewey \
  --pattern '*.apk' --output "$dist/download/dewey-android.apk"

sed -e "s/{{EXT_VERSION}}/${ext_tag#ext-v}/g" \
    -e "s/{{ANDROID_VERSION}}/${android_tag#android-v}/g" \
    "$here/src/index.html" > "$dist/index.html"

echo "Built site/dist: extension $ext_tag, Android $android_tag"
