# Makefile (repo root)
# Usage examples:
#   make help
#   make up
#   make logs
#   make test
#   make test SERVICE=api-gateway
#   make build SERVICE=config-server
#
# Requires: docker + docker compose
# Optional: mvn (for local unit tests outside docker)

SHELL := /bin/bash

# ---- Configure these ----
COMPOSE ?= docker compose
# If your compose file isn't auto-detected, uncomment one:
# COMPOSE_FILE ?= -f docker-compose.yml
# COMPOSE_FILE ?= -f compose.yml

# List your service directory names here (Option A: each has its own pom.xml)
SERVICES := api-gateway config-server discovery-server user-service stream-service chat-service \
            sentiment-service sponsor-detection-service ml-engine-service monitoring-service notification-service

# ---- Helpers ----
define assert_service
	@if [[ -z "$(SERVICE)" ]]; then \
		echo "ERROR: SERVICE is required. Example: make test SERVICE=api-gateway"; \
		exit 1; \
	fi
	@if [[ ! -d "$(SERVICE)" ]]; then \
		echo "ERROR: SERVICE directory '$(SERVICE)' not found."; \
		echo "Known services: $(SERVICES)"; \
		exit 1; \
	fi
endef

.PHONY: help
help:
	@echo ""
	@echo "Common:"
	@echo "  make up            Build (if needed) + start everything (detached)"
	@echo "  make down          Stop everything"
	@echo "  make restart       down then up"
	@echo "  make logs          Follow logs for all services"
	@echo "  make ps            Show running containers"
	@echo ""
	@echo "Build:"
	@echo "  make build         Build all docker images"
	@echo "  make build SERVICE=<name>   Build one docker service image"
	@echo ""
	@echo "Test:"
	@echo "  make test          Run Maven tests for ALL services (local Maven)"
	@echo "  make test SERVICE=<name>    Run Maven tests for ONE service"
	@echo ""
	@echo "Clean:"
	@echo "  make clean         docker compose down (keeps volumes)"
	@echo "  make nuke          docker compose down -v (removes volumes/data)"
	@echo ""
	@echo "Config:"
	@echo "  SERVICES = $(SERVICES)"
	@echo ""

# ---- Docker Compose lifecycle ----
.PHONY: build
build:
	@if [[ -n "$(SERVICE)" ]]; then \
		echo "Building docker image for $(SERVICE)"; \
		$(COMPOSE) $(COMPOSE_FILE) build $(SERVICE); \
	else \
		echo "Building docker images for all services"; \
		$(COMPOSE) $(COMPOSE_FILE) build; \
	fi

.PHONY: up
up:
	@echo "Starting system (detached)..."
	@$(COMPOSE) $(COMPOSE_FILE) up -d

.PHONY: down
down:
	@echo "Stopping system..."
	@$(COMPOSE) $(COMPOSE_FILE) down

.PHONY: restart
restart: down up

.PHONY: logs
logs:
	@$(COMPOSE) $(COMPOSE_FILE) logs -f

.PHONY: ps
ps:
	@$(COMPOSE) $(COMPOSE_FILE) ps

.PHONY: clean
clean: down

.PHONY: nuke
nuke:
	@echo "Stopping system + removing volumes..."
	@$(COMPOSE) $(COMPOSE_FILE) down -v

# ---- Maven tests (Option A: each service has its own pom.xml) ----
# Run all service tests (local Maven)
.PHONY: test
test:
	@if [[ -n "$(SERVICE)" ]]; then \
		$(MAKE) test-one SERVICE=$(SERVICE); \
	else \
		set -e; \
		echo "Running Maven tests for all services..."; \
		for s in $(SERVICES); do \
			if [[ -f "$$s/pom.xml" ]]; then \
				echo ""; \
				echo "===== TEST $$s ====="; \
				( cd $$s && mvn -q -DskipTests=false test ); \
			else \
				echo "WARN: $$s has no pom.xml, skipping"; \
			fi; \
		done; \
		echo ""; \
		echo "All tests completed."; \
	fi

.PHONY: test-one
test-one:
	$(assert_service)
	@if [[ ! -f "$(SERVICE)/pom.xml" ]]; then \
		echo "ERROR: $(SERVICE) has no pom.xml"; \
		exit 1; \
	fi
	@echo "Running Maven tests for $(SERVICE)..."
	@cd $(SERVICE) && mvn -q -DskipTests=false test

# ---- Optional: build jars locally (not docker) ----
.PHONY: package
package:
	@if [[ -n "$(SERVICE)" ]]; then \
		$(MAKE) package-one SERVICE=$(SERVICE); \
	else \
		set -e; \
		echo "Packaging all services (local Maven)..."; \
		for s in $(SERVICES); do \
			if [[ -f "$$s/pom.xml" ]]; then \
				echo ""; \
				echo "===== PACKAGE $$s ====="; \
				( cd $$s && mvn -q -DskipTests package ); \
			else \
				echo "WARN: $$s has no pom.xml, skipping"; \
			fi; \
		done; \
	fi

.PHONY: package-one
package-one:
	$(assert_service)
	@echo "Packaging $(SERVICE)..."
	@cd $(SERVICE) && mvn -q -DskipTests package
