package com.pasaree.shared.util

/** Tujuan hasil penguraian deep-link. */
sealed interface TujuanTautan {
    data class Produk(val produkId: String) : TujuanTautan
}

private val ID_VALID = Regex("^[A-Za-z0-9_-]{1,64}$")

/**
 * Mengurai deep-link:
 * - `chefgenie://pasaree/produk/{id}`
 * - `https://pasaree.chefgenie.id/p/{id}`
 * Mengembalikan null bila bukan tautan Pasaree yang dikenal (id dibatasi karakter aman).
 */
fun uraiTautan(uri: String?): TujuanTautan? {
    if (uri.isNullOrBlank()) return null
    val bersih = uri.substringBefore('#').substringBefore('?').trimEnd('/')
    val id = when {
        bersih.startsWith("chefgenie://pasaree/produk/") -> bersih.removePrefix("chefgenie://pasaree/produk/")
        bersih.startsWith("https://pasaree.chefgenie.id/p/") -> bersih.removePrefix("https://pasaree.chefgenie.id/p/")
        else -> return null
    }
    return if (ID_VALID.matches(id)) TujuanTautan.Produk(id) else null
}
