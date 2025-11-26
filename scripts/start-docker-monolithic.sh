#!/bin/bash

# Start Arcana Cloud in Monolithic Mode using Docker Compose
set -e

echo "Starting Arcana Cloud in Monolithic Mode with Docker..."

cd "$(dirname "$0")/../deployment/monolithic"

# Build and start all services
echo "Building and starting services..."
docker-compose up --build -d

echo "Services started successfully!"
echo ""
echo "Services:"
echo "  - Application: http://localhost:8080 (gRPC: localhost:9090)"
echo "  - MySQL:       localhost:3306"
echo "  - Redis:       localhost:6379"
echo ""
echo "API Documentation: http://localhost:8080/swagger-ui.html"
echo ""
echo "To view logs: docker-compose logs -f"
echo "To stop: docker-compose down"
