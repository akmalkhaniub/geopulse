#!/usr/bin/env bash
USER="${1:-admin}"
PASS="${2:-demo1234}"
URL="${KEYCLOAK_URL:-http://localhost:8180}"

curl -s -X POST "$URL/realms/fleet/protocol/openid-connect/token" \
  -d "client_id=fleet-cli" \
  -d "grant_type=password" \
  -d "username=$USER" \
  -d "password=$PASS" | jq -r '.access_token'
