package com.pasaree.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class RuteTest {
    private val json = Json { serializersModule = KonfigurasiBackStack.serializersModule }

    @Test
    fun backStackAwal_tanpaTautan_berandaSaja() {
        assertEquals(listOf<NavKey>(Beranda), backStackAwal(null))
    }

    @Test
    fun backStackAwal_denganTautan_berandaLaluDetail() {
        assertEquals(listOf(Beranda, DetailProduk("P1")), backStackAwal("P1"))
    }

    @Test
    fun rute_roundTripSerialisasi_untukPemulihanBackStack() {
        val asli: List<NavKey> = listOf(Beranda, Pencarian("sambal"), DetailProduk("01J0ABC"))
        val teks = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.PolymorphicSerializer(NavKey::class)), asli)
        val pulih = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.PolymorphicSerializer(NavKey::class)), teks)
        assertEquals(asli, pulih)
    }
}
