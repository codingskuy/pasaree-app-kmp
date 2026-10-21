package com.pasaree.app

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Rute type-safe Navigation3 sesuai graf `produk/30-modul-KMP-bersama.md` §4.
 * Rute lanjutan (Keranjang, Checkout, Bayar, ...) ditambahkan oleh tiket berikutnya.
 */
@Serializable data object Beranda : NavKey
@Serializable data class Pencarian(val kataKunci: String = "") : NavKey
@Serializable data class DetailProduk(val produkId: String) : NavKey

/**
 * Konfigurasi serializer back-stack: dibutuhkan agar back-stack dapat disimpan/dipulihkan
 * (rotasi dan process-death) di semua platform.
 */
val KonfigurasiBackStack = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Beranda::class)
            subclass(Pencarian::class)
            subclass(DetailProduk::class)
        }
    }
}

/** Back-stack awal: deep-link produk membuka Beranda lalu DetailProduk agar tombol kembali bermakna. */
fun backStackAwal(tautanProduk: String?): List<NavKey> =
    if (tautanProduk == null) listOf(Beranda) else listOf(Beranda, DetailProduk(tautanProduk))
