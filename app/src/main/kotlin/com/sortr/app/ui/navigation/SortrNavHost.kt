package com.sortr.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sortr.app.ui.home.HomeScreen
import com.sortr.app.ui.permission.MediaAccess
import com.sortr.app.ui.settings.SettingsScreen
import com.sortr.app.ui.swipe.SwipeScreen
import com.sortr.app.ui.trash.TrashScreen
import com.sortr.app.ui.vault.VaultScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class SwipeRoute(val filterKey: String)

@Serializable
data object TrashRoute

@Serializable
data object VaultRoute

/** [startBackup] opens Settings straight into exporting a vault backup. */
@Serializable
data class SettingsRoute(val startBackup: Boolean = false)

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
                onOpenVault = dropUnlessResumed { navController.navigate(VaultRoute) },
                onOpenSettings = dropUnlessResumed { navController.navigate(SettingsRoute()) },
            )
        }
        composable<SwipeRoute> {
            SwipeScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenTrash = dropUnlessResumed {
                    navController.navigate(TrashRoute) { popUpTo(HomeRoute) }
                },
                onOpenVault = dropUnlessResumed { navController.navigate(VaultRoute) },
            )
        }
        composable<TrashRoute> {
            TrashScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable<VaultRoute> {
            VaultScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onBackUp = dropUnlessResumed { navController.navigate(SettingsRoute(startBackup = true)) },
            )
        }
        composable<SettingsRoute> { entry ->
            SettingsScreen(
                startBackup = entry.toRoute<SettingsRoute>().startBackup,
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenVault = dropUnlessResumed { navController.navigate(VaultRoute) },
            )
        }
    }
}
