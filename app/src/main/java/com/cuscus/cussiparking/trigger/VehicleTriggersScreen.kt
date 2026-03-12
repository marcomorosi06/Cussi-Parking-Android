package com.cuscus.cussiparking.ui.triggers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cuscus.cussiparking.local.TriggerEntity

// ─────────────────────────────────────────────
// TRIGGERS SCREEN
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleTriggersScreen(
    viewModel: VehicleTriggersViewModel,
    onNavigateBack: () -> Unit
) {
    val context  = LocalContext.current
    val triggers by viewModel.triggers.collectAsState()

    var showAddDialog           by remember { mutableStateOf(false) }
    var triggerToDelete         by remember { mutableStateOf<TriggerEntity?>(null) }
    var showBgLocationRationale by remember { mutableStateOf(false) }

    fun hasBackgroundLocation(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        else true

    fun isBatteryOptimizationIgnored(): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    val batterySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    val bgPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { showAddDialog = true }

    val basePermissionsToRequest = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
    }.toTypedArray()

    val basePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocation())
                showBgLocationRationale = true
            else
                showAddDialog = true
        }
    }

    val showBgWarning      = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocation()
    val showBatteryWarning = !isBatteryOptimizationIgnored()

    val wifiTriggers = triggers.filter { it.type == "wifi" }
    val btTriggers   = triggers.filter { it.type == "bluetooth" }
    val nfcTriggers  = triggers.filter { it.type == "nfc" }

    // Stato per il dialog di scrittura NFC
    var nfcWriteTrigger by remember { mutableStateOf<TriggerEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Trigger Automatici", fontWeight = FontWeight.Bold)
                        Text(
                            viewModel.vehicleName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                },
                navigationIcon = {
                    BouncyIconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = { basePermLauncher.launch(basePermissionsToRequest) },
                icon           = { Icon(Icons.Default.Add, contentDescription = null) },
                text           = { Text("Aggiungi trigger", fontWeight = FontWeight.SemiBold) },
                shape          = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor   = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier        = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding  = PaddingValues(
                start  = 16.dp,
                top    = 12.dp,
                end    = 16.dp,
                bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ── Spiegazione ────────────────────────────
            item {
                InfoBanner(
                    icon    = Icons.Default.AutoMode,
                    title   = "Parcheggio automatico",
                    body    = "Quando ti disconnetti da un dispositivo Bluetooth o avvicini il telefono a un tag NFC, la posizione viene salvata automaticamente.",
                    bgColor = MaterialTheme.colorScheme.secondaryContainer,
                    fgColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // ── Warning: background location ──────────
            if (showBgWarning) {
                item {
                    WarningBanner(
                        icon        = Icons.Default.LocationOff,
                        title       = "Posizione in background mancante",
                        body        = "Vai in Impostazioni → App → CussiParking → Permessi → Posizione → \"Consenti sempre\".",
                        buttonLabel = "Concedi ora",
                        onButton    = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                                bgPermLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    )
                }
            }

            // ── Warning: ottimizzazione batteria ──────
            if (showBatteryWarning) {
                item {
                    WarningBanner(
                        icon        = Icons.Default.BatteryAlert,
                        title       = "Ottimizzazione batteria attiva",
                        body        = "Android potrebbe bloccare i trigger automatici. Disabilita l'ottimizzazione per un funzionamento affidabile.",
                        buttonLabel = "Disabilita",
                        onButton    = {
                            batterySettingsLauncher.launch(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    )
                }
            }

            // ── Empty state ────────────────────────────
            if (triggers.isEmpty()) {
                item {
                    val pulse = rememberInfiniteTransition(label = "trigger_pulse")
                    val pulseScale by pulse.animateFloat(
                        initialValue  = 0.93f,
                        targetValue   = 1.07f,
                        animationSpec = infiniteRepeatable(
                            tween(1800, easing = FastOutSlowInEasing),
                            RepeatMode.Reverse
                        ),
                        label = "trigger_pulse_scale"
                    )
                    Column(
                        modifier                = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment     = Alignment.CenterHorizontally,
                        verticalArrangement     = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape    = RoundedCornerShape(20.dp),
                            color    = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(72.dp).scale(pulseScale)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AutoMode,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint     = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        Text(
                            "Nessun trigger configurato",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Premi + per aggiungerne uno.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Sezione WiFi ───────────────────────────
            if (wifiTriggers.isNotEmpty()) {
                item { SectionLabel(label = "WiFi", color = MaterialTheme.colorScheme.primary) }
                items(wifiTriggers, key = { it.id }) { trigger ->
                    TriggerCard(
                        trigger  = trigger,
                        onToggle = { viewModel.toggleTrigger(trigger) },
                        onDelete = { triggerToDelete = trigger }
                    )
                }
            }

            // ── Sezione Bluetooth ──────────────────────
            if (btTriggers.isNotEmpty()) {
                item {
                    SectionLabel(
                        label  = "Bluetooth",
                        color  = MaterialTheme.colorScheme.secondary,
                        topPad = if (wifiTriggers.isNotEmpty()) 8.dp else 0.dp
                    )
                }
                items(btTriggers, key = { it.id }) { trigger ->
                    TriggerCard(
                        trigger  = trigger,
                        onToggle = { viewModel.toggleTrigger(trigger) },
                        onDelete = { triggerToDelete = trigger }
                    )
                }
            }

            // ── Sezione NFC ────────────────────────────
            if (nfcTriggers.isNotEmpty()) {
                item {
                    SectionLabel(
                        label  = "NFC",
                        color  = MaterialTheme.colorScheme.tertiary,
                        topPad = if (wifiTriggers.isNotEmpty() || btTriggers.isNotEmpty()) 8.dp else 0.dp
                    )
                }
                items(nfcTriggers, key = { it.id }) { trigger ->
                    NfcTriggerCard(
                        trigger    = trigger,
                        onToggle   = { viewModel.toggleTrigger(trigger) },
                        onDelete   = { triggerToDelete = trigger },
                        onWriteTag = { nfcWriteTrigger = trigger }
                    )
                }
            }
        }
    }

    // ── Dialog: rationale background location ─────
    if (showBgLocationRationale) {
        AlertDialog(
            onDismissRequest = { showBgLocationRationale = false; showAddDialog = true },
            icon             = { Icon(Icons.Default.LocationOn, null) },
            title            = { Text("Posizione in background", fontWeight = FontWeight.Bold) },
            text             = {
                Text(
                    "Per salvare la posizione precisa quando esci dall'auto (con app chiusa), " +
                            "CussiParking ha bisogno del permesso \"Consenti sempre\".\n\n" +
                            "Nella schermata successiva seleziona \"Consenti sempre\".\n\n" +
                            "Senza questo permesso verrà usata l'ultima posizione nota."
                )
            },
            confirmButton    = {
                Button(
                    onClick = {
                        showBgLocationRationale = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                            bgPermLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        else showAddDialog = true
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Concedi") }
            },
            dismissButton    = {
                TextButton(onClick = { showBgLocationRationale = false; showAddDialog = true }) {
                    Text("Salta")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ── Dialog: aggiungi trigger ──────────────────
    if (showAddDialog) {
        AddTriggerBottomSheet(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onConfirm = { type, identifier, label, mode ->
                viewModel.addTrigger(type, identifier, label, mode)
                showAddDialog = false
            }
        )
    }

    // ── Dialog: conferma eliminazione ─────────────
    triggerToDelete?.let { trigger ->
        AlertDialog(
            onDismissRequest = { triggerToDelete = null },
            icon             = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title            = { Text("Rimuovi trigger", fontWeight = FontWeight.Bold) },
            text             = {
                Text("Rimuovere il trigger \"${trigger.label}\"? La posizione non verrà più salvata automaticamente.")
            },
            confirmButton    = {
                Button(
                    onClick = { viewModel.deleteTrigger(trigger); triggerToDelete = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape   = RoundedCornerShape(12.dp)
                ) { Text("Rimuovi") }
            },
            dismissButton    = {
                TextButton(onClick = { triggerToDelete = null }) { Text("Annulla") }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ── Dialog: scrivi tag NFC ────────────────────
    // ── Dialog: scrivi tag NFC (Stile Moneta Animata) ──
    nfcWriteTrigger?.let { trigger ->
        // Animazione di "respiro" per l'attesa del tag
        val pulse = rememberInfiniteTransition(label = "nfc_pulse")
        val pulseScale by pulse.animateFloat(
            initialValue  = 0.90f,
            targetValue   = 1.10f,
            animationSpec = infiniteRepeatable(
                tween(1000, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )

        AlertDialog(
            onDismissRequest = { nfcWriteTrigger = null },
            icon = {
                // Icona stile "Moneta Fisica" pulsante
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.onSurface, // Sfondo a contrasto netto
                    modifier = Modifier
                        .size(72.dp)
                        .scale(pulseScale) // Applica la pulsazione
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Nfc,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.surface, // Icona in negativo
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    "Pronto per la scrittura",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Avvicina un tag NFC al retro del telefono per programmare il trigger \"${trigger.label}\".",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, // Riportato a primary
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Usa un tag vuoto o riscrivibile (es. NTAG213). Mantieni in posizione finché non senti la vibrazione.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.requestNfcWrite(trigger)
                        nfcWriteTrigger = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), // Riportato a primary
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Avvia scrittura", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { nfcWriteTrigger = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Annulla")
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }
}

// ─────────────────────────────────────────────
// TRIGGER CARD — rinnovata
// ─────────────────────────────────────────────
@Composable
private fun TriggerCard(
    trigger: TriggerEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val isWifi     = trigger.type == "wifi"
    val typeIcon   = if (isWifi) Icons.Default.Wifi else Icons.Default.Bluetooth
    val modeIcon   = if (trigger.locationMode == "precise") Icons.Default.GpsFixed else Icons.Default.GpsNotFixed
    val modeLabel  = if (trigger.locationMode == "precise") "GPS preciso" else "Ultima posizione nota"

    val accentColor = if (isWifi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val containerColor = if (isWifi) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val onContainerColor = if (isWifi) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer

    val cardAlpha = if (trigger.enabled) 1f else 0.55f

    ElevatedCard(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (trigger.enabled) 1.dp else 0.dp
        ),
        colors    = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar icona tipo con colore accent
            Surface(
                shape  = RoundedCornerShape(12.dp),
                color  = if (trigger.enabled) containerColor
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        typeIcon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint     = if (trigger.enabled) onContainerColor
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Info testuale
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    trigger.label,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (trigger.enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Badge modalità posizione
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (trigger.enabled)
                        accentColor.copy(alpha = 0.10f)
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            modeIcon,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint     = if (trigger.enabled) accentColor
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            modeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (trigger.enabled) accentColor
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Toggle abilitato
            Switch(
                checked          = trigger.enabled,
                onCheckedChange  = { onToggle() },
                modifier         = Modifier.padding(horizontal = 2.dp)
            )

            // Elimina
            BouncyIconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Rimuovi",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// NFC TRIGGER CARD — con pulsante "Scrivi tag"
// ─────────────────────────────────────────────
// ─────────────────────────────────────────────
// NFC TRIGGER CARD — Stile "Tag Fisico"
// ─────────────────────────────────────────────
@Composable
private fun NfcTriggerCard(
    trigger: TriggerEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onWriteTag: () -> Unit
) {
    val modeLabel = if (trigger.locationMode == "precise") "GPS preciso" else "Ultima posizione nota"
    val cardAlpha = if (trigger.enabled) 1f else 0.55f

    ElevatedCard(
        modifier  = Modifier.fillMaxWidth().alpha(cardAlpha),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (trigger.enabled) 1.dp else 0.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar icona NFC — Effetto "Moneta / Tag Fisico"
            Surface(
                shape    = androidx.compose.foundation.shape.CircleShape, // Cerchio perfetto
                color    = if (trigger.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Nfc,
                        contentDescription = null,
                        tint     = if (trigger.enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Testo e badge modalità GPS
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    trigger.label,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (trigger.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines   = 1
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Pulsante inline "Scrivi tag" (Stile più pulito e coerente)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (trigger.locationMode == "precise") Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                            null,
                            modifier = Modifier.size(12.dp),
                            tint     = if (trigger.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            modeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (trigger.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (trigger.enabled) {
                        Surface(
                            onClick = onWriteTag,
                            color   = MaterialTheme.colorScheme.primaryContainer,
                            shape   = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier          = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    null,
                                    modifier = Modifier.size(12.dp),
                                    tint     = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Scrivi tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Switch(
                checked         = trigger.enabled,
                onCheckedChange = { onToggle() },
                modifier        = Modifier.padding(horizontal = 2.dp)
            )
            BouncyIconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Rimuovi",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// BOTTOM SHEET: Aggiungi trigger — 3 step animati
// ─────────────────────────────────────────────
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTriggerBottomSheet(
    viewModel: VehicleTriggersViewModel,
    onDismiss: () -> Unit,
    onConfirm: (type: String, identifier: String, label: String, mode: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var step               by remember { mutableIntStateOf(1) }
    var selectedType       by remember { mutableStateOf("") }
    var selectedIdentifier by remember { mutableStateOf("") }
    var selectedLabel      by remember { mutableStateOf("") }
    var selectedMode       by remember { mutableStateOf("precise") }
    var showLastKnownWarning by remember { mutableStateOf(false) }
    var manualInput        by remember { mutableStateOf("") }
    var showManual         by remember { mutableStateOf(false) }

    val wifiNetworks = remember { viewModel.getAvailableWifiNetworks() }
    val currentWifi  = remember { viewModel.getCurrentWifiSsid() }
    val btDevices    = remember { viewModel.getPairedBluetoothDevices() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        dragHandle       = {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        // Contenuto animato all'apertura
        val contentVisible = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { contentVisible.value = true }

        AnimatedVisibility(
            visible = contentVisible.value,
            enter   = fadeIn(tween(220)) + slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMediumLow
                ),
                initialOffsetY = { it / 5 }
            )
        ) {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Limita l'altezza massima del sheet a ~80% dello schermo
                    .fillMaxHeight(0.82f)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            ) {
                // ══════════════════════════════════════════
                // HEADER fisso (non scrolla)
                // ══════════════════════════════════════════
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp, bottom = 20.dp)
                ) {
                    Surface(
                        shape    = RoundedCornerShape(12.dp),
                        color    = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.AutoMode,
                                contentDescription = null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Nuovo trigger",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            when (step) {
                                1    -> "Tipo di trigger"
                                2    -> when (selectedType) {
                                    "wifi"      -> "Rete WiFi"
                                    "nfc"       -> "Nome tag NFC"
                                    else        -> "Dispositivo Bluetooth"
                                }
                                3    -> "Modalità posizione"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StepIndicator(current = step, total = 3)
                }

                HorizontalDivider(
                    color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                )

                // ══════════════════════════════════════════
                // CORPO scrollabile
                // ══════════════════════════════════════════
                AnimatedContent(
                    targetState    = step,
                    label          = "trigger_step",
                    modifier       = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    transitionSpec = {
                        val forward = targetState > initialState
                        (fadeIn(tween(200)) + slideInHorizontally(
                            tween(220),
                            initialOffsetX = { if (forward) it / 4 else -it / 4 }
                        )) togetherWith (fadeOut(tween(150)) + slideOutHorizontally(
                            tween(180),
                            targetOffsetX = { if (forward) -it / 4 else it / 4 }
                        ))
                    }
                ) { currentStep ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 16.dp, bottom = 8.dp)
                    ) {
                        when (currentStep) {

                            // STEP 1 — tipo
                            1 -> {
                                Text(
                                    "Quale evento vuoi usare come trigger?",
                                    style    = MaterialTheme.typography.bodyMedium,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 14.dp)
                                )

                                // Bluetooth spostato in alto poiché è l'unico selezionabile
                                TriggerTypeButton(
                                    icon     = Icons.Default.Bluetooth,
                                    title    = "Disconnessione Bluetooth",
                                    subtitle = "Quando ti allontani da un dispositivo BT (es. autoradio)",
                                    selected = selectedType == "bluetooth",
                                    enabled  = true,
                                    onClick  = { selectedType = "bluetooth" }
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // WiFi disabilitato con il nuovo flag
                                TriggerTypeButton(
                                    icon     = Icons.Default.Wifi,
                                    title    = "Disconnessione WiFi",
                                    subtitle = "Feature futura.",
                                    selected = selectedType == "wifi",
                                    enabled  = false, // <--- DISABILITATO QUI
                                    onClick  = { selectedType = "wifi" }
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // NFC: tap fisico sul tag
                                TriggerTypeButton(
                                    icon     = Icons.Default.Nfc,
                                    title    = "Tag NFC",
                                    subtitle = "Avvicina il telefono al tag per salvare la posizione manualmente.",
                                    selected = selectedType == "nfc",
                                    enabled  = true,
                                    onClick  = { selectedType = "nfc" }
                                )
                            }

                            // STEP 2
                            2 -> {
                                when (selectedType) {
                                    "wifi" -> {
                                        if (currentWifi != null && !showManual) {
                                            SectionLabel(
                                                label  = "Rete attuale",
                                                color  = MaterialTheme.colorScheme.primary,
                                                topPad = 0.dp
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            SuggestionChip(
                                                selected = selectedIdentifier == currentWifi,
                                                label    = currentWifi,
                                                icon     = Icons.Default.Wifi,
                                                onClick  = { selectedIdentifier = currentWifi; selectedLabel = currentWifi }
                                            )
                                            Spacer(modifier = Modifier.height(14.dp))
                                        }
                                        if (wifiNetworks.isNotEmpty() && !showManual) {
                                            SectionLabel(
                                                label  = "Reti nelle vicinanze",
                                                color  = MaterialTheme.colorScheme.onSurfaceVariant,
                                                topPad = 0.dp
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            wifiNetworks.forEach { wifi ->
                                                SuggestionChip(
                                                    selected = selectedIdentifier == wifi.ssid,
                                                    label    = wifi.ssid,
                                                    icon     = Icons.Default.Wifi,
                                                    onClick  = { selectedIdentifier = wifi.ssid; selectedLabel = wifi.ssid }
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                        AnimatedVisibility(visible = showManual) {
                                            OutlinedTextField(
                                                value         = manualInput,
                                                onValueChange = { manualInput = it; selectedIdentifier = it; selectedLabel = it },
                                                label         = { Text("Nome rete (SSID)") },
                                                leadingIcon   = { Icon(Icons.Default.Wifi, null) },
                                                singleLine    = true,
                                                modifier      = Modifier.fillMaxWidth(),
                                                shape         = RoundedCornerShape(14.dp)
                                            )
                                        }
                                        TextButton(onClick = { showManual = !showManual }) {
                                            Icon(
                                                if (showManual) Icons.Default.List else Icons.Default.Edit,
                                                null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (showManual) "Mostra lista reti" else "Inserisci manualmente")
                                        }
                                    }

                                    "bluetooth" -> {
                                        if (btDevices.isEmpty()) {
                                            Surface(
                                                color    = MaterialTheme.colorScheme.errorContainer,
                                                shape    = RoundedCornerShape(14.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier          = Modifier.padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.BluetoothDisabled,
                                                        null,
                                                        tint     = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        "Nessun dispositivo Bluetooth accoppiato.\nAccoppia prima il dispositivo nelle impostazioni di sistema.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                }
                                            }
                                        } else {
                                            SectionLabel(
                                                label  = "Dispositivi accoppiati",
                                                color  = MaterialTheme.colorScheme.secondary,
                                                topPad = 0.dp
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            btDevices.forEach { bt ->
                                                SuggestionChip(
                                                    selected = selectedIdentifier == bt.mac,
                                                    label    = bt.name,
                                                    icon     = Icons.Default.Bluetooth,
                                                    onClick  = { selectedIdentifier = bt.mac; selectedLabel = bt.name }
                                                )
                                            }
                                        }
                                    }

                                    "nfc" -> {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            // Icona stile "Moneta Fisica"
                                            Surface(
                                                shape = androidx.compose.foundation.shape.CircleShape,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(64.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Nfc,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.surface,
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                "Personalizza il tuo Tag",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                "Scegli un nome per riconoscerlo (es. \"Cruscotto Auto\", \"Portachiavi\").",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                                            )

                                            OutlinedTextField(
                                                value         = manualInput,
                                                onValueChange = {
                                                    manualInput        = it
                                                    selectedIdentifier = "nfc_${viewModel.vehicleId}_${it.lowercase().replace(" ", "_")}"
                                                    selectedLabel      = it
                                                },
                                                label       = { Text("Nome tag NFC") },
                                                leadingIcon = { Icon(Icons.Default.Label, null) },
                                                singleLine  = true,
                                                modifier    = Modifier.fillMaxWidth(),
                                                shape       = RoundedCornerShape(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // STEP 3 — modalità GPS
                            3 -> {
                                Text(
                                    "Come vuoi rilevare la posizione?",
                                    style    = MaterialTheme.typography.bodyMedium,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 14.dp)
                                )
                                TriggerTypeButton(
                                    icon     = Icons.Default.GpsFixed,
                                    title    = "GPS preciso",
                                    subtitle = "Acquisisce il GPS al momento. Più accurato ma usa più batteria.",
                                    selected = selectedMode == "precise",
                                    onClick  = { selectedMode = "precise"; showLastKnownWarning = false }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TriggerTypeButton(
                                    icon     = Icons.Default.GpsNotFixed,
                                    title    = "Ultima posizione nota",
                                    subtitle = "Veloce e risparmia batteria. Usa l'ultima posizione registrata.",
                                    selected = selectedMode == "last_known",
                                    onClick  = { selectedMode = "last_known"; showLastKnownWarning = true }
                                )
                                AnimatedVisibility(
                                    visible = showLastKnownWarning,
                                    enter   = fadeIn(tween(200)) + expandVertically(tween(220)),
                                    exit    = fadeOut(tween(150)) + shrinkVertically(tween(180))
                                ) {
                                    Surface(
                                        color    = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape    = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp)
                                    ) {
                                        Row(
                                            modifier          = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(
                                                Icons.Default.WarningAmber,
                                                contentDescription = null,
                                                tint     = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.size(16.dp).padding(top = 1.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "La posizione salvata potrebbe essere imprecisa: viene usata l'ultima rilevata dal telefono, che potrebbe risalire a ore prima.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ══════════════════════════════════════════
                // FOOTER fisso (non scrolla)
                // ══════════════════════════════════════════
                HorizontalDivider(
                    color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    if (step > 1) {
                        TextButton(onClick = { step-- }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Indietro", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text("Annulla", fontWeight = FontWeight.Bold)
                        }
                    }
                    when (step) {
                        1 -> Button(
                            onClick  = { if (selectedType.isNotEmpty()) step = 2 },
                            enabled  = selectedType.isNotEmpty(),
                            shape    = RoundedCornerShape(12.dp)
                        ) { Text("Avanti", fontWeight = FontWeight.Bold) }

                        2 -> Button(
                            onClick  = { if (selectedIdentifier.isNotEmpty()) step = 3 },
                            enabled  = selectedIdentifier.isNotEmpty(),
                            shape    = RoundedCornerShape(12.dp)
                        ) { Text("Avanti", fontWeight = FontWeight.Bold) }

                        3 -> Button(
                            onClick = { onConfirm(selectedType, selectedIdentifier, selectedLabel, selectedMode) },
                            shape   = RoundedCornerShape(12.dp)
                        ) { Text("Aggiungi", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Step indicator — 3 dot pill
// ─────────────────────────────────────────────
@Composable
private fun StepIndicator(current: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            val active = index + 1 == current
            val done   = index + 1 < current
            val width by animateDpAsState(
                targetValue   = if (active) 20.dp else 6.dp,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                label         = "dot_width_$index"
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(RoundedCornerShape(50))
                    .background(
                        when {
                            active -> MaterialTheme.colorScheme.primary
                            done   -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            else   -> MaterialTheme.colorScheme.outlineVariant
                        }
                    )
            )
        }
    }
}

// ─────────────────────────────────────────────
// Section label
// ─────────────────────────────────────────────
@Composable
private fun SectionLabel(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    topPad: androidx.compose.ui.unit.Dp = 8.dp
) {
    Text(
        label.uppercase(),
        style      = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color      = color,
        modifier   = Modifier.padding(top = topPad, bottom = 2.dp)
    )
}

// ─────────────────────────────────────────────
// TriggerTypeButton
// ─────────────────────────────────────────────
@Composable
private fun TriggerTypeButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true, // <-- Parametro aggiunto
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue   = if (selected && enabled) 1.02f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label         = "type_btn_scale"
    )
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor   = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    // Definiamo l'opacità: 100% se abilitato, 40% se disabilitato
    val viewAlpha = if (enabled) 1f else 0.4f

    Surface(
        onClick  = { if (enabled) onClick() }, // Blocca il click se disabilitato
        color    = containerColor,
        shape    = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(viewAlpha)
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape    = RoundedCornerShape(10.dp),
                color    = contentColor.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = contentColor, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color      = contentColor
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.75f)
                )
            }
            AnimatedVisibility(
                visible = selected && enabled,
                enter   = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit    = scaleOut() + fadeOut()
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// SuggestionChip
// ─────────────────────────────────────────────
@Composable
private fun SuggestionChip(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick  = onClick,
        color    = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape    = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                null,
                modifier = Modifier.size(16.dp),
                tint     = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                label,
                style    = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                color    = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
            AnimatedVisibility(
                visible = selected,
                enter   = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit    = scaleOut() + fadeOut()
            ) {
                Icon(
                    Icons.Default.Check,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint     = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Info banner (secondaryContainer)
// ─────────────────────────────────────────────
@Composable
private fun InfoBanner(
    icon: ImageVector,
    title: String,
    body: String,
    bgColor: androidx.compose.ui.graphics.Color,
    fgColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        color    = bgColor,
        shape    = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, null, tint = fgColor, modifier = Modifier.size(20.dp).padding(top = 2.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    title,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = fgColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = fgColor)
            }
        }
    }
}

// ─────────────────────────────────────────────
// Warning banner (errorContainer)
// ─────────────────────────────────────────────
@Composable
private fun WarningBanner(
    icon: ImageVector,
    title: String,
    body: String,
    buttonLabel: String,
    onButton: () -> Unit
) {
    Surface(
        color    = MaterialTheme.colorScheme.errorContainer,
        shape    = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier          = Modifier.padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                icon,
                null,
                tint     = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp).padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onButton,
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(buttonLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Bouncy Icon Button
// ─────────────────────────────────────────────
@Composable
private fun BouncyIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.75f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label         = "bouncy"
    )
    LaunchedEffect(pressed) {
        if (pressed) { kotlinx.coroutines.delay(140); pressed = false }
    }
    IconButton(
        onClick  = { pressed = true; onClick() },
        modifier = Modifier.scale(scale)
    ) { content() }
}