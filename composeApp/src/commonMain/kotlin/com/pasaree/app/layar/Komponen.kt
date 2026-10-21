package com.pasaree.app.layar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pasaree.shared.domain.GalatDomain
import com.pasaree.shared.domain.JenisGalat
import com.pasaree.shared.model.ProdukRingkas
import com.pasaree.shared.util.formatIdr

fun pesanGalat(g: GalatDomain): String = when (g.jenis) {
    JenisGalat.JARINGAN -> "Tidak dapat terhubung. Periksa koneksi lalu coba lagi."
    JenisGalat.TIDAK_DITEMUKAN -> "Produk tidak ditemukan atau sudah tidak tayang."
    JenisGalat.AUTENTIKASI -> "Sesi berakhir. Silakan masuk lagi."
    else -> g.message ?: "Terjadi kesalahan."
}

@Composable
fun KotakGalat(galat: GalatDomain, cobaLagi: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(pesanGalat(galat), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("pesan_galat"))
        Button(onClick = cobaLagi, modifier = Modifier.testTag("tombol_coba_lagi")) { Text("Coba lagi") }
    }
}

/** Kartu produk; harga memakai [formatIdr] (satu-satunya pemformat) dan apa adanya dari server. */
@Composable
fun KartuProduk(produk: ProdukRingkas, buka: () -> Unit, modifier: Modifier = Modifier) {
    val harga = formatIdr(produk.hargaMulaiIdr)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("kartu_produk_${produk.id}")
            .semantics { contentDescription = "${produk.judul}, mulai $harga, lapak ${produk.lapak.namaLapak}, rating ${produk.ratingRata}" }
            .clickable(onClick = buka),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(produk.judul, style = MaterialTheme.typography.headlineMedium)
            Text("Mulai $harga", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(produk.lapak.namaLapak, style = MaterialTheme.typography.bodySmall)
                Text("Rating ${produk.ratingRata}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
