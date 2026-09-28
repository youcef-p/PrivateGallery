package com.privategallery.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.privategallery.app.ui.auth.ForgotPasswordScreen
import com.privategallery.app.ui.auth.LoginScreen
import com.privategallery.app.ui.auth.RegisterScreen
import com.privategallery.app.ui.folder.FolderDetailScreen
import com.privategallery.app.ui.folder.FolderSettingsScreen
import com.privategallery.app.ui.home.HomeScreen
import com.privategallery.app.ui.invite.InviteUserScreen
import com.privategallery.app.ui.settings.AppSettingsScreen
import com.privategallery.app.ui.settings.SecuritySettingsScreen
import com.privategallery.app.ui.splash.SplashScreen
import com.privategallery.app.ui.sync.SyncStatusScreen
import com.privategallery.app.ui.viewer.MediaViewerScreen

@Composable
fun PrivateGalleryNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onAuthenticated = { navController.navigate(Routes.HOME) { popUpTo(Routes.SPLASH) { inclusive = true } } },
                onUnauthenticated = { navController.navigate(Routes.LOGIN) { popUpTo(Routes.SPLASH) { inclusive = true } } }
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { navController.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
                onNavigateToForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) }
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = { navController.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenFolder = { folderId -> navController.navigate(Routes.folderDetail(folderId)) },
                onOpenSettings = { navController.navigate(Routes.APP_SETTINGS) }
            )
        }
        composable(
            Routes.FOLDER_DETAIL,
            arguments = listOf(navArgument("folderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getString("folderId").orEmpty()
            FolderDetailScreen(
                folderId = folderId,
                onOpenMedia = { mediaId -> navController.navigate(Routes.mediaViewer(folderId, mediaId)) },
                onOpenInvite = { navController.navigate(Routes.inviteUser(folderId)) },
                onOpenSettings = { navController.navigate(Routes.folderSettings(folderId)) },
                onOpenSyncStatus = { navController.navigate(Routes.syncStatus(folderId)) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.MEDIA_VIEWER,
            arguments = listOf(
                navArgument("folderId") { type = NavType.StringType },
                navArgument("mediaId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            MediaViewerScreen(
                folderId = backStackEntry.arguments?.getString("folderId").orEmpty(),
                initialMediaId = backStackEntry.arguments?.getString("mediaId").orEmpty(),
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.INVITE_USER,
            arguments = listOf(navArgument("folderId") { type = NavType.StringType })
        ) { backStackEntry ->
            InviteUserScreen(
                folderId = backStackEntry.arguments?.getString("folderId").orEmpty(),
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.FOLDER_SETTINGS,
            arguments = listOf(navArgument("folderId") { type = NavType.StringType })
        ) { backStackEntry ->
            FolderSettingsScreen(
                folderId = backStackEntry.arguments?.getString("folderId").orEmpty(),
                onNavigateBack = { navController.popBackStack() },
                onFolderDeleted = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }
            )
        }
        composable(Routes.APP_SETTINGS) {
            AppSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenSecuritySettings = { navController.navigate(Routes.SECURITY_SETTINGS) },
                onLoggedOut = { navController.navigate(Routes.LOGIN) { popUpTo(Routes.HOME) { inclusive = true } } }
            )
        }
        composable(Routes.SECURITY_SETTINGS) {
            SecuritySettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(
            Routes.SYNC_STATUS,
            arguments = listOf(navArgument("folderId") { type = NavType.StringType })
        ) { backStackEntry ->
            SyncStatusScreen(
                folderId = backStackEntry.arguments?.getString("folderId").orEmpty(),
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
