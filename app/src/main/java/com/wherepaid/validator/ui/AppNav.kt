package com.wherepaid.validator.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object Routes {
    const val RECORDS = "records"
    const val PERMISSIONS = "permissions"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{id}"
    fun detail(id: Long) = "detail/$id"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.RECORDS) {
        composable(Routes.RECORDS) {
            RecordListScreen(
                onOpen = { nav.navigate(Routes.detail(it)) },
                onPermissions = { nav.navigate(Routes.PERMISSIONS) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.PERMISSIONS) { PermissionScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            RecordDetailScreen(
                id = entry.arguments?.getLong("id") ?: 0L,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
