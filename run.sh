#!/bin/sh
# Compiles and starts the Bank Management System.
# Database settings can be overridden with DB_URL, DB_USER and DB_PASSWORD.
set -e
cd "$(dirname "$0")"
CP="jcalendar-tz-1.3.3-4.jar:mysql-connector-java-8.0.28.jar"
mkdir -p out
javac -cp "$CP" -d out *.java
java -cp "out:.:$CP" login
