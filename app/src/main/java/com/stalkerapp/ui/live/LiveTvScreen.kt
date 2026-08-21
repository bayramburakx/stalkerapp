package com.stalkerapp.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.ChannelCustomization
import com.stalkerapp.data.ChannelCustomizer
import com.stalkerapp.data.Genre
import com.stalkerapp.data.Profile
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.components.ChannelRow
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.GlassChip
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.playback.PlaybackManager
import kotlinx.coroutines.launch

@Composable
fun LiveTvScreen(
    profile: Profile?,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenGuide: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val cooldown by vm.cooldownSeconds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lang = app.store.settings().language
    fun t(text: String) = com.stalkerapp.util.L10n.t(lang, text)

    // Aktif kanal kaynağı: Stalker portal (varsayılan) ya da M3U / Xtream.
    val kind = vm.activeSourceKind()
    val sourceId = vm.activeSourceId()
    val isExternal = kind == "m3u" || kind == "xtream"

    // Hiçbir kaynak eklenmemiş veya aktif kaynak türü Ayarlar'dan kapatılmış.
    if (vm.enabledSourceKind() == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(
                    t("Henüz bir kaynak eklemedin"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    t("Ayarlar → Playlist & Kaynaklar bölümünden Stalker portal, M3U listesi veya Xtream Codes ekleyerek kanalları burada görebilirsin."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    var genres by remember { mutableStateOf<List<Genre>?>(null) }
    var channels by remember { mutableStateOf<List<Channel>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedGenre by remember { mutableStateOf(0L) }
    var selectedCustomGroup by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    // Kanal yönetimi: özelleştirme sürümü değişince liste yeniden okunur.
    var customVersion by remember { mutableStateOf(0) }
    // Kanal adının altında gösterilecek "şu an oynayan" programlar (harici EPG).
    var nowPlaying by remember { mutableStateOf(emptyMap<Long, String>()) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val hiddenGroups = settings.hiddenChannelGroups.toSet()

    LaunchedEffect(profile, kind, sourceId) {
        selectedGenre = 0L
        selectedCustomGroup = null
        query = ""
    }

    LaunchedEffect(selectedGenre, profile, kind, sourceId) {
        loading = true
        error = null
        try {
            val loaded = vm.loadChannelsForActiveSource(profile)
            if (loaded == null) {
                error = if (isExternal) t("Kaynak yüklenemedi — Ayarlar'dan kontrol edin")
                else t("Portal bağlı değil")
            } else {
                val (g, ch) = loaded
                genres = g
                channels = ch
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    // Kanal listesi yüklendiğinde EPG'den "şu an oynayan" programları hazırla.
    LaunchedEffect(channels) {
        val ch = channels ?: return@LaunchedEffect
        nowPlaying = runCatching { vm.repository.nowPlayingTitles(ch) }.getOrDefault(emptyMap())
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Kanal yönetimi özelleştirmeleri (özel gruplar / sıralama / ad / logo).
        // Veri hesapları Column dışında tutulur çünkü Multi View overlay'i de bu kapsamı kullanır.
        val customization = remember(customVersion) { app.store.channelCustomization() }

        // Kanal yönetimi uygulanmış liste: ad düzenleyici + özel logo + özel grup.
        // 10k+ kanallı M3U listelerinde bu hesaplar her arama tuşunda yeniden
        // çalışmasın diye remember ile önbelleğe alınır (yalnızca kanal listesi,
        // özelleştirme veya gizlenen gruplar değişince yeniden hesaplanır).
        val rawChannels = channels.orEmpty()
        val customGroups = remember(rawChannels, customization) {
            customization.customGroups.filter { g ->
                rawChannels.any { customization.channelGroup[it.id.toString()] == g.name }
            }
        }
        val genreList = remember(genres, hiddenGroups, customGroups, customization) {
            (
                ChannelCustomizer.sortedGenres(
                    genres.orEmpty().filter { it.id == 0L || it.title !in hiddenGroups },
                    customization
                ) +
                customGroups.map { Genre(0, it.name) }
            ).distinctBy { it.title }
        }
        val allChannels = remember(rawChannels, customization, hiddenGroups) {
            rawChannels
                .map { ChannelCustomizer.apply(it, customization) }
                .filter { it.tvGenreTitle !in hiddenGroups }
        }
        // Kategori filtresi tür başlığına göre yapılır (Stalker/M3U/Xtream hepsinde çalışır).
        val activeGenreTitle = genreList.firstOrNull { it.id == selectedGenre }?.title
        val displayed = remember(allChannels, selectedGenre, selectedCustomGroup, query, activeGenreTitle, customization) {
            val filtered = allChannels.filter { ch ->
                val inGenre = selectedGenre <= 0L || ch.tvGenreTitle == activeGenreTitle || ch.tvGenreId == selectedGenre
                val inCustom = selectedCustomGroup == null || ch.tvGenreTitle == selectedCustomGroup
                inGenre && inCustom &&
                    (query.isBlank() || ch.name.contains(query.trim(), ignoreCase = true))
            }
            val sortGroup = selectedCustomGroup ?: if (selectedGenre > 0L) activeGenreTitle else null
            if (sortGroup != null) {
                ChannelCustomizer.sortedChannels(filtered, sortGroup, customization)
            } else filtered
        }

        Column(modifier = Modifier.fillMaxSize()) {
        if (cooldown > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    t("Cooldown aktif") + " — " + cooldown + "s " + t("sonra istek gönderilebilir"),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isTv = com.stalkerapp.ui.tv.isTvDevice(LocalContext.current)
            var isInputModalOpen by remember { mutableStateOf(false) }
            var isFocused by remember { mutableStateOf(false) }
            val searchShape = RoundedCornerShape(50)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(searchShape)
                    .background(if (isTv && isFocused) Color(0xFF1E293B) else MaterialTheme.colorScheme.surface.copy(alpha = 0.60f))
                    .border(
                        width = if (isTv && isFocused) 2.5.dp else 1.dp,
                        color = if (isTv && isFocused) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                        shape = searchShape
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable(isTv)
                    .clickable(isTv) { isInputModalOpen = true }
                    .onKeyEvent { ev ->
                        if (isTv && com.stalkerapp.ui.tv.isTvSelectKey(ev)) {
                            isInputModalOpen = true; true
                        } else false
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = if (isTv && isFocused) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                if (isTv) {
                    Text(
                        text = if (query.isNotBlank()) query else t("Kanal ara… (OK tuşuna basın)"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (query.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isBlank()) {
                                Text(
                                    t("Kanal ara…"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                    )
                }
            }

            if (isTv && isInputModalOpen) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { isInputModalOpen = false },
                    title = { Text(t("Kanal Ara"), color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        var tempText by remember { mutableStateOf(query) }
                        val focusReq = remember { androidx.compose.ui.focus.FocusRequester() }
                        Column {
                            androidx.compose.material3.OutlinedTextField(
                                value = tempText,
                                onValueChange = { tempText = it; query = it },
                                singleLine = true,
                                placeholder = { Text(t("Kanal adı yazın…")) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusReq)
                            )
                            LaunchedEffect(Unit) {
                                kotlinx.coroutines.delay(100)
                                runCatching { focusReq.requestFocus() }
                            }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { isInputModalOpen = false }) {
                            Text(t("Tamam"), color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            query = ""
                            isInputModalOpen = false
                        }) {
                            Text(t("Temizle"), color = Color.White.copy(0.6f))
                        }
                    },
                    containerColor = Color(0xFF131722),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }

        if (genreList.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    GlassChip(
                        selected = selectedGenre == 0L && selectedCustomGroup == null,
                        onClick = { selectedGenre = 0L; selectedCustomGroup = null },
                        label = t("Tümü")
                    )
                }
                items(genreList.filter { it.title != "Tümü" }, key = { it.title }) { g ->
                    GlassChip(
                        selected = if (g.id == 0L) selectedCustomGroup == g.title
                            else selectedGenre == g.id && selectedCustomGroup == null,
                        onClick = {
                            if (g.id == 0L) {
                                selectedCustomGroup = g.title
                                selectedGenre = 0L
                            } else {
                                selectedGenre = g.id
                                selectedCustomGroup = null
                            }
                        },
                        label = g.title
                    )
                }
            }
        }

        when {
            loading && channels == null -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LoadingBox()
                if (kind == "m3u") {
                    Text(
                        t("Büyük M3U listesi indiriliyor, birkaç dakika sürebilir…"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
            error != null && channels == null -> EmptyState("$error\n\n${t("Geri dönüp tekrar deneyin")}")
            allChannels.isEmpty() -> EmptyState(t("Kanal bulunamadı"))
            displayed.isEmpty() -> EmptyState(t("Sonuç bulunamadı"))
            else -> {
                val favChannels by vm.favoriteChannels.collectAsStateWithLifecycle()
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(displayed, key = { it.id }) { ch ->
                        val isFav = favChannels.any { it.id == ch.id }
                        ChannelRow(
                            channel = ch,
                            baseUrl = profile?.baseUrl.orEmpty(),
                            hideNumber = settings.hideChannelNumbers,
                            isFavorite = isFav,
                            nowPlaying = nowPlaying[ch.id],
                            onToggleFavorite = { vm.toggleFavoriteChannel(ch) },
                            onClick = {
                                scope.launch {
                                    val list = allChannels
                                    val idx = list.indexOfFirst { it.id == ch.id }
                                    if (idx >= 0) {
                                        PlaybackManager.playChannel(list, idx, profile)
                                        onOpenPlayer()
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        }
    }
}
