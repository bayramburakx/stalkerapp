package com.stalkerapp.ui.live

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.ChannelCustomization
import com.stalkerapp.data.ChannelCustomizer
import com.stalkerapp.data.CustomChannelGroup
import com.stalkerapp.data.Genre
import com.stalkerapp.data.Profile
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.components.ChannelRow
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.GlassChip
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.ui.multiview.MultiViewScreen
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

    // Aktif kanal kaynağı: Stalker portal (varsayılan) ya da M3U / Xtream.
    val kind = vm.activeSourceKind()
    val sourceId = vm.activeSourceId()
    val isExternal = kind == "m3u" || kind == "xtream"
    val sourceName = when (kind) {
        "m3u" -> vm.m3uSources().firstOrNull { it.id == sourceId }?.name
        "xtream" -> vm.xtreamSources().firstOrNull { it.id == sourceId }?.name
        else -> null
    }

    // Hiçbir kaynak eklenmemiş veya aktif kaynak türü Ayarlar'dan kapatılmış.
    if (vm.enabledSourceKind() == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(
                    "Henüz bir kaynak eklemedin",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ayarlar → Playlist & Kaynaklar bölümünden Stalker portal, M3U listesi " +
                        "veya Xtream Codes ekleyerek kanalları burada görebilirsin.",
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
    // Kanal yönetimi: özelleştirme sürümü değişince liste yeniden okunur (uzun bas → yönet).
    var customVersion by remember { mutableStateOf(0) }
    var manageChannel by remember { mutableStateOf<Channel?>(null) }
    // Kanal adının altında gösterilecek "şu an oynayan" programlar (harici EPG).
    var nowPlaying by remember { mutableStateOf(emptyMap<Long, String>()) }
    var multiView by remember { mutableStateOf(false) }
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
                error = if (isExternal) "Kaynak yüklenemedi — Ayarlar'dan kontrol edin"
                else "Portal bağlı değil"
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
        Column(modifier = Modifier.fillMaxSize()) {
        if (cooldown > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Cooldown aktif — ${cooldown}s sonra istek gönderilebilir",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Aktif kaynak rozeti (Stalker dışındaki kaynaklar için).
        if (isExternal && sourceName != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    "Kaynak: ${sourceName.ifBlank { if (kind == "m3u") "M3U" else "Xtream" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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
                                "Kanal ara…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                )
            }

            // Multi View butonu: 2/4 kanalı aynı anda izle (TiviMate tarzı).
            IconButton(
                onClick = { if (!channels.isNullOrEmpty()) multiView = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.GridView,
                    contentDescription = "Multi View",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Kanal yönetimi özelleştirmeleri (özel gruplar / sıralama / ad / logo).
        val customization = remember(customVersion) { app.store.channelCustomization() }

        // Kanal yönetimi uygulanmış liste: ad düzenleyici + özel logo + özel grup.
        val rawChannels = channels.orEmpty()
        val customGroups = customization.customGroups.filter { g ->
            rawChannels.any { customization.channelGroup[it.id.toString()] == g.name }
        }
        val genreList = (
            ChannelCustomizer.sortedGenres(
                genres.orEmpty().filter { it.id == 0L || it.title !in hiddenGroups },
                customization
            ) +
            customGroups.map { Genre(0, it.name) }
        ).distinctBy { it.title }
        if (genreList.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    GlassChip(
                        selected = selectedGenre == 0L && selectedCustomGroup == null,
                        onClick = { selectedGenre = 0L; selectedCustomGroup = null },
                        label = "Tümü"
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

        val allChannels = rawChannels
            .map { ChannelCustomizer.apply(it, customization) }
            .filter { it.tvGenreTitle !in hiddenGroups }
        // Kategori filtresi tür başlığına göre yapılır (Stalker/M3U/Xtream hepsinde çalışır).
        val activeGenreTitle = genreList.firstOrNull { it.id == selectedGenre }?.title
        val filtered = allChannels.filter { ch ->
            val inGenre = selectedGenre <= 0L || ch.tvGenreTitle == activeGenreTitle || ch.tvGenreId == selectedGenre
            val inCustom = selectedCustomGroup == null || ch.tvGenreTitle == selectedCustomGroup
            inGenre && inCustom &&
                (query.isBlank() || ch.name.contains(query.trim(), ignoreCase = true))
        }
        // Seçili grupta manuel sıralama (Ayarlar → Kanal Yönetimi / uzun bas → taşı).
        val sortGroup = selectedCustomGroup ?: if (selectedGenre > 0L) activeGenreTitle else null
        val displayed = if (sortGroup != null) {
            ChannelCustomizer.sortedChannels(filtered, sortGroup, customization)
        } else filtered

        when {
            loading && channels == null -> LoadingBox()
            error != null && channels == null -> EmptyState("$error\n\nGeri dönüp tekrar deneyin")
            allChannels.isEmpty() -> EmptyState("Kanal bulunamadı")
            displayed.isEmpty() -> EmptyState("Sonuç bulunamadı")
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
                            // Uzun bas → kanal yönetimi (özel logo, gruba taşı, sıralama).
                            onLongClick = { manageChannel = it },
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

        // Kanal yönetimi dialog'u (Column kapsamındaki özelleştirme/sıra ile çalışır).
        manageChannel?.let { ch ->
            ChannelManageDialog(
                channel = ch,
                customization = customization,
                groupChannels = displayed,
                onAction = { updated ->
                    app.store.saveChannelCustomization(updated)
                    customVersion++
                },
                onDismiss = { manageChannel = null }
            )
        }
        }

        // Multi View: seçili listedeki kanallar 2/4 bölmeli ekranda aynı anda oynatılır.
        if (multiView) {
            MultiViewScreen(
                channels = displayed,
                profile = profile,
                panes = settings.multiViewPanes,
                onSelectChannel = { ch ->
                    multiView = false
                    scope.launch {
                        val list = allChannels
                        val idx = list.indexOfFirst { it.id == ch.id }
                        if (idx >= 0) {
                            PlaybackManager.playChannel(list, idx, profile)
                            onOpenPlayer()
                        }
                    }
                },
                onClose = { multiView = false }
            )
        }
    }
}

/**
 * Kanal yönetimi dialog'u: özel logo, özel gruba taşıma, manuel sıralama
 * (yukarı/aşağı) ve sıralama sıfırlama. Her eylem [onAction] ile anında kaydedilir.
 */
@Composable
private fun ChannelManageDialog(
    channel: Channel,
    customization: ChannelCustomization,
    groupChannels: List<Channel>,
    onAction: (ChannelCustomization) -> Unit,
    onDismiss: () -> Unit
) {
    val key = channel.id.toString()
    val currentGroup = ChannelCustomizer.groupOf(channel, customization)
    val inCustomGroup = customization.customGroups.any { it.name == currentGroup }
    val customLogos = customization.customLogos
    var logoUrl by remember(key) { mutableStateOf(customLogos[key].orEmpty()) }
    var epgId by remember(key) { mutableStateOf(customization.channelEpgIds[key].orEmpty()) }
    var newGroup by remember { mutableStateOf("") }

    // Geçerli grubun sıralaması: kayıtlı manuel sıralama yoksa özgün sıra.
    val currentOrder = customization.channelOrder[currentGroup] ?: groupChannels.map { it.id }
    val pos = currentOrder.indexOf(channel.id)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ChannelCustomizer.displayName(channel.name, customization), maxLines = 1) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Grup: $currentGroup",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Özel Logo", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = logoUrl,
                        onValueChange = { logoUrl = it },
                        label = { Text("Logo URL (boş = kaldır)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        val updated = customization.copy(
                            customLogos = if (logoUrl.isBlank()) customLogos - key else customLogos + (key to logoUrl.trim())
                        )
                        onAction(updated)
                    }) { Text("Uygula") }
                }

                Text("EPG Eşleştirme (xmltv_id)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Harici EPG'deki kanal kimliği ile manuel eşleştir (boş = otomatik).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = epgId,
                        onValueChange = { epgId = it.take(60) },
                        label = { Text("xmltv_id (örn. TRT1.tr)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        val updated = customization.copy(
                            channelEpgIds = if (epgId.isBlank()) customization.channelEpgIds - key
                            else customization.channelEpgIds + (key to epgId.trim())
                        )
                        onAction(updated)
                    }) { Text("Uygula") }
                }

                Text("Özel Gruba Taşı", style = MaterialTheme.typography.titleSmall)
                if (customization.customGroups.isNotEmpty()) {
                    customization.customGroups.forEach { g ->
                        val isCurrent = g.name == currentGroup
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                g.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (isCurrent) {
                                Text(
                                    "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                TextButton(onClick = {
                                    onAction(customization.copy(
                                        channelGroup = customization.channelGroup + (key to g.name)
                                    ))
                                }) { Text("Taşı") }
                            }
                        }
                    }
                } else {
                    Text(
                        "Henüz özel grup yok — aşağıdan bir tane oluştur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newGroup,
                        onValueChange = { newGroup = it.take(30) },
                        label = { Text("Yeni grup adı") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val name = newGroup.trim()
                            if (name.isNotBlank()) {
                                val g = CustomChannelGroup(
                                    id = "g_" + System.currentTimeMillis().toString(36),
                                    name = name
                                )
                                val updated = customization.copy(
                                    customGroups = customization.customGroups + g,
                                    channelGroup = customization.channelGroup + (key to name)
                                )
                                onAction(updated)
                                newGroup = ""
                            }
                        },
                        enabled = newGroup.trim().isNotBlank()
                    ) { Text("Oluştur & Taşı") }
                }
                if (inCustomGroup) {
                    TextButton(onClick = {
                        onAction(customization.copy(
                            channelGroup = customization.channelGroup - key
                        ))
                    }) { Text("Özel Gruptan Çıkar (orijinal gruba dön)") }
                }

                Text("Manuel Sıralama", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val list = currentOrder.toMutableList()
                            val p = list.indexOf(channel.id)
                            if (p > 0) {
                                list[p] = list[p - 1].also { list[p - 1] = list[p] }
                                onAction(customization.copy(
                                    channelOrder = customization.channelOrder + (currentGroup to list)
                                ))
                            }
                        },
                        enabled = pos > 0
                    ) { Text("↑ Yukarı") }
                    OutlinedButton(
                        onClick = {
                            val list = currentOrder.toMutableList()
                            val p = list.indexOf(channel.id)
                            if (p >= 0 && p < list.size - 1) {
                                list[p] = list[p + 1].also { list[p + 1] = list[p] }
                                onAction(customization.copy(
                                    channelOrder = customization.channelOrder + (currentGroup to list)
                                ))
                            }
                        },
                        enabled = pos >= 0 && pos < currentOrder.size - 1
                    ) { Text("↓ Aşağı") }
                    OutlinedButton(
                        onClick = {
                            onAction(customization.copy(
                                channelOrder = customization.channelOrder - currentGroup
                            ))
                        },
                        enabled = customization.channelOrder.containsKey(currentGroup)
                    ) { Text("Sıralamayı Sıfırla") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
    )
}
