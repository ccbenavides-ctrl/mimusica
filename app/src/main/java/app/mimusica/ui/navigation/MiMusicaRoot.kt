package app.mimusica.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.mimusica.ui.components.LocalAppContainer
import app.mimusica.ui.screens.AlbumScreen
import app.mimusica.ui.screens.ArtistScreen
import app.mimusica.ui.screens.AutoPlaylistScreen
import app.mimusica.ui.screens.DiscoverScreen
import app.mimusica.ui.screens.DownloadsScreen
import app.mimusica.ui.screens.IaItemScreen
import app.mimusica.ui.screens.ImportHelpScreen
import app.mimusica.ui.screens.ImportResultScreen
import app.mimusica.ui.screens.ImportScreen
import app.mimusica.ui.screens.LibraryScreen
import app.mimusica.ui.screens.MiniPlayer
import app.mimusica.ui.screens.NowPlayingScreen
import app.mimusica.ui.screens.PlaylistDetailScreen
import app.mimusica.ui.screens.PlaylistsScreen
import app.mimusica.ui.screens.SearchScreen
import app.mimusica.ui.screens.SettingsScreen

object Routes {
    const val LIBRARY = "library"
    const val DISCOVER = "discover"
    const val PLAYLISTS = "playlists"
    const val SEARCH = "search"
    const val SEARCH_PATTERN = "search?q={q}"
    const val NOW_PLAYING = "now_playing"
    const val ARTIST = "artist/{name}"
    const val ALBUM = "album/{key}"
    const val PLAYLIST = "playlist/{id}"
    const val AUTO = "auto/{id}"
    const val IA_ITEM = "ia/{identifier}"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
    const val IMPORT_RESULT = "import/{id}"
    const val IMPORT_HELP = "import_help"

    fun artist(name: String) = "artist/${Uri.encode(name)}"
    fun album(key: String) = "album/${Uri.encode(key)}"
    fun playlist(id: Long) = "playlist/$id"
    fun auto(id: String) = "auto/$id"
    fun iaItem(identifier: String) = "ia/${Uri.encode(identifier)}"
    fun importResult(id: Long) = "import/$id"
    fun search(q: String) = "search?q=${Uri.encode(q)}"
}

private data class TopTab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TopTab(Routes.LIBRARY, "Biblioteca", Icons.Filled.LibraryMusic),
    TopTab(Routes.DISCOVER, "Descubrir", Icons.Filled.Explore),
    TopTab(Routes.PLAYLISTS, "Listas", Icons.AutoMirrored.Filled.QueueMusic),
    TopTab(Routes.SEARCH, "Buscar", Icons.Filled.Search),
)

private fun baseRoute(route: String?): String? = route?.substringBefore('?')?.substringBefore('/')

@Composable
fun MiMusicaRoot() {
    val navController = rememberNavController()
    val container = LocalAppContainer.current
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBars = currentRoute != Routes.NOW_PLAYING

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBars) {
                Column {
                    MiniPlayer(playerState) { navController.navigate(Routes.NOW_PLAYING) { launchSingleTop = true } }
                    NavigationBar {
                        val currentBase = baseRoute(currentRoute)
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentBase == tab.route,
                                onClick = { navController.navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                    onOpenAlbum = { navController.navigate(Routes.album(it)) },
                    onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenImport = { navController.navigate(Routes.IMPORT) },
                )
            }
            composable(Routes.DISCOVER) {
                DiscoverScreen(
                    onOpenIaItem = { navController.navigate(Routes.iaItem(it)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.PLAYLISTS) {
                PlaylistsScreen(
                    onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
                    onOpenAuto = { navController.navigate(Routes.auto(it)) },
                    onOpenImport = { navController.navigate(Routes.IMPORT) },
                )
            }
            composable(
                Routes.SEARCH_PATTERN,
                arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                SearchScreen(
                    initialQuery = entry.arguments?.getString("q").orEmpty(),
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                    onOpenAlbum = { navController.navigate(Routes.album(it)) },
                    onOpenIaItem = { navController.navigate(Routes.iaItem(it)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.NOW_PLAYING) {
                NowPlayingScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ARTIST, arguments = listOf(navArgument("name") { type = NavType.StringType })) { entry ->
                ArtistScreen(
                    name = entry.arguments?.getString("name").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenAlbum = { navController.navigate(Routes.album(it)) },
                )
            }
            composable(Routes.ALBUM, arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
                AlbumScreen(albumKey = entry.arguments?.getString("key").orEmpty(), onBack = { navController.popBackStack() })
            }
            composable(Routes.PLAYLIST, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                PlaylistDetailScreen(playlistId = entry.arguments?.getLong("id") ?: 0L, onBack = { navController.popBackStack() })
            }
            composable(Routes.AUTO, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                AutoPlaylistScreen(autoId = entry.arguments?.getString("id").orEmpty(), onBack = { navController.popBackStack() })
            }
            composable(Routes.IA_ITEM, arguments = listOf(navArgument("identifier") { type = NavType.StringType })) { entry ->
                IaItemScreen(identifier = entry.arguments?.getString("identifier").orEmpty(), onBack = { navController.popBackStack() })
            }
            composable(Routes.DOWNLOADS) {
                DownloadsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenImportHelp = { navController.navigate(Routes.IMPORT_HELP) },
                )
            }
            composable(Routes.IMPORT) {
                ImportScreen(
                    onBack = { navController.popBackStack() },
                    onOpenResult = { navController.navigate(Routes.importResult(it)) },
                    onOpenHelp = { navController.navigate(Routes.IMPORT_HELP) },
                )
            }
            composable(Routes.IMPORT_RESULT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                ImportResultScreen(
                    importId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onSearch = { q -> navController.navigate(Routes.search(q)) },
                    onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
                )
            }
            composable(Routes.IMPORT_HELP) {
                ImportHelpScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
