#!/bin/sh
#
# Gradle start up script for UN*X
#
APP_HOME=$(cd "$(dirname "$0")" && pwd)
APP_NAME="Gradle"
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Find java executable
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
