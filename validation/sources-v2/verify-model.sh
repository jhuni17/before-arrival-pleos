#!/bin/sh
# Public API35 compile/model check only. This does not build or install an APK.
set -eu
cd "$(dirname "$0")/../.."
api35_jar=${1:?Pass the public android-all API35 jar path}
if [ -n "${JAVA_HOME:-}" ]; then
    source_javac="$JAVA_HOME/bin/javac"
    source_java="$JAVA_HOME/bin/java"
else
    source_javac=javac
    source_java=java
fi
source_checks_dir=$(mktemp -d)
trap 'rm -rf "$source_checks_dir"' EXIT HUP INT TERM
rg --files android/app/src/main/java android/app/src/sample/java | sort > "$source_checks_dir/sources.txt"
printf '%s\n' validation/sources-v2/compile-only/com/beforearrival/briefing/R.java >> "$source_checks_dir/sources.txt"
"$source_javac" -encoding UTF-8 --release 17 -cp "$api35_jar" -d "$source_checks_dir/classes" @"$source_checks_dir/sources.txt"
"$source_javac" -encoding UTF-8 --release 17 -cp "$api35_jar:$source_checks_dir/classes" -d "$source_checks_dir/classes" validation/sources-v2/CatalogHarness.java
"$source_java" -cp "$api35_jar:$source_checks_dir/classes" com.beforearrival.briefing.CatalogHarness shared/catalog.json
