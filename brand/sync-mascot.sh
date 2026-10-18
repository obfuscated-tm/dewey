#!/bin/sh
# Copies brand/mascot/dewey-{pose}{,-dark}.svg into the apps that show them:
#   ios/App/Assets.xcassets/Dewey{Pose}.imageset/   (light + dark appearance)
#   extension/overlays/mascot/                      (overlays/mascot.js picks light/dark)
# After redrawing Dewey, replace the files in brand/mascot (same names) and
# run this from anywhere. extension/test/mascot.test.js fails if a copy drifts.
set -e
here=$(cd "$(dirname "$0")" && pwd)
src="$here/mascot"
ios="$here/../ios/App/Assets.xcassets"
ext="$here/../extension/overlays/mascot"
mkdir -p "$ext"

for pose in hello caught-up archiving uh-oh; do
  # hello → DeweyHello, caught-up → DeweyCaughtUp, uh-oh → DeweyUhOh
  name="Dewey$(echo "$pose" | awk -F- '{ for (i = 1; i <= NF; i++) printf "%s%s", toupper(substr($i,1,1)), substr($i,2) }')"
  set="$ios/$name.imageset"
  mkdir -p "$set"
  cp "$src/dewey-$pose.svg" "$src/dewey-$pose-dark.svg" "$set/"
  cp "$src/dewey-$pose.svg" "$src/dewey-$pose-dark.svg" "$ext/"
  cat > "$set/Contents.json" <<EOF
{
  "images" : [
    { "filename" : "dewey-$pose.svg", "idiom" : "universal" },
    { "appearances" : [ { "appearance" : "luminosity", "value" : "dark" } ], "filename" : "dewey-$pose-dark.svg", "idiom" : "universal" }
  ],
  "info" : { "author" : "xcode", "version" : 1 },
  "properties" : { "preserves-vector-representation" : true }
}
EOF
done
echo "Mascot copied to iOS assets and extension/overlays/mascot."
