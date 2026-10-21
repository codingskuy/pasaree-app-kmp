# ADR-0003 — Pin build: AGP, Compose Multiplatform, dan Navigation3 KMP

Status: Diusulkan | Tanggal: 2026-10-21

(Bernomor 0003 karena PR #10 memakai ADR-0002 untuk tata kelola cabang.)

## Konteks

Pin di `AGENTS.md` / ADR-0001 tidak dapat dibangun saat PSR-KMP-002 dikerjakan:

1. AGP `8.6.3` tidak ada di Google Maven (versi 8.6.x yang terbit: 8.6.0 dan 8.6.1).
2. `androidx.navigation3:navigation3-ui:1.0.0` hanya terbit untuk Android; dipakai dari `commonMain` gagal resolusi untuk iOS (`iosArm64`, `iosSimulatorArm64`). Port multiplatform dirilis JetBrains sebagai `org.jetbrains.androidx.navigation3:navigation3-ui`.
3. Dependensi transitif Navigation3 (activity 1.12, navigationevent) mensyaratkan AGP 8.9.1+ dan `compileSdk` 36.
4. Compose Multiplatform 1.8.2 tidak memuat Navigation3 KMP yang cocok; seri 1.9.x memuatnya.

## Keputusan

| Aspek | Pin lama | Pin baru |
|---|---|---|
| AGP | 8.6.3 (tidak ada) | 8.9.3 |
| Gradle wrapper | (belum ada) | 8.13 |
| compileSdk | (belum ada) | 36 (minSdk 28, targetSdk 35 tetap) |
| Compose Multiplatform | 1.8.2 | 1.9.3 (material3 1.9.0, versi terpisah) |
| Navigation3 | `androidx.navigation3` 1.0.0 | `org.jetbrains.androidx.navigation3` 1.0.0-alpha06 |

Kotlin 2.2.20, Ktor 3.1.3, SQLDelight 2.0.3 tidak berubah. Struktur modul: `:shared` (model, network, domain, storage, sync, util) dan `:composeApp` (UI + Navigation3), sesuai `TownHall produk/30-modul-KMP-bersama.md`.

## Konsekuensi

- Navigation3 KMP masih alpha: API dapat berubah; naikkan lewat ADR baru dan evaluasi saat rilis stabil (JetBrains sudah menerbitkan 1.1.x dan 1.2.0-rc01).
- Uji unit dijalankan di host JVM lewat target Android (`:shared:testDebugUnitTest`); tidak ada target desktop/JVM.
- AGENTS.md dan ADR-0001 tidak diedit (ADR immutable); manajer perlu menyelaraskan teks pin di AGENTS.md.
