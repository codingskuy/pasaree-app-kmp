package com.pasaree.app.layar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.pasaree.shared.domain.aksiTampil
import com.pasaree.shared.domain.bisaMengulas
import com.pasaree.shared.domain.formatHitungMundur
import com.pasaree.shared.domain.pantauPesanan
import com.pasaree.shared.domain.sisaBatasBayar
import com.pasaree.shared.domain.susunTimeline
import com.pasaree.shared.model.MetodeBayar
import com.pasaree.shared.model.PesananDetail
import com.pasaree.shared.model.PesananRingkas
import com.pasaree.shared.model.StatusPesanan
import com.pasaree.shared.model.UlasanBuat
import com.pasaree.shared.util.formatIdr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

/** Hitung mundur berdetak tiap detik dari [batasIso]; null bila tidak ada batas. */
@Composable
private fun rememberHitungMundur(batasIso: String?): String? {
    var sekarang by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(batasIso) {
        while (batasIso != null) { sekarang = Clock.System.now(); delay(1_000) }
    }
    return batasIso?.let { formatHitungMundur(sisaBatasBayar(it, sekarang)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarBayar(graf: GrafApp, pesananId: String, keStatus: (String) -> Unit, keMasuk: () -> Unit, kembali: () -> Unit) {
    var metode by rememberSaveable { mutableStateOf(MetodeBayar.QRIS.kode) }
    var memproses by remember { mutableStateOf(false) }
    var galat by remember { mutableStateOf<GalatDomain?>(null) }
    var batas by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val hitungMundur = rememberHitungMundur(batas)

    LaunchedEffect(pesananId) {
        // Batas bayar 30 menit: dari detail pesanan bila server menyertakannya, jika tidak dari waktu dibuat + 30 menit (asumsi).
        graf.ambilPesanan(pesananId).onSuccess { d ->
            batas = d.batasBayarHingga ?: runCatching { (Instant.parse(d.dibuatPada) + 30.minutes).toString() }.getOrNull()
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Bayar") }, navigationIcon = { TextButton(onClick = kembali, modifier = Modifier.testTag("tombol_kembali")) { Text("Kembali") } })
    }) { isi ->
        Column(Modifier.padding(isi).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pesanan $pesananId", style = MaterialTheme.typography.headlineMedium)
            hitungMundur?.let { Text("Sisa waktu bayar $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("hitung_mundur")) }
            MetodeBayar.entries.forEach { m ->
                Row(Modifier.fillMaxWidth().clickable { metode = m.kode }.testTag("metode_${m.kode}"), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = metode == m.kode, onClick = { metode = m.kode })
                    Text(m.label)
                }
            }
            galat?.let {
                Text(pesanGalat(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("pesan_galat"))
                if (it.jenis == JenisGalat.AUTENTIKASI) OutlinedButton(onClick = keMasuk) { Text("Masuk") }
            }
            Text("Pembayaran diproses kasir Pasaree; aplikasi tidak menyimpan data kartu atau rekening.", style = MaterialTheme.typography.bodySmall)
            Button(
                enabled = !memproses,
                onClick = {
                    memproses = true
                    galat = null
                    scope.launch {
                        // Kunci idempotensi dibuat/dipakai ulang di BayarPesanan; mengetuk lagi setelah gagal aman dari tagihan ganda.
                        graf.bayar(pesananId, MetodeBayar.entries.first { it.kode == metode }).fold(
                            onSuccess = { keStatus(pesananId) },
                            onFailure = { galat = it as? GalatDomain },
                        )
                        memproses = false
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("tombol_bayar"),
            ) { Text(if (memproses) "Memproses..." else "Bayar sekarang") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarStatusPesanan(graf: GrafApp, pesananId: String, keUlasan: (String) -> Unit, kembali: () -> Unit) {
    var detail by remember { mutableStateOf<PesananDetail?>(null) }
    var galat by remember { mutableStateOf<GalatDomain?>(null) }
    var muatUlang by remember { mutableStateOf(0) }
    var pesan by remember { mutableStateOf<String?>(null) }
    var alasanRetur by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Poll maksimal tiap 15 detik; berhenti di status akhir.
    LaunchedEffect(pesananId, muatUlang) {
        pantauPesanan(graf.api, pesananId).collect { hasil ->
            hasil.fold(onSuccess = { detail = it; galat = null }, onFailure = { galat = it as? GalatDomain })
        }
    }
    val batas = detail?.takeIf { it.status == StatusPesanan.DIPESAN }?.let { d ->
        d.batasBayarHingga ?: runCatching { (Instant.parse(d.dibuatPada) + 30.minutes).toString() }.getOrNull()
    }
    val hitungMundur = rememberHitungMundur(batas)

    Scaffold(topBar = {
        TopAppBar(title = { Text("Status pesanan") }, navigationIcon = { TextButton(onClick = kembali, modifier = Modifier.testTag("tombol_kembali")) { Text("Kembali") } })
    }) { isi ->
        Column(Modifier.padding(isi).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("status_pesanan"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val d = detail
            if (d == null) {
                if (galat != null) KotakGalat(galat!!, cobaLagi = { muatUlang++ }) else CircularProgressIndicator()
                return@Column
            }
            Text("Pesanan ${d.id} · ${formatIdr(d.totalIdr)}", style = MaterialTheme.typography.headlineMedium)
            Text("Status: ${d.status}", modifier = Modifier.testTag("status_sekarang"))
            hitungMundur?.let { Text("Batas bayar: sisa $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("hitung_mundur")) }
            HorizontalDivider()
            susunTimeline(d).forEach { t ->
                Text(
                    (if (t.tercapai) "[x] " else "[ ] ") + t.status + (t.waktu?.let { " · $it" } ?: ""),
                    modifier = Modifier.testTag("timeline_${t.status}"),
                    color = if (t.tercapai) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            d.subPesanan.forEach { Text("${it.lapak.namaLapak}: ${it.status} · ${formatIdr(it.subtotalIdr)}", style = MaterialTheme.typography.bodySmall) }
            d.buktiSerahTrip?.let { Text("Bukti serah trip: $it", style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider()
            // Aksi hanya tampil bila server mengembalikannya di aksi_tersedia.
            val aksi = aksiTampil(d)
            if (aksi.konfirmasiTerima) {
                Button(
                    onClick = {
                        scope.launch {
                            graf.konfirmasiTerima(d.id).fold(onSuccess = { pesan = "Terima kasih. Status: $it"; muatUlang++ }, onFailure = { pesan = pesanGalat(it as GalatDomain) })
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("tombol_konfirmasi_terima"),
                ) { Text("Konfirmasi pesanan diterima") }
            }
            if (aksi.ajukanRetur) {
                // Retur (non-pangan segar, maks 1x24 jam) hanya bila server mengembalikan aksi `ajukan_retur`.
                Text("Retur tersedia (non-pangan segar, maks 1x24 jam setelah diterima).", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("info_retur"))
                OutlinedTextField(alasanRetur, { alasanRetur = it }, label = { Text("Alasan retur (5-280 karakter)") }, modifier = Modifier.fillMaxWidth().testTag("input_alasan_retur"))
                d.subPesanan.filter { it.subId != null && it.status == StatusPesanan.DITERIMA_PEMBELI }.forEach { sub ->
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                graf.ajukanRetur(d.id, sub.subId!!, alasanRetur).fold(onSuccess = { pesan = "Retur diajukan. Status: $it"; muatUlang++ }, onFailure = { pesan = pesanGalat(it as GalatDomain) })
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("tombol_retur_${sub.subId}"),
                    ) { Text("Ajukan retur: ${sub.lapak.namaLapak}") }
                }
            }
            pesan?.let { Text(it, modifier = Modifier.testTag("pesan_aksi")) }
            if (bisaMengulas(d)) {
                Text("Beri ulasan (maks 7 hari setelah selesai)", style = MaterialTheme.typography.headlineMedium)
                if (d.item.isEmpty()) Text("Daftar produk pesanan belum tersedia dari server.", style = MaterialTheme.typography.bodySmall)
                d.item.forEach { OutlinedButton(onClick = { keUlasan(it.produkId) }, modifier = Modifier.testTag("ulas_${it.produkId}")) { Text("Ulas ${it.judul}") } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarPesananSaya(graf: GrafApp, keDetail: (String) -> Unit, keMasuk: () -> Unit, kembali: () -> Unit) {
    var daftar by remember { mutableStateOf<List<PesananRingkas>?>(null) }
    var galat by remember { mutableStateOf<GalatDomain?>(null) }
    var ulang by remember { mutableStateOf(0) }
    LaunchedEffect(ulang) {
        graf.ambilPesanan.daftar().fold(onSuccess = { daftar = it.data; galat = null }, onFailure = { galat = it as? GalatDomain })
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Pesanan saya") }, navigationIcon = { TextButton(onClick = kembali, modifier = Modifier.testTag("tombol_kembali")) { Text("Kembali") } })
    }) { isi ->
        Box(Modifier.padding(isi).fillMaxSize()) {
            val g = galat
            val d = daftar
            when {
                g != null -> Column {
                    KotakGalat(g, cobaLagi = { ulang++ })
                    if (g.jenis == JenisGalat.AUTENTIKASI) OutlinedButton(onClick = keMasuk, modifier = Modifier.padding(16.dp)) { Text("Masuk") }
                }
                d == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                d.isEmpty() -> Text("Belum ada pesanan.", Modifier.padding(16.dp).testTag("pesanan_kosong"))
                else -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(d, key = { it.id }) { p ->
                        Card(Modifier.fillMaxWidth().testTag("pesanan_${p.id}").clickable { keDetail(p.id) }.semantics { contentDescription = "Pesanan ${p.id}, ${p.status}, ${formatIdr(p.totalIdr)}" }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(p.id, style = MaterialTheme.typography.bodyLarge)
                                Text("${p.status} · ${formatIdr(p.totalIdr)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarUlasan(graf: GrafApp, pesananId: String, produkId: String, selesai: () -> Unit, kembali: () -> Unit) {
    var bintang by rememberSaveable { mutableStateOf(5) }
    var teks by rememberSaveable { mutableStateOf("") }
    var foto by rememberSaveable { mutableStateOf("") }
    var galat by remember { mutableStateOf<GalatDomain?>(null) }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Ulasan") }, navigationIcon = { TextButton(onClick = kembali, modifier = Modifier.testTag("tombol_kembali")) { Text("Kembali") } })
    }) { isi ->
        Column(Modifier.padding(isi).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..5).forEach { n ->
                    OutlinedButton(onClick = { bintang = n }, modifier = Modifier.testTag("bintang_$n")) { Text(if (n <= bintang) "★$n" else "☆$n") }
                }
            }
            OutlinedTextField(teks, { teks = it }, label = { Text("Ulasan Anda") }, modifier = Modifier.fillMaxWidth().testTag("input_ulasan"))
            // Unggah foto belum ada di kontrak mobile: sementara tautan foto (satu URL), asumsi.
            OutlinedTextField(foto, { foto = it }, label = { Text("Tautan foto (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("input_foto"))
            galat?.let { Text(pesanGalat(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("pesan_galat")) }
            Button(
                onClick = {
                    scope.launch {
                        graf.kirimUlasan(UlasanBuat(pesananId, produkId, bintang, teks.ifBlank { null }, listOfNotNull(foto.trim().ifBlank { null })))
                            .fold(onSuccess = { selesai() }, onFailure = { galat = it as? GalatDomain })
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("tombol_kirim_ulasan"),
            ) { Text("Kirim ulasan") }
        }
    }
}
