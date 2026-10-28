#!/bin/bash
# Rebuilds graphics-shim.jar: Java2D-backed test doubles for android.graphics used by SceneRenderShotTest.
cd "$(dirname "$0")"; source /workspace/tools/env.sh
UIG=$(find $GRADLE_USER_HOME -name "ui-graphics-release.aar" -path "*1.6.3*" | head -1); mkdir -p /tmp/uig && (cd /tmp/uig && unzip -q -o $UIG classes.jar)
KT=$(find $GRADLE_USER_HOME -name "kotlin-stdlib-1.9*.jar" | head -1)
rm -rf out && mkdir out && javac -nowarn -d out -cp /tmp/uig/classes.jar:$KT $(find src -name "*.java") && (cd out && jar cf ../graphics-shim.jar .)
