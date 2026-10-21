package com.pasaree.app.layar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pasaree.app.GrafApp
import com.pasaree.shared.domain.KriteriaKatalog
import com.pasaree.shared.domain.PagerProduk
import kotlinx.coroutines.launch

/** Daftar produk dengan paginasi cursor; memuat halaman berikutnya saat mendekati ujung. */
@Composable
private fun DaftarProduk(
    pager: PagerProduk,
    keDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    kosong: String = "Belum ada produk.",
) {
    val state by pager.state.collectAsState()
    val daftar: LazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(pager) { pager.muatAwal() }
    LaunchedEffect(daftar, state.item.size) {
        snapshotFlow { daftar.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { terakhir -> if (terakhir >= state.item.size - 3) pager.muatLagi() }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = daftar,
            modifier = Modifier.fillMaxSize().testTag("daftar_produk"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            items(state.item, key = { it.id }) { KartuProduk(it, buka = { keDetail(it.id) }) }
            if (state.memuat) item { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() } }
            state.galat?.let { g -> item { KotakGalat(g, cobaLagi = { scope.launch { pager.muatLagi() } }) } }
            if (!state.memuat && state.galat == null && state.item.isEmpty()) item { Text(kosong, modifier = Modifier.testTag("katalog_kosong")) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarBeranda(graf: GrafApp, keCari: (String) -> Unit, keDetail: (String) -> Unit, keKeranjang: () -> Unit) {
    val pager = remember(graf) { PagerProduk(graf.cariProduk, KriteriaKatalog(sort = "terbaru")) }
    var kata by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text("Pasaree") }, actions = { TextButton(onClick = keKeranjang, modifier = Modifier.testTag("tombol_keranjang")) { Text("Keranjang") } }) }) { isi ->
        Column(Modifier.padding(isi)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = kata,
                    onValueChange = { kata = it },
                    label = { Text("Cari produk") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("input_cari"),
                )
                Button(onClick = { keCari(kata.trim()) }, modifier = Modifier.testTag("tombol_cari")) { Text("Cari") }
            }
            DaftarProduk(pager, keDetail)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarPencarian(graf: GrafApp, kataKunciAwal: String, keDetail: (String) -> Unit, kembali: () -> Unit) {
    var kata by rememberSaveable { mutableStateOf(kataKunciAwal) }
    var diajukan by rememberSaveable { mutableStateOf(kataKunciAwal) }
    val pager = remember(graf, diajukan) { PagerProduk(graf.cariProduk, KriteriaKatalog(q = diajukan.ifBlank { null })) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Pencarian") }, navigationIcon = { TextButton(onClick = kembali, modifier = Modifier.testTag("tombol_kembali")) { Text("Kembali") } })
    }) { isi ->
        Column(Modifier.padding(isi)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = kata,
                    onValueChange = { kata = it },
                    label = { Text("Kata kunci") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("input_pencarian"),
                )
                Button(onClick = { diajukan = kata.trim() }, modifier = Modifier.testTag("tombol_terapkan")) { Text("Cari") }
            }
            if (diajukan.isNotBlank()) Text("Hasil untuk \"$diajukan\"", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            DaftarProduk(pager, keDetail, kosong = "Tidak ada produk yang cocok.")
        }
    }
}
