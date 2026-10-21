package com.pasaree.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pasaree.shared.util.TujuanTautan
import com.pasaree.shared.util.uraiTautan

class MainActivity : ComponentActivity() {
    private var produkIdTautan by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Deep-link hanya dipakai pada peluncuran pertama; setelah rotasi/process-death
        // back-stack dipulihkan dari state tersimpan, bukan dari intent.
        if (savedInstanceState == null) produkIdTautan = idDariIntent(intent)
        setContent { BeliApp(produkIdTautan = produkIdTautan) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        idDariIntent(intent)?.let { produkIdTautan = it }
    }

    private fun idDariIntent(intent: Intent?): String? =
        (uraiTautan(intent?.data?.toString()) as? TujuanTautan.Produk)?.produkId
}
