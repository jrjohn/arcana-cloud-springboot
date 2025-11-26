#!/bin/bash

# Start Arcana Cloud in Monolithic Mode
set -e

echo "Starting Arcana Cloud in Monolithic Mode..."

# Set environment variables
export SPRING_PROFILES_ACTIVE=monolithic
export DEPLOYMENT_MODE=monolithic

# Build the project
echo "Building the project..."
./gradlew build -x test

# Run the application
echo "Starting the application..."
java -jar build/libs/*.jar --spring.profiles.active=monolithic
