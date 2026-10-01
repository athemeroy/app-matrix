#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
"$JAVAC" -encoding UTF-8 -d "$OUT" \
  "$ROOT"/apps/journal/src/main/java/dev/appmatrix/journal/domain/*.java \
  "$ROOT"/apps/journal/src/main/java/dev/appmatrix/journal/DraftStore.java \
  "$ROOT"/apps/journal/src/test/java/dev/appmatrix/journal/domain/JournalStoreTest.java \
  "$ROOT"/apps/journal/src/test/java/dev/appmatrix/journal/DraftStoreTest.java
"$JAVA" -Djava.awt.headless=true -cp "$OUT" dev.appmatrix.journal.domain.JournalStoreTest
"$JAVA" -cp "$OUT" dev.appmatrix.journal.DraftStoreTest
