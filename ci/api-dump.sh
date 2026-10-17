#!/usr/bin/env bash
# Writes method/field signatures (javap, no code) of the Minecraft and Fabric API classes the mod
# uses into $1, so they can be read without source access. Packages: ci/api-packages.txt (every
# class directly in the package); single classes: ci/api-classes.txt.
set -u
out="$1"; mkdir -p "$out"
mc=$(find ~/.gradle/caches .gradle -name 'minecraft-client*.jar' -path '*fabric-loom*' 2>/dev/null | grep -v sources | xargs ls -S 2>/dev/null | head -1)
[ -z "$mc" ] && mc=$(find ~/.gradle/caches .gradle -name '*.jar' -path '*minecraft*' 2>/dev/null | grep -iE 'client|merged' | grep -v sources | xargs ls -S 2>/dev/null | head -1)
echo "minecraft jar: $mc" > "$out/README.txt"
unzip -Z1 "$mc" | grep '^net/minecraft/.*\.class$' | sed 's/\.class$//; s#/#.#g' | sort > "$out/classes.txt"
fab=$(find ~/.gradle/caches -name 'fabric-*.jar' -path '*net.fabricmc.fabric-api*' 2>/dev/null | grep -v sources | sort -u)
: > "$out/fabric-classes.txt"
for j in $fab; do unzip -Z1 "$j" 2>/dev/null | grep '^net/fabricmc/fabric/api/.*\.class$' | sed 's/\.class$//; s#/#.#g' >> "$out/fabric-classes.txt"; done
sort -u -o "$out/fabric-classes.txt" "$out/fabric-classes.txt"
# nested fabric jars (fabric-api bundles its modules)
cp=$(echo $fab | tr ' ' ':'):$mc
dump() { # $1 = class, $2 = file
  echo "=== $1" >> "$2"
  javap -p -cp "$cp" "$1" >> "$2" 2>&1 || true
}
while read -r pkg; do
  [ -z "$pkg" ] && continue; case "$pkg" in \#*) continue;; esac
  f="$out/pkg-$pkg.txt"; : > "$f"
  # one javap run per 300 classes (a JVM start per class takes far too long)
  grep -E "^${pkg//./\\.}\.[A-Za-z0-9_\$]+$" "$out/classes.txt" "$out/fabric-classes.txt" -h | xargs -r -n 300 javap -p -cp "$cp" >> "$f" 2>&1 || true
done < ci/api-packages.txt
f="$out/classes-picked.txt"; : > "$f"
grep -v '^#' ci/api-classes.txt | grep -v '^$' | xargs -r javap -p -cp "$cp" >> "$f" 2>&1 || true
ls -la "$out"
