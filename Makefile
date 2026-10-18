.DEFAULT_GOAL := help
BACKEND_DIR := backend
MVNW := ./mvnw
WEB_PACKAGE := @sharedops/web

.PHONY: help setup hooks-install hooks-check hooks-check-staged \
        backend-run frontend-run \
        format format-check check test verify

help:
	@echo "SharedOPS development commands"
	@echo ""
	@echo "Setup:"
	@echo "  make setup                Install JS dependencies and Git hooks"
	@echo "  make hooks-install        Install pre-commit and commit-msg hooks"
	@echo ""
	@echo "Development:"
	@echo "  make backend-run          Run Spring Boot locally"
	@echo "  make frontend-run         Run React/Vite locally (after web setup)"
	@echo ""
	@echo "Code quality:"
	@echo "  make format               Auto-format Java and supported files"
	@echo "  make format-check         Check formatting without modifying files"
	@echo "  make check                Run formatting and Java lint checks"
	@echo "  make hooks-check          Run all file hooks with auto-fix"
	@echo "  make hooks-check-staged   Run hooks on staged changes"
	@echo "  make test                 Run backend unit tests"
	@echo "  make verify               Run backend verification and formatting checks"

setup:
	corepack pnpm install --frozen-lockfile
	$(MAKE) hooks-install

hooks-install:
	pre-commit install --hook-type pre-commit --hook-type commit-msg

# Local development
backend-run:
	cd $(BACKEND_DIR) && $(MVNW) spring-boot:run -Dspring-boot.run.profiles=local

# Enable once web/package.json exists
frontend-run:
	corepack pnpm --filter $(WEB_PACKAGE) dev

# Formatting
format:
	cd $(BACKEND_DIR) && $(MVNW) spotless:apply
	corepack pnpm run format:write

format-check:
	cd $(BACKEND_DIR) && $(MVNW) spotless:check
	corepack pnpm run format:check

check:
	cd $(BACKEND_DIR) && $(MVNW) spotless:check checkstyle:check
	corepack pnpm run format:check

# Git hooks
hooks-check:
	pre-commit run --all-files

hooks-check-staged:
	pre-commit run

# Tests and verification
test:
	cd $(BACKEND_DIR) && $(MVNW) test

verify:
	cd $(BACKEND_DIR) && $(MVNW) verify
	corepack pnpm run format:check
