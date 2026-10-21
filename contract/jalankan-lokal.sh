#!/usr/bin/env bash
# Lari NYATA Schemathesis: membangun dan menjalankan pasaree-backend-service lokal + Postgres lokal,
# mendaftarkan akun PEMBELI (peran konsumen aplikasi mobile), lalu menguji endpoint mobile terhadap
# docs/api/openapi.yaml backend yang sama. Telemetri mati; tidak ada data keluar mesin.
#
# Pemakaian:
#   BACKEND_DIR=/jalur/pasaree-backend-service DATABASE_URL=postgres://postgres@localhost:5432/pasaree_uji \
#     contract/jalankan-lokal.sh
# DATABASE_URL harus basis data KOSONG khusus uji. Variabel lain: PORT (default 8197), MAX_EXAMPLES (25),
# SEED (20261022), LAPORAN_DIR (contract/laporan).
set -uo pipefail
cd "$(dirname "$0")"
: "${BACKEND_DIR:?BACKEND_DIR wajib (checkout pasaree-backend-service)}"
: "${DATABASE_URL:?DATABASE_URL wajib (DB kosong khusus uji)}"
PORT="${PORT:-8197}"
export SCHEMATHESIS_TELEMETRY=false E2E_TELEMETRY_DISABLED=1
export JWT_SECRET="rahasia-uji-kmp-lebih-dari-32-karakter" PORT
export LUMBUNG_BASE_URL="http://127.0.0.1:1" LUMBUNG_SERVICE_KEY="kunci-uji-kmp"
LAPORAN_DIR="${LAPORAN_DIR:-$PWD/laporan}"; mkdir -p "$LAPORAN_DIR"

( cd "$BACKEND_DIR" && cargo build --bin pasaree-backend-service ) || exit 2
( cd "$BACKEND_DIR" && exec ./target/debug/pasaree-backend-service ) > "$LAPORAN_DIR/server.log" 2>&1 &
PID=$!
trap 'kill $PID 2>/dev/null || true' EXIT
BASE="http://127.0.0.1:$PORT"
for _ in $(seq 1 60); do curl -fs "$BASE/kesehatan" >/dev/null 2>&1 && break; sleep 0.5; done
curl -fs "$BASE/kesehatan" >/dev/null || { echo "backend tidak naik, lihat $LAPORAN_DIR/server.log" >&2; exit 3; }

EMAIL="st-kmp@contoh.id"
curl -s -X POST "$BASE/auth/daftar" -H 'content-type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"kata_sandi\":\"rahasia-123\",\"nama\":\"ST KMP\"}" >/dev/null
TOKEN=$(curl -s -X POST "$BASE/auth/login" -H 'content-type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"kata_sandi\":\"rahasia-123\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')

BASE_URL="$BASE" SPEC="$BACKEND_DIR/docs/api/openapi.yaml" TOKEN="$TOKEN" \
  MAX_EXAMPLES="${MAX_EXAMPLES:-25}" SEED="${SEED:-20261022}" LAPORAN_DIR="$LAPORAN_DIR" \
  INCLUDE_PATH_REGEX="${INCLUDE_PATH_REGEX:-^/(kesehatan|auth|saya|katalog|keranjang|checkout|pesanan|ulasan|trip)}" \
  ./schemathesis.sh
