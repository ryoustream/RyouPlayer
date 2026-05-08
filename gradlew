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

set_java_home() {
    if [ -n "$JAVA_HOME" ]; then
        JAVACMD="$JAVA_HOME/bin/java"
        if [ ! -x "$JAVACMD" ]; then
            die "JAVA_HOME is set to an invalid directory: $JAVA_HOME"
        fi
    else
        JAVACMD="java"
        which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found."
    fi
}

die() {
    echo
    echo "ERROR: $*"
    echo
    exit 1
}

set_java_home

# Collect all arguments for the java command
eval set -- $DEFAULT_JVM_OPTS '"$@"'

exec "$JAVACMD" "$@" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
