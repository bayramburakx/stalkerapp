package com.stalkerapp

import android.Manifest
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
import androidx.compose.runtime.remember
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
    // Başlangıç ekranı: ilk açılışta profil oluşturma (onboarding); sonrasında
    // doğrudan Ana Sayfa — kaynaklar Ayarlar → Playlist & Kaynaklar'dan eklenir.
    val startDestination = remember {
        if (!app.store.isOnboardingDone()) "onboarding" else "home"
    }
    NavHost(navController = navController, startDestination = startDestination) {
        composable("onboarding") {
            OnboardingScreen(onDone = {
                // İlk açılışta kullanıcıyı doğrudan login'e zorlama; uygulama normal
                // açılsın, portalı isterse Ayarlar → Playlist & Kaynaklar'dan ekler.
                navController.navigate("home") { popUpTo("onboarding") { inclusive = true } }
            })
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
                    navController.navigate("onboarding") {
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
