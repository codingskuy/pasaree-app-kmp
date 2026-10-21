package com.pasaree.app.layar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pasaree.app.GrafApp
import com.pasaree.shared.domain.GalatDomain
import com.pasaree.shared.domain.JenisGalat
import com.pasaree.shared.model.ProdukTayang
import com.pasaree.shared.model.TripLumbung
import com.pasaree.shared.util.formatIdr
import kotlinx.coroutines.launch

private sealed interface StateDetail {
    data object Memuat : StateDetail
    data class Siap(val produk: ProdukTayang) : StateDetail
    data class Gagal(val galat: GalatDomain) : StateDetail
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarDetailProduk(graf: GrafApp, produkId: String, keKeranjang: () -> Unit, kembali: () -> Unit) {
    var state by remember(produkId) { mutableStateOf<StateDetail>(StateDetail.Memuat) }
    var percobaan by remember(produkId) { mutableIntStateOf(0) }
    LaunchedEffect(produkId, percobaan) {
        state = StateDetail.Memuat
        state = graf.ambilDetailProduk(produkId).fold(
            onSuccess = { StateDetail.Siap(it) },
            onFailure = { StateDetail.Gagal(it as GalatDomain) },
        )
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Detail produk") },
            navigationIcon = { TextButton(onClick = kembali, modifier = Modifier.testTag("tombol_kembali")) { Text("Kembali") } },
        )
    }) { isi ->
        Box(Modifier.padding(isi).fillMaxSize()) {
            when (val s = state) {
                StateDetail.Memuat -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is StateDetail.Gagal -> KotakGalat(s.galat, cobaLagi = { percobaan++ })
                is StateDetail.Siap -> IsiDetail(s.produk, graf, keKeranjang)
            }
        }
    }
}

@Composable
private fun IsiDetail(p: ProdukTayang, graf: GrafApp, keKeranjang: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pesan by remember(p.id) { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("detail_produk"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(p.judul, style = MaterialTheme.typography.headlineLarge)
        Text("${p.lapak.namaLapak} · Rating ${p.ratingRata} (${p.totalUlasan} ulasan)", style = MaterialTheme.typography.bodySmall)
        if (p.tag.isNotEmpty()) Text(p.tag.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        Text(p.deskripsi, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider()
        Text("Varian dan stok", style = MaterialTheme.typography.headlineMedium)
        p.varian.forEach { v ->
            val stok = p.stokVarian(v.id)
            val habis = !p.bisaMasukKeranjang(v.id)
            Row(
                Modifier.fillMaxWidth().testTag("varian_${v.id}").semantics {
                    contentDescription = "${v.namaVarian}, ${formatIdr(v.hargaIdr)}, " + if (habis) "stok habis" else "stok $stok"
                },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(v.namaVarian, style = MaterialTheme.typography.bodyLarge)
                    Text(formatIdr(v.hargaIdr), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Text(if (habis) "Stok habis" else "Stok: $stok", style = MaterialTheme.typography.bodySmall,
                        color = if (habis) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
                // Varian stok 0 tidak dapat masuk keranjang (tombol nonaktif + ditolak di domain).
                Button(
                    onClick = {
                        scope.launch {
                            graf.keranjang.tambah(p, v.id).fold(
                                onSuccess = { pesan = "Ditambahkan ke keranjang." },
                                onFailure = { e -> pesan = if ((e as? GalatDomain)?.jenis == JenisGalat.AUTENTIKASI) "Masuk dulu untuk menyimpan keranjang di akun (item tersimpan di perangkat)." else pesanGalat(e as GalatDomain) },
                            )
                        }
                    },
                    enabled = !habis,
                    modifier = Modifier.testTag("tambah_${v.id}"),
                ) { Text(if (habis) "Habis" else "Tambah") }
            }
        }
        pesan?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("pesan_keranjang")) }
        TextButton(onClick = keKeranjang, modifier = Modifier.testTag("tombol_lihat_keranjang")) { Text("Lihat keranjang") }
        HorizontalDivider()
        Text("Trip Lumbung tersedia", style = MaterialTheme.typography.headlineMedium)
        if (p.tripTersedia.isEmpty()) {
            Text("Belum ada trip tersedia. Checkout memerlukan trip yang valid.", modifier = Modifier.testTag("trip_kosong"))
        } else {
            if (p.basi) Text("Jadwal trip dari cache terakhir (Lumbung belum merespons).", style = MaterialTheme.typography.bodySmall)
            p.tripTersedia.forEach { BarisTrip(it) }
        }
    }
}

@Composable
private fun BarisTrip(t: TripLumbung) {
    Column(Modifier.fillMaxWidth().testTag("trip_${t.tripId}")) {
        Text("${t.rute ?: t.tripId} · berangkat ${t.jadwalBerangkat}", style = MaterialTheme.typography.bodyLarge)
        Text("Ongkir ${formatIdr(t.ongkirIdr)}" + (t.titikSerah?.let { " · titik serah $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
    }
}
