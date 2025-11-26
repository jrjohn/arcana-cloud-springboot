#!/bin/bash

# Generate Protocol Buffer and gRPC code
set -e

echo "Generating Protocol Buffer and gRPC code..."

cd "$(dirname "$0")/.."

# Run Gradle protobuf tasks
./gradlew generateProto

echo "Protocol Buffer code generated successfully!"
echo "Generated files are in: build/generated/source/proto/"
