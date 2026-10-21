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

## Lari lokal nyata (backend + Postgres lokal)

```bash
BACKEND_DIR=/jalur/pasaree-backend-service DATABASE_URL=postgres://postgres@localhost:5432/pasaree_uji \
  PATH=contract/.venv/bin:$PATH contract/jalankan-lokal.sh
```

Skrip membangun backend, menjalankannya pada port 8197 dengan DB kosong khusus uji, mendaftarkan akun PEMBELI, dan menguji endpoint mobile (`kesehatan|auth|saya|katalog|keranjang|checkout|pesanan|ulasan|trip`) memakai spec backend yang sama. Telemetri mati (`SCHEMATHESIS_TELEMETRY=false`, `E2E_TELEMETRY_DISABLED=1`). Laporan JUnit dan `server.log` ke `contract/laporan/` (diabaikan git); cache `.schemathesis/` juga diabaikan git.

### Hasil lari 2026-10-21 (Schemathesis 4.32.2, seed 20261022, 25 contoh/operasi, backend branch `feat/psr-be-006`)

564 test case dihasilkan, **13 kegagalan unik, 8 dilewati** (bukan 0: ini temuan nyata terhadap backend, belum diperbaiki di sini):

| Operasi | Temuan |
|---|---|
| `GET /katalog/produk` | ST202: `cursor=` kosong ditolak 400 "cursor tidak valid" (format cursor tidak dijelaskan di spec) |
| `GET /trip` | ST105: 400 `text/plain` untuk parameter kueri ganda (galat bukan JSON baku) |
| `POST /checkout/pratinjau` | ST202: 422 "Keranjang kosong" untuk body valid; ST103: 400 tanpa `Content-Type` tidak terdokumentasi |
| `POST /pesanan` | ST202 dan ST103 (400 tidak terdokumentasi) |
| `GET /pesanan` | ST202 (400 untuk kombinasi kueri valid) |
| `PUT /keranjang`, `POST /ulasan`, `POST /auth/refresh`, `POST /pesanan/{id}/bayar`, `POST /pesanan/{id}/retur` | ST103: 400 tanpa `Content-Type: application/json` tidak ada di spec |
| `POST /auth/login` | ST202 |

Peringatan: 9 operasi hanya mendapat 404 karena data uji (produk, pesanan) belum disemai; spec menyatakan base path `/v1` sedangkan backend lokal melayani di akar. Perbaikan = issue/PR di `pasaree-backend-service` (spec atau kode), bukan di repo ini.
