package dev.blaze.controller

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModelProvider
import dev.blaze.controller.amp.AmpRepository
import dev.blaze.controller.ble.BleAmpTransport
import dev.blaze.controller.ui.HomeViewModel
import dev.blaze.controller.tuner.MicAudioSource
import dev.blaze.controller.tuner.TunerController
import dev.blaze.controller.ui.SystemActions
import dev.blaze.controller.ui.screens.MainScreen
import dev.blaze.controller.ui.screens.TAB_AMP
import dev.blaze.controller.ui.screens.TAB_TUNER
import dev.blaze.controller.ui.theme.PedalboardTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MainActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                val transport = BleAmpTransport(applicationContext)
                return HomeViewModel(AmpRepository(transport, scope), transport, scope, ownsScope = true) as T
            }
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            askedThisSession = true
            val granted = hasBluetoothPermissions()
            // Once Android stops offering the dialog (denied twice / "don't ask again") only system settings can help.
            vm.onPermissionResult(granted, permanentlyDenied = !granted && !canStillAsk())
        }

    private var askedThisSession = false

    // ---- microphone (tuner) ----
    private val tuner by lazy { TunerController(MicAudioSource()) }
    private var micGranted by mutableStateOf(false)
    private var micNeedsSettings by mutableStateOf(false)
    private var micAsked = false

    private val micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
        micNeedsSettings = !granted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
    }

    private fun openAppSettings() =
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null)))

    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            vm.onBluetoothEnabled(isBluetoothOn())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PedalboardTheme {
                val ui by vm.ui.collectAsState()
                val tunerUi by tuner.ui.collectAsState()
                val lifecycleOwner = LocalLifecycleOwner.current
                var tab by rememberSaveable { mutableIntStateOf(TAB_AMP) }
                BackHandler(enabled = tab != TAB_AMP) { tab = TAB_AMP }

                // Ask for the microphone once, automatically, the first time the tuner tab is opened;
                // afterwards the button on the tab does it (or opens the app settings).
                LaunchedEffect(tab) {
                    if (tab == TAB_TUNER && !micGranted && !micAsked) {
                        micAsked = true
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                // The microphone is open only while the tuner tab is selected AND the activity is STARTED:
                // switching tab or leaving the app cancels run(), which releases the AudioRecord.
                LaunchedEffect(tab == TAB_TUNER && micGranted) {
                    if (tab == TAB_TUNER && micGranted) {
                        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { tuner.run() }
                    }
                }

                val system = remember {
                    SystemActions(
                        onGrantPermission = ::requestPermissions,
                        onOpenBluetoothSettings = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                        onOpenAppSettings = ::openAppSettings,
                        onRequestMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onOpenUrl = { url -> startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) },
                    )
                }
                MainScreen(
                    ui = ui, tuner = tunerUi, micGranted = micGranted, micNeedsSettings = micNeedsSettings,
                    tab = tab, onTab = { tab = it }, actions = vm, system = system,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(btStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        vm.onBluetoothEnabled(isBluetoothOn())
        // Prompt automatically only once per process; afterwards the on-screen button does it (or opens settings).
        if (hasBluetoothPermissions()) vm.onPermissionResult(true) else if (!askedThisSession) requestPermissions()
        vm.onForeground() // no-op until connected; afterwards re-reads the amp whenever the app comes back
        micGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED // may have changed in system settings
    }

    override fun onStop() {
        unregisterReceiver(btStateReceiver)
        super.onStop()
    }

    private fun requestPermissions() {
        permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
    }

    private fun canStillAsk() = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        .any { shouldShowRequestPermissionRationale(it) }

    private fun hasBluetoothPermissions() =
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT).all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

    private fun isBluetoothOn() =
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled == true
}
