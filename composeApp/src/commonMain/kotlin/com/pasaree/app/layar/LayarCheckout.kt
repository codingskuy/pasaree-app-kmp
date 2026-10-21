package com.pasaree.app.layar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pasaree.app.GrafApp
import com.pasaree.shared.domain.CheckoutController
import com.pasaree.shared.domain.StateCheckout
import com.pasaree.shared.model.KodeGalat
import com.pasaree.shared.model.PratinjauCheckout
import com.pasaree.shared.util.formatIdr
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarCheckout(graf: GrafApp, tripId: String, titikId: String, keBayar: (String) -> Unit, kembali: () -> Unit) {
    val ctrl = remember(graf, tripId, titikId) { CheckoutController(graf.api, tripId, titikId, praSinkron = { graf.sync.pastikanKeranjangTersinkron() }) }
    val state by ctrl.state.collectAsState()
    val scope = rememberCoroutineScope()

    // Pratinjau selalu dipanggil saat layar dibuka, sebelum pesanan boleh dibuat.
    LaunchedEffect(ctrl) { ctrl.muatPratinjau() }
    LaunchedEffect(state) {
        (state as? StateCheckout.Dibuat)?.let { keBayar(it.pesanan.pesananId) }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Checkout") }, navigationIcon = { TextButton(onClick = kembali, modifier = Modifier.testTag("tombol_kembali")) { Text("Kembali") } })
    }) { isi ->
        Column(Modifier.padding(isi).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val s = state) {
                StateCheckout.Belum, StateCheckout.Memuat, is StateCheckout.Dibuat -> CircularProgressIndicator()
                is StateCheckout.Gagal -> KotakGalat(s.galat, cobaLagi = { scope.launch { ctrl.muatPratinjau() } })
                is StateCheckout.Siap -> {
                    s.alasanUlang?.let { Banner(it, s.pesanUlang) }
                    RincianPratinjau(s.pratinjau)
                    Button(
                        onClick = { scope.launch { ctrl.buatPesanan() } },
                        modifier = Modifier.fillMaxWidth().testTag("tombol_buat_pesanan"),
                    ) { Text("Buat pesanan · ${formatIdr(s.pratinjau.totalIdr)}") }
                }
            }
        }
    }
}

@Composable
private fun Banner(kode: String, pesan: String?) {
    val judul = when (kode) {
        KodeGalat.STOK_HABIS -> "Stok berubah"
        KodeGalat.HARGA_BERUBAH -> "Harga berubah"
        KodeGalat.TRIP_PENUH -> "Trip penuh"
        else -> "Pratinjau diperbarui"
    }
    Column(Modifier.fillMaxWidth().testTag("banner_pratinjau_ulang")) {
        Text("$judul. Periksa angka terbaru di bawah sebelum melanjutkan.", color = MaterialTheme.colorScheme.error)
        if (!pesan.isNullOrBlank()) Text(pesan, style = MaterialTheme.typography.bodySmall)
    }
}

/** Semua angka diambil langsung dari server; tidak ada penjumlahan di klien. */
@Composable
private fun RincianPratinjau(p: PratinjauCheckout) {
    Column(Modifier.testTag("rincian_pratinjau"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Baris("Subtotal", p.subtotalIdr, "subtotal")
        Baris("Ongkir (diteruskan ke kas trip Lumbung)", p.ongkirIdr, "ongkir")
        Baris("Biaya layanan", p.biayaLayananIdr, "biaya_layanan")
        HorizontalDivider()
        Baris("Total bayar", p.totalIdr, "total", tebal = true)
        Text("Komisi lapak dipotong dari pendapatan lapak, bukan dari total bayar Anda.", style = MaterialTheme.typography.bodySmall)
        p.peringatan.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun Baris(label: String, nilai: Int, tag: String, tebal: Boolean = false) {
    Row(Modifier.fillMaxWidth().testTag("baris_$tag"), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = if (tebal) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(formatIdr(nilai), style = if (tebal) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge)
    }
}
