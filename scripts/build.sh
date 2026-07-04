#!/bin/bash
# Build wrapper — warmup + build in one process
export ANDROID_HOME=/home/z/android-sdk
export JAVA_HOME=/home/z/jdk-21.0.3+9
export PATH=$JAVA_HOME/bin:$PATH
cd /home/z/my-project/cortex
LOG=/home/z/my-project/build.log

echo "BUILD_STARTED $(date -Iseconds)" > "$LOG"

echo "--- Step 1: warmup ---" >> "$LOG"
./gradlew help --console=plain >> "$LOG" 2>&1
echo "WARMUP_EXIT=$?" >> "$LOG"

echo "--- Step 2: assembleDebug ---" >> "$LOG"
./gradlew :app:assembleDebug --console=plain --stacktrace >> "$LOG" 2>&1
EXIT=$?
echo "BUILD_EXIT=$EXIT" >> "$LOG"

echo "--- Step 3: assembleRelease ---" >> "$LOG"
./gradlew :app:assembleRelease --console=plain >> "$LOG" 2>&1
EXIT2=$?
echo "RELEASE_EXIT=$EXIT2" >> "$LOG"

echo "BUILD_FINISHED $(date -Iseconds)" >> "$LOG"
ls -la app/build/outputs/apk/debug/ >> "$LOG" 2>&1
ls -la app/build/outputs/apk/release/ >> "$LOG" 2>&1
