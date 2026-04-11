#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env-dev"

if [ ! -f "$ENV_FILE" ]; then
  echo "Error: .env-dev file not found. Copy .env.example to .env-dev and fill in your credentials."
  exit 1
fi

set -a
source "$ENV_FILE"
set +a

SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
