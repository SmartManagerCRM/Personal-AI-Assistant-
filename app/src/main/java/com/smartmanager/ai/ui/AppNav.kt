package com.smartmanager.ai.ui

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNav(onStartStandby: () -> Unit, onTalk: () -> Unit) {
    val nav = rememberNavController()
    val vm: AppViewModel = hiltViewModel()
    NavHost(nav, startDestination = "main") {
        composable("main") { MainScreen(vm, onSettings = { nav.navigate("settings") }, onStartStandby = onStartStandby, onTalk = onTalk) }
        composable("settings") { SettingsHub(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(it) }) }
        composable("voice") { VoiceScreen(vm) { nav.popBackStack() } }
        composable("wake") { WakeScreen(vm, onStartStandby, onModels = { nav.navigate("models") }) { nav.popBackStack() } }
        composable("language") { LanguageScreen(vm) { nav.popBackStack() } }
        composable("models") { ModelsScreen(vm) { nav.popBackStack() } }
        composable("online") { OnlineScreen(vm) { nav.popBackStack() } }
        composable("permissions") { PermissionsScreen(vm) { nav.popBackStack() } }
        composable("memory") { MemoryScreen(vm) { nav.popBackStack() } }
        composable("apps") { AppsScreen(vm) { nav.popBackStack() } }
        composable("history") { HistoryScreen(vm) { nav.popBackStack() } }
        composable("license") { LicenseScreen(vm) { nav.popBackStack() } }
        composable("performance") { PerformanceScreen(vm) { nav.popBackStack() } }
    }
}
