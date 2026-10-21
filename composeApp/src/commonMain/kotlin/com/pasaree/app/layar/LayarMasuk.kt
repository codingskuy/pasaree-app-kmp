package com.pasaree.app.layar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pasaree.app.GrafApp
import com.pasaree.shared.domain.GalatDomain
import kotlinx.coroutines.launch

/** Layar masuk minimal; token hanya di memori (lihat `SesiAuth`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarMasuk(graf: GrafApp, selesai: () -> Unit, kembali: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var sandi by remember { mutableStateOf("") }
    var galat by remember { mutableStateOf<GalatDomain?>(null) }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Masuk") }, navigationIcon = { TextButton(onClick = kembali, modifier = Modifier.testTag("tombol_kembali")) { Text("Kembali") } })
    }) { isi ->
        Column(Modifier.padding(isi).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth().testTag("input_email"))
            OutlinedTextField(sandi, { sandi = it }, label = { Text("Kata sandi") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().testTag("input_sandi"))
            galat?.let { Text(pesanGalat(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("pesan_galat")) }
            Button(
                onClick = { scope.launch { graf.sesi.login(email, sandi).fold(onSuccess = { selesai() }, onFailure = { galat = it as? GalatDomain }) } },
                modifier = Modifier.fillMaxWidth().testTag("tombol_login"),
            ) { Text("Masuk") }
        }
    }
}
