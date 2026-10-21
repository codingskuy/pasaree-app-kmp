package com.pasaree.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.pasaree.app.layar.LayarBeranda
import com.pasaree.app.layar.LayarCheckout
import com.pasaree.app.layar.LayarDetailProduk
import com.pasaree.app.layar.LayarKeranjang
import com.pasaree.app.layar.LayarMasuk
import com.pasaree.app.layar.LayarBayar
import com.pasaree.app.layar.LayarPesananSaya
import com.pasaree.app.layar.LayarPencarian
import com.pasaree.app.layar.LayarStatusPesanan
import com.pasaree.app.layar.LayarUlasan
import com.pasaree.app.tema.TemaPasaree

/**
 * Akar aplikasi beli. [produkIdTautan] berasal dari deep-link (`chefgenie://pasaree/produk/{id}`)
 * dan hanya dipakai sebagai back-stack awal; setelahnya back-stack dipulihkan dari state tersimpan.
 */
@Composable
fun BeliApp(graf: GrafApp, produkIdTautan: String? = null) {
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
        // Sinkronisasi saat aplikasi dibuka: kuras antrean aksi + tarik delta katalog.
        LaunchedEffect(graf) { graf.sync.now() }
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
                            kePesananSaya = { tumpukan.add(PesananSaya) },
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
                    is Bayar -> NavEntry(rute) {
                        LayarBayar(
                            graf = graf,
                            pesananId = rute.pesananId,
                            keStatus = { id -> tumpukan.removeLastOrNull(); tumpukan.add(StatusPesanan(id)) },
                            keMasuk = { tumpukan.add(Masuk) },
                            kembali = kembali,
                        )
                    }
                    is StatusPesanan -> NavEntry(rute) {
                        LayarStatusPesanan(
                            graf = graf,
                            pesananId = rute.pesananId,
                            keUlasan = { produkId -> tumpukan.add(Ulasan(rute.pesananId, produkId)) },
                            kembali = kembali,
                        )
                    }
                    is PesananSaya -> NavEntry(rute) {
                        LayarPesananSaya(graf = graf, keDetail = { tumpukan.add(StatusPesanan(it)) }, keMasuk = { tumpukan.add(Masuk) }, kembali = kembali)
                    }
                    is Ulasan -> NavEntry(rute) { LayarUlasan(graf = graf, pesananId = rute.pesananId, produkId = rute.produkId, selesai = kembali, kembali = kembali) }
                    else -> error("Rute tidak dikenal: $rute")
                }
            },
        )
    }
}
