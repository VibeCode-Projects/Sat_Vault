#!/usr/bin/env bash
set -e

echo "Starting MySQL container..."
docker compose up -d

echo "Waiting for MySQL to be ready..."
for i in $(seq 1 30); do
  if docker inspect -f '{{.State.Health.Status}}' satvault-mysql 2>/dev/null | grep -q "healthy"; then
    echo "MySQL is ready!"
    break
  fi
  sleep 2
done

chmod +x ./mvnw
echo "Starting SatVault Spring Boot application on http://localhost:8080 ..."
./mvnw spring-boot:run
