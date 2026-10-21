# Uji kontrak: Schemathesis

Uji berbasis properti terhadap `docs/api/openapi.yaml` milik `pasaree-backend-service` (sumber kebenaran kontrak; repo ini tidak menyalinnya).

## Pakai

```bash
python3 -m venv contract/.venv && contract/.venv/bin/pip install schemathesis
export PATH="$PWD/contract/.venv/bin:$PATH"
BASE_URL=http://localhost:8080 contract/schemathesis.sh
```

Variabel: `BASE_URL` (default `http://localhost:8080`), `SPEC` (jalur/URL spec; bila kosong diunduh via `gh api` ke `contract/.cache/`), `TOKEN` (JWT pembeli audiens `pasaree`, opsional, jangan di-commit), `MAX_EXAMPLES` (default 20), `CHECKS` (default `all`).

CI: workflow manual `Schemathesis (manual)` (`workflow_dispatch`). Secret opsional `BACKEND_SPEC_TOKEN` (akses baca repo backend) dan `PASAREE_TEST_JWT`. Arahkan hanya ke staging/mock, jangan produksi.

## Catatan

- Temuan dari backend dicatat sebagai issue di `pasaree-backend-service`, bukan diperbaiki di sini.
- Endpoint `/web/*` memakai peran lain; untuk lari penuh sediakan token per peran atau batasi dengan `--include-path-regex` lewat argumen tambahan.
