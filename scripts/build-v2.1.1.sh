#!/bin/bash
# Build both debug + release for v2.1.1 with heap tuning for 4GB system.
export ANDROID_HOME=/home/z/android-sdk
export JAVA_HOME=/home/z/jdk-21.0.3+9
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH

cd /home/z/my-project/lumen-ai/cortex
LOG=/home/z/my-project/lumen-ai/build-v2.1.1.log

echo "BUILD_STARTED $(date -Iseconds)" > "$LOG"

echo "--- assembleDebug ---" >> "$LOG"
./gradlew :app:assembleDebug --console=plain \
  -Dorg.gradle.jvmargs="-Xmx2560m -XX:MaxMetaspaceSize=512m" \
  -Dorg.gradle.workers.max=2 \
  >> "$LOG" 2>&1
EXIT1=$?
echo "DEBUG_EXIT=$EXIT1" >> "$LOG"

echo "--- assembleRelease ---" >> "$LOG"
./gradlew :app:assembleRelease --console=plain \
  -Dorg.gradle.jvmargs="-Xmx2560m -XX:MaxMetaspaceSize=512m" \
  -Dorg.gradle.workers.max=2 \
  >> "$LOG" 2>&1
EXIT2=$?
echo "RELEASE_EXIT=$EXIT2" >> "$LOG"

echo "BUILD_FINISHED $(date -Iseconds)" >> "$LOG"
ls -la app/build/outputs/apk/debug/ >> "$LOG" 2>&1
ls -la app/build/outputs/apk/release/ >> "$LOG" 2>&1

echo "DEBUG_EXIT=$EXIT1 RELEASE_EXIT=$EXIT2"
