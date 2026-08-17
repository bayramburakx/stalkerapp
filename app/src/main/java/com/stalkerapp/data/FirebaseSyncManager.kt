package com.stalkerapp.data

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.stalkerapp.R
import com.stalkerapp.StalkerApp
import com.stalkerapp.util.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Firebase tabanlı hesap + bulut yedek senkronu.
 *
 * Model (Stremio tarzı): hesap = tüm veriler bulutta.
 *  - İlk girişte (bulutta veri yoksa) yerel veri buluta yüklenir.
 *  - Sonraki girişlerde bulut verisi yerel verinin yerine geri yüklenir
 *    ("kaldığı yerden devam" — başka cihazda aynı hesaba girilince her şey gelir).
 *  - Oturum açıkken uygulama arka plana geçince veriler otomatik buluta yazılır.
 *
 * Google girişi için web client ID gereklidir (Firebase konsolu →
 * Authentication → Sign-in method → Google → "Web SDK configuration").
 * Boşsa Google butonu devre dışıdır; e-posta/şifre her koşulda çalışır.
 */
class FirebaseSyncManager(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    private val _syncState = MutableStateFlow("")
    /** Son senkron durumu (başarılı yedekleme/geri yükleme mesajı). */
    val syncState: StateFlow<String> = _syncState

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
        }
    }

    val isSignedIn: Boolean get() = auth.currentUser != null

    val userEmail: String get() = auth.currentUser?.email.orEmpty()

    /**
     * Google Sign-In için web client ID (strings.xml → google_web_client_id).
     * Firebase konsolu → Authentication → Google → "Web SDK configuration"
     * kısmındaki değer yapıştırılır. Boşsa Google butonu devre dışı kalır.
     */
    fun googleWebClientId(): String =
        context.getString(R.string.google_web_client_id).ifBlank { "" }

    // ---------- E-posta / şifre ----------

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> =
        withContext(Dispatchers.IO) {
            runCatching { auth.signInWithEmailAndPassword(email.trim(), password).await().user!! }
        }

    suspend fun createAccount(email: String, password: String): Result<FirebaseUser> =
        withContext(Dispatchers.IO) {
            runCatching { auth.createUserWithEmailAndPassword(email.trim(), password).await().user!! }
        }

    suspend fun sendPasswordReset(email: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { auth.sendPasswordResetEmail(email.trim()).await(); Unit }
        }

    /** Google hesabından gelen idToken ile Firebase oturumu açar. */
    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<FirebaseUser> =
        withContext(Dispatchers.IO) {
            runCatching {
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential).await().user!!
            }
        }

    fun signOut() {
        auth.signOut()
        GoogleSignIn.getClient(
            context,
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            ).build()
        ).signOut()
        _syncState.value = ""
    }

    // ---------- Firestore yedek senkronu ----------

    private fun backupDoc(uid: String) = firestore.collection("users").document(uid).collection("backup").document("main")

    /**
     * Giriş sonrası senkron:
     *  1. Hesap deposuna geçilir (setAccount) — her hesabın kendi verisi olur.
     *  2. Bulutta veri varsa geri yükle ("kaldığı yerden devam").
     *  3. Yoksa: giriş öncesi misafir depodaki veri (varsa) hesaba taşınır ve buluta yazılır.
     */
    suspend fun syncAfterLogin(store: Store): SyncResult = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext SyncResult.NotSignedIn
        runCatching {
            // Misafir depodaki veriyi al (henüz hesaba geçmeden).
            val guestJson = store.backupJson()
            val guestHasData = store.guestHasData()

            // Hesap deposuna geç.
            store.setAccount(uid)

            val doc = backupDoc(uid).get().await()
            val cloudJson = doc?.getString("json")
            if (cloudJson.isNullOrBlank()) {
                // İlk giriş: misafir veri varsa hesaba taşı, yoksa boş başla.
                val json = if (guestHasData) guestJson else store.backupJson()
                if (guestHasData) store.restoreJson(guestJson)
                backupDoc(uid).set(
                    mapOf(
                        "json" to json,
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "appVersion" to 1
                    ),
                    SetOptions.merge()
                ).await()
                SyncResult.Pushed
            } else {
                // Bulut verisi baskın: geri yükle ("kaldığı yerden devam").
                val ok = store.restoreJson(cloudJson)
                if (ok) SyncResult.Restored else SyncResult.Failed(L10n.t(store.settings().language, "Yedek geçersiz"))
            }
        }.getOrElse { SyncResult.Failed(it.message ?: L10n.t(store.settings().language, "Senkron hatası")) }
    }

    /** Oturum açıkken yerel veriyi buluta yazar (çıkış/arka plan öncesi). */
    suspend fun pushBackup(store: Store): Boolean = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext false
        runCatching {
            backupDoc(uid).set(
                mapOf(
                    "json" to store.backupJson(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "appVersion" to 1
                ),
                SetOptions.merge()
            ).await()
            _syncState.value = L10n.t(store.settings().language, "Yedek buluta kaydedildi ✓")
            true
        }.getOrDefault(false)
    }

    /** Bulut verisini yerel veriyle değiştirir (hesap ekranındaki "geri yükle" butonu). */
    suspend fun restoreFromCloud(store: Store): Boolean = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext false
        runCatching {
            val json = backupDoc(uid).get().await()?.getString("json")
            if (json.isNullOrBlank()) {
                _syncState.value = L10n.t(store.settings().language, "Bulutta yedek yok")
                false
            } else {
                val ok = store.restoreJson(json)
                _syncState.value = if (ok) L10n.t(store.settings().language, "Bulut yedeği geri yüklendi ✓") else L10n.t(store.settings().language, "Yedek geçersiz")
                ok
            }
        }.getOrDefault(false)
    }

    /** Buluttaki yedek zaman damgası (konsolda görüntüleme için). */
    suspend fun lastBackupTime(): Long? = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext null
        runCatching {
            val ts = backupDoc(uid).get().await()?.getTimestamp("updatedAt") ?: return@runCatching null
            ts.toDate().time
        }.getOrDefault(null)
    }

    sealed class SyncResult {
        data object NotSignedIn : SyncResult()
        data object Pushed : SyncResult()
        data object Restored : SyncResult()
        data class Failed(val message: String) : SyncResult()
    }

    companion object {
        /** Uygulama genelinde erişim (StalkerApp içinde init edilir). */
        lateinit var instance: FirebaseSyncManager
            private set

        fun init(context: Context): FirebaseSyncManager {
            if (!::instance.isInitialized) {
                instance = FirebaseSyncManager(context.applicationContext)
            }
            return instance
        }

        /** StalkerApp'ten kısa erişim. */
        val current: FirebaseSyncManager
            get() = instance
    }
}

/** FirebaseSyncManager'a StalkerApp üzerinden erişim. */
fun StalkerApp.firebase(): FirebaseSyncManager = FirebaseSyncManager.instance
