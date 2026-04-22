package me.app.pixel.ide.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import me.app.pixel.ide.ui.home.HomeScreen
import me.app.pixel.ide.ui.newcanvas.NewCanvasScreen
import me.app.pixel.ide.ui.editor.EditorScreen
import me.app.pixel.ide.ui.project.ProjectManagementScreen
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        
        composable("home") {
            HomeScreen(
                onNavigateToEditor = { navController.navigate("new_canvas") },
                onNavigateToFilePicker = { navController.navigate("project_management") }, // 进入项目管理
                onNavigateToSettings = { },
                onNavigateToAiTerminal = { }
            )
        }
        
        composable("new_canvas") {
            NewCanvasScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEditor = { name, width, height, bg ->
                    navController.navigate("editor/$name/$width/$height/$bg")
                }
            )
        }

        // 路由：新建的画布
        composable(
            route = "editor/{name}/{width}/{height}/{bg}",
            arguments = listOf(
                navArgument("name") { type = NavType.StringType },
                navArgument("width") { type = NavType.IntType },
                navArgument("height") { type = NavType.IntType },
                navArgument("bg") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name") ?: "未命名"
            val width = backStackEntry.arguments?.getInt("width") ?: 64
            val height = backStackEntry.arguments?.getInt("height") ?: 64
            val bg = backStackEntry.arguments?.getString("bg") ?: "TRANSPARENT"

            EditorScreen(
                name = name, width = width, height = height, bg = bg, filePath = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 新增路由：项目管理大厅
        composable("project_management") {
            ProjectManagementScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenProject = { path ->
                    val encoded = URLEncoder.encode(path, "UTF-8")
                    navController.navigate("editor_open/$encoded")
                }
            )
        }

        // 新增路由：打开现有的本地 PNG 项目
        composable(
            route = "editor_open/{filePath}",
            arguments = listOf(navArgument("filePath") { type = NavType.StringType })
        ) { backStackEntry ->
            val filePath = URLDecoder.decode(backStackEntry.arguments?.getString("filePath") ?: "", "UTF-8")
            EditorScreen(
                name = "", width = 0, height = 0, bg = "TRANSPARENT", filePath = filePath, // 传入文件路径
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}