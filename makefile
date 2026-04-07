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

COMPOSE ?= docker compose

JAVA_SERVICES := eureka-server config-server api-gateway chat-service sentiment-service video-service recommendation-service
PYTHON_SERVICES := ml-engine
FRONTEND_SERVICE := frontend
SERVICES := $(JAVA_SERVICES) $(PYTHON_SERVICES) $(FRONTEND_SERVICE)

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
	@echo "  make test          Run Java, Python, and frontend checks"
	@echo "  make test SERVICE=<name>    Run checks for one service"
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

.PHONY: test
test:
	@if [[ -n "$(SERVICE)" ]]; then \
		$(MAKE) test-one SERVICE=$(SERVICE); \
	else \
		set -e; \
		echo "Running Maven tests for Java services..."; \
		for s in $(JAVA_SERVICES); do \
			if [[ -f "$$s/pom.xml" ]]; then \
				echo ""; \
				echo "===== TEST $$s ====="; \
				( cd $$s && mvn -q -DskipTests=false test ); \
			fi; \
		done; \
		echo ""; \
		echo "===== TEST ml-engine ====="; \
		( cd ml-engine && PYTHONPATH=src/main/python pytest src/test/python ); \
		echo ""; \
		echo "===== CHECK frontend ====="; \
		( cd frontend && npm run lint && npm run build ); \
		echo ""; \
		echo "All checks completed."; \
	fi

.PHONY: test-one
test-one:
	$(assert_service)
	@if [[ -f "$(SERVICE)/pom.xml" ]]; then \
		echo "Running Maven tests for $(SERVICE)..."; \
		cd $(SERVICE) && mvn -q -DskipTests=false test; \
	elif [[ "$(SERVICE)" == "ml-engine" ]]; then \
		echo "Running pytest for ml-engine..."; \
		cd ml-engine && PYTHONPATH=src/main/python pytest src/test/python; \
	elif [[ "$(SERVICE)" == "frontend" ]]; then \
		echo "Running frontend lint/build..."; \
		cd frontend && npm run lint && npm run build; \
	else \
		echo "ERROR: Unsupported SERVICE=$(SERVICE)"; \
		exit 1; \
	fi

# ---- Optional: build jars locally (not docker) ----
.PHONY: package
package:
	@if [[ -n "$(SERVICE)" ]]; then \
		$(MAKE) package-one SERVICE=$(SERVICE); \
	else \
		set -e; \
		echo "Packaging all services (local Maven)..."; \
		for s in $(JAVA_SERVICES); do \
			if [[ -f "$$s/pom.xml" ]]; then \
				echo ""; \
				echo "===== PACKAGE $$s ====="; \
				( cd $$s && mvn -q -DskipTests package ); \
			fi; \
		done; \
	fi

.PHONY: package-one
package-one:
	$(assert_service)
	@echo "Packaging $(SERVICE)..."
	@cd $(SERVICE) && mvn -q -DskipTests package
