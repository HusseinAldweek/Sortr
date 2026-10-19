package com.sortr.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sortr.app.ui.home.HomeScreen
import com.sortr.app.ui.people.PeopleScreen
import com.sortr.app.ui.people.PersonScreen
import com.sortr.app.ui.people.UnsortedFacesScreen
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

@Serializable
data object PeopleRoute

@Serializable
data class PersonRoute(val personId: Long)

@Serializable
data object UnsortedFacesRoute

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

    /** Navigates only while [entry] is on screen, ignoring double taps during a transition. */
    fun <T : Any> NavBackStackEntry.go(route: T) {
        if (lifecycle.currentState == Lifecycle.State.RESUMED) navController.navigate(route)
    }

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> { entry ->
            HomeScreen(
                access = access,
                onRequestAccess = onRequestAccess,
                onOpenAppSettings = onOpenAppSettings,
                onStartSession = { filterKey -> entry.go(SwipeRoute(filterKey)) },
                onOpenPeople = { entry.go(PeopleRoute) },
                onOpenPerson = { personId -> entry.go(PersonRoute(personId)) },
                onOpenUnsorted = { entry.go(UnsortedFacesRoute) },
                onOpenTrash = { entry.go(TrashRoute) },
                onOpenVault = { entry.go(VaultRoute) },
                onOpenSettings = { entry.go(SettingsRoute()) },
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
        composable<PeopleRoute> { entry ->
            PeopleScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenPerson = { personId -> entry.go(PersonRoute(personId)) },
                onOpenUnsorted = { entry.go(UnsortedFacesRoute) },
            )
        }
        composable<PersonRoute> { entry ->
            PersonScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onStartSession = { filterKey -> entry.go(SwipeRoute(filterKey)) },
            )
        }
        composable<UnsortedFacesRoute> { entry ->
            UnsortedFacesScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onStartSession = { filterKey -> entry.go(SwipeRoute(filterKey)) },
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
