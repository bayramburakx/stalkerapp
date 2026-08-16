package com.stalkerapp.ui.account

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.stalkerapp.data.FirebaseSyncManager
import com.stalkerapp.ui.theme.accentBrush
import kotlinx.coroutines.launch

/**
 * Firebase hesap ekranı: e-posta/şifre ile kayıt + giriş ve "Google ile devam et".
 * Giriş başarılı olunca bulut yedeği senkronlanır (ilk girişte yerel veri yüklenir,
 * sonrakilerde bulut verisi geri yüklenir) ve [onSignedIn] çağrılır.
 */
@Composable
fun LoginScreen(
    firebase: FirebaseSyncManager,
    onSignedIn: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf("login") } // "login" | "register"
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    val webClientId = remember { firebase.googleWebClientId() }
    val googleAvailable = webClientId.isNotBlank()

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(
                    ApiException::class.java
                )
                scope.launch {
                    busy = true
                    error = null
                    firebase.signInWithGoogle(account)
                        .onSuccess { completeSignIn(firebase, context, onSignedIn, scope) }
                        .onFailure { e -> error = e.message ?: "Google girişi başarısız" }
                    busy = false
                }
            } catch (e: ApiException) {
                error = "Google girişi iptal edildi veya başarısız"
            }
        } else {
            error = "Google girişi iptal edildi"
        }
    }

    fun launchGoogle() {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(context, options)
        googleLauncher.launch(client.signInIntent)
    }

    fun submit() {
        if (busy) return
        val mail = email.trim()
        when {
            mail.isEmpty() -> error = "E-posta adresi girin"
            password.isEmpty() -> error = "Şifre girin"
            mode == "register" && password.length < 6 -> error = "Şifre en az 6 karakter olmalı"
            mode == "register" && password != password2 -> error = "Şifreler eşleşmiyor"
            else -> {
                busy = true
                error = null
                info = null
                scope.launch {
                    val result = if (mode == "register") {
                        firebase.createAccount(mail, password)
                    } else {
                        firebase.signInWithEmail(mail, password)
                    }
                    result
                        .onSuccess { completeSignIn(firebase, context, onSignedIn, scope) }
                        .onFailure { e ->
                            error = when {
                                e.message?.contains("already in use") == true -> "Bu e-posta zaten kayıtlı — giriş yapın"
                                e.message?.contains("invalid-email") == true -> "Geçersiz e-posta adresi"
                                e.message?.contains("wrong-password") == true -> "Şifre hatalı"
                                e.message?.contains("user-not-found") == true -> "Bu e-posta ile kayıt bulunamadı"
                                e.message?.contains("invalid-credential") == true -> "E-posta veya şifre hatalı"
                                e.message?.contains("too-many-requests") == true -> "Çok fazla deneme — bir süre sonra tekrar deneyin"
                                else -> e.message ?: "İşlem başarısız"
                            }
                        }
                    busy = false
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(accentBrush())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (onBack != null) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (mode == "login") "Hesabına Giriş Yap" else "Hesap Oluştur",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Verilerin bulutta senkronlanır — başka cihazda kaldığın yerden devam et",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(32.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-posta") },
                    leadingIcon = { Icon(Icons.Filled.Email, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Şifre") },
                    leadingIcon = { Icon(Icons.Filled.Lock, null) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                if (mode == "register") {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password2,
                        onValueChange = { password2 = it },
                        label = { Text("Şifre (tekrar)") },
                        leadingIcon = { Icon(Icons.Filled.Lock, null) },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                if (info != null) {
                    Text(
                        text = info.orEmpty(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { submit() },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (mode == "login") "Giriş Yap" else "Kayıt Ol", fontWeight = FontWeight.Bold)
                    }
                }

                if (mode == "login") {
                    TextButton(onClick = {
                        if (email.isBlank()) { error = "Önce e-posta adresini girin"; return@TextButton }
                        scope.launch {
                            firebase.sendPasswordReset(email.trim())
                                .onSuccess { info = "Şifre sıfırlama bağlantısı e-postana gönderildi" }
                                .onFailure { e -> error = e.message ?: "Şifre sıfırlama başarısız" }
                        }
                    }) {
                        Text("Şifremi unuttum")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    Text(
                        " veya ",
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                }

                Button(
                    onClick = { launchGoogle() },
                    enabled = googleAvailable && !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        "Google ile devam et" + if (!googleAvailable) " (kurulum gerekli)" else "",
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!googleAvailable) {
                    Text(
                        "Google girişi henüz yapılandırılmadı. Firebase konsolunda Google\n" +
                            "oturumunu etkinleştirip Web client ID'yi doldur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))
                TextButton(onClick = { mode = if (mode == "login") "register" else "login"; error = null; info = null }) {
                    Text(
                        if (mode == "login") "Hesabın yok mu? Kayıt ol" else "Zaten hesabın var mı? Giriş yap",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Giriş başarılı → bulut yedeğini senkronla → onSignedIn.
 * Senkron başarısız olsa bile kullanıcı içeri alınır (çevrimdışı çalışma).
 */
private fun completeSignIn(
    firebase: FirebaseSyncManager,
    context: android.content.Context,
    onSignedIn: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val store = (context.applicationContext as com.stalkerapp.StalkerApp).store
    scope.launch {
        val result = firebase.syncAfterLogin(store)
        // Store verileri değiştiyse ViewModel akışlarını tazelemek gerekir;
        // LoginScreen onSignedIn ile Home'a dönerken MainViewModel baştan okur.
        onSignedIn()
    }
}
