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
import com.pasaree.app.layar.LayarDetailProduk
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
        NavDisplay(
            backStack = tumpukan,
            onBack = { tumpukan.removeLastOrNull() },
            entryProvider = { rute: NavKey ->
                when (rute) {
                    is Beranda -> NavEntry(rute) {
                        LayarBeranda(
                            graf = graf,
                            keCari = { tumpukan.add(Pencarian(it)) },
                            keDetail = { tumpukan.add(DetailProduk(it)) },
                        )
                    }
                    is Pencarian -> NavEntry(rute) {
                        LayarPencarian(
                            graf = graf,
                            kataKunciAwal = rute.kataKunci,
                            keDetail = { tumpukan.add(DetailProduk(it)) },
                            kembali = { tumpukan.removeLastOrNull() },
                        )
                    }
                    is DetailProduk -> NavEntry(rute) {
                        LayarDetailProduk(
                            graf = graf,
                            produkId = rute.produkId,
                            kembali = { tumpukan.removeLastOrNull() },
                        )
                    }
                    else -> error("Rute tidak dikenal: $rute")
                }
            },
        )
    }
}
