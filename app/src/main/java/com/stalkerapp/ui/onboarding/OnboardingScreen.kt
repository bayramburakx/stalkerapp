package com.stalkerapp.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stalkerapp.StalkerApp

/**
 * İlk açılış rehberi: portal adresi, MAC ve senkron adımlarını kısaca anlatır.
 * Bittiğinde bayrak işaretlenir ve bir daha gösterilmez.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as StalkerApp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Markalı başlık
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF123D8B)))
                ),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(52.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Stalker Player'a Hoş Geldin",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            "IPTV portalını bağlayıp film, dizi ve canlı TV izlemeye 3 adımda başla.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(Modifier.height(32.dp))

        OnboardingStep(
            icon = Icons.Default.Link,
            title = "1. Portal adresini gir",
            desc = "Sağlayıcından aldığın portal adresini yaz, örn. http://ip:port/c\nKullanıcı adı/şifre varsa onları da ekleyebilirsin."
        )
        Spacer(Modifier.height(14.dp))
        OnboardingStep(
            icon = Icons.Default.Tv,
            title = "2. MAC adresi eşleştir",
            desc = "Portalda kayıtlı cihaz MAC'ini gir (00:1A:79:…). Portal MAC'i tanımıyorsa bağlantı kabul edilmez."
        )
        Spacer(Modifier.height(14.dp))
        OnboardingStep(
            icon = Icons.Default.CloudSync,
            title = "3. Katalog senkronlanır",
            desc = "Film + dizi kütüphanesi arka planda çekilir (birkaç dakika). Senkron ilerlemesini Ayarlar'dan takip edebilirsin."
        )

        Spacer(Modifier.height(36.dp))
        Button(
            onClick = {
                app.store.setOnboardingDone(true)
                onDone()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Başla", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OnboardingStep(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                icon,
                contentDescription = null,
                tint = Color(0xFF8AB4F8),
                modifier = Modifier.size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
    }
}
