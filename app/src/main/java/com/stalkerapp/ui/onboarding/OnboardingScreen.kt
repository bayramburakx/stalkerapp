package com.stalkerapp.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stalkerapp.R
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.M3uSource
import com.stalkerapp.data.Portal
import com.stalkerapp.data.StalkerClient
import com.stalkerapp.data.XtreamClient
import com.stalkerapp.data.XtreamSource
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.util.L10n
import kotlinx.coroutines.launch

private data class Feature(val icon: ImageVector, val title: String, val desc: String)

private val FEATURES = listOf(
    Feature(Icons.Default.LiveTv, "Canlı TV", "Stalker, Xtream ve M3U kanalları tek listede"),
    Feature(Icons.Default.Movie, "Film & Dizi", "Kaldığın yerden devam et, binge moduyla izle"),
    Feature(Icons.Default.Schedule, "EPG Rehberi", "Program akışını gör, istediğin yayına atla"),
    Feature(Icons.Default.Cast, "TV'ye Yayınla", "Chromecast ile içeriği büyük ekrana aktar")
)

/**
 * Adım adım kurulum sihirbazı:
 *  1. Dil seçimi (Türkçe / English)
 *  2. Hoş geldin + özellik tanıtımı
 *  3. İlk kaynak (Stalker / Xtream / M3U) — atlanabilir
 *
 * Profil oluşturma artık giriş sonrası Netflix tarzı profil seçicide yapılır.
 * Her adımdan "Atla" ile çıkılabilir; kaynaklar her zaman sonradan
 * Ayarlar → Playlist & Kaynaklar'dan eklenebilir.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 4 })

    val detectedIsTv = remember { com.stalkerapp.ui.tv.isTvDevice(context) }
    var selectedLayout by remember { mutableStateOf(if (detectedIsTv) "tv" else "mobile") }

    // Dil: sihirbaz başında seçilir ve kaydedilir
    var lang by remember { mutableStateOf(app.store.settings().language) }
    fun t(text: String) = L10n.t(lang, text)

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
        app.store.saveSettings(
            app.store.settings().copy(
                language = lang,
                preferredLayout = selectedLayout
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pagerState.currentPage > 0) {
                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Geri"), tint = Color.White)
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { finish() }) {
                Text(t("Atla"), color = Color.White.copy(alpha = 0.8f))
            }
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> DeviceModePage(
                    current = selectedLayout,
                    detectedIsTv = detectedIsTv,
                    lang = lang,
                    onSelect = { selectedLayout = it },
                    onContinue = { scope.launch { pagerState.animateScrollToPage(1) } }
                )
                1 -> LanguagePage(
                    current = lang,
                    onSelect = { lang = it },
                    onContinue = { scope.launch { pagerState.animateScrollToPage(2) } }
                )
                2 -> WelcomePage(lang = lang) { scope.launch { pagerState.animateScrollToPage(3) } }
                3 -> SourcePage(
                    lang = lang,
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
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (i == pagerState.currentPage) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == pagerState.currentPage) Color(0xFF00E5FF)
                            else Color.White.copy(alpha = 0.3f)
                        )
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Cihaz Deneyimi Seçimi Ekranı (Mobil vs Android TV). */
@Composable
private fun DeviceModePage(
    current: String,
    detectedIsTv: Boolean,
    lang: String,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit
) {
    fun t(text: String) = L10n.t(lang, text)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Image(
                painter = painterResource(R.drawable.portio_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(22.dp))
            )
            Spacer(Modifier.height(16.dp))
            Text(
                t("Kullanım Deneyiminizi Seçin"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                t("Arayüzü cihazınıza en uygun şekilde optimize edelim."),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            DeviceModeCard(
                icon = Icons.Default.Tv,
                title = t("Android TV & TV Box"),
                desc = t("Kumanda D-Pad ile gezinme, 10-foot sinematik arayüz, otomatik odaklanma ve büyük posterler."),
                isRecommended = detectedIsTv,
                selected = current == "tv",
                onClick = { onSelect("tv") }
            )
            Spacer(Modifier.height(14.dp))
            DeviceModeCard(
                icon = Icons.Default.Movie,
                title = t("Mobil Telefon & Tablet"),
                desc = t("Dokunmatik kontroller, portre/yatay görünüm, alt menü ve resim içinde resim (PiP)."),
                isRecommended = !detectedIsTv,
                selected = current == "mobile",
                onClick = { onSelect("mobile") }
            )

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(t("Devam Et"), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DeviceModeCard(
    icon: ImageVector,
    title: String,
    desc: String,
    isRecommended: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.04f else 1.0f, label = "dev_card_scale")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(
                width = if (isFocused) 3.dp else if (selected) 2.dp else 1.dp,
                color = if (isFocused) Color(0xFF00E5FF) else if (selected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .onKeyEvent { ev ->
                if (com.stalkerapp.ui.tv.isTvSelectKey(ev)) {
                    onClick(); true
                } else false
            },
        shape = RoundedCornerShape(16.dp),
        color = if (isFocused) Color(0xFF1E293B)
        else if (selected) Color(0xFF131A2A)
        else Color.White.copy(alpha = 0.05f)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) Color(0xFF00E5FF) else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    if (isRecommended) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF00E5FF).copy(alpha = 0.25f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "Önerilen",
                                color = Color(0xFF00E5FF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Text("✓", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/** İlk ekran: uygulama dili seçimi (Türkçe / English). */
@Composable
private fun LanguagePage(
    current: String,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Spacer(Modifier.height(24.dp))
        Image(
            painter = painterResource(R.drawable.portio_logo),
            contentDescription = null,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Portio",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Dilini seç  •  Choose your language",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        LanguageCard(
            code = "tr",
            title = "Türkçe",
            subtitle = "Uygulamayı Türkçe kullan",
            selected = current == "tr",
            onClick = { onSelect("tr") }
        )
        Spacer(Modifier.height(12.dp))
        LanguageCard(
            code = "en",
            title = "English",
            subtitle = "Use the app in English",
            selected = current == "en",
            onClick = { onSelect("en") }
        )

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(if (current == "en") "Continue" else "Devam Et", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LanguageCard(
    code: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        label = "lang_card_scale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(
                width = if (isFocused) 3.dp else if (selected) 1.5.dp else 0.dp,
                color = if (isFocused) Color(0xFF00E5FF) else if (selected) Color(0xFF38BDF8) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .onKeyEvent { ev ->
                if (com.stalkerapp.ui.tv.isTvSelectKey(ev)) {
                    onClick(); true
                } else false
            },
        shape = RoundedCornerShape(16.dp),
        color = if (isFocused) Color(0xFF1E293B)
        else if (selected) Color.White.copy(alpha = 0.16f)
        else Color.White.copy(alpha = 0.06f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isFocused) Color(0xFF00E5FF) else Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            }
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Text("✓", color = Color.Black, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = Color.White.copy(alpha = 0.06f))
}

@Composable
private fun WelcomePage(lang: String, onStart: () -> Unit) {
    fun t(text: String) = L10n.t(lang, text)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Image(
                painter = painterResource(R.drawable.portio_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
            Spacer(Modifier.height(20.dp))
            Text(
                t("Hoş Geldin"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                t("Tüm IPTV kaynakların tek bir akıllı arayüzde"),
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
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(f.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(t(f.title), style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(t(f.desc), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            var isStartFocused by remember { mutableStateOf(false) }
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .onFocusChanged { isStartFocused = it.isFocused }
                    .border(
                        width = if (isStartFocused) 3.dp else 0.dp,
                        color = if (isStartFocused) Color(0xFF00E5FF) else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
                    ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(t("Başla"), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SourcePage(
    lang: String,
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
    fun t(text: String) = L10n.t(lang, text)

    fun savePortalAndFinish() {
        if (pName.isBlank() || pUrl.isBlank()) {
            onError(t("Portal adı ve URL zorunludur"))
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
            else onError(result.exceptionOrNull()?.message ?: t("Bağlantı hatası"))
        }
    }

    fun saveXtreamAndFinish() {
        val srv = xServer.trim()
        if (!srv.startsWith("http")) {
            onError(t("Geçerli bir http(s) sunucu adresi girin"))
            return
        }
        if (xUser.trim().isBlank()) {
            onError(t("Kullanıcı adı gerekli"))
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
                onError(t("Xtream doğrulaması başarısız — sunucu, kullanıcı adı veya şifre hatalı"))
            }
        }
    }

    fun saveM3uAndFinish() {
        val trimmed = mUrl.trim()
        if (!trimmed.startsWith("http") && mContent.isBlank()) {
            onError(t("Geçerli bir http(s) M3U URL girin veya içeriği yapıştırın"))
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

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
        Spacer(Modifier.height(16.dp))
        Text(
            t("İlk Kaynağını Ekle"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            t("İçeriğinin geldiği kaynağı seç — sonradan Ayarlar → Playlist & Kaynaklar'dan dilediğin kadar ekleyebilirsin."),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(20.dp))

        SourceChoiceCard(
            icon = Icons.Default.Tv,
            title = t("Stalker Portal"),
            desc = t("Portal URL'si + MAC adresi (ör. MAG/STB portalı)"),
            selected = sourceChoice == "portal",
            onClick = { onChoice(if (sourceChoice == "portal") null else "portal") }
        )
        SourceChoiceCard(
            icon = Icons.Default.Cloud,
            title = t("Xtream Codes"),
            desc = t("Sunucu adresi + kullanıcı adı + şifre"),
            selected = sourceChoice == "xtream",
            onClick = { onChoice(if (sourceChoice == "xtream") null else "xtream") }
        )
        SourceChoiceCard(
            icon = Icons.Default.PlaylistPlay,
            title = t("M3U Playlist"),
            desc = t("#EXTM3U liste adresi veya içerik"),
            selected = sourceChoice == "m3u",
            onClick = { onChoice(if (sourceChoice == "m3u") null else "m3u") }
        )

        when (sourceChoice) {
            "portal" -> {
                Spacer(Modifier.height(20.dp))
                Text(t("Stalker Portal"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = pName, onValueChange = onPName, label = { Text(t("Portal Adı")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = pUrl, onValueChange = onPUrl, label = { Text(t("Portal URL (http://ip:port/portal)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pMac,
                        onValueChange = onPMac,
                        label = { Text(t("MAC Adresi (opsiyonel)")) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    TextButton(onClick = { onPMac(StalkerClient.generateMac()) }) { Text(t("Yeni MAC")) }
                }
                OutlinedTextField(value = pUser, onValueChange = onPUser, label = { Text(t("Kullanıcı Adı (opsiyonel)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = pPass,
                    onValueChange = onPPass,
                    label = { Text(t("Şifre (opsiyonel)")) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            "xtream" -> {
                Spacer(Modifier.height(20.dp))
                Text(t("Xtream Codes"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = xName, onValueChange = onXName, label = { Text(t("İsim")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = xServer, onValueChange = onXServer, label = { Text(t("Sunucu (http://host:port)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = xUser, onValueChange = onXUser, label = { Text(t("Kullanıcı adı")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = xPass, onValueChange = onXPass, label = { Text(t("Şifre")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            "m3u" -> {
                Spacer(Modifier.height(20.dp))
                Text(t("M3U Playlist"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = mName, onValueChange = onMName, label = { Text(t("İsim (opsiyonel)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mUrl, onValueChange = onMUrl, label = { Text(t("M3U URL")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = mContent,
                    onValueChange = onMContent,
                    label = { Text(t("İçerik (isteğe bağlı — #EXTM3U metni)")) },
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
                    Text(t("Bağlanılıyor…"))
                } else {
                    Text(t("Kaydet ve Devam Et"), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = onDone,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(t("Şimdilik atla"), color = Color.White.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(16.dp))
        }
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
    var isFocused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        label = "choice_card_scale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(
                width = if (isFocused) 3.dp else if (selected) 1.5.dp else 0.dp,
                color = if (isFocused) Color(0xFF00E5FF) else if (selected) Color(0xFF38BDF8) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .onKeyEvent { ev ->
                if (com.stalkerapp.ui.tv.isTvSelectKey(ev)) {
                    onClick(); true
                } else false
            },
        shape = RoundedCornerShape(14.dp),
        color = if (isFocused) Color(0xFF1E293B)
        else if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        else Color.White.copy(alpha = 0.06f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isFocused) Color(0xFF00E5FF) else Color.White,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isFocused) Color(0xFF00E5FF) else Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            }
            if (selected) {
                Text("✓", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = Color.White.copy(alpha = 0.06f))
}