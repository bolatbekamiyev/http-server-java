#!/bin/sh

set -e
mvn -B --quiet package -Ddir=/tmp/codecrafters-http-target
exec java -jar /tmp/codecrafters-http-target/java_http.jar "$@"