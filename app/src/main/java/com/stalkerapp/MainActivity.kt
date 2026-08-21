package com.stalkerapp

import android.Manifest
import android.app.Activity
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.FirebaseSyncManager
import com.stalkerapp.data.UpdateChecker
import com.stalkerapp.data.UpdateInfo
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.components.ToastHost
import com.stalkerapp.ui.components.UpdateDialog
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.screens.ContentDetailScreen
import com.stalkerapp.ui.screens.EpgScreen
import com.stalkerapp.ui.screens.FavoritesScreen
import com.stalkerapp.ui.screens.HomeScreen
import com.stalkerapp.ui.screens.LoginScreen
import com.stalkerapp.ui.screens.PersonDetailScreen
import com.stalkerapp.ui.screens.PlayerScreen
import com.stalkerapp.ui.screens.ProfileSelectScreen
import com.stalkerapp.ui.screens.SearchScreen
import com.stalkerapp.ui.screens.SettingsScreen
import com.stalkerapp.ui.screens.OnboardingScreen
import com.stalkerapp.ui.theme.PortioTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PortioTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NotificationPermission()
                        AppNav()
                        ToastHost()
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PlaybackManager.isPlaying()) {
            PlaybackManager.enterPip(this)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode && !PlaybackManager.isCasting() && !PlaybackManager.isBackgroundPlaybackEnabled()) {
            PlaybackManager.pause()
        }
        if (FirebaseSyncManager.current.isSignedIn) {
            val store = (applicationContext as StalkerApp).store
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

    fun safeBack() {
        if (!navController.popBackStack()) {
            navController.navigate("home") {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

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

    val firebase = remember { FirebaseSyncManager.init(app) }

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    val updateContext = LocalContext.current
    LaunchedEffect(Unit) {
        val store = app.store
        val info = runCatching { UpdateChecker().latest() }.getOrNull() ?: return@LaunchedEffect
        if (!UpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME)) return@LaunchedEffect
        if (store.updateSkipVersion() == info.version) return@LaunchedEffect
        if (System.currentTimeMillis() < store.updateRemindTs()) return@LaunchedEffect
        updateInfo = info
    }
    updateInfo?.let { info: UpdateInfo ->
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
                    vm.refreshFlows()
                    navController.navigate("profiles") { popUpTo("login") { inclusive = true } }
                },
                onBack = { safeBack() }
            )
        }
        composable("profiles") {
            ProfileSelectScreen(
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
                modifier = Modifier,
                onBack = { safeBack() }
            )
        }
        composable("home") {
            HomeScreen(
                onOpenPlayer = { navController.navigate("player") },
                onOpenVod = { id, series -> navController.navigate("vod/$id?series=$series") },
                onOpenSearch = { navController.navigate("search") },
                onOpenGuide = { navController.navigate("epg") },
                onOpenOnboarding = {
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
            EpgScreen(
                profile = (LocalContext.current.applicationContext as StalkerApp).repository.cachedProfile(),
                onBack = { safeBack() },
                onOpenPlayer = { navController.navigate("player") }
            )
        }
        composable("settings") {
            SettingsScreen(
                vm = vm,
                onOpenPlayer = { navController.navigate("player") },
                onOpenProfiles = {
                    navController.navigate("profiles") {
                        popUpTo("settings") { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onBack = { safeBack() }
            )
        }
        composable("search") {
            SearchScreen(
                onBack = { safeBack() },
                onOpenVod = { id, series -> navController.navigate("vod/$id?series=$series") },
                onOpenPlayer = { navController.navigate("player") }
            )
        }
        composable("player") { PlayerScreen(navController) }
        composable(
            route = "person/{name}?isDirector={isDirector}",
            arguments = listOf(
                navArgument("name") { type = NavType.StringType },
                navArgument("isDirector") { type = NavType.BoolType; defaultValue = false }
            )
        ) { entry ->
            PersonDetailScreen(
                personName = Uri.decode(entry.arguments?.getString("name").orEmpty()),
                isDirector = entry.arguments?.getBoolean("isDirector") ?: false,
                onBack = { safeBack() },
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
            ContentDetailScreen(
                vodId = entry.arguments?.getLong("vodId") ?: 0L,
                isSeriesHint = entry.arguments?.getBoolean("series") ?: false,
                onBack = { safeBack() },
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
