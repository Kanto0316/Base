package com.netk.mvola.ui

import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.netk.mvola.data.NoteRepository
import com.netk.mvola.data.local.NetKDatabase
import com.netk.mvola.ui.notes.EditorScreen
import com.netk.mvola.ui.notes.HomeScreen
import com.netk.mvola.ui.notes.NoteDetailScreen
import com.netk.mvola.ui.navigation.Destination

@Composable
fun BaseAndroidApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val database = NetKDatabase.getInstance(context)
    val notesViewModel: NotesViewModel = viewModel(factory = NotesViewModel.Factory(NoteRepository(context.applicationContext, database.noteDao())))
    NavHost(navController, Destination.Home.route, modifier) {
        composable(Destination.Home.route) {
            HomeScreen(notesViewModel, { navController.navigate(Destination.Editor.route()) }, { navController.navigate(Destination.Detail.route(it)) })
        }
        composable(Destination.Editor.route, arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { entry ->
            EditorScreen(notesViewModel, entry.arguments?.getLong("id")?.takeIf { it >= 0 }, navController::popBackStack)
        }
        composable(Destination.Detail.route, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            NoteDetailScreen(notesViewModel, id, navController::popBackStack, { navController.navigate(Destination.Editor.route(id)) })
        }
    }
}
