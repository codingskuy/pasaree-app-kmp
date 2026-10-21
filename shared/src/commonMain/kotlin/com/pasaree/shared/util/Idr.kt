package com.pasaree.shared.util

/**
 * Satu-satunya pemformat harga di aplikasi (AGENTS.md: dilarang format manual di luar fungsi ini).
 * Contoh: 56000 -> "Rp56.000". Uang selalu integer IDR tanpa sen.
 */
fun formatIdr(nilai: Long): String {
    val negatif = nilai < 0
    val digit = (if (negatif) -nilai else nilai).toString()
    val sb = StringBuilder()
    digit.forEachIndexed { i, c ->
        if (i > 0 && (digit.length - i) % 3 == 0) sb.append('.')
        sb.append(c)
    }
    return (if (negatif) "-Rp" else "Rp") + sb
}

fun formatIdr(nilai: Int): String = formatIdr(nilai.toLong())
