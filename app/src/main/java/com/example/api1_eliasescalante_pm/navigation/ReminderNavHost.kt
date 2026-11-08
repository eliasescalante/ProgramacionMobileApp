package com.example.api1_eliasescalante_pm.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.api1_eliasescalante_pm.ui.ui.screens.ReminderCreateScreen
import com.example.api1_eliasescalante_pm.ui.ui.screens.ReminderDetailScreen
import com.example.api1_eliasescalante_pm.ui.ui.screens.ReminderListScreen
import com.example.api1_eliasescalante_pm.ui.ui.screens.ReminderSettingsScreen

object ReminderRoutes {
    const val LIST = "reminders"
    const val CREATE = "reminders/create"
    const val DETAIL = "reminders/detail/{reminderId}"
    const val SETTINGS = "reminders/settings"

    fun detail(id: String) = "reminders/detail/$id"
}

@Composable
fun ReminderNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ReminderRoutes.LIST
    ) {
        composable(ReminderRoutes.LIST) {
            ReminderListScreen(
                onCrear = {
                    navController.navigate(ReminderRoutes.CREATE)
                },
                onReminderClick = { id ->
                    navController.navigate(ReminderRoutes.detail(id))
                },
                onOpenSettings = {
                    navController.navigate(ReminderRoutes.SETTINGS)
                }
            )
        }

        composable(ReminderRoutes.CREATE) {
            ReminderCreateScreen(
                onGuardar = {
                    navController.popBackStack()
                },
                onCancelar = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = ReminderRoutes.DETAIL,
            arguments = listOf(
                navArgument("reminderId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->

            val reminderId = backStackEntry.arguments
                ?.getString("reminderId")
                .orEmpty()

            ReminderDetailScreen(
                reminderId = reminderId,
                onVolver = {
                    navController.popBackStack()
                }
            )
        }

        composable(ReminderRoutes.SETTINGS) {
            ReminderSettingsScreen(
                onVolver = {
                    navController.popBackStack()
                }
            )
        }
    }
}
