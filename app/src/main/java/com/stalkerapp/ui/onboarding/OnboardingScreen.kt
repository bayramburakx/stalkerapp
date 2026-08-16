package com.stalkerapp.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.M3uSource
import com.stalkerapp.data.Portal
import com.stalkerapp.data.StalkerClient
import com.stalkerapp.data.XtreamClient
import com.stalkerapp.data.XtreamSource
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.rememberMainViewModel
import kotlinx.coroutines.launch

private val AVATARS = listOf(
    "😀", "😎", "🦊", "🐼", "🐸", "🐙", "🦁", "🐯",
    "🚀", "🎬", "🍿", "🎮", "⚽", "🎵", "👾", "🤖"
)

private data class Feature(val icon: ImageVector, val title: String, val desc: String)

private val FEATURES = listOf(
    Feature(Icons.Default.LiveTv, "Canlı TV", "Stalker, Xtream ve M3U kanalları tek listede"),
    Feature(Icons.Default.Movie, "Film & Dizi", "Kaldığın yerden devam et, binge moduyla izle"),
    Feature(Icons.Default.Schedule, "EPG Rehberi", "Program akışını gör, istediğin yayına atla"),
    Feature(Icons.Default.Cast, "TV'ye Yayınla", "Chromecast ile içeriği büyük ekrana aktar")
)

/**
 * Adım adım kurulum sihirbazı:
 *  1. Hoş geldin + özellik tanıtımı
 *  2. Profil (ad + avatar)
 *  3. İlk kaynak (Stalker / Xtream / M3U) — atlanabilir
 *
 * Her adımdan "Atla" ile çıkılabilir; kaynaklar her zaman sonradan
 * Ayarlar → Playlist & Kaynaklar'dan eklenebilir.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    var name by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf(AVATARS.first()) }

    // Kaynak adımı durumu
    var sourceChoice by remember { mutableStateOf<String?>(null) } // "portal" | "xtream" | "m3u"
    var pName by remember { mutableStateOf("") }
    var pUrl by remember { mutableStateOf("") }
    var pMac by remember { mutableStateOf("") }
    var pUser by remember { mutableStateOf("") }
    var pPass by remember { mutableStateOf("") }
    var xName by remember { mutableStateOf("") }
    var xServer by remember { mutableStateOf("") }
    var xUser by remember { mutableStateOf("") }
    var xPass by remember { mutableStateOf("") }
    var mName by remember { mutableStateOf("") }
    var mUrl by remember { mutableStateOf("") }
    var mContent by remember { mutableStateOf("") }
    var sourceBusy by remember { mutableStateOf(false) }
    var sourceError by remember { mutableStateOf<String?>(null) }

    fun finish() {
        // Profil adımındaki bilgilerle aktif profili kaydet (ilk kurulumda "default" oluşur).
        val current = vm.userProfile.value
        vm.saveUserProfile(
            com.stalkerapp.data.UserProfile(
                id = current.id,
                name = name.trim().ifBlank { "İzleyici" },
                avatar = avatar
            )
        )
        app.store.setOnboardingDone(true)
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        // Üst çubuk: geri + atla
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pagerState.currentPage > 0) {
                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { finish() }) {
                Text("Atla", color = Color.White.copy(alpha = 0.8f))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> WelcomePage { scope.launch { pagerState.animateScrollToPage(1) } }
                1 -> ProfilePage(
                    name = name,
                    onName = { name = it },
                    avatar = avatar,
                    onAvatar = { avatar = it },
                    onNext = { scope.launch { pagerState.animateScrollToPage(2) } }
                )
                2 -> SourcePage(
                    vm = vm,
                    sourceChoice = sourceChoice,
                    onChoice = { sourceChoice = it },
                    pName = pName, pUrl = pUrl, pMac = pMac, pUser = pUser, pPass = pPass,
                    onPName = { pName = it }, onPUrl = { pUrl = it }, onPMac = { pMac = it },
                    onPUser = { pUser = it }, onPPass = { pPass = it },
                    xName = xName, xServer = xServer, xUser = xUser, xPass = xPass,
                    onXName = { xName = it }, onXServer = { xServer = it },
                    onXUser = { xUser = it }, onXPass = { xPass = it },
                    mName = mName, mUrl = mUrl, mContent = mContent,
                    onMName = { mName = it }, onMUrl = { mUrl = it }, onMContent = { mContent = it },
                    busy = sourceBusy,
                    error = sourceError,
                    onBusy = { sourceBusy = it },
                    onError = { sourceError = it },
                    onDone = { finish() }
                )
            }
        }

        // İlerleme noktaları
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (i == pagerState.currentPage) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == pagerState.currentPage) Color.White
                            else Color.White.copy(alpha = 0.3f)
                        )
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WelcomePage(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF123D8B)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Hoş Geldin",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            "Tüm IPTV içeriğin tek uygulamada — kurulum bir dakika sürer.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(28.dp))
        FEATURES.forEach { f ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E3A8A).copy(alpha = 0.35f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(f.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(f.title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(f.desc, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Başla", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ProfilePage(
    name: String,
    onName: (String) -> Unit,
    avatar: String,
    onAvatar: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Profilini Oluştur",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            "İzleme deneyimini kişiselleştirmek için bir ad ve avatar seç. " +
                "Favorilerin ve izleme geçmişin bu profilde saklanır.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { onName(it.take(24)) },
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
        AVATARS.chunked(4).forEach { rowAvatars ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
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
                            .clickable { onAvatar(emoji) }
                            .then(if (selected) Modifier.padding(4.dp) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = MaterialTheme.typography.headlineMedium.fontSize)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Devam", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SourcePage(
    vm: MainViewModel,
    sourceChoice: String?,
    onChoice: (String?) -> Unit,
    pName: String, pUrl: String, pMac: String, pUser: String, pPass: String,
    onPName: (String) -> Unit, onPUrl: (String) -> Unit, onPMac: (String) -> Unit,
    onPUser: (String) -> Unit, onPPass: (String) -> Unit,
    xName: String, xServer: String, xUser: String, xPass: String,
    onXName: (String) -> Unit, onXServer: (String) -> Unit, onXUser: (String) -> Unit, onXPass: (String) -> Unit,
    mName: String, mUrl: String, mContent: String,
    onMName: (String) -> Unit, onMUrl: (String) -> Unit, onMContent: (String) -> Unit,
    busy: Boolean,
    error: String?,
    onBusy: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()

    fun savePortalAndFinish() {
        if (pName.isBlank() || pUrl.isBlank()) {
            onError("Portal adı ve URL zorunludur")
            return
        }
        onBusy(true)
        onError(null)
        val portal = Portal(
            id = java.util.UUID.randomUUID().toString(),
            name = pName.trim(),
            url = StalkerClient.normalizeBase(pUrl.trim()),
            mac = pMac.trim(),
            username = pUser.trim(),
            password = pPass
        )
        scope.launch {
            vm.savePortal(portal)
            val result = vm.connect(portal)
            onBusy(false)
            if (result.isSuccess) onDone()
            else onError(result.exceptionOrNull()?.message ?: "Bağlantı hatası")
        }
    }

    fun saveXtreamAndFinish() {
        val srv = xServer.trim()
        if (!srv.startsWith("http")) {
            onError("Geçerli bir http(s) sunucu adresi girin")
            return
        }
        if (xUser.trim().isBlank()) {
            onError("Kullanıcı adı gerekli")
            return
        }
        onBusy(true)
        onError(null)
        val candidate = XtreamSource(
            id = "xt_" + srv.hashCode().toString() + xUser.trim().hashCode().toString(),
            name = xName.ifBlank { srv },
            server = srv,
            username = xUser.trim(),
            password = xPass.trim()
        )
        scope.launch {
            val ok = runCatching { XtreamClient().validate(candidate) }.getOrDefault(false)
            onBusy(false)
            if (ok) {
                vm.saveXtreamSource(candidate)
                vm.setActiveSource("xtream", candidate.id)
                onDone()
            } else {
                onError("Xtream doğrulaması başarısız — sunucu, kullanıcı adı veya şifre hatalı")
            }
        }
    }

    fun saveM3uAndFinish() {
        val trimmed = mUrl.trim()
        if (!trimmed.startsWith("http") && mContent.isBlank()) {
            onError("Geçerli bir http(s) M3U URL girin veya içeriği yapıştırın")
            return
        }
        onBusy(true)
        onError(null)
        val source = M3uSource(
            id = "m3u_" + trimmed.hashCode().toString() + System.currentTimeMillis().toString().takeLast(4),
            name = mName.ifBlank { trimmed.ifBlank { "M3U Listem" } },
            url = trimmed,
            content = mContent.trim()
        )
        vm.saveM3uSource(source)
        vm.setActiveSource("m3u", source.id)
        onBusy(false)
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "İlk Kaynağını Ekle",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            "İçeriğinin geldiği kaynağı seç — sonradan Ayarlar → Playlist & Kaynaklar'dan dilediğin kadar ekleyebilirsin.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(20.dp))

        SourceChoiceCard(
            icon = Icons.Default.Tv,
            title = "Stalker Portal",
            desc = "Portal URL'si + MAC adresi (ör. MAG/STB portalı)",
            selected = sourceChoice == "portal",
            onClick = { onChoice(if (sourceChoice == "portal") null else "portal") }
        )
        SourceChoiceCard(
            icon = Icons.Default.Cloud,
            title = "Xtream Codes",
            desc = "Sunucu adresi + kullanıcı adı + şifre",
            selected = sourceChoice == "xtream",
            onClick = { onChoice(if (sourceChoice == "xtream") null else "xtream") }
        )
        SourceChoiceCard(
            icon = Icons.Default.PlaylistPlay,
            title = "M3U Playlist",
            desc = "#EXTM3U liste adresi veya içerik",
            selected = sourceChoice == "m3u",
            onClick = { onChoice(if (sourceChoice == "m3u") null else "m3u") }
        )

        when (sourceChoice) {
            "portal" -> {
                Spacer(Modifier.height(20.dp))
                Text("Stalker Portal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = pName, onValueChange = onPName, label = { Text("Portal Adı") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = pUrl, onValueChange = onPUrl, label = { Text("Portal URL (http://ip:port/portal)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pMac,
                        onValueChange = onPMac,
                        label = { Text("MAC Adresi (opsiyonel)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    TextButton(onClick = { onPMac(StalkerClient.generateMac()) }) { Text("Yeni MAC") }
                }
                OutlinedTextField(value = pUser, onValueChange = onPUser, label = { Text("Kullanıcı Adı (opsiyonel)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = pPass,
                    onValueChange = onPPass,
                    label = { Text("Şifre (opsiyonel)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            "xtream" -> {
                Spacer(Modifier.height(20.dp))
                Text("Xtream Codes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = xName, onValueChange = onXName, label = { Text("İsim") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = xServer, onValueChange = onXServer, label = { Text("Sunucu (http://host:port)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = xUser, onValueChange = onXUser, label = { Text("Kullanıcı adı") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = xPass, onValueChange = onXPass, label = { Text("Şifre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            "m3u" -> {
                Spacer(Modifier.height(20.dp))
                Text("M3U Playlist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = mName, onValueChange = onMName, label = { Text("İsim (opsiyonel)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mUrl, onValueChange = onMUrl, label = { Text("M3U URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = mContent,
                    onValueChange = onMContent,
                    label = { Text("İçerik (isteğe bağlı — #EXTM3U metni)") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (sourceChoice != null) {
            Spacer(Modifier.height(16.dp))
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = {
                    when (sourceChoice) {
                        "portal" -> savePortalAndFinish()
                        "xtream" -> saveXtreamAndFinish()
                        "m3u" -> saveM3uAndFinish()
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text("Bağlanılıyor…")
                } else {
                    Text("Kaydet ve Devam Et", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = onDone,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Şimdilik atla", color = Color.White.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SourceChoiceCard(
    icon: ImageVector,
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        else Color.White.copy(alpha = 0.06f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            }
            if (selected) {
                Text("✓", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = Color.White.copy(alpha = 0.06f))
}
