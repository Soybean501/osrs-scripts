#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$PROJECT_DIR/src"
OUT_DIR="$PROJECT_DIR/out"
CLIENT_JAR="/Users/harrytowers/DreamBot/BotData/client.jar"
SCRIPTS_DIR="$HOME/DreamBot/Scripts"

mkdir -p "$OUT_DIR" "$SCRIPTS_DIR"

# Each package becomes its own jar under build/ with its own version from @ScriptManifest
# For now, emit two jars: HelloWorld and DommiksNeedles if present

# Compile all sources
find "$SRC_DIR" -name "*.java" > "$OUT_DIR/sources.txt"
if [ ! -s "$OUT_DIR/sources.txt" ]; then
  echo "No Java sources found in $SRC_DIR" >&2
  exit 1
fi
javac -cp "$CLIENT_JAR" -d "$OUT_DIR" @"$OUT_DIR/sources.txt"

# Package per class if exists
cd "$OUT_DIR"

if [ -f scripts/HelloWorld.class ]; then
  jar cf "$PROJECT_DIR/HelloWorld.jar" -C "$OUT_DIR" scripts/HelloWorld.class
  cp -f "$PROJECT_DIR/HelloWorld.jar" "$SCRIPTS_DIR/"
fi

if [ -f scripts/DommiksNeedles.class ]; then
  jar cf "$PROJECT_DIR/DommiksNeedles.jar" -C "$OUT_DIR" scripts/DommiksNeedles.class
  cp -f "$PROJECT_DIR/DommiksNeedles.jar" "$SCRIPTS_DIR/"
fi

# Package LogLocation tool
if [ -f scripts/LogLocation.class ]; then
  jar cf "$PROJECT_DIR/LogLocation.jar" -C "$OUT_DIR" scripts/LogLocation.class
  cp -f "$PROJECT_DIR/LogLocation.jar" "$SCRIPTS_DIR/"
fi

echo "Built and installed jars to $SCRIPTS_DIR"