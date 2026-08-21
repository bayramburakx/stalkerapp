package com.stalkerapp.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.stalkerapp.data.FirebaseSyncManager
import com.stalkerapp.ui.components.GlassSurface
import com.stalkerapp.ui.components.PortioButton
import com.stalkerapp.ui.components.PortioButtonStyle
import com.stalkerapp.ui.components.PortioPrimaryButton
import com.stalkerapp.ui.components.PortioSecondaryButton
import com.stalkerapp.ui.components.PortioTextField
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.ui.theme.PortioShape
import com.stalkerapp.util.L10n
import kotlinx.coroutines.launch

/**
 * Portio Giriş & Kayıt Ekranı (Firebase Auth + Google Sign-In)
 */
@Composable
fun LoginScreen(
    firebase: FirebaseSyncManager,
    lang: String,
    onSignedIn: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun t(text: String) = L10n.t(lang, text)

    var mode by remember { mutableStateOf("login") }
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
                        .onFailure { e -> error = e.message ?: t("Google girişi başarısız") }
                    busy = false
                }
            } catch (e: ApiException) {
                error = when (e.statusCode) {
                    10 -> t("Google yapılandırma hatası: Firebase'e SHA-1 parmak izi ve Google oturumu eklenmiş olmalı")
                    12500 -> t("Google sunucu hatası — Firebase konsolunda Google oturum açma etkin mi kontrol et")
                    12501 -> t("Google girişi iptal edildi")
                    else -> t("Google girişi başarısız") + " (kod ${e.statusCode})"
                }
            }
        } else {
            error = t("Google girişi iptal edildi")
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
            mail.isEmpty() -> error = t("E-posta adresi girin")
            password.isEmpty() -> error = t("Şifre girin")
            mode == "register" && password.length < 6 -> error = t("Şifre en az 6 karakter olmalı")
            mode == "register" && password != password2 -> error = t("Şifreler eşleşmiyor")
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
                                e.message?.contains("already in use") == true -> t("Bu e-posta zaten kayıtlı — giriş yapın")
                                e.message?.contains("invalid-email") == true -> t("Geçersiz e-posta adresi")
                                e.message?.contains("wrong-password") == true -> t("Şifre hatalı")
                                e.message?.contains("user-not-found") == true -> t("Bu e-posta ile kayıt bulunamadı")
                                e.message?.contains("invalid-credential") == true -> t("E-posta veya şifre hatalı")
                                e.message?.contains("too-many-requests") == true -> t("Çok fazla deneme — bir süre sonra tekrar deneyin")
                                else -> e.message ?: t("İşlem başarısız")
                            }
                        }
                    busy = false
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 500.dp)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (onBack != null) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = t("Geri"),
                                tint = Color.White
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                ) {
                    Image(
                        painter = painterResource(com.stalkerapp.R.drawable.portio_logo),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (mode == "login") t("Hesabına Giriş Yap") else t("Hesap Oluştur"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = t("Verilerin bulutta senkronlanır — başka cihazda kaldığın yerden devam et"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.height(32.dp))

                GlassSurface(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PortioTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = t("E-posta"),
                            leadingIcon = { Icon(Icons.Filled.Email, null, tint = Color.White.copy(alpha = 0.7f)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                        PortioTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = t("Şifre"),
                            leadingIcon = { Icon(Icons.Filled.Lock, null, tint = Color.White.copy(alpha = 0.7f)) },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                        if (mode == "register") {
                            PortioTextField(
                                value = password2,
                                onValueChange = { password2 = it },
                                label = t("Şifre (tekrar)"),
                                leadingIcon = { Icon(Icons.Filled.Lock, null, tint = Color.White.copy(alpha = 0.7f)) },
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )
                        }

                        if (error != null) {
                            Text(
                                text = error.orEmpty(),
                                color = PortioColors.Error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        if (info != null) {
                            Text(
                                text = info.orEmpty(),
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        PortioPrimaryButton(
                            text = if (mode == "login") t("Giriş Yap") else t("Kayıt Ol"),
                            onClick = { submit() },
                            loading = busy,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        )

                        if (mode == "login") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(PortioShape.CardSmall)
                                    .clickable {
                                        if (email.isBlank()) { error = t("Önce e-posta adresini girin"); return@clickable }
                                        scope.launch {
                                            firebase.sendPasswordReset(email.trim())
                                                .onSuccess { info = t("Şifre sıfırlama bağlantısı e-postana gönderildi") }
                                                .onFailure { e -> error = e.message ?: t("Şifre sıfırlama başarısız") }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    t("Şifremi unuttum"),
                                    color = Color.White.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = PortioColors.Hairline)
                            Text(
                                " ${t("veya")} ",
                                modifier = Modifier.padding(horizontal = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = PortioColors.Hairline)
                        }

                        PortioSecondaryButton(
                            text = if (!googleAvailable) t("Google ile devam et (kurulum gerekli)") else t("Google ile devam et"),
                            onClick = { launchGoogle() },
                            enabled = googleAvailable && !busy,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        )
                        if (!googleAvailable) {
                            Text(
                                t("Google girişi henüz yapılandırılmadı. Firebase konsolunda Google oturumunu etkinleştirip Web client ID'yi doldur."),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PortioShape.CardSmall)
                        .clickable { mode = if (mode == "login") "register" else "login"; error = null; info = null }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (mode == "login") t("Hesabın yok mu? Kayıt ol") else t("Zaten hesabın var mı? Giriş yap"),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(10.dp))
                PortioButton(
                    onClick = onSignedIn,
                    style = PortioButtonStyle.Glass,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text(
                        t("Giriş Yapmadan Devam Et (Misafir)"),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun completeSignIn(
    firebase: FirebaseSyncManager,
    context: android.content.Context,
    onSignedIn: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val store = (context.applicationContext as com.stalkerapp.StalkerApp).store
    scope.launch {
        firebase.syncAfterLogin(store)
        onSignedIn()
    }
}
