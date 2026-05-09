#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
# Gradle wrapper shell script.
#

# Attempt to set APP_HOME
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
PRG="$0"

while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done

SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

APP_HOME="${APP_HOME:-./}"
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

die() {
    echo
    echo "ERROR: $*"
    echo
    exit 1
}

if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command found."
fi

# ── FIXED: Pass JVM opts and Gradle task args separately ─────────────────────
# Bug was: eval set + double "$@" caused task names (e.g. lintDebug)
# to be parsed as Java main class names.
exec "$JAVACMD" \
    -Xmx512m \
    -Xms64m \
    ${JAVA_OPTS} \
    ${GRADLE_OPTS} \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
