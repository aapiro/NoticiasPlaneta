# NoticiasPlaneta

DOCKER_HOST_DEFAULT := unix://$(HOME)/.colima/default/docker.sock

.PHONY: help test test-unit test-it build run up down logs backfill-ai

help:
	@echo "make test       - toda la suite (unit + arquitectura + aceptación)"
	@echo "make test-unit  - solo tests rápidos, sin Docker"
	@echo "make build      - empaqueta el jar"
	@echo "make up / down  - levanta o para el stack con Docker Compose"
	@echo "make backfill-ai - reencola enriquecidos de artículos publicados"

# Colima/Rancher: Testcontainers necesita saber dónde está el socket dentro de la VM.
TC_ENV = DOCKER_HOST=$(DOCKER_HOST_DEFAULT) TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock

test:
	$(TC_ENV) ./mvnw -B test

test-unit:
	./mvnw -B test -Dtest='!*AcceptanceTest'

build:
	./mvnw -B package

run:
	./mvnw spring-boot:run -Dspring-boot.run.profiles=local

up:
	docker compose up -d

down:
	docker compose down

logs:
	docker compose logs -f app

backfill-ai:
	./scripts/backfill-ai-jobs.sh
