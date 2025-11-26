#!/bin/bash

# Start Arcana Cloud in Layered Mode using Docker Compose
set -e

echo "Starting Arcana Cloud in Layered Mode..."

cd "$(dirname "$0")/../deployment/layered"

# Build and start all services
echo "Building and starting services..."
docker-compose up --build -d

echo "Services started successfully!"
echo ""
echo "Services:"
echo "  - Controller: http://localhost:8080"
echo "  - Service:    http://localhost:8081 (gRPC: localhost:9090)"
echo "  - Repository: http://localhost:8082 (gRPC: localhost:9091)"
echo "  - MySQL:      localhost:3306"
echo "  - Redis:      localhost:6379"
echo ""
echo "API Documentation: http://localhost:8080/swagger-ui.html"
echo ""
echo "To view logs: docker-compose logs -f"
echo "To stop: docker-compose down"
