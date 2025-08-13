#!/usr/bin/env sh

# Minimal Gradle wrapper bootstrap to use Gradle via gradle-wrapper.jar

APP_HOME=$(cd "$(dirname "$0")"; pwd -P)

JAVA_EXE="$(which java)"
if [ ! -x "$JAVA_EXE" ]; then
  echo "ERROR: Java not found in PATH" >&2
  exit 1
fi

WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "ERROR: gradle-wrapper.jar missing. Please install Gradle or run with a system Gradle." >&2
  exit 1
fi

exec "$JAVA_EXE" -Dorg.gradle.appname=gradlew -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
