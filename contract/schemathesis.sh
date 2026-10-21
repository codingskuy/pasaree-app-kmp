#!/usr/bin/env bash
# Uji properti (Schemathesis) terhadap openapi.yaml pasaree-backend-service.
# Env: BASE_URL (default http://localhost:8080), SPEC (jalur/URL spec; default: unduh via gh),
#      TOKEN (JWT pembeli, opsional; jangan commit), MAX_EXAMPLES (default 20), CHECKS (default all).
set -euo pipefail

cd "$(dirname "$0")"
BASE_URL="${BASE_URL:-http://localhost:8080}"
MAX_EXAMPLES="${MAX_EXAMPLES:-20}"
CHECKS="${CHECKS:-all}"
CACHE=".cache"
mkdir -p "$CACHE"

if [ -z "${SPEC:-}" ]; then
  SPEC="$CACHE/openapi.yaml"
  gh api repos/Coding-Skuy/pasaree-backend-service/contents/docs/api/openapi.yaml \
    -q .content | base64 -d > "$SPEC"
fi

command -v st >/dev/null 2>&1 || {
  echo "schemathesis belum terpasang: python3 -m venv .venv && .venv/bin/pip install schemathesis" >&2
  exit 127
}

ARGS=(run "$SPEC" --url "$BASE_URL" --checks "$CHECKS" --max-examples "$MAX_EXAMPLES")
[ -n "${TOKEN:-}" ] && ARGS+=(--header "Authorization: Bearer $TOKEN")
ARGS+=(--header "X-Client: kmp-android" --header "X-App-Version: 0.0.0")

echo "Schemathesis -> $BASE_URL (spec: $SPEC)"
exec st "${ARGS[@]}"
