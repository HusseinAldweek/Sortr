package com.sortr.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sortr.app.ui.home.HomeScreen
import com.sortr.app.ui.permission.MediaAccess
import com.sortr.app.ui.settings.SettingsScreen
import com.sortr.app.ui.swipe.SwipeScreen
import com.sortr.app.ui.trash.TrashScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class SwipeRoute(val filterKey: String)

@Serializable
data object TrashRoute

@Serializable
data object SettingsRoute

@Composable
fun SortrNavHost(
    access: MediaAccess,
    onRequestAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> { entry ->
            HomeScreen(
                access = access,
                onRequestAccess = onRequestAccess,
                onOpenAppSettings = onOpenAppSettings,
                onStartSession = { filterKey ->
                    // Ignore double taps that arrive while the screen is already leaving.
                    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                        navController.navigate(SwipeRoute(filterKey))
                    }
                },
                onOpenTrash = dropUnlessResumed { navController.navigate(TrashRoute) },
                onOpenSettings = dropUnlessResumed { navController.navigate(SettingsRoute) },
            )
        }
        composable<SwipeRoute> {
            SwipeScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenTrash = dropUnlessResumed {
                    navController.navigate(TrashRoute) { popUpTo(HomeRoute) }
                },
            )
        }
        composable<TrashRoute> {
            TrashScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
    }
}
