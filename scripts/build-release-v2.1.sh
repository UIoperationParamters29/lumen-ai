#!/bin/bash
# Release-only build with extra heap to avoid daemon OOM on 4GB systems.
export ANDROID_HOME=/home/z/android-sdk
export JAVA_HOME=/home/z/jdk-21.0.3+9
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH

cd /home/z/my-project/lumen-ai/cortex
LOG=/home/z/my-project/lumen-ai/build-release-v2.1.log

echo "RELEASE_BUILD_STARTED $(date -Iseconds)" > "$LOG"
./gradlew :app:assembleRelease --console=plain --stacktrace \
  -Dorg.gradle.jvmargs="-Xmx2560m -XX:MaxMetaspaceSize=512m" \
  -Dorg.gradle.daemon=false \
  -Dorg.gradle.workers.max=2 \
  >> "$LOG" 2>&1
EXIT=$?
echo "RELEASE_EXIT=$EXIT" >> "$LOG"
echo "BUILD_FINISHED $(date -Iseconds)" >> "$LOG"
ls -la app/build/outputs/apk/release/ >> "$LOG" 2>&1
echo "RELEASE_EXIT=$EXIT"
