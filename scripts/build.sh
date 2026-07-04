#!/bin/bash
# Build wrapper — fully detached, uses persistent daemon
export ANDROID_HOME=/home/z/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
cd /home/z/my-project/cortex
LOG=/home/z/my-project/build.log

echo "BUILD_STARTED $(date -Iseconds)" > "$LOG"
echo "--- Step 1: daemon warmup (help) ---" >> "$LOG"
./gradlew :app:help --console=plain >> "$LOG" 2>&1
echo "HELP_EXIT=$?" >> "$LOG"
echo "--- Step 2: assembleDebug ---" >> "$LOG"
./gradlew :app:assembleDebug --console=plain --stacktrace >> "$LOG" 2>&1
EXIT=$?
echo "" >> "$LOG"
echo "BUILD_FINISHED exit=$EXIT $(date -Iseconds)" >> "$LOG"
ls -la /home/z/my-project/cortex/app/build/outputs/apk/debug/ >> "$LOG" 2>&1
