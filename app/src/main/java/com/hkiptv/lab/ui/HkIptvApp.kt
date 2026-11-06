package com.hkiptv.lab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.hkiptv.lab.HkIptvApplication
import com.hkiptv.lab.ui.screens.*

private data class Destination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val destinations = listOf(Destination("home", "Início", Icons.Default.Home), Destination("search", "Busca", Icons.Default.Search), Destination("providers", "Provedores", Icons.Default.Dns), Destination("settings", "Ajustes", Icons.Default.Settings))

@Composable fun HkIptvApp(application: HkIptvApplication) {
    val vm: AppViewModel = viewModel(factory = AppViewModel.factory(application.providerRepository, application.httpClient))
    val state by vm.state.collectAsState()
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    state.error?.let { message -> AlertDialog(onDismissRequest = vm::clearError, confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } }, title = { Text("Não foi possível concluir") }, text = { Text(message) }) }
    Scaffold(bottomBar = {
        NavigationBar { destinations.forEach { item -> NavigationBarItem(selected = current?.destination?.route == item.route, onClick = { nav.navigate(item.route) { launchSingleTop = true; popUpTo("home") { saveState = true }; restoreState = true } }, icon = { Icon(item.icon, null) }, label = { Text(item.label) }) } }
    }) { padding ->
        NavHost(nav, "home", Modifier.padding(padding)) {
            composable("home") { HomeScreen(state, vm::loadCatalog) { vm.select(it); nav.navigate("details") } }
            composable("search") { SearchScreen(state, vm::search) { vm.select(it); nav.navigate("details") } }
            composable("providers") { ProvidersScreen(state, vm::addProvider, vm::editProvider, vm::enableProvider, vm::makePrimary, vm::removeProvider) }
            composable("settings") { SettingsScreen() }
            composable("details") { DetailsScreen(state.selected, onBack = { nav.popBackStack() }, onSources = { id -> vm.loadSources(id); nav.navigate("sources") }, onSeasons = { nav.navigate("seasons") }) }
            composable("seasons") { SeasonsScreen(state.selected) { nav.popBackStack() } }
            composable("sources") { SourcesScreen(state.sources, state.loading, onBack = { nav.popBackStack() }) { source -> vm.selectSource(source); nav.navigate("player") } }
            composable("player") { PlayerScreen(state.selectedSource) { nav.popBackStack() } }
        }
    }
}
