package com.cuscus.cussiparking

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.app.PendingIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cuscus.cussiparking.local.VehicleEntity
import com.cuscus.cussiparking.trigger.NfcTriggerHandler
import com.cuscus.cussiparking.trigger.NfcWriteHelper
import com.cuscus.cussiparking.trigger.UnknownNfcTagState
import com.cuscus.cussiparking.ui.auth.AuthScreen
import com.cuscus.cussiparking.ui.auth.AuthViewModel
import com.cuscus.cussiparking.ui.auth.RegisterScreen
import com.cuscus.cussiparking.ui.home.HomeScreen
import com.cuscus.cussiparking.ui.home.HomeViewModel
import com.cuscus.cussiparking.ui.members.VehicleMembersScreen
import com.cuscus.cussiparking.ui.members.VehicleMembersViewModel
import com.cuscus.cussiparking.ui.settings.SettingsScreen
import com.cuscus.cussiparking.ui.theme.CussiParkingTheme
import com.cuscus.cussiparking.ui.triggers.VehicleTriggersScreen
import com.cuscus.cussiparking.ui.triggers.VehicleTriggersViewModel
import com.cuscus.cussiparking.ui.welcome.WelcomeScreen
import androidx.lifecycle.lifecycleScope
import com.cuscus.cussiparking.ui.logs.VehicleLogsScreen
import com.cuscus.cussiparking.ui.logs.VehicleLogsViewModel
import com.cuscus.cussiparking.ui.triggers.UnknownNfcTagBottomSheet
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null

    private var pendingNfcWrite: Triple<Int, String, String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appContainer    = application as CussiParkingApplication
        val repository      = appContainer.repository
        val settingsManager = appContainer.settingsManager

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            CussiParkingTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val startDest = if (settingsManager.isOnboardingCompleted()) "home" else "welcome_screen"

                    val unknownTag by UnknownNfcTagState.pending.collectAsState()
                    val allVehicles by produceState<List<VehicleEntity>>(initialValue = emptyList()) {
                        value = appContainer.database.vehicleDao().getAllVehicles()
                    }
                    if (unknownTag != null) {
                        UnknownNfcTagBottomSheet(
                            tagData    = unknownTag!!,
                            vehicles   = allVehicles,
                            triggerDao = appContainer.database.triggerDao(),
                            onDismiss  = { UnknownNfcTagState.consume() }
                        )
                    }
                    // ─────────────────────────────────────────────────────────────────

                    NavHost(navController = navController, startDestination = startDest) {

                        // ==========================================
                        // HOME
                        // ==========================================
                        composable("home") {
                            val homeViewModel: HomeViewModel = viewModel(
                                factory = HomeViewModel.Factory(repository)
                            )
                            HomeScreen(
                                viewModel            = homeViewModel,
                                onLogout             = { },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToMembers  = { serverId, vehicleName, profileId ->
                                    val encodedName = URLEncoder.encode(vehicleName, "UTF-8")
                                    navController.navigate("members/$serverId/$encodedName/$profileId")
                                },
                                onNavigateToTriggers = { vehicleId, vehicleName ->
                                    val encodedName = URLEncoder.encode(vehicleName, "UTF-8")
                                    navController.navigate("triggers/$vehicleId/$encodedName")
                                },
                                onNavigateToLogs = { vehicleId, profileId, isOwner ->
                                    navController.navigate("logs/$vehicleId?profileId=$profileId&isOwner=$isOwner")
                                }
                            )
                        }

                        // ==========================================
                        // LOGIN
                        // ==========================================
                        composable("auth/{profileId}") { backStackEntry ->
                            val profileId = backStackEntry.arguments?.getString("profileId")
                            val authViewModel: AuthViewModel = viewModel(
                                factory = AuthViewModel.Factory(repository, settingsManager, profileId)
                            )
                            AuthScreen(
                                viewModel            = authViewModel,
                                onLoginSuccess       = { navController.popBackStack("settings", inclusive = false) },
                                onNavigateToRegister = { navController.navigate("register/$profileId") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateBack       = { navController.popBackStack() }
                            )
                        }

                        // ==========================================
                        // REGISTRAZIONE
                        // ==========================================
                        composable("register/{profileId}") { backStackEntry ->
                            val profileId = backStackEntry.arguments?.getString("profileId")
                            val authViewModel: AuthViewModel = viewModel(
                                factory = AuthViewModel.Factory(repository, settingsManager, profileId)
                            )
                            RegisterScreen(
                                viewModel            = authViewModel,
                                onRegisterSuccess    = { navController.popBackStack("settings", inclusive = false) },
                                onNavigateToLogin    = { navController.popBackStack() },
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }

                        // ==========================================
                        // GESTIONE MEMBRI
                        // ==========================================
                        composable("members/{vehicleServerId}/{vehicleName}/{profileId}") { backStackEntry ->
                            val vehicleServerId = backStackEntry.arguments
                                ?.getString("vehicleServerId")?.toIntOrNull() ?: return@composable
                            val vehicleName = URLDecoder.decode(
                                backStackEntry.arguments?.getString("vehicleName") ?: "Veicolo", "UTF-8"
                            )
                            val profileId = backStackEntry.arguments?.getString("profileId")
                                ?: return@composable
                            val currentUserId = settingsManager.getProfile(profileId)?.userId ?: -1
                            val membersViewModel: VehicleMembersViewModel = viewModel(
                                factory = VehicleMembersViewModel.Factory(
                                    repository, settingsManager,
                                    vehicleServerId, vehicleName, currentUserId, profileId
                                )
                            )
                            VehicleMembersScreen(
                                viewModel      = membersViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // ==========================================
                        // TRIGGER AUTOMATICI
                        // ==========================================
                        composable("triggers/{vehicleId}/{vehicleName}") { backStackEntry ->
                            val vehicleId = backStackEntry.arguments
                                ?.getString("vehicleId")?.toIntOrNull() ?: return@composable
                            val vehicleName = URLDecoder.decode(
                                backStackEntry.arguments?.getString("vehicleName") ?: "Veicolo", "UTF-8"
                            )
                            val triggersViewModel: VehicleTriggersViewModel = viewModel(
                                factory = VehicleTriggersViewModel.Factory(
                                    triggerDao  = appContainer.database.triggerDao(),
                                    context     = this@MainActivity,
                                    vehicleId   = vehicleId,
                                    vehicleName = vehicleName
                                )
                            )
                            val nfcPending by triggersViewModel.nfcWritePending.collectAsState()
                            LaunchedEffect(nfcPending) {
                                val trigger = nfcPending ?: return@LaunchedEffect
                                startNfcWrite(trigger.localVehicleId, trigger.vehicleName, trigger.locationMode)
                                triggersViewModel.clearNfcWrite()
                            }
                            VehicleTriggersScreen(
                                viewModel      = triggersViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // ==========================================
                        // IMPOSTAZIONI
                        // ==========================================
                        composable("settings") {
                            SettingsScreen(
                                settingsManager     = settingsManager,
                                onNavigateBack      = { navController.popBackStack() },
                                onNavigateToAuth    = { profileId -> navController.navigate("auth/$profileId") },
                                onDeleteAccount     = { profileId, password, onResult ->
                                    lifecycleScope.launch {
                                        val result = repository.deleteAccount(profileId, password)
                                        result.onSuccess { onResult(true, it) }
                                        result.onFailure { onResult(false, it.message ?: "Errore") }
                                    }
                                },
                                onLogout            = { profileId ->
                                    lifecycleScope.launch { repository.logoutProfile(profileId) }
                                },
                                onRemoveProfile     = { profileId ->
                                    lifecycleScope.launch { repository.removeProfile(profileId) }
                                },
                                onNavigateToWelcome = { navController.navigate("welcome_screen") }
                            )
                        }

                        // ==========================================
                        // WELCOME / ONBOARDING
                        // ==========================================
                        composable("welcome_screen") {
                            WelcomeScreen(
                                settingsManager = settingsManager,
                                onFinish        = {
                                    settingsManager.setOnboardingCompleted(true)
                                    navController.navigate("home") {
                                        popUpTo("welcome") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // ==========================================
                        // VEHICLES LOGS
                        // ==========================================
                        composable("logs/{vehicleId}?profileId={profileId}&isOwner={isOwner}") { backStackEntry ->
                            val vId = backStackEntry.arguments?.getString("vehicleId")?.toIntOrNull() ?: return@composable
                            val pId = backStackEntry.arguments?.getString("profileId") ?: ""
                            val isOwner = backStackEntry.arguments?.getString("isOwner")?.toBoolean() ?: false

                            val context = androidx.compose.ui.platform.LocalContext.current
                            val app = context.applicationContext as CussiParkingApplication

                            val viewModel: com.cuscus.cussiparking.ui.logs.VehicleLogsViewModel = viewModel(
                                factory = com.cuscus.cussiparking.ui.logs.VehicleLogsViewModel.Factory(app.repository, pId, vId, isOwner)
                            )
                            com.cuscus.cussiparking.ui.logs.VehicleLogsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }

        NfcTriggerHandler.handleIntent(this, intent)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NFC — SCRITTURA TAG
    // ─────────────────────────────────────────────────────────────────────────

    fun startNfcWrite(vehicleId: Int, vehicleName: String, locationMode: String) {
        pendingNfcWrite = Triple(vehicleId, vehicleName, locationMode)
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
        Toast.makeText(this, "Avvicina il tag NFC al telefono…", Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        if (pendingNfcWrite != null) {
            val pi = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_MUTABLE
            )
            nfcAdapter?.enableForegroundDispatch(this, pi, null, null)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val write = pendingNfcWrite

        if (write != null && (
                    intent.action == NfcAdapter.ACTION_TAG_DISCOVERED ||
                            intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
                            intent.action == NfcAdapter.ACTION_TECH_DISCOVERED)
        ) {
            // ── Modalità SCRITTURA ────────────────────────────────────────────
            @Suppress("DEPRECATION")
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                val error = NfcWriteHelper.writeTag(tag, write.first, write.second, write.third)
                if (error == null) {
                    Toast.makeText(this, "✓ Tag NFC scritto con successo!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "⚠ $error", Toast.LENGTH_LONG).show()
                }
            }
            pendingNfcWrite = null
            nfcAdapter?.disableForegroundDispatch(this)

        } else {
            NfcTriggerHandler.handleIntent(this, intent)
        }
    }
}
