# ADR-0004 — Pin SQLDelight 2.1.0

Status: Diusulkan | Tanggal: 2026-10-21

## Konteks

`AGENTS.md` dan ADR-0001 mengunci SQLDelight `2.0.3`, tetapi versi itu tidak pernah terbit di Maven Central (seri 2.0 berhenti di 2.0.2, lalu 2.1.0). Plugin Gradle `app.cash.sqldelight:2.0.3` gagal resolusi saat PSR-KMP-005 dikerjakan.

## Keputusan

SQLDelight dipin ke `2.1.0` (masih seri 2.x sesuai acceptance criteria PSR-KMP-005; dibuktikan kompatibel dengan Kotlin 2.2.20 lewat uji `:shared:testDebugUnitTest`). Skema ada di `shared/src/commonMain/sqldelight/com/pasaree/shared/db/Pasaree.sq` (keranjang, cache katalog, antrean aksi, kunci idempotensi, meta sinkron). Driver: `android-driver` (Android), `native-driver` (iOS); `sqlite-driver` JDBC hanya pada source set uji `androidUnitTest` (bukan target desktop).

## Konsekuensi

- Bagian lain ADR-0001 tidak berubah (bayar tidak pernah antre offline; tulis lokal dulu; server menang untuk stok/harga; qty gabung max(lokal, server)).
- AGENTS.md tidak diedit (ADR immutable); manajer perlu menyelaraskan teks pin.
- Melengkapi ADR-0003 (pin build lainnya).
