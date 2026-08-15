package com.stalkerapp.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.UserProfile

private val AVATARS = listOf(
    "😀", "😎", "🦊", "🐼", "🐸", "🐙", "🦁", "🐯",
    "🚀", "🎬", "🍿", "🎮", "⚽", "🎵", "👾", "🤖"
)

/**
 * İlk açılış: kullanıcı yalnızca kendi profilini oluşturur (ad + avatar) —
 * portal/kaynak bilgisi İSTENMEZ, kaynaklar sonra Ayarlar → Playlist & Kaynaklar
 * bölümünden eklenir (Stremio benzeri akış).
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as StalkerApp
    var name by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf(AVATARS.first()) }

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
                .background(Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF123D8B)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(52.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Hoş Geldin",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            "İzleme deneyimini kişiselleştirmek için bir profil oluştur.\n" +
                "Portal, M3U ve Xtream kaynaklarını daha sonra Ayarlar'dan ekleyebilirsin.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(Modifier.height(32.dp))

        // Profil adı
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(24) },
            label = { Text("Adın") },
            placeholder = { Text("Örn. Burak") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "Avatarını seç",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        // Avatar ızgarası
        val rows = AVATARS.chunked(4)
        rows.forEach { rowAvatars ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowAvatars.forEach { emoji ->
                    val selected = emoji == avatar
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                else Color.White.copy(alpha = 0.06f)
                            )
                            .clickable { avatar = emoji }
                            .then(if (selected) Modifier.padding(4.dp) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = MaterialTheme.typography.headlineMedium.fontSize)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(36.dp))
        Button(
            onClick = {
                val finalName = name.trim().ifBlank { "İzleyici" }
                app.store.saveUserProfile(UserProfile(name = finalName, avatar = avatar))
                app.store.setOnboardingDone(true)
                onDone()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Devam Et", fontWeight = FontWeight.Bold)
        }
        Text(
            "Profilini daha sonra Ayarlar → Hesap bölümünden değiştirebilirsin.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}
