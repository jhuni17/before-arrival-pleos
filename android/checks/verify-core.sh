#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -n "${JAVA_HOME:-}" ]; then
    java_bin="$JAVA_HOME/bin/java"
    javac_bin="$JAVA_HOME/bin/javac"
else
    java_bin=java
    javac_bin=javac
fi
mkdir -p build/core-check
source_dir=app/src/main/java/com/beforearrival/briefing
"$javac_bin" -encoding UTF-8 --release 17 -d build/core-check \
    "$source_dir/RouteInput.java" "$source_dir/SampleRouteInput.java" \
    "$source_dir/Briefing.java" "$source_dir/NavigationRouteInput.java" \
    "$source_dir/NaviTimeContract.java" "$source_dir/AccessPolicy.java" \
    checks/BriefingCheck.java checks/NavigationCheck.java
"$java_bin" -cp build/core-check com.beforearrival.briefing.BriefingCheck
"$java_bin" -cp build/core-check com.beforearrival.briefing.NavigationCheck
