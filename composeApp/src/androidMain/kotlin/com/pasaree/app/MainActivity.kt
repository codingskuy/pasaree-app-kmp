package com.pasaree.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pasaree.shared.storage.buatDriverAndroid
import com.pasaree.shared.util.TujuanTautan
import com.pasaree.shared.util.uraiTautan

/** Satu GrafApp per proses (bertahan saat Activity dibuat ulang karena rotasi). */
object GrafProses {
    @Volatile private var graf: GrafApp? = null

    fun ambil(context: Context): GrafApp =
        graf ?: synchronized(this) { graf ?: GrafApp.buat(buatDriverAndroid(context)).also { graf = it } }
}

class MainActivity : ComponentActivity() {
    private var produkIdTautan by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Deep-link hanya dipakai pada peluncuran pertama; setelah rotasi/process-death
        // back-stack dipulihkan dari state tersimpan, bukan dari intent.
        if (savedInstanceState == null) produkIdTautan = idDariIntent(intent)
        val graf = GrafProses.ambil(applicationContext)
        setContent { BeliApp(graf = graf, produkIdTautan = produkIdTautan) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        idDariIntent(intent)?.let { produkIdTautan = it }
    }

    private fun idDariIntent(intent: Intent?): String? =
        (uraiTautan(intent?.data?.toString()) as? TujuanTautan.Produk)?.produkId
}
