#!/usr/bin/env bash
# Packs the MCP server into dewey-claude.mcpb, a one-click Claude Desktop
# extension (double-click it, or drag it onto Claude's Settings → Extensions).
# Bundles server.js, src/ and production node_modules; version from package.json.
# Usage: mcp/scripts/pack.sh [out-file]   (default: mcp/dist/dewey-claude.mcpb)
set -euo pipefail

mcp="$(cd "$(dirname "$0")/.." && pwd)"
root="$(dirname "$mcp")"
out="${1:-$mcp/dist/dewey-claude.mcpb}"
stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT

cp -R "$mcp/server.js" "$mcp/src" "$mcp/package.json" "$mcp/package-lock.json" "$stage/"
cp "$root/brand/icons/ios/app-icon-1024.png" "$stage/icon.png"
version="$(node -p "require('$mcp/package.json').version")"
node -e '
  const fs = require("fs"), [src, dst, v] = process.argv.slice(1);
  const m = JSON.parse(fs.readFileSync(src, "utf8"));
  m.version = v;
  fs.writeFileSync(dst, JSON.stringify(m, null, 2) + "\n");
' "$mcp/manifest.json" "$stage/manifest.json" "$version"

(cd "$stage" && npm ci --omit=dev --ignore-scripts --no-audit --no-fund --loglevel=error)

mkdir -p "$(dirname "$out")"
rm -f "$out"
npx --yes @anthropic-ai/mcpb@2 validate "$stage/manifest.json"
npx --yes @anthropic-ai/mcpb@2 pack "$stage" "$out"
echo "Packed $out (dewey-mcp $version)"
