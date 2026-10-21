# Panduan Kontribusi — pasaree-app-kmp

Baca dulu [AGENTS.md](./AGENTS.md) dan semua ADR di [docs/decisions/](./docs/decisions/). Alasan alur ini ada di [ADR-0002](./docs/decisions/ADR-0002-tata-kelola-cabang.md).

## Alur: fork → branch → atomic commit → PR

1. Fork repo ke akun sendiri: `gh repo fork Coding-Skuy/pasaree-app-kmp --clone=false`, lalu clone fork dan tambah remote `upstream`:
   ```bash
   gh repo clone <akun>/pasaree-app-kmp && cd pasaree-app-kmp
   git remote add upstream https://github.com/Coding-Skuy/pasaree-app-kmp.git
   ```
2. Buat branch dari `upstream/main`. Satu branch = satu tiket = satu PR.
   Format: `feat/<kode-tiket>-<slug>`, `fix/…`, `test/…`, `docs/…`, `chore/…`; kode tiket huruf kecil (mis. `feat/psr-kmp-002-katalog`).
3. Commit kecil dan atomik (satu perubahan logis per commit), conventional commits berbahasa Indonesia: `feat:`, `fix:`, `test:`, `docs:`, `chore:`. Perbarui `CHANGELOG.md` bila perilaku berubah.
4. Tulis uji lebih dulu (merah-hijau) dari acceptance criteria tiket; jalankan uji lokal sebelum push.
5. Push ke fork, buka PR ke `Coding-Skuy/pasaree-app-kmp` base `main`, isi template PR, dan tulis `Closes #N`.
6. Minta tinjauan. Perbaikan ditambahkan sebagai commit baru pada branch yang sama.

## Aturan

- Dilarang merge sendiri, force-push ke `main`, atau mengubah proteksi branch/pengaturan repo.
- Dokumen `versions/v*/` dan ADR lama immutable; perubahan keputusan = ADR baru bernomor berikutnya.
- Kontrak API mengacu ke `docs/api/openapi.yaml` di `pasaree-backend-service`; ketidaksesuaian dicatat di PR/issue, bukan diputuskan sepihak.
- Jangan commit rahasia (`.env`, kunci, token). Token akses disimpan di penyimpanan aman platform.
- Jangan hitung total checkout di klien; harga lewat `formatIdr()`.
