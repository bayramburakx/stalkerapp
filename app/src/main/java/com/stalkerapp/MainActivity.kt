package com.stalkerapp

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stalkerapp.StalkerApp
import com.stalkerapp.ui.PlayerScreen
import com.stalkerapp.ui.home.HomeScreen
import com.stalkerapp.ui.login.LoginScreen
import com.stalkerapp.ui.onboarding.OnboardingScreen
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
    // Başlangıç ekranı: ilk açılışta onboarding; kayıtlı profil varsa doğrudan
    // Ana Sayfa (otomatik giriş, login ekranı atlanır); yoksa login.
    val startDestination = remember {
        when {
            !app.store.isOnboardingDone() -> "onboarding"
            app.store.activePortal() != null &&
                app.store.loadProfile(app.store.activePortalId().orEmpty()) != null -> "home"
            else -> "login"
        }
    }
    NavHost(navController = navController, startDestination = startDestination) {
        composable("onboarding") {
            OnboardingScreen(onDone = { navController.navigate("login") { popUpTo("onboarding") { inclusive = true } } })
        }
        composable("login") { LoginScreen(onConnected = { navController.navigate("home") { popUpTo("login") { inclusive = true } } }) }
        composable("home") { HomeScreen(onOpenPlayer = { navController.navigate("player") }, onOpenVod = { id, series -> navController.navigate("vod/$id?series=$series") }, onOpenSearch = { navController.navigate("search") }) }
        composable("search") { SearchScreen(onBack = { navController.popBackStack() }, onOpenVod = { id, series -> navController.navigate("vod/$id?series=$series") }, onOpenPlayer = { navController.navigate("player") }) }
        composable("player") { PlayerScreen(navController) }
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
                onOpenPlayer = { navController.navigate("player") }
            )
        }
    }
}
