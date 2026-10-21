package com.pasaree.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.pasaree.app.layar.LayarBeranda
import com.pasaree.app.layar.LayarCheckout
import com.pasaree.app.layar.LayarDetailProduk
import com.pasaree.app.layar.LayarKeranjang
import com.pasaree.app.layar.LayarMasuk
import com.pasaree.app.layar.LayarPencarian
import com.pasaree.app.tema.TemaPasaree

/**
 * Akar aplikasi beli. [produkIdTautan] berasal dari deep-link (`chefgenie://pasaree/produk/{id}`)
 * dan hanya dipakai sebagai back-stack awal; setelahnya back-stack dipulihkan dari state tersimpan.
 */
@Composable
fun BeliApp(graf: GrafApp = GrafApp.bawaan, produkIdTautan: String? = null) {
    TemaPasaree {
        val tumpukan = rememberNavBackStack(KonfigurasiBackStack, *backStackAwal(produkIdTautan).toTypedArray())
        var terakhirDiproses by remember { mutableStateOf(produkIdTautan) }
        LaunchedEffect(produkIdTautan) {
            // Tautan yang masuk saat aplikasi sudah berjalan (onNewIntent); tautan awal sudah ada di back-stack.
            if (produkIdTautan != null && produkIdTautan != terakhirDiproses) {
                tumpukan.add(DetailProduk(produkIdTautan))
                terakhirDiproses = produkIdTautan
            }
        }
        val kembali: () -> Unit = { tumpukan.removeLastOrNull() }
        NavDisplay(
            backStack = tumpukan,
            onBack = kembali,
            entryProvider = { rute: NavKey ->
                when (rute) {
                    is Beranda -> NavEntry(rute) {
                        LayarBeranda(
                            graf = graf,
                            keCari = { tumpukan.add(Pencarian(it)) },
                            keDetail = { tumpukan.add(DetailProduk(it)) },
                            keKeranjang = { tumpukan.add(Keranjang) },
                        )
                    }
                    is Pencarian -> NavEntry(rute) {
                        LayarPencarian(graf = graf, kataKunciAwal = rute.kataKunci, keDetail = { tumpukan.add(DetailProduk(it)) }, kembali = kembali)
                    }
                    is DetailProduk -> NavEntry(rute) {
                        LayarDetailProduk(graf = graf, produkId = rute.produkId, keKeranjang = { tumpukan.add(Keranjang) }, kembali = kembali)
                    }
                    is Keranjang -> NavEntry(rute) {
                        LayarKeranjang(
                            graf = graf,
                            keCheckout = { trip, titik -> tumpukan.add(Checkout(trip, titik)) },
                            keMasuk = { tumpukan.add(Masuk) },
                            kembali = kembali,
                        )
                    }
                    is Checkout -> NavEntry(rute) {
                        LayarCheckout(
                            graf = graf,
                            tripId = rute.tripId,
                            titikId = rute.titikId,
                            keBayar = { id -> tumpukan.add(Bayar(id)) },
                            kembali = kembali,
                        )
                    }
                    is Masuk -> NavEntry(rute) { LayarMasuk(graf = graf, selesai = kembali, kembali = kembali) }
                    // Ditempati sementara; layar bayar dikerjakan di PSR-KMP-004.
                    is Bayar -> NavEntry(rute) { Text("Bayar pesanan ${rute.pesananId} (PSR-KMP-004)", Modifier.padding(16.dp)) }
                    else -> error("Rute tidak dikenal: $rute")
                }
            },
        )
    }
}
