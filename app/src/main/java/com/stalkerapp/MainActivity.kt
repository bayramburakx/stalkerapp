package com.stalkerapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stalkerapp.StalkerApp
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.PlayerScreen
import com.stalkerapp.ui.account.LoginScreen
import com.stalkerapp.ui.account.ProfilePickerScreen
import com.stalkerapp.data.FirebaseSyncManager
import com.stalkerapp.ui.favorites.FavoritesScreen
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.home.HomeScreen
import com.stalkerapp.ui.live.EpgGuideScreen
import com.stalkerapp.ui.onboarding.OnboardingScreen
import com.stalkerapp.ui.person.PersonScreen
import com.stalkerapp.ui.search.SearchScreen
import com.stalkerapp.ui.theme.StalkerTheme
import com.stalkerapp.ui.vod.VodDetailScreen
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.data.UpdateChecker
import com.stalkerapp.data.UpdateInfo
import com.stalkerapp.ui.components.UpdateDialog
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StalkerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NotificationPermission()
                    AppNav()
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PlaybackManager.enterPip(this)
    }

    override fun onStop() {
        super.onStop()
        // "Arka Planda Oynatmaya Devam Et" kapalıysa ve PiP'te değilsek (örn.
        // ev tuşuyla çıkış) oynatmayı duraklat — medya sessizce çalıp durmasın.
        // Yayın (Chromecast) sırasında atlanır: TV'deki oynatma etkilenmemeli.
        if (!isInPictureInPictureMode && !PlaybackManager.isCasting() && !PlaybackManager.isBackgroundPlaybackEnabled()) {
            PlaybackManager.pause()
        }
        // Oturum açıksa yapılan değişiklikleri buluta yaz (diğer cihazlara taşır).
        if (FirebaseSyncManager.current.isSignedIn) {
            val store = (applicationContext as StalkerApp).store
            // pushBackup suspend olduğu için bir coroutine içinde çağrılır.
            val appScope = (applicationContext as StalkerApp).appScope
            appScope.launch { FirebaseSyncManager.current.pushBackup(store) }
        }
    }
}

@Composable
private fun NotificationPermission() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun AppNav() {
    val app = LocalContext.current.applicationContext as StalkerApp
    val navController = rememberNavController()
    val vm: MainViewModel = rememberMainViewModel(app)
    // Ana ekran widget'ından gelen kanal: uygulama açılır açılmaz o kanal oynatılır.
    val activity = LocalContext.current as? Activity
    val widgetChannelId = remember {
        activity?.intent?.getLongExtra(com.stalkerapp.widget.FavoritesWidgetProvider.EXTRA_PLAY_CHANNEL, -1L) ?: -1L
    }
    LaunchedEffect(widgetChannelId) {
        if (widgetChannelId > 0) {
            vm.playChannelById(widgetChannelId)
            navController.navigate("player") { launchSingleTop = true }
        }
    }
    // Firebase hesap yönetimi (giriş/çıkış için uygulama genelinde kullanılır).
    val firebase = remember { FirebaseSyncManager.init(app) }

    // Uygulama içi güncelleme: açılışta bir kez kontrol edilir. Yeni sürüm
    // varsa pop-up gösterilir — "Şimdi Güncelle" / "Sonra Hatırlat" / "Bir Daha Sorma".
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    val updateContext = LocalContext.current
    LaunchedEffect(Unit) {
        val store = app.store
        val info = runCatching { UpdateChecker().latest() }.getOrNull() ?: return@LaunchedEffect
        if (!UpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME)) return@LaunchedEffect
        // "Bir daha sorma" denmişse bu sürüm tekrar sorulmaz; "sonra hatırlat"
        // süresi dolmadıysa beklenir.
        if (store.updateSkipVersion() == info.version) return@LaunchedEffect
        if (System.currentTimeMillis() < store.updateRemindTs()) return@LaunchedEffect
        updateInfo = info
    }
    updateInfo?.let { info ->
        UpdateDialog(
            info = info,
            lang = app.store.settings().language,
            onDismiss = { updateInfo = null },
            onUpdateNow = {
                updateInfo = null
                runCatching {
                    updateContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
                }
            },
            onRemindLater = {
                updateInfo = null
                app.store.setUpdateRemindTs(System.currentTimeMillis() + 24 * 60 * 60 * 1000L)
            },
            onNeverAsk = {
                updateInfo = null
                app.store.setUpdateSkipVersion(info.version)
            }
        )
    }

    // Başlangıç ekranı: ilk açılışta onboarding; sonrasında oturum varsa
    // Netflix tarzı profil seçici, yoksa Giriş ekranı.
    val startDestination = remember {
        when {
            !app.store.isOnboardingDone() -> "onboarding"
            firebase.isSignedIn -> "profiles"
            else -> "login"
        }
    }
    NavHost(navController = navController, startDestination = startDestination) {
        composable("onboarding") {
            OnboardingScreen(onDone = {
                // Onboarding sonrası: hesabı olan profil seçiciye, olmayan giriş ekranına gider.
                if (firebase.isSignedIn) {
                    navController.navigate("profiles") { popUpTo("onboarding") { inclusive = true } }
                } else {
                    navController.navigate("login") { popUpTo("onboarding") { inclusive = true } }
                }
            })
        }
        composable("login") {
            LoginScreen(
                firebase = firebase,
                lang = app.store.settings().language,
                onSignedIn = {
                    // Bulut yedeği geri yüklendiyse Store değişti; akışları tazele.
                    vm.refreshFlows()
                    navController.navigate("profiles") { popUpTo("login") { inclusive = true } }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("profiles") {
            ProfilePickerScreen(
                vm = vm,
                firebase = firebase,
                lang = app.store.settings().language,
                onProfileSelected = {
                    navController.navigate("home") { popUpTo("profiles") { inclusive = true } }
                }
            )
        }
        composable("favorites") {
            FavoritesScreen(
                profile = (LocalContext.current.applicationContext as StalkerApp).repository.cachedProfile(),
                onOpenPlayer = { navController.navigate("player") },
                onOpenVod = { id, series -> navController.navigate("vod/$id?series=$series") },
                modifier = Modifier
            )
        }
        composable("home") {
            HomeScreen(
                onOpenPlayer = { navController.navigate("player") },
                onOpenVod = { id, series -> navController.navigate("vod/$id?series=$series") },
                onOpenSearch = { navController.navigate("search") },
                onOpenGuide = { navController.navigate("epg") },
                onOpenOnboarding = {
                    // Oturum kapalıysa giriş ekranına, açıksa kurulum sihirbazına.
                    if (firebase.isSignedIn) {
                        navController.navigate("onboarding") {
                            popUpTo("home") { inclusive = false }
                            launchSingleTop = true
                        }
                    } else {
                        navController.navigate("login") {
                            popUpTo("home") { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                onOpenProfiles = {
                    navController.navigate("profiles") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable("epg") {
            EpgGuideScreen(
                profile = (LocalContext.current.applicationContext as StalkerApp).repository.cachedProfile(),
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.navigate("player") }
            )
        }
        composable("search") { SearchScreen(onBack = { navController.popBackStack() }, onOpenVod = { id, series -> navController.navigate("vod/$id?series=$series") }, onOpenPlayer = { navController.navigate("player") }) }
        composable("player") { PlayerScreen(navController) }
        composable(
            route = "person/{name}?isDirector={isDirector}",
            arguments = listOf(
                navArgument("name") { type = NavType.StringType },
                navArgument("isDirector") { type = NavType.BoolType; defaultValue = false }
            )
        ) { entry ->
            PersonScreen(
                name = Uri.decode(entry.arguments?.getString("name").orEmpty()),
                isDirector = entry.arguments?.getBoolean("isDirector") ?: false,
                onBack = { navController.popBackStack() },
                onOpenVod = { id, series -> navController.navigate("vod/$id?series=$series") }
            )
        }
        composable(
            route = "vod/{vodId}?series={series}",
            arguments = listOf(
                navArgument("vodId") { type = NavType.LongType },
                navArgument("series") { type = NavType.BoolType; defaultValue = false }
            )
        ) { entry ->
            VodDetailScreen(
                vodId = entry.arguments?.getLong("vodId") ?: 0L,
                isSeriesHint = entry.arguments?.getBoolean("series") ?: false,
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.navigate("player") },
                onOpenVod = { id, series ->
                    navController.navigate("vod/$id?series=$series") { launchSingleTop = true }
                },
                onOpenPerson = { name, isDirector ->
                    navController.navigate("person/${Uri.encode(name)}?isDirector=$isDirector")
                }
            )
        }
    }
}
