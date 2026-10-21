# ADR-0002 — Tata kelola cabang dan alur kontribusi

Status: Diusulkan | Tanggal: 2026-10-21

## Konteks

Pekerjaan di repo ini dikerjakan banyak kontributor, termasuk agent AI. Tanpa alur seragam, riwayat `main` rawan campur-aduk antar tiket dan perubahan tanpa tinjauan.

## Keputusan

1. Alur fork → branch dari `upstream/main` → atomic commit → PR ke `Coding-Skuy/pasaree-app-kmp` (rinci di `CONTRIBUTING.md`).
2. Penamaan branch `feat|fix|test|docs|chore/<kode-tiket-huruf-kecil>-<slug>`; satu branch = satu tiket = satu PR; PR memakai `Closes #N`.
3. Conventional commits berbahasa Indonesia; PR memakai `.github/pull_request_template.md`.
4. `.github/CODEOWNERS` menetapkan `@Coding-Skuy` sebagai pemilik seluruh repo sampai ada tim lain.
5. Usulan proteksi branch `main` (USULAN SAJA, belum diterapkan oleh PR ini; penerapan oleh admin repo):
   - wajib PR + minimal 1 persetujuan, termasuk dari code owner;
   - tolak push langsung dan force-push, tolak penghapusan branch `main`;
   - wajib status check lulus (build + uji unit) bila CI sudah ada;
   - persetujuan lama dibatalkan saat ada commit baru; riwayat linear (squash atau rebase merge).

## Konsekuensi

- Merge hanya oleh manusia setelah tinjauan; agent tidak merge atau menutup issue.
- Perubahan aturan ini butuh ADR baru yang menyatakan "Menggantikan ADR-0002".
