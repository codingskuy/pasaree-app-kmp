package com.pasaree.app.layar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pasaree.app.GrafApp
import com.pasaree.shared.domain.GalatDomain
import com.pasaree.shared.domain.ItemKeranjang
import com.pasaree.shared.domain.JenisGalat
import com.pasaree.shared.domain.infoKomisiLapak
import com.pasaree.shared.domain.perLapak
import com.pasaree.shared.model.TripLumbung
import com.pasaree.shared.util.formatIdr
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarKeranjang(
    graf: GrafApp,
    keCheckout: (tripId: String, titikId: String) -> Unit,
    keMasuk: () -> Unit,
    kembali: () -> Unit,
) {
    val item by graf.keranjang.item.collectAsState()
    val ringkasan by graf.keranjang.ringkasan.collectAsState()
    val scope = rememberCoroutineScope()
    var galat by remember { mutableStateOf<GalatDomain?>(null) }
    var trip by remember { mutableStateOf<List<TripLumbung>>(emptyList()) }
    var tripTerpilih by remember { mutableStateOf<TripLumbung?>(null) }
    var tripGalat by remember { mutableStateOf<GalatDomain?>(null) }

    LaunchedEffect(item.isEmpty()) {
        if (item.isNotEmpty()) {
            graf.ambilTrip().fold(
                onSuccess = { trip = it.data; tripGalat = null; if (tripTerpilih == null) tripTerpilih = it.data.firstOrNull() },
                onFailure = { tripGalat = it as? GalatDomain },
            )
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Keranjang") }, navigationIcon = { TextButton(onClick = kembali, modifier = Modifier.testTag("tombol_kembali")) { Text("Kembali") } })
    }) { isi ->
        if (item.isEmpty()) {
            Text("Keranjang masih kosong.", Modifier.padding(isi).padding(16.dp).testTag("keranjang_kosong"))
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(isi).fillMaxSize().testTag("daftar_keranjang"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Satu kartu per lapak = satu sub-pesanan.
            items(item.perLapak(), key = { it.lapakSlug }) { k ->
                Card(Modifier.fillMaxWidth().testTag("lapak_${k.lapakSlug}")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(k.lapakNama, style = MaterialTheme.typography.headlineMedium)
                        k.item.forEach { BarisItem(it, ubah = { qty -> scope.launch { graf.keranjang.ubahQty(it.varianId, qty).onFailure { e -> galat = e as? GalatDomain } } }) }
                        // Informasi komisi untuk lapak: tidak menambah total bayar pembeli.
                        val subtotalLapak = k.item.sumOf { it.hargaIdr * it.qty }
                        Text(infoKomisiLapak(subtotalLapak).teks, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("info_komisi_${k.lapakSlug}"))
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ringkasan.subtotalIdr?.let { Text("Subtotal (dari server): ${formatIdr(it)}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("subtotal_server")) }
                    ringkasan.peringatanStok.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    galat?.let {
                        Text(pesanGalat(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("pesan_galat"))
                        if (it.jenis == JenisGalat.AUTENTIKASI) OutlinedButton(onClick = keMasuk, modifier = Modifier.testTag("tombol_masuk")) { Text("Masuk") }
                    }
                    Text("Pilih trip Lumbung", style = MaterialTheme.typography.headlineMedium)
                    if (trip.isEmpty()) {
                        Text(tripGalat?.let { pesanGalat(it) } ?: "Belum ada trip tersedia. Checkout diblokir tanpa trip yang valid.", modifier = Modifier.testTag("trip_kosong"))
                        if (tripGalat?.jenis == JenisGalat.AUTENTIKASI) OutlinedButton(onClick = keMasuk) { Text("Masuk") }
                    }
                    trip.forEach { t ->
                        val dipilih = t.tripId == tripTerpilih?.tripId
                        OutlinedButton(onClick = { tripTerpilih = t }, modifier = Modifier.fillMaxWidth().testTag("pilih_trip_${t.tripId}")) {
                            Text((if (dipilih) "[Dipilih] " else "") + "${t.rute ?: t.tripId} · ongkir ${formatIdr(t.ongkirIdr)}")
                        }
                    }
                    Button(
                        onClick = { tripTerpilih?.let { keCheckout(it.tripId, it.titikSerah ?: "") } },
                        enabled = tripTerpilih != null,
                        modifier = Modifier.fillMaxWidth().testTag("tombol_checkout"),
                    ) { Text("Lanjut ke checkout") }
                }
            }
        }
    }
}

@Composable
private fun BarisItem(i: ItemKeranjang, ubah: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag("item_${i.varianId}").semantics { contentDescription = "${i.judul}, ${i.namaVarian}, ${formatIdr(i.hargaIdr)}, jumlah ${i.qty}" },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(i.judul, style = MaterialTheme.typography.bodyLarge)
            Text("${i.namaVarian} · ${formatIdr(i.hargaIdr)}", style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { ubah(i.qty - 1) }, modifier = Modifier.testTag("kurang_${i.varianId}")) { Text("-") }
            Text("${i.qty}", Modifier.padding(horizontal = 4.dp).testTag("qty_${i.varianId}"))
            OutlinedButton(onClick = { ubah(i.qty + 1) }, enabled = i.qty < i.stokTersedia, modifier = Modifier.testTag("tambah_${i.varianId}")) { Text("+") }
        }
    }
}
