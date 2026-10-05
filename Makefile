.PHONY: help test verify build run infra-up infra-down docker-up docker-down coverage clean

help:
	@echo "Ecom BE - Available targets:"
	@echo "  make test        Run unit tests"
	@echo "  make verify      Run tests + JaCoCo coverage check"
	@echo "  make build       Build JAR (skip tests)"
	@echo "  make run         Run in dev mode (start infra first)"
	@echo "  make infra-up    Start Redis/Kafka/MySQL"
	@echo "  make infra-down  Stop infrastructure"
	@echo "  make docker-up   Start full stack"
	@echo "  make docker-down Stop full stack"
	@echo "  make clean       Remove build artifacts"

test:
	./mvnw test

verify:
	./mvnw verify

build:
	./mvnw package -DskipTests -q

run:
	./mvnw spring-boot:run

infra-up:
	docker compose -f docker-compose-infra.yml up -d

infra-down:
	docker compose -f docker-compose-infra.yml down

docker-up:
	docker compose up -d --build --wait
	@echo "App running at http://localhost:8080"
	@echo "Swagger UI: http://localhost:8080/swagger-ui.html"

docker-down:
	docker compose down

clean:
	./mvnw clean