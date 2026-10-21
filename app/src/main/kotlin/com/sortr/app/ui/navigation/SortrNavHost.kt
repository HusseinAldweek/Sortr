package com.sortr.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sortr.app.ui.albums.AlbumScreen
import com.sortr.app.ui.albums.AlbumsScreen
import com.sortr.app.ui.home.HomeActions
import com.sortr.app.ui.home.HomeScreen
import com.sortr.app.ui.library.CustomSessionScreen
import com.sortr.app.ui.library.DatesScreen
import com.sortr.app.ui.library.DuplicatesScreen
import com.sortr.app.ui.library.StatsScreen
import com.sortr.app.ui.people.PeopleScreen
import com.sortr.app.ui.people.PersonScreen
import com.sortr.app.ui.people.UnsortedFacesScreen
import com.sortr.app.ui.permission.MediaAccess
import com.sortr.app.ui.screenshots.ScreenshotsScreen
import com.sortr.app.ui.settings.SettingsScreen
import com.sortr.app.ui.swipe.SwipeScreen
import com.sortr.app.ui.trash.TrashScreen
import com.sortr.app.ui.vault.VaultScreen
import com.sortr.app.ui.whatsapp.WhatsAppScreen
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

@Serializable
data object DatesRoute

@Serializable
data object AlbumsRoute

@Serializable
data class AlbumRoute(val relativePath: String)

@Serializable
data object DuplicatesRoute

@Serializable
data object StatsRoute

@Serializable
data object CustomSessionRoute

@Serializable
data object WhatsAppRoute

@Serializable
data object ScreenshotsRoute

/** [startBackup] opens Settings straight into exporting a vault backup. */
@Serializable
data class SettingsRoute(val startBackup: Boolean = false)

private val PushSpec = tween<IntOffset>(durationMillis = 350, easing = FastOutSlowInEasing)

/** How far the screen underneath moves: a quarter of the width. */
private const val PARALLAX = 4

@Composable
fun SortrNavHost(
    access: MediaAccess,
    videoAccess: Boolean,
    onRequestAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
    pendingSession: String? = null,
    onPendingSessionOpened: () -> Unit = {},
) {
    val navController = rememberNavController()

    /** Navigates only while [entry] is on screen, ignoring double taps during a transition. */
    fun <T : Any> NavBackStackEntry.go(route: T) {
        if (lifecycle.currentState == Lifecycle.State.RESUMED) navController.navigate(route)
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        // iOS-style push: the new screen slides in from the side while the old one drifts a
        // little the same way. Start/End follow the layout direction, so this mirrors in RTL.
        enterTransition = { slideIntoContainer(SlideDirection.Start, PushSpec) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start, PushSpec) { it / PARALLAX } },
        popEnterTransition = { slideIntoContainer(SlideDirection.End, PushSpec) { it / PARALLAX } },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, PushSpec) },
    ) {
        composable<HomeRoute> { entry ->
            HomeScreen(
                access = access,
                videoAccess = videoAccess,
                onRequestAccess = onRequestAccess,
                onOpenAppSettings = onOpenAppSettings,
                actions = HomeActions(
                    startSession = { filterKey -> entry.go(SwipeRoute(filterKey)) },
                    openPeople = { entry.go(PeopleRoute) },
                    openPerson = { personId -> entry.go(PersonRoute(personId)) },
                    openUnsorted = { entry.go(UnsortedFacesRoute) },
                    openTrash = { entry.go(TrashRoute) },
                    openVault = { entry.go(VaultRoute) },
                    openSettings = { entry.go(SettingsRoute()) },
                    openDates = { entry.go(DatesRoute) },
                    openAlbums = { entry.go(AlbumsRoute) },
                    openDuplicates = { entry.go(DuplicatesRoute) },
                    openStats = { entry.go(StatsRoute) },
                    openCustom = { entry.go(CustomSessionRoute) },
                    openWhatsApp = { entry.go(WhatsAppRoute) },
                    openScreenshots = { entry.go(ScreenshotsRoute) },
                ),
            )
        }
        composable<ScreenshotsRoute> { entry ->
            ScreenshotsScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onStartSession = { filterKey -> entry.go(SwipeRoute(filterKey)) },
                onOpenAlbum = { path -> entry.go(AlbumRoute(path)) },
            )
        }
        composable<WhatsAppRoute> { entry ->
            WhatsAppScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onStartSession = { filterKey -> entry.go(SwipeRoute(filterKey)) },
                onOpenAlbum = { path -> entry.go(AlbumRoute(path)) },
            )
        }
        composable<DatesRoute> { entry ->
            DatesScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onStartSession = { filterKey -> entry.go(SwipeRoute(filterKey)) },
            )
        }
        composable<AlbumsRoute> { entry ->
            AlbumsScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenAlbum = { path -> entry.go(AlbumRoute(path)) },
            )
        }
        composable<AlbumRoute> { entry ->
            AlbumScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onStartSession = { filterKey -> entry.go(SwipeRoute(filterKey)) },
            )
        }
        composable<DuplicatesRoute> { entry ->
            DuplicatesScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onStartSession = { filterKey -> entry.go(SwipeRoute(filterKey)) },
            )
        }
        composable<StatsRoute> {
            StatsScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable<CustomSessionRoute> { entry ->
            CustomSessionScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onStartSession = { filterKey -> entry.go(SwipeRoute(filterKey)) },
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

    // The widget's Start button and the goal reminder open a session on top of Home.
    LaunchedEffect(pendingSession) {
        val filterKey = pendingSession ?: return@LaunchedEffect
        onPendingSessionOpened()
        navController.navigate(SwipeRoute(filterKey)) { popUpTo(HomeRoute) }
    }
}
