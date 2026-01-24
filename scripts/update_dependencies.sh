#!/usr/bin/env bash
# update_dependencies.sh - Runs Gradle dependencyUpdates and prints a summary.
# Requires the Gradle Versions Plugin (com.github.ben-manes.versions) to be applied.
./gradlew dependencyUpdates -Drevision=release
