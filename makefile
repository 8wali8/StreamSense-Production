# Makefile (repo root)
# Usage examples:
#   make help
#   make up
#   make logs
#   make test
#   make test SERVICE=api-gateway
#   make build SERVICE=config-server
#
# Requires: docker + docker compose + Java 21 + Maven
# Optional: Node for the frontend, uv (https://docs.astral.sh/uv/) for the Python services, Python for demo tooling

SHELL := /bin/bash

COMPOSE ?= docker compose

JAVA_SERVICES := eureka-server config-server api-gateway chat-service sentiment-service video-service recommendation-service analytics-service
PYTHON_SERVICES := ml-engine video-capture-service
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
	@echo "  make up            Package Java, build images, start everything"
	@echo "  make up-fast       Start existing images/containers without packaging"
	@echo "  make secrets       Create git-ignored local secret files from the *.example files"
	@echo "  make down          Stop everything"
	@echo "  make restart       down then up"
	@echo "  make logs          Follow logs for all services"
	@echo "  make ps            Show running containers"
	@echo "  make smoke-e2e     Run final API-level Compose smoke path"
	@echo "  make replay-smoke  Verify VOD replay alias path against a running stack"
	@echo "  make demo-seed     Seed demo chat and video-frame data"
	@echo "  make demo-open     Print/open demo URLs"
	@echo "  make twitch-up     Start stack with .env.twitch.local loaded"
	@echo "  make twitch-status Query Twitch chat connector status"
	@echo "  make twitch-video-up     Start stack with Twitch chat/video env loaded"
	@echo "  make twitch-video-status Query Twitch video capture status"
	@echo "  make twitch-transcript-up     Start stack with Twitch video/transcript env loaded"
	@echo "  make twitch-transcript-status Query transcript capture status preview"
	@echo "  make twitch-analytics-up      Start stack with Twitch transcript analytics env loaded"
	@echo "  make twitch-analytics-status  Query aggregate analytics summary"
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

# ---- Local secrets ----
# Compose mounts ./secrets/<NAME> at /run/secrets/<NAME>; kustomize builds the
# streamsense-secrets Secret from k8s/secrets/streamsense.env. Both are git-ignored.
.PHONY: secrets
secrets:
	@for example in secrets/*.example; do \
		target="$${example%.example}"; \
		if [[ ! -f "$$target" ]]; then cp "$$example" "$$target"; echo "created $$target from example"; fi; \
	done
	@if [[ ! -f k8s/secrets/streamsense.env ]]; then \
		cp k8s/secrets/streamsense.env.example k8s/secrets/streamsense.env; \
		echo "created k8s/secrets/streamsense.env from example"; \
	fi

.PHONY: up
up: package secrets
	@echo "Building images and starting system (detached)..."
	@$(COMPOSE) $(COMPOSE_FILE) up -d --build

.PHONY: up-fast
up-fast: secrets
	@echo "Starting existing system (detached, no package/build)..."
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
		( cd ml-engine && uv run --locked pytest ); \
		echo ""; \
		echo "===== TEST video-capture-service ====="; \
		( cd video-capture-service && uv run --locked pytest ); \
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
	elif [[ "$(SERVICE)" == "ml-engine" || "$(SERVICE)" == "video-capture-service" ]]; then \
		echo "Running pytest for $(SERVICE)..."; \
		cd $(SERVICE) && uv run --locked pytest; \
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
	@if [[ -f "$(SERVICE)/pom.xml" ]]; then \
		echo "Packaging $(SERVICE)..."; \
		cd $(SERVICE) && mvn -q -DskipTests package; \
	else \
		echo "No package step for $(SERVICE)"; \
	fi

.PHONY: demo-seed
demo-seed:
	@python tools/demo/seed_demo.py

.PHONY: demo-open
demo-open:
	@python tools/demo/open_demo.py

.PHONY: smoke-e2e
smoke-e2e: secrets
	@python tools/smoke/compose_smoke.py --start-compose --teardown

.PHONY: replay-smoke
replay-smoke:
	@python tools/smoke/replay_smoke.py

.PHONY: twitch-up
twitch-up:
	@if [[ ! -f ".env.twitch.local" ]]; then \
		echo "ERROR: .env.twitch.local is required for Twitch verification"; \
		exit 1; \
	fi
	@set -a; source .env.twitch.local; set +a; $(MAKE) up

.PHONY: twitch-status
twitch-status:
	@curl -fsS http://localhost:8080/api/chat/twitch/status

.PHONY: twitch-video-up
twitch-video-up:
	@if [[ ! -f ".env.twitch.local" ]]; then \
		echo "ERROR: .env.twitch.local is required for Twitch video verification"; \
		exit 1; \
	fi
	@set -a; source .env.twitch.local; set +a; $(MAKE) up

.PHONY: twitch-video-status
twitch-video-status:
	@curl -fsS http://localhost:8080/api/video/capture/status

.PHONY: twitch-transcript-up
twitch-transcript-up:
	@if [[ ! -f ".env.twitch.local" ]]; then \
		echo "ERROR: .env.twitch.local is required for Twitch transcript verification"; \
		exit 1; \
	fi
	@set -a; source .env.twitch.local; set +a; \
	STREAMSENSE_TWITCH_VIDEO_ENABLED=true \
	STREAMSENSE_TWITCH_TRANSCRIPT_ENABLED=true \
	$(MAKE) up

.PHONY: twitch-transcript-status
twitch-transcript-status:
	@curl -fsS http://localhost:8080/api/video/capture/status

.PHONY: twitch-analytics-up
twitch-analytics-up:
	@if [[ ! -f ".env.twitch.local" ]]; then \
		echo "ERROR: .env.twitch.local is required for Twitch analytics verification"; \
		exit 1; \
	fi
	@set -a; source .env.twitch.local; set +a; \
	STREAMSENSE_TWITCH_VIDEO_ENABLED=true \
	STREAMSENSE_TWITCH_TRANSCRIPT_ENABLED=true \
	$(MAKE) up

.PHONY: twitch-analytics-status
twitch-analytics-status:
	@if [[ ! -f ".env.twitch.local" ]]; then \
		echo "ERROR: .env.twitch.local is required for Twitch analytics status"; \
		exit 1; \
	fi
	@set -a; source .env.twitch.local; set +a; \
	STREAMER="$${TWITCH_VIDEO_CHANNELS%%,*}"; \
	if [[ -z "$$STREAMER" ]]; then STREAMER="$${TWITCH_CHANNELS%%,*}"; fi; \
	if [[ -z "$$STREAMER" ]]; then echo "ERROR: TWITCH_VIDEO_CHANNELS or TWITCH_CHANNELS is required"; exit 1; fi; \
	curl -fsS "http://localhost:8080/api/analytics/streams/$$STREAMER/summary?windowMinutes=15"
