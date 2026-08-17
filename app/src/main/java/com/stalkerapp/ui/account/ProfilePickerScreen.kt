package com.stalkerapp.ui.account

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stalkerapp.data.FirebaseSyncManager
import com.stalkerapp.data.UserProfile
import com.stalkerapp.ui.MainViewModel

private val AVATARS = listOf(
    "😀", "😎", "🦊", "🐼", "🐸", "🐙", "🦁", "🐯",
    "🚀", "🎬", "🍿", "🎮", "⚽", "🎵", "👾", "🤖"
)

/**
 * Netflix tarzı profil seçme ekranı. Giriş sonrası (ve oturum açıkken
 * uygulama açılışında) gösterilir: profil kartına dokununca içeri girilir.
 * "+" ile yeni profil eklenir; uzun basma/düzenleme ile silinebilir.
 */
@Composable
fun ProfilePickerScreen(
    vm: MainViewModel,
    firebase: FirebaseSyncManager,
    lang: String,
    onProfileSelected: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val profiles by vm.profiles.collectAsState()
    val activeId by vm.userProfile.collectAsState()
    fun t(text: String) = com.stalkerapp.util.L10n.t(lang, text)

    var editing by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<UserProfile?>(null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (onBack != null) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Geri"))
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    t("Kim izliyor?"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    t("Profili seç, kaldığın yerden devam et"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(40.dp))

                // Profil kartları — 2 sütunlu ızgara
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    profiles.take(2).forEach { p ->
                        ProfileCard(
                            lang = lang,
                            p = p,
                            isActive = p.id == activeId.id,
                            editing = editing,
                            onClick = {
                                if (p.id != activeId.id) vm.switchProfile(p.id)
                                onProfileSelected()
                            },
                            onEdit = { editTarget = p },
                            onDelete = { editTarget = p }
                        )
                        Spacer(Modifier.width(16.dp))
                    }
                }
                if (profiles.size > 2) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        profiles.drop(2).take(2).forEach { p ->
                            ProfileCard(
                                lang = lang,
                                p = p,
                                isActive = p.id == activeId.id,
                                editing = editing,
                                onClick = {
                                    if (p.id != activeId.id) vm.switchProfile(p.id)
                                    onProfileSelected()
                                },
                                onEdit = { editTarget = p },
                                onDelete = { editTarget = p }
                            )
                            Spacer(Modifier.width(16.dp))
                        }
                    }
                }
                if (profiles.size > 4) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        profiles.drop(4).take(2).forEach { p ->
                            ProfileCard(
                                lang = lang,
                                p = p,
                                isActive = p.id == activeId.id,
                                editing = editing,
                                onClick = {
                                    if (p.id != activeId.id) vm.switchProfile(p.id)
                                    onProfileSelected()
                                },
                                onEdit = { editTarget = p },
                                onDelete = { editTarget = p }
                            )
                            Spacer(Modifier.width(16.dp))
                        }
                    }
                }

                // "+ Profil Ekle" kartı
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    AddProfileCard(lang = lang, onClick = { showAddDialog = true })
                }

                Spacer(Modifier.height(32.dp))
                if (editing) {
                    OutlinedButton(onClick = { editing = false }) { Text(t("Bitir")) }
                } else {
                    OutlinedButton(onClick = { editing = true }) { Text(t("Profilleri Yönet")) }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "${t("Giriş")}: ${firebase.userEmail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showAddDialog) {
        AddProfileDialog(
            lang = lang,
            title = t("Yeni Profil"),
            onDismiss = { showAddDialog = false },
            onSave = { name, avatar ->
                vm.addProfile(name, avatar)
                showAddDialog = false
            }
        )
    }

    editTarget?.let { target ->
        EditProfileDialog(
            lang = lang,
            profile = target,
            onDismiss = { editTarget = null },
            onSave = { p ->
                vm.saveUserProfile(p)
                editTarget = null
            },
            onDelete = {
                vm.deleteProfile(target.id)
                editTarget = null
            }
        )
    }
}

@Composable
private fun ProfileCard(
    lang: String,
    p: UserProfile,
    isActive: Boolean,
    editing: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    fun t(text: String) = com.stalkerapp.util.L10n.t(lang, text)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    else Color.White.copy(alpha = 0.08f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(p.avatar.ifBlank { "😀" }, style = MaterialTheme.typography.displaySmall)
            if (isActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            if (editing) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Filled.Create, contentDescription = t("Düzenle"), modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            p.name.ifBlank { t("İzleyici") },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
        if (isActive) {
            Text(
                t("Aktif"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AddProfileCard(lang: String, onClick: () -> Unit) {
    fun t(text: String) = com.stalkerapp.util.L10n.t(lang, text)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(t("Profil Ekle"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddProfileDialog(
    lang: String,
    title: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    fun t(text: String) = com.stalkerapp.util.L10n.t(lang, text)
    var name by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf(AVATARS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onSave(name.trim().ifBlank { t("İzleyici") }, avatar) }) { Text(t("Kaydet")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Vazgeç")) } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text(t("Profil adı")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(t("Avatar seç"), style = MaterialTheme.typography.labelMedium)
                val rows = AVATARS.chunked(4)
                rows.forEach { rowAvatars ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowAvatars.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (emoji == avatar) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        else Color.White.copy(alpha = 0.08f)
                                    )
                                    .clickable { avatar = emoji },
                                contentAlignment = Alignment.Center
                            ) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun EditProfileDialog(
    lang: String,
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit,
    onDelete: () -> Unit
) {
    fun t(text: String) = com.stalkerapp.util.L10n.t(lang, text)
    var name by remember { mutableStateOf(profile.name) }
    var avatar by remember { mutableStateOf(profile.avatar.ifBlank { AVATARS.first() }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onSave(profile.copy(name = name.trim().ifBlank { t("İzleyici") }, avatar = avatar)) }) { Text(t("Kaydet")) }
        },
        dismissButton = {
            Row {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = t("Sil"), tint = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text(t("Vazgeç")) }
            }
        },
        title = { Text(t("Profili Düzenle")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text(t("Profil adı")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(t("Avatar seç"), style = MaterialTheme.typography.labelMedium)
                val rows = AVATARS.chunked(4)
                rows.forEach { rowAvatars ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowAvatars.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (emoji == avatar) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        else Color.White.copy(alpha = 0.08f)
                                    )
                                    .clickable { avatar = emoji },
                                contentAlignment = Alignment.Center
                            ) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
                        }
                    }
                }
            }
        }
    )
}
