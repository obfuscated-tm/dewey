#!/usr/bin/env bash
# Builds dewey.owen64.me into site/dist: the page, brand files, screenshots,
# and the latest extension ZIP, Android APK and Claude add-on pulled from GitHub Releases
# (works with a private repo, since gh uses your own login).
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(dirname "$here")"
dist="$here/dist"

rm -rf "$dist"
mkdir -p "$dist/brand" "$dist/img/app" "$dist/download"

cp "$here"/src/{style.css,site.js,404.html} "$dist/"
cp "$root"/brand/logo/dewey-{horizontal,horizontal-dark,mark}.svg "$dist/brand/"
cp "$root/brand/mascot/dewey-hello.svg" "$dist/brand/"
cp "$root/brand/icons/chrome/icon.svg" "$root/brand/icons/chrome/icon32.png" "$dist/brand/"
cp "$root"/docs/images/{hero,grades,materials,home,archive,ios-preview}.png "$dist/img/"
cp "$here"/src/img/app/*.jpg "$dist/img/app/"

# Newest tag with a prefix: gh lists releases newest first.
latest_tag() {
  gh release list --repo obfuscated-tm/dewey --limit 50 --json tagName -q '.[].tagName' \
    | { grep "^$1" || true; } | head -1
}

ext_tag="$(latest_tag ext-v)"
android_tag="$(latest_tag android-v)"
mcp_tag="$(latest_tag mcp-v)"

gh release download "$ext_tag" --repo obfuscated-tm/dewey \
  --pattern 'dewey-extension.zip' --dir "$dist/download"
gh release download "$android_tag" --repo obfuscated-tm/dewey \
  --pattern '*.apk' --output "$dist/download/dewey-android.apk"
# The Claude add-on only has a download from mcp-v1.2.0 on (release-mcp.yml).
# Without one, the page's Claude tab says how to set it up from source instead.
mcp_download=0
if [ -n "$mcp_tag" ] && gh release download "$mcp_tag" --repo obfuscated-tm/dewey \
     --pattern 'dewey-claude.mcpb' --dir "$dist/download" 2>/dev/null; then
  mcp_download=1
else
  echo "warning: no dewey-claude.mcpb in ${mcp_tag:-any mcp-v release}; the Claude tab links to the README" >&2
fi

# The site's own version (site/package.json) goes in every page's footer.
site_version="$(node -p "require('$here/package.json').version")"

# <!--mcp-download-->…<!--/mcp-download--> shows with a download, and
# <!--mcp-source-->…<!--/mcp-source--> without one; the other is cut.
if [ "$mcp_download" = 1 ]; then cut=mcp-source; else cut=mcp-download; fi

for page in index privacy terms web; do
  sed -e "/<!--$cut-->/,/<!--\/$cut-->/d" \
      -e "s/{{EXT_VERSION}}/${ext_tag#ext-v}/g" \
      -e "s/{{MCP_VERSION}}/${mcp_tag#mcp-v}/g" \
      -e "s/{{ANDROID_VERSION}}/${android_tag#android-v}/g" \
      -e "s/{{SITE_VERSION}}/$site_version/g" \
      "$here/src/$page.html" > "$dist/$page.html"
done

echo "Built site/dist $site_version: extension $ext_tag, Android $android_tag, Claude ${mcp_tag:-none}$([ "$mcp_download" = 1 ] || echo ' (no download)')"
