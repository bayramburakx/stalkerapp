package com.stalkerapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import com.stalkerapp.R
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.M3uSource
import com.stalkerapp.data.Portal
import com.stalkerapp.data.StalkerClient
import com.stalkerapp.data.XtreamClient
import com.stalkerapp.data.XtreamSource
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.components.PortioButton
import com.stalkerapp.ui.components.PortioButtonStyle
import com.stalkerapp.ui.components.PortioCard
import com.stalkerapp.ui.components.PortioPrimaryButton
import com.stalkerapp.ui.components.PortioSourceCard
import com.stalkerapp.ui.components.PortioTextField
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.ui.theme.PortioShape
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
 * Portio Onboarding / İlk Kurulum Sihirbazı Ekranı
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

    var lang by remember { mutableStateOf(app.store.settings().language) }
    fun t(text: String) = L10n.t(lang, text)

    var sourceChoice by remember { mutableStateOf<String?>(null) }
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
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
            PortioButton(
                onClick = { finish() },
                style = PortioButtonStyle.Glass,
                modifier = Modifier.height(40.dp).widthIn(min = 96.dp)
            ) {
                Text(t("Atla"), color = Color.White, fontWeight = FontWeight.Medium)
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

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 5.dp)
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
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            Image(
                painter = painterResource(R.drawable.portio_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(22.dp))
            )
            Spacer(Modifier.height(20.dp))
            Text(
                t("Kullanım Deneyiminizi Seçin"),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                t("Arayüzü cihazınıza en uygun şekilde optimize edelim."),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))

            PortioSourceCard(
                icon = Icons.Default.Tv,
                title = t("Android TV & TV Box"),
                desc = t("Kumanda D-Pad ile gezinme, 10-foot sinematik arayüz, otomatik odaklanma ve büyük posterler."),
                selected = current == "tv",
                onClick = { onSelect("tv") }
            )
            Spacer(Modifier.height(14.dp))
            PortioSourceCard(
                icon = Icons.Default.Movie,
                title = t("Mobil Telefon & Tablet"),
                desc = t("Dokunmatik kontroller, portre/yatay görünüm, alt menü ve resim içinde resim (PiP)."),
                selected = current == "mobile",
                onClick = { onSelect("mobile") }
            )

            Spacer(Modifier.height(36.dp))
            PortioPrimaryButton(
                text = t("Devam Et"),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

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
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Image(
                painter = painterResource(R.drawable.portio_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "Portio",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Dilini seç  •  Choose your language",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(36.dp))

            PortioCard(
                onClick = { onSelect("tr") },
                shape = PortioShape.Card,
                modifier = Modifier.fillMaxWidth()
            ) { isFocused ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Türkçe", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Uygulamayı Türkçe kullan", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                    }
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (current == "tr") Color.White else Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (current == "tr") Text("✓", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            PortioCard(
                onClick = { onSelect("en") },
                shape = PortioShape.Card,
                modifier = Modifier.fillMaxWidth()
            ) { isFocused ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("English", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Use the app in English", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                    }
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (current == "en") Color.White else Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (current == "en") Text("✓", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(36.dp))
            PortioPrimaryButton(
                text = if (current == "en") "Continue" else "Devam Et",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
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
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            Image(
                painter = painterResource(R.drawable.portio_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
            Spacer(Modifier.height(22.dp))
            Text(
                t("Hoş Geldin"),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                t("Tüm IPTV kaynakların tek bir akıllı arayüzde"),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )
            Spacer(Modifier.height(32.dp))
            FEATURES.forEach { f ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
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
            Spacer(Modifier.height(28.dp))
            PortioPrimaryButton(
                text = t("Başla"),
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            )
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
                .padding(horizontal = 28.dp)
        ) {
            Spacer(Modifier.height(20.dp))
            Text(
                t("İlk Kaynağını Ekle"),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                t("İçeriğinin geldiği kaynağı seç — sonradan Ayarlar → Playlist & Kaynaklar'dan dilediğin kadar ekleyebilirsin."),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 10.dp)
            )
            Spacer(Modifier.height(24.dp))

            PortioSourceCard(
                icon = Icons.Default.Tv,
                title = t("Stalker Portal"),
                desc = t("Portal URL'si + MAC adresi (ör. MAG/STB portalı)"),
                selected = sourceChoice == "portal",
                onClick = { onChoice(if (sourceChoice == "portal") null else "portal") }
            )
            Spacer(Modifier.height(12.dp))
            PortioSourceCard(
                icon = Icons.Default.Cloud,
                title = t("Xtream Codes"),
                desc = t("Sunucu adresi + kullanıcı adı + şifre"),
                selected = sourceChoice == "xtream",
                onClick = { onChoice(if (sourceChoice == "xtream") null else "xtream") }
            )
            Spacer(Modifier.height(12.dp))
            PortioSourceCard(
                icon = Icons.Default.PlaylistPlay,
                title = t("M3U Playlist"),
                desc = t("#EXTM3U liste adresi veya içerik"),
                selected = sourceChoice == "m3u",
                onClick = { onChoice(if (sourceChoice == "m3u") null else "m3u") }
            )

            when (sourceChoice) {
                "portal" -> {
                    Spacer(Modifier.height(24.dp))
                    Text(t("Stalker Portal"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    PortioTextField(value = pName, onValueChange = onPName, label = t("Portal Adı"))
                    Spacer(Modifier.height(10.dp))
                    PortioTextField(value = pUrl, onValueChange = onPUrl, label = t("Portal URL (http://ip:port/portal)"))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PortioTextField(
                            value = pMac,
                            onValueChange = onPMac,
                            label = t("MAC Adresi (opsiyonel)"),
                            modifier = Modifier.weight(1f)
                        )
                        PortioButton(
                            onClick = { onPMac(StalkerClient.generateMac()) },
                            style = PortioButtonStyle.Glass,
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text(t("Yeni MAC"), color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    PortioTextField(value = pUser, onValueChange = onPUser, label = t("Kullanıcı Adı (opsiyonel)"))
                    Spacer(Modifier.height(10.dp))
                    PortioTextField(
                        value = pPass,
                        onValueChange = onPPass,
                        label = t("Şifre (opsiyonel)"),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(Modifier.height(20.dp))
                    PortioPrimaryButton(
                        text = t("Bağlan & Kaydet"),
                        onClick = { savePortalAndFinish() },
                        loading = busy,
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    )
                }
                "xtream" -> {
                    Spacer(Modifier.height(24.dp))
                    Text(t("Xtream Codes"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    PortioTextField(value = xName, onValueChange = onXName, label = t("İsim"))
                    Spacer(Modifier.height(10.dp))
                    PortioTextField(value = xServer, onValueChange = onXServer, label = t("Sunucu (http://host:port)"))
                    Spacer(Modifier.height(10.dp))
                    PortioTextField(value = xUser, onValueChange = onXUser, label = t("Kullanıcı adı"))
                    Spacer(Modifier.height(10.dp))
                    PortioTextField(value = xPass, onValueChange = onXPass, label = t("Şifre"), visualTransformation = PasswordVisualTransformation())
                    Spacer(Modifier.height(20.dp))
                    PortioPrimaryButton(
                        text = t("Bağlan & Kaydet"),
                        onClick = { saveXtreamAndFinish() },
                        loading = busy,
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    )
                }
                "m3u" -> {
                    Spacer(Modifier.height(24.dp))
                    Text(t("M3U Playlist"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    PortioTextField(value = mName, onValueChange = onMName, label = t("İsim (opsiyonel)"))
                    Spacer(Modifier.height(10.dp))
                    PortioTextField(value = mUrl, onValueChange = onMUrl, label = t("M3U URL"))
                    Spacer(Modifier.height(10.dp))
                    PortioTextField(
                        value = mContent,
                        onValueChange = onMContent,
                        label = t("İçerik (isteğe bağlı — #EXTM3U metni)"),
                        singleLine = false,
                        maxLines = 5
                    )
                    Spacer(Modifier.height(20.dp))
                    PortioPrimaryButton(
                        text = t("Kaydet & Başla"),
                        onClick = { saveM3uAndFinish() },
                        loading = busy,
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    )
                }
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error,
                    color = PortioColors.Error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
