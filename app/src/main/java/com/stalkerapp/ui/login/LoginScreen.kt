package com.stalkerapp.ui.login

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Portal
import com.stalkerapp.data.PortalStatus
import com.stalkerapp.data.StalkerClient
import com.stalkerapp.ui.MainViewModel
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun LoginScreen(onConnected: () -> Unit) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = viewModel { MainViewModel(app) }
    val scope = rememberCoroutineScope()
    val status by vm.portalStatus.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }

    fun fillForm(p: Portal) {
        editingId = p.id
        name = p.name
        url = p.url
        mac = p.mac
        username = p.username
        password = p.password
    }

    fun errorText(t: Throwable?): String =
        t?.message?.takeIf { it.isNotBlank() }
            ?: t?.javaClass?.simpleName
            ?: "Bağlantı hatası"

    LaunchedEffect(Unit) {
        val active = vm.store.activePortal()
        if (active != null && status is PortalStatus.Idle) {
            val result = vm.connect(active)
            if (result.isSuccess) onConnected()
            else snackbar.showSnackbar(errorText(result.exceptionOrNull()))
        }
    }

    LaunchedEffect(vm.statusMessage) {
        vm.statusMessage.value?.let {
            snackbar.showSnackbar(it)
            vm.showMessage(null)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Stalker Portal Player",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Çoklu profil desteği. Portal URL'nizi ve cihaz MAC adresinizi girin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val portals = vm.store.portals()
            if (portals.isNotEmpty()) {
                Text("Kayıtlı Portallar", style = MaterialTheme.typography.titleMedium)
                portals.forEach { p ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { fillForm(p) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    p.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "MAC: ${p.mac.ifBlank { "otomatik" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { vm.deletePortal(p.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Sil")
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        editingId = null
                        name = ""; url = ""; mac = ""; username = ""; password = ""
                    }
                ) { Text("Yeni Portal") }
            }

            Text("Portal Ekle / Düzenle", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Portal Adı") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Portal URL (http://ip:port/portal)") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it.uppercase() },
                    label = { Text("MAC Adresi (opsiyonel)") },
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { mac = StalkerClient.generateMac() }) { Text("Yeni MAC") }
            }
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Kullanıcı Adı (opsiyonel)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Şifre (opsiyonel)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (status is PortalStatus.Connecting) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("Bağlanılıyor…")
                    }
                } else {
                    Button(
                        onClick = {
                            if (name.isBlank() || url.isBlank()) {
                                scope.launch { snackbar.showSnackbar("Portal adı ve URL zorunludur") }
                                return@Button
                            }
                            scope.launch {
                                val portal = Portal(
                                    id = editingId ?: UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    url = StalkerClient.normalizeBase(url.trim()),
                                    mac = mac.trim(),
                                    username = username.trim(),
                                    password = password
                                )
                                vm.savePortal(portal)
                                val result = vm.connect(portal)
                                if (result.isSuccess) onConnected()
                                else snackbar.showSnackbar(errorText(result.exceptionOrNull()))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Kaydet ve Bağlan")
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
