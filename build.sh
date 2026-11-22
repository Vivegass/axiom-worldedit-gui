#!/usr/bin/env bash
# Builds the addon with plain javac against the jars Prism Launcher already has
# (Minecraft 26.3 is unobfuscated, so no Loom/remapping is needed).
set -euo pipefail
cd "$(dirname "$0")"

VERSION="0.2.0"
JDK="${JDK:-/c/Program Files/Java/jdk-25.0.2}"
PRISM="${PRISM:-$(cygpath -m "$APPDATA")/PrismLauncher}"
LIBS="$PRISM/libraries"
MODS="${MODS:-$PRISM/instances/PWGood__26.3/minecraft/mods}"
AXIOM="${AXIOM:-$MODS/Axiom-6.1.5-for-MC26.3.jar}"
WORLDEDIT="${WORLDEDIT:-$MODS/worldedit-mod-7.4.6.jar}"
FABRIC_API="${FABRIC_API:-$(ls "$MODS"/fabric-api-*.jar | head -1)}"

CP="$AXIOM;$WORLDEDIT"
CP="$CP;$LIBS/com/mojang/minecraft/26.3/minecraft-26.3-client.jar"
CP="$CP;$LIBS/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar"
CP="$CP;$LIBS/net/fabricmc/sponge-mixin/0.17.5+mixin.0.8.7/sponge-mixin-0.17.5+mixin.0.8.7.jar"
CP="$CP;$LIBS/org/joml/joml/1.10.9/joml-1.10.9.jar"
CP="$CP;$LIBS/com/google/code/gson/gson/2.14.0/gson-2.14.0.jar"
CP="$CP;$LIBS/com/google/guava/guava/33.6.0-jre/guava-33.6.0-jre.jar"
CP="$CP;$LIBS/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar"
CP="$CP;$LIBS/it/unimi/dsi/fastutil/8.5.18/fastutil-8.5.18.jar"
CP="$CP;$LIBS/com/mojang/brigadier/1.3.11/brigadier-1.3.11.jar"
CP="$CP;$LIBS/com/mojang/datafixerupper/10.0.21/datafixerupper-10.0.21.jar"

rm -rf build && mkdir -p build/classes build/deps
# Axiom's public client API ships inside the Axiom jar; some Axiom classes we use refer to it.
(cd build/deps && "$JDK/bin/jar" xf "$AXIOM" META-INF/jars/axiomclientapi-unobf.jar)
CP="$CP;build/deps/META-INF/jars/axiomclientapi-unobf.jar"
# Some Axiom classes implement Fabric API interfaces; only the module they need goes on the classpath.
(cd build/deps && "$JDK/bin/jar" xf "$FABRIC_API" $("$JDK/bin/jar" tf "$FABRIC_API" | grep 'fabric-block-getter-api'))
for j in build/deps/META-INF/jars/fabric-*.jar; do CP="$CP;$j"; done
"$JDK/bin/javac" --release 25 -proc:none -encoding UTF-8 -Xlint:-options -cp "$CP" -d build/classes $(find src/main/java -name '*.java')
cp -r src/main/resources/. build/classes/
cp LICENSE build/classes/LICENSE_axiom_worldedit_gui
"$JDK/bin/jar" --create --file "build/axiom-worldedit-gui-$VERSION.jar" -C build/classes .
echo "Built build/axiom-worldedit-gui-$VERSION.jar"

if [ "${1:-}" = "--install" ]; then
  # Two versions of the addon in mods/ would stop the game from starting.
  rm -f "$MODS"/axiom-worldedit-gui-*.jar
  cp "build/axiom-worldedit-gui-$VERSION.jar" "$MODS/"
  echo "Copied to $MODS"
fi
