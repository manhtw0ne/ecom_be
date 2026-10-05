# Ecom BE - Common Development Commands
# Usage: pwsh -File scripts/dev.ps1 <command>
# Example: pwsh -File scripts/dev.ps1 test

param([string]$Command = "help")

switch ($Command) {
    "help" {
        Write-Host "Available commands:" -ForegroundColor Cyan
        Write-Host "  test        - Run unit tests"
        Write-Host "  verify      - Run tests + coverage check"
        Write-Host "  build       - Build JAR (skip tests)"
        Write-Host "  run         - Run app in dev mode (needs infra)"
        Write-Host "  infra-up    - Start Redis/Kafka/MySQL via Docker"
        Write-Host "  infra-down  - Stop infrastructure containers"
        Write-Host "  docker-up   - Start full stack (app + infra)"
        Write-Host "  docker-down - Stop full stack"
        Write-Host "  coverage    - Open JaCoCo coverage report"
        Write-Host "  swagger     - Open Swagger UI in browser"
        Write-Host "  clean       - Clean build artifacts"
    }
    "test" {
        ./mvnw test
    }
    "verify" {
        ./mvnw verify
        Write-Host "`n Coverage report: target/site/jacoco/index.html" -ForegroundColor Green
    }
    "build" {
        ./mvnw package -DskipTests -q
        Write-Host "JAR built: target/*.jar" -ForegroundColor Green
    }
    "run" {
        Write-Host "Starting in dev mode. Make sure infra is running (infra-up first)" -ForegroundColor Yellow
        ./mvnw spring-boot:run
    }
    "infra-up" {
        docker compose -f docker-compose-infra.yml up -d
        Write-Host "Infrastructure started: MySQL:3306, Redis:6379, Kafka:9092" -ForegroundColor Green
    }
    "infra-down" {
        docker compose -f docker-compose-infra.yml down
    }
    "docker-up" {
        docker compose up -d --build --wait
        Write-Host "Full stack running at http://localhost:8080" -ForegroundColor Green
        Write-Host "Swagger UI: http://localhost:8080/swagger-ui.html" -ForegroundColor Cyan
        Write-Host "Grafana:    http://localhost:3000 (admin/local-grafana-password)" -ForegroundColor Cyan
    }
    "docker-down" {
        docker compose down
    }
    "coverage" {
        Start-Process "target/site/jacoco/index.html"
    }
    "swagger" {
        Start-Process "http://localhost:8080/swagger-ui.html"
    }
    "clean" {
        ./mvnw clean
        Write-Host "Build artifacts cleaned" -ForegroundColor Green
    }
    default {
        Write-Host "Unknown command: $Command. Run with 'help' to see available commands." -ForegroundColor Red
        exit 1
    }
}