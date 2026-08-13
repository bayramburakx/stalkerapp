package com.stalkerapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stalkerapp.ui.MainViewModel

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cooldown by vm.cooldownSeconds.collectAsStateWithLifecycle()

    var timezoneOffset by remember(settings.timezoneOffset) { mutableFloatStateOf(settings.timezoneOffset.toFloat()) }
    var requestInterval by remember(settings.requestIntervalMs) { mutableFloatStateOf(settings.requestIntervalMs.toFloat()) }
    var buffer by remember(settings.maxBufferMs) { mutableFloatStateOf((settings.maxBufferMs / 1000).toFloat()) }
    var showEpgTimes by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineMedium)

        // Timezone
        Text("Zaman Dilimi Ofseti", style = MaterialTheme.typography.titleMedium)
        Text(
            "EPG kaymalarını düzeltmek için sağlayıcı sunucusu ile kendi saatiniz arasındaki farkı girin. (+3, -2 vb.)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = timezoneOffset,
            onValueChange = {
                timezoneOffset = it
                vm.saveSettings(settings.copy(timezoneOffset = it.toInt()))
            },
            valueRange = -12f..12f,
            steps = 23
        )
        Text("Ofset: ${timezoneOffset.toInt()} saat", style = MaterialTheme.typography.bodyLarge)

        // Rate limit
        Text("İstek Aralığı (Rate Limit)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Stalker portalları ardışık isteklere duyarlıdır. İstekler arası minimum bekleme süresi. (ms)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = requestInterval,
            onValueChange = {
                requestInterval = it
                vm.saveSettings(settings.copy(requestIntervalMs = it.toLong()))
            },
            valueRange = 0f..3000f,
            steps = 29
        )
        Text("${requestInterval.toLong()} ms", style = MaterialTheme.typography.bodyLarge)

        // Buffer
        Text("Oynatma Tamponu (Buffer)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Canlı yayın takılmalarını azaltmak için tampon süresi. (saniye)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = buffer,
            onValueChange = {
                buffer = it
                vm.saveSettings(settings.copy(maxBufferMs = it.toInt() * 1000))
            },
            valueRange = 15f..120f,
            steps = 20
        )
        Text("${buffer.toInt()} sn", style = MaterialTheme.typography.bodyLarge)

        // Cooldown
        Text("Cooldown Yönetimi", style = MaterialTheme.typography.titleMedium)
        Text(
            if (cooldown > 0) "Sunucu istekleri engellendi. Kalan süre: ${cooldown}s"
            else "Sunucu engeli yok.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (cooldown > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = { vm.clearCooldown() },
            enabled = cooldown > 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cooldown'u Temizle")
        }

        // Cache info
        Text("Önbellek (Cache)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Kategori ve kanal listeleri istek sayısını azaltmak için bellekte önbelleklenir. Portal değiştirildiğinde önbellek temizlenir.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = showEpgTimes, onCheckedChange = { showEpgTimes = it })
            Text("EPG saatlerini yerel saate göre göster", modifier = Modifier.padding(start = 8.dp))
        }
    }
}
