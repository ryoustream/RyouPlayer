package com.ryoustream.player.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp

sealed class BottomNavDest(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val iconSelected: ImageVector,
) {
    object Home : BottomNavDest(
        route = "home",
        label = "Beranda",
        icon = Icons.Outlined.Home,
        iconSelected = Icons.Filled.Home,
    )
    object Folders : BottomNavDest(
        route = "folders",
        label = "Folder",
        icon = Icons.Outlined.FolderOpen,
        iconSelected = Icons.Filled.Folder,
    )
    object Library : BottomNavDest(
        route = "library",
        label = "Pustaka",
        icon = Icons.Outlined.VideoLibrary,
        iconSelected = Icons.Filled.VideoLibrary,
    )
}

val bottomNavDestinations = listOf(
    BottomNavDest.Home,
    BottomNavDest.Folders,
    BottomNavDest.Library,
)

val bottomNavRoutes = bottomNavDestinations.map { it.route }.toSet()

@Composable
fun RyouBottomNavBar(
    currentRoute: String?,
    onNavigate: (BottomNavDest) -> Unit,
) {
    NavigationBar {
        bottomNavDestinations.forEach { dest ->
            val selected = currentRoute == dest.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(dest) },
                icon = {
                    Icon(
                        imageVector = if (selected) dest.iconSelected else dest.icon,
                        contentDescription = dest.label,
                    )
                },
                label = { Text(dest.label, fontSize = 11.sp) },
            )
        }
    }
}
