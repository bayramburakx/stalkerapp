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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stalkerapp.ui.LiveTvScreen
import com.stalkerapp.ui.PlayerScreen
import com.stalkerapp.ui.VodDetailScreen
import com.stalkerapp.ui.home.HomeScreen
import com.stalkerapp.ui.login.LoginScreen
import com.stalkerapp.ui.theme.StalkerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "login") {
        composable("login") { LoginScreen(onConnected = { navController.navigate("home") { popUpTo("login") { inclusive = true } } }) }
        composable("home") { HomeScreen(onOpenPlayer = { navController.navigate("player") }, onOpenVod = { id -> navController.navigate("vod/$id") }) }
        composable("player") { PlayerScreen() }
        composable(
            route = "vod/{vodId}",
            arguments = listOf(navArgument("vodId") { type = NavType.LongType })
        ) { entry ->
            VodDetailScreen(
                vodId = entry.arguments?.getLong("vodId") ?: 0L,
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.navigate("player") }
            )
        }
    }
}
