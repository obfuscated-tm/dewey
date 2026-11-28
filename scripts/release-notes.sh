#!/usr/bin/env bash
# Prints the CHANGELOG.md section for one release, for a GitHub release's notes.
# Usage: scripts/release-notes.sh "Android 0.7"   (matches "## <date> — Android 0.7",
# also "## <date> — Extension 1.13.0 · MCP 1.1.1")
# Prints nothing if there is no such heading.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
awk -v want="$1" '
  /^## / {
    if (found) exit
    h = $0 " ·"
    if (index(h, "— " want " ·") || index(h, "· " want " ·")) { found = 1; next }
  }
  found { print }
' "$root/CHANGELOG.md" | sed -e '/./,$!d'
