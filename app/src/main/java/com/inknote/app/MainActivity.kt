package com.inknote.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.inknote.app.ui.CanvasScreen
import com.inknote.app.ui.CloudScreen
import com.inknote.app.ui.InkTheme
import com.inknote.app.ui.LibraryScreen
import com.inknote.app.ui.PagesScreen
import com.inknote.app.ui.SearchScreen
import com.inknote.app.ui.SettingsScreen
import com.inknote.app.ui.Shell

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Başka bir uygulamadan "PDF ile aç" ile gelindiyse içe aktar
        if (savedInstanceState == null) intent?.data?.let { uri: Uri -> if (intent.type == "application/pdf") vm.importPdf(uri) }
        setContent { InkTheme { App(vm) } }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPdf(uri)
    }
    val launchPdf = { pickPdf.launch(arrayOf("application/pdf")) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? ComponentActivity
    LaunchedEffect(vm.isFullScreen) {
        activity?.window?.let { win ->
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(win, win.decorView)
            if (vm.isFullScreen) {
                insetsController.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    BackHandler(enabled = vm.isFullScreen || vm.route != Route.LIBRARY) {
        if (vm.isFullScreen) {
            vm.isFullScreen = false
        } else {
            vm.route = if (vm.route == Route.PAGES) Route.CANVAS else Route.LIBRARY
        }
    }
    Box(Modifier.fillMaxSize()) {
        Shell(vm) {
            when (vm.route) {
                Route.LIBRARY -> LibraryScreen(vm, launchPdf)
                Route.CANVAS -> CanvasScreen(vm, launchPdf)
                Route.PAGES -> PagesScreen(vm)
                Route.SEARCH -> SearchScreen(vm)
                Route.CLOUD -> CloudScreen()
                Route.SETTINGS -> SettingsScreen(vm)
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
    }
}
