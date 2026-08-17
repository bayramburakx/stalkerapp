package com.stalkerapp.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import com.stalkerapp.data.CustomChannelGroup
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
    // Kanal yönetimi: özelleştirme sürümü değişince liste yeniden okunur (uzun bas → yönet).
    var customVersion by remember { mutableStateOf(0) }
    var manageChannel by remember { mutableStateOf<Channel?>(null) }
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

        // Aktif kaynak rozeti (Stalker dışındaki kaynaklar için).
        if (isExternal && sourceName != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    t("Kaynak") + ": " + sourceName.ifBlank { if (kind == "m3u") "M3U" else "Xtream" },
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
            loading && channels == null -> LoadingBox()
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
                lang = lang,
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
    }
}

/**
 * Kanal yönetimi dialog'u: özel logo, EPG eşleştirme, özel gruba taşıma,
 * manuel sıralama (yukarı/aşağı) ve sıralama sıfırlama. Her eylem [onAction]
 * ile anında kaydedilir. Uygulamanın cam (glass) diline uygun tasarlanmıştır:
 * yarı saydam scrim, yuvarlatılmış yüzey, bölüm başlıkları ve ince çerçeve.
 */
@Composable
private fun ChannelManageDialog(
    lang: String,
    channel: Channel,
    customization: ChannelCustomization,
    groupChannels: List<Channel>,
    onAction: (ChannelCustomization) -> Unit,
    onDismiss: () -> Unit
) {
    fun t(text: String) = com.stalkerapp.util.L10n.t(lang, text)
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

    val dialogShape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .shadow(24.dp, dialogShape)
                .clip(dialogShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f), dialogShape)
                .padding(vertical = 20.dp, horizontal = 18.dp)
        ) {
            // Başlık: kanal adı + mevcut grup
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        ChannelCustomizer.displayName(channel.name, customization),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${t("Grup")}: $currentGroup",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = t("Kapat"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )

            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SectionHeader(t("Özel Logo"), MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = logoUrl,
                        onValueChange = { logoUrl = it },
                        label = { Text(t("Logo URL (boş = kaldır)")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        val updated = customization.copy(
                            customLogos = if (logoUrl.isBlank()) customLogos - key else customLogos + (key to logoUrl.trim())
                        )
                        onAction(updated)
                    }) { Text(t("Uygula")) }
                }

                SectionHeader(t("EPG Eşleştirme (xmltv_id)"), MaterialTheme.colorScheme.primary)
                Text(
                    t("Harici EPG'deki kanal kimliği ile manuel eşleştir (boş = otomatik)."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = epgId,
                        onValueChange = { epgId = it.take(60) },
                        label = { Text(t("xmltv_id (örn. TRT1.tr)")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        val updated = customization.copy(
                            channelEpgIds = if (epgId.isBlank()) customization.channelEpgIds - key
                            else customization.channelEpgIds + (key to epgId.trim())
                        )
                        onAction(updated)
                    }) { Text(t("Uygula")) }
                }

                SectionHeader(t("Özel Gruba Taşı"), MaterialTheme.colorScheme.primary)
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
                                OutlinedButton(onClick = {
                                    onAction(customization.copy(
                                        channelGroup = customization.channelGroup + (key to g.name)
                                    ))
                                }) { Text(t("Taşı")) }
                            }
                        }
                    }
                } else {
                    Text(
                        t("Henüz özel grup yok — aşağıdan bir tane oluştur."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newGroup,
                        onValueChange = { newGroup = it.take(30) },
                        label = { Text(t("Yeni grup adı")) },
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
                    ) { Text(t("Oluştur & Taşı")) }
                }
                if (inCustomGroup) {
                    OutlinedButton(onClick = {
                        onAction(customization.copy(
                            channelGroup = customization.channelGroup - key
                        ))
                    }) { Text(t("Özel Gruptan Çıkar (orijinal gruba dön)")) }
                }

                SectionHeader(t("Manuel Sıralama"), MaterialTheme.colorScheme.primary)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                        enabled = pos > 0,
                        modifier = Modifier.weight(1f)
                    ) { Text("↑ " + t("Yukarı")) }
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
                        enabled = pos >= 0 && pos < currentOrder.size - 1,
                        modifier = Modifier.weight(1f)
                    ) { Text("↓ " + t("Aşağı")) }
                    OutlinedButton(
                        onClick = {
                            onAction(customization.copy(
                                channelOrder = customization.channelOrder - currentGroup
                            ))
                        },
                        enabled = customization.channelOrder.containsKey(currentGroup),
                        modifier = Modifier.weight(1f)
                    ) { Text(t("Sıralamayı Sıfırla")) }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(t("Kapat"), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}
