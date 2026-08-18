package com.stalkerapp.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.EpgProgram
import com.stalkerapp.data.Genre
import com.stalkerapp.data.Profile
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.components.ChannelLogo
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.GlassChip
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.ui.components.resolveUrl
import com.stalkerapp.ui.rememberMainViewModel
import kotlinx.coroutines.launch

private val L10nLocal: Map<String, String> = mapOf(
    // Türkçe -> English
    "Portal bağlı değil" to "Portal not connected",
    "Geri" to "Back",
    "EPG Rehberi" to "EPG Guide",
    "Kanal ara…" to "Search channels…",
    "Tümü" to "All",
    "Liste" to "List",
    "Izgara" to "Grid",
    "Geri dönüp tekrar deneyin" to "Go back and try again",
    "Kanal bulunamadı" to "No channels found",
    "Kanal" to "Channel",
    "EPG yok" to "No EPG",
    "Varsayılan" to "Default",
)
private fun str(lang: String, text: String): String =
    if (lang == "en") L10nLocal[text] ?: text else text

/**
 * Tam ekran EPG rehberi: kanallar + "şimdi/sonra" programları. İlk ~60 kanalın
 * EPG'si arka planda çekilir; kalanlar için "yükleniyor" gösterilir.
 */
@Composable
fun EpgGuideScreen(
    profile: Profile?,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val lang = vm.store.settings().language

    var genres by remember { mutableStateOf<List<Genre>?>(null) }
    var channels by remember { mutableStateOf<List<Channel>?>(null) }
    var epg by remember { mutableStateOf<Map<Long, List<EpgProgram>>>(emptyMap()) }
    var selectedGenre by remember { mutableStateOf(0L) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // Liste / Izgara (TiviMate tarzı kanal × zaman) görünümü.
    var gridMode by remember { mutableStateOf(false) }

    // Aktif kaynak: Stalker portal (profil) ya da Xtream/M3U (profil null olabilir).
    // Rehber her iki durumda da çalışır — Xtream/M3U'da kanallar kaynaktan,
    // programlar harici XMLTV EPG'sinden gelir.
    val isExternal = vm.activeSourceKind() == "m3u" || vm.activeSourceKind() == "xtream"

    LaunchedEffect(profile) {
        if (isExternal) {
            val loaded = runCatching { vm.loadChannelsForActiveSource(profile) }
                .getOrNull()
            genres = loaded?.first?.filter { it.id != 0L } ?: emptyList()
            error = null
        } else if (profile != null) {
            runCatching { vm.repository.loadGenres(profile) }
                .onSuccess { genres = it }
                .onFailure { error = it.message; genres = emptyList() }
        }
    }

    LaunchedEffect(selectedGenre, profile) {
        loading = true
        error = null
        try {
            val list = if (isExternal) {
                val all = vm.loadChannelsForActiveSource(profile)?.second.orEmpty()
                // Xtream/M3U'da tür filtresi kanalın tvGenreId/tvGenreTitle'ına göre
                // bellekte uygulanır (Stalker'daki sunucu tarafı filtre yerine).
                if (selectedGenre <= 0L) all
                else all.filter { it.tvGenreId == selectedGenre || it.tvGenreTitle == genres?.firstOrNull { g -> g.id == selectedGenre }?.title }
            } else if (profile != null) {
                vm.repository.loadChannels(profile, selectedGenre)
            } else {
                emptyList()
            }
            channels = list
            // EPG'yi ilk 60 kanal için çek (her kanal ayrı istek; tamamı çok yavaş olur).
            val map = mutableMapOf<Long, List<EpgProgram>>()
            list.take(60).forEach { ch ->
                val programs = runCatching { vm.repository.loadEpg(profile, ch) }
                    .getOrDefault(emptyList())
                map[ch.id] = programs
            }
            epg = map
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Üst bar: geri + başlık
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = str(lang, "Geri"), tint = Color.White)
                }
                Text(
                    str(lang, "EPG Rehberi"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Anasayfa dili: cam arama çubuğu.
                val searchShape = RoundedCornerShape(50)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(searchShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.60f))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), searchShape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isBlank()) {
                                Text(
                                    str(lang, "Kanal ara…"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                    )
                }
            }

            val genreList = genres.orEmpty()
            if (genreList.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        GlassChip(
                            selected = selectedGenre == 0L,
                            onClick = { selectedGenre = 0L },
                            label = str(lang, "Tümü")
                        )
                    }
                    items(genreList) { g ->
                        GlassChip(
                            selected = selectedGenre == g.id,
                            onClick = { selectedGenre = g.id },
                            label = g.title
                        )
                    }
                }
            }

            // Görünüm: Liste / Izgara.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassChip(
                    selected = !gridMode,
                    onClick = { gridMode = false },
                    label = str(lang, "Liste")
                )
                GlassChip(
                    selected = gridMode,
                    onClick = { gridMode = true },
                    label = str(lang, "Izgara")
                )
            }

            val list = channels.orEmpty()
            val filtered = if (query.isBlank()) list
            else list.filter { it.name.contains(query.trim(), ignoreCase = true) }

            when {
                loading && channels == null -> LoadingBox()
                error != null && channels == null -> EmptyState("$error\n\n${str(lang, "Geri dönüp tekrar deneyin")}")
                filtered.isEmpty() -> EmptyState(str(lang, "Kanal bulunamadı"))
                gridMode -> EpgGridView(
                    channels = filtered,
                    epg = epg,
                    baseUrl = profile?.baseUrl.orEmpty(),
                    onPlay = { ch ->
                        val idx = list.indexOfFirst { it.id == ch.id }
                        if (idx >= 0) {
                            PlaybackManager.playChannel(list, idx, profile)
                            onOpenPlayer()
                        }
                    },
                    onPlayCatchup = { ch, p ->
                        // Geçmiş yayın (catch-up): kanal akışını utc/lutc parametreleriyle
                        // oynat. Kaynak türüne göre doğru formatı CatchupHelper üretir.
                        val url = runCatching {
                            com.stalkerapp.data.CatchupHelper.buildStalkerCatchupUrl(
                                vm.repository.channelStreamUrl(ch, profile),
                                p.startTs,
                                p.stopTs
                            )
                        }.getOrNull()
                        if (!url.isNullOrBlank()) {
                            PlaybackManager.play(url, "${ch.name} - ${p.name}", ch.logo)
                            onOpenPlayer()
                        } else {
                            PlaybackManager.playChannel(list, list.indexOfFirst { it.id == ch.id }, profile)
                            onOpenPlayer()
                        }
                    }
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(filtered, key = { it.id }) { ch ->
                        val programs = epg[ch.id].orEmpty()
                        val now = programs.firstOrNull { it.isCurrent }
                        val next = programs.firstOrNull { p ->
                            !p.isCurrent && p.startTs > System.currentTimeMillis() / 1000
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val idx = list.indexOfFirst { it.id == ch.id }
                                    if (idx >= 0) {
                                        PlaybackManager.playChannel(list, idx, profile)
                                        onOpenPlayer()
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ChannelLogo(
                                    logo = resolveUrl(ch.logo, profile?.baseUrl.orEmpty()),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        ch.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${str(lang, "Kanal")} ${ch.number}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                                Text(
                                    when {
                                        programs.isEmpty() -> str(lang, "EPG yok")
                                        programs.first().isDefault -> str(lang, "Varsayılan")
                                        else -> "EPG"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (programs.isEmpty() || programs.firstOrNull()?.isDefault == true)
                                        Color.White.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                            if (now != null) {
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (now.isDefault) Color.White.copy(alpha = 0.08f)
                                            else Color(0xFF1E3A8A).copy(alpha = 0.55f)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Kanalın altında şu an oynanan program adı.
                                    Text(
                                        "● ${now.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (!now.isDefault) {
                                        Text(
                                            "${vm.repository.formatEpoch(now.startTs)} — ${vm.repository.formatEpoch(now.stopTs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF8AB4F8)
                                        )
                                    }
                                }
                            }
                            if (next != null && !next.isDefault) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${vm.repository.formatEpoch(next.startTs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.45f),
                                        modifier = Modifier.width(96.dp)
                                    )
                                    Text(
                                        next.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}

/**
 * TiviMate tarzı ızgara EPG: solda sabit kanal etiketi, sağda kanal × zaman
 * şeritleri (programlar süreye göre orantılı genişlikte). Yatay kaydırma üst
 * zaman ekseni ve tüm kanal şeritlerinde senkron çalışır (paylaşılan ScrollState).
 */
@Composable
private fun EpgGridView(
    channels: List<Channel>,
    epg: Map<Long, List<EpgProgram>>,
    baseUrl: String,
    onPlay: (Channel) -> Unit,
    onPlayCatchup: suspend (Channel, EpgProgram) -> Unit
) {
    val hourWidthDp = 110f
    val rowHeight = 56.dp
    val hScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val now = System.currentTimeMillis() / 1000
    val dayStart = now - (now % 86400)
    val totalWidthDp = (hourWidthDp * 24).dp

    Column(modifier = Modifier.fillMaxSize()) {
        // Üst zaman ekseni: 00:00 - 23:00 arası, her saat 110dp.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(hScroll)
                .background(Color(0xFF101418))
                .height(26.dp)
        ) {
            Box(modifier = Modifier.width(totalWidthDp).height(26.dp)) {
                for (h in 0 until 24) {
                    Box(
                        modifier = Modifier
                            .offset(x = (h * hourWidthDp).dp)
                            .width(hourWidthDp.dp)
                            .height(26.dp)
                    ) {
                        Text(
                            text = "%02d:00".format(h),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 5.dp)
                        )
                    }
                }
            }
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            items(channels, key = { it.id }) { ch ->
                val programs = epg[ch.id].orEmpty()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sabit sol sütun: kanal logosu + adı.
                    Row(
                        modifier = Modifier
                            .width(148.dp)
                            .height(rowHeight)
                            .background(Color(0xFF0C0F12))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelLogo(logo = resolveUrl(ch.logo, baseUrl), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = ch.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp
                        )
                    }
                    // Zaman şeridi: programlar süreye göre konumlandırılır.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(hScroll)
                    ) {
                        Box(modifier = Modifier.width(totalWidthDp).height(rowHeight)) {
                            programs.forEach { p ->
                                val startTs = maxOf(p.startTs, dayStart)
                                val stopTs = minOf(p.stopTs, dayStart + 86400)
                                if (stopTs > startTs) {
                                    val left = (startTs - dayStart) / 3600f * hourWidthDp
                                    val width = (stopTs - startTs) / 3600f * hourWidthDp
                                    if (width >= 6f) {
                                        Box(
                                            modifier = Modifier
                                                .offset(x = left.dp, y = 3.dp)
                                                .width(width.dp)
                                                .height(rowHeight - 6.dp)
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(
                                                    if (p.isCurrent) Color(0xFF1E3A8A).copy(alpha = 0.85f)
                                                    else if (p.stopTs < now && ch.isTvArchive && ch.archiveDuration > 0) Color(0xFF2E7D32).copy(alpha = 0.5f)
                                                    else Color.White.copy(alpha = 0.10f)
                                                )
                                                .clickable {
                                                    // Geçmiş yayınlar (catch-up) yeşil işaretlenir ve oynatılabilir.
                                                    if (p.stopTs < now && ch.isTvArchive && ch.archiveDuration > 0) {
                                                        scope.launch { onPlayCatchup(ch, p) }
                                                    } else {
                                                        onPlay(ch)
                                                    }
                                                }
                                                .padding(horizontal = 5.dp, vertical = 2.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = p.name.ifBlank { "—" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (p.isCurrent) Color.White else Color.White.copy(alpha = 0.75f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            }
        }
    }
}
