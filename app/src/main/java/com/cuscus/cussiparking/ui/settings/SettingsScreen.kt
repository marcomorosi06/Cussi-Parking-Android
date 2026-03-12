/*
 * SettingsScreen.kt — Material 3 Expressive Rewrite
 * - Design allineato al resto dell'app
 * - Selettore mappa per coordinate custom
 * - Dialog → BottomSheet per Aggiungi Server
 * - Full feature parity maintained
 */
package com.cuscus.cussiparking.ui.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cuscus.cussiparking.data.ServerProfile
import com.cuscus.cussiparking.data.SettingsManager
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.Locale
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.rotate

enum class MapProvider(val label: String) {
    CARTO("CartoDB (Minimal)"),
    OSM("OpenStreetMap (Dettaglio)")
}

enum class MapThemeMode(val label: String) {
    SYSTEM("Sistema"),
    LIGHT("Chiaro"),
    DARK("Scuro")
}

// ─────────────────────────────────────────────
// Spring specs
// ─────────────────────────────────────────────
private val bouncySpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness    = Spring.StiffnessMedium
)

// ─────────────────────────────────────────────
// SETTINGS SCREEN
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onNavigateBack: () -> Unit,
    onNavigateToAuth: (profileId: String) -> Unit,
    onDeleteAccount: (profileId: String, password: String, onResult: (Boolean, String) -> Unit) -> Unit,
    onLogout: (profileId: String) -> Unit,
    onRemoveProfile: (profileId: String) -> Unit,
    onNavigateToWelcome: () -> Unit
) {
    val isOffline   by settingsManager.isOfflineMode.collectAsState()
    val mapBehavior by settingsManager.mapBehavior.collectAsState()
    val profiles    by settingsManager.profiles.collectAsState()

    var showAddServerSheet       by remember { mutableStateOf(false) }
    var showMapPicker            by remember { mutableStateOf(false) }
    var profileToDelete          by remember { mutableStateOf<ServerProfile?>(null) }
    var profileToRename          by remember { mutableStateOf<ServerProfile?>(null) }
    var profileToDeleteAccount   by remember { mutableStateOf<ServerProfile?>(null) }

    // Coordinate custom (state locale per editing)
    var customLatInput by remember { mutableStateOf(settingsManager.customLat.value.toString()) }
    var customLngInput by remember { mutableStateOf(settingsManager.customLng.value.toString()) }

    // Se l'utente apre il map picker mostriamo l'overlay fullscreen
    if (showMapPicker) {
        CustomLocationMapPicker(
            initialLat = settingsManager.customLat.collectAsState().value,
            initialLng = settingsManager.customLng.collectAsState().value,
            onConfirm  = { lat, lng ->
                settingsManager.saveCustomLocation(lat, lng)
                customLatInput = lat.toString()
                customLngInput = lng.toString()
                showMapPicker = false
            },
            onCancel = { showMapPicker = false }
        )
        return
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Impostazioni",
                        style      = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    BouncyIconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor         = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        LazyColumn(
            modifier        = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ══════════════════════════════════════════
            // SEZIONE: SERVER COLLEGATI
            // ══════════════════════════════════════════
            item {
                SectionHeader(icon = Icons.Default.Storage, title = "SERVER COLLEGATI")
            }

            if (profiles.isEmpty()) {
                item {
                    AnimatedEmptyState(
                        icon     = Icons.Outlined.CloudOff,
                        title    = "Nessun server collegato",
                        subtitle = "Aggiungi un server per sincronizzare i tuoi veicoli."
                    )
                }
            }

            items(profiles, key = { it.id }) { profile ->
                AnimatedVisibility(
                    visible = true,
                    enter   = slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec  = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
                    ) + fadeIn()
                ) {
                    ServerProfileCard(
                        profile         = profile,
                        onLogin         = { onNavigateToAuth(profile.id) },
                        onLogout        = { onLogout(profile.id) },
                        onRename        = { profileToRename = profile },
                        onDelete        = { profileToDelete = profile },
                        onDeleteAccount = { profileToDeleteAccount = profile }
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick         = { showAddServerSheet = true },
                    modifier        = Modifier.fillMaxWidth(),
                    shape           = RoundedCornerShape(16.dp),
                    contentPadding  = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(Icons.Default.AddLink, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aggiungi Server", fontWeight = FontWeight.SemiBold)
                }
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }

            // ══════════════════════════════════════════
            // SEZIONE: SINCRONIZZAZIONE
            // ══════════════════════════════════════════
            item {
                SectionHeader(icon = Icons.Default.Sync, title = "SINCRONIZZAZIONE")
            }

            item {
                SettingsCard {
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape    = RoundedCornerShape(12.dp),
                            color    = if (isOffline)
                                MaterialTheme.colorScheme.secondaryContainer
                            else
                                MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AnimatedContent(
                                    targetState  = isOffline,
                                    transitionSpec = {
                                        scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn() togetherWith
                                                scaleOut() + fadeOut()
                                    },
                                    label = "offline_icon"
                                ) { offline ->
                                    Icon(
                                        if (offline) Icons.Default.WifiOff else Icons.Default.Wifi,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint     = if (offline)
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        else
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Modalità Offline",
                                style      = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            AnimatedContent(
                                targetState  = isOffline,
                                transitionSpec = {
                                    slideInVertically { -it } + fadeIn() togetherWith
                                            slideOutVertically { it } + fadeOut()
                                },
                                label = "offline_label"
                            ) { offline ->
                                Text(
                                    if (offline) "Nessun server contattato" else "Sincronizzazione attiva",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked         = isOffline,
                            onCheckedChange = { settingsManager.setOfflineMode(it) }
                        )
                    }
                }
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }

            // ══════════════════════════════════════════
            // SEZIONE: MAPPA
            // ══════════════════════════════════════════
            item {
                SectionHeader(icon = Icons.Default.Map, title = "MAPPA")
            }

            item {
                SettingsCard {
                    Column {
                        Text(
                            "Centro mappa all'apertura",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        ConnectedSegmentControl(
                            options = listOf(
                                Triple("gps", "GPS", Icons.Default.GpsFixed),
                                Triple("vehicle", "Veicolo", Icons.Default.DirectionsCar),
                                Triple("custom", "Custom", Icons.Default.PinDrop)
                            ),
                            selected = mapBehavior,
                            onSelect = { settingsManager.saveMapBehavior(it) }
                        )

                        // ── Pannello coordinate custom ────────
                        AnimatedVisibility(
                            visible = mapBehavior == "custom",
                            enter = expandVertically(
                                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
                            ) + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {

                                // Anteprima coordinate salvate
                                val savedLat by settingsManager.customLat.collectAsState()
                                val savedLng by settingsManager.customLng.collectAsState()
                                val hasCoords = savedLat != 0.0 || savedLng != 0.0

                                if (hasCoords) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(
                                            alpha = 0.5f
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.PinDrop,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                String.format(
                                                    Locale.US,
                                                    "%.5f, %.5f",
                                                    savedLat,
                                                    savedLng
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                // Bottone selettore mappa — CTA principale
                                Button(
                                    onClick = { showMapPicker = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Map,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (hasCoords) "Cambia posizione sulla mappa"
                                        else "Seleziona sulla mappa",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Input manuale come alternativa secondaria
                                Text(
                                    "oppure inserisci manualmente",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customLatInput,
                                        onValueChange = { customLatInput = it },
                                        label = { Text("Lat") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.NorthEast,
                                                null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    OutlinedTextField(
                                        value = customLngInput,
                                        onValueChange = { customLngInput = it },
                                        label = { Text("Lng") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.SouthEast,
                                                null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedButton(
                                    onClick = {
                                        val lat = customLatInput.toDoubleOrNull()
                                        val lng = customLngInput.toDoubleOrNull()
                                        if (lat != null && lng != null) {
                                            settingsManager.saveCustomLocation(lat, lng)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Salva coordinate")
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                // Sezione Info & Supporto
                Text(
                    "INFO APP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )

                // Bottone "Rivedi Tutorial"
                ElevatedCard(
                    onClick = onNavigateToWelcome, // <--- Avvia il tutorial
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.School, // O Icons.Default.Info
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Rivedi il tutorial", fontWeight = FontWeight.SemiBold)
                            Text("Scopri di nuovo come funziona l'app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // In fondo a tutto, il footer animato
                MadeWithLoveFooter()

                Spacer(modifier = Modifier.height(32.dp))
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // ── Bottom Sheet: Aggiungi Server ─────────────────
    if (showAddServerSheet) {
        AddServerBottomSheet(
            onDismiss = { showAddServerSheet = false },
            onConfirm = { label, url, email ->
                val newProfile = settingsManager.addProfile(label, url, email)
                showAddServerSheet = false
                onNavigateToAuth(newProfile.id)
            }
        )
    }

    // ── Dialog: Rinomina Profilo ───────────────────────
    profileToRename?.let { profile ->
        RenameProfileDialog(
            currentLabel = profile.label,
            onDismiss    = { profileToRename = null },
            onConfirm    = { newLabel ->
                settingsManager.updateProfileLabel(profile.id, newLabel)
                profileToRename = null
            }
        )
    }

    // ── Dialog: Elimina Account ────────────────────────
    profileToDeleteAccount?.let { profile ->
        DeleteAccountDialog(
            profile   = profile,
            onDismiss = { profileToDeleteAccount = null },
            onConfirm = { password, onResult ->
                onDeleteAccount(profile.id, password) { success, message ->
                    onResult(success, message)
                    if (success) profileToDeleteAccount = null
                }
            }
        )
    }

    // ── Dialog: Rimuovi Profilo ────────────────────────
    profileToDelete?.let { profile ->
        RemoveProfileDialog(
            profile   = profile,
            onDismiss = { profileToDelete = null },
            onConfirm = { onRemoveProfile(profile.id); profileToDelete = null }
        )
    }
}

// ─────────────────────────────────────────────
// CUSTOM LOCATION MAP PICKER
// Stesso stile di MapSelectionOverlay:
// CartoDB Positron, pin animato, layout asimmetrico
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomLocationMapPicker(
    initialLat: Double,
    initialLng: Double,
    onConfirm: (Double, Double) -> Unit,
    onCancel: () -> Unit
) {
    var currentCenter by remember {
        mutableStateOf(
            GeoPoint(
                if (initialLat != 0.0) initialLat else 41.8902,
                if (initialLng != 0.0) initialLng else 12.4922
            )
        )
    }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var showManualInput by remember { mutableStateOf(false) }

    // ── STATI PER MENU MAPPA ──
    var mapProvider by remember { mutableStateOf(MapProvider.CARTO) }
    var mapThemeMode by remember { mutableStateOf(MapThemeMode.SYSTEM) }
    var showProviderMenu by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }

    // ── ANIMAZIONE DEL PIN CENTRALE ──
    var isMapMoving by remember { mutableStateOf(false) }
    var mapInteractionCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(mapInteractionCount) {
        if (mapInteractionCount > 0) {
            isMapMoving = true
            kotlinx.coroutines.delay(250)
            isMapMoving = false
        }
    }

    val pinOffsetY by animateFloatAsState(
        targetValue = if (isMapMoving) -44f else -24f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pin_bounce_y"
    )

    val shadowScale by animateFloatAsState(
        targetValue = if (isMapMoving) 0.5f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "shadow_scale"
    )

    // ── GESTIONE DINAMICA TILES ──
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkMap = when (mapThemeMode) {
        MapThemeMode.SYSTEM -> isSystemDark
        MapThemeMode.LIGHT -> false
        MapThemeMode.DARK -> true
    }

    val currentTileSource = remember(mapProvider, isDarkMap) {
        when (mapProvider) {
            MapProvider.CARTO -> {
                val themePath = if (isDarkMap) "dark_all" else "light_all"
                object : org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase(
                    "CartoDB_${themePath}", 0, 20, 256, ".png",
                    arrayOf(
                        "https://a.basemaps.cartocdn.com/$themePath/",
                        "https://b.basemaps.cartocdn.com/$themePath/",
                        "https://c.basemaps.cartocdn.com/$themePath/"
                    )
                ) {
                    override fun getTileURLString(pMapTileIndex: Long): String {
                        return baseUrl +
                                org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex) + "/" +
                                org.osmdroid.util.MapTileIndex.getX(pMapTileIndex) + "/" +
                                org.osmdroid.util.MapTileIndex.getY(pMapTileIndex) + mImageFilenameEnding
                    }
                }
            }
            MapProvider.OSM -> TileSourceFactory.MAPNIK
        }
    }

    LaunchedEffect(mapViewRef) {
        mapViewRef?.controller?.setCenter(currentCenter)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Posizione predefinita", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    BouncyIconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Annulla")
                    }
                },
                actions = {
                    // Menu Tema
                    Box {
                        IconButton(onClick = { showThemeMenu = true }) {
                            Icon(
                                imageVector = when (mapThemeMode) {
                                    MapThemeMode.LIGHT -> Icons.Default.LightMode
                                    MapThemeMode.DARK -> Icons.Default.DarkMode
                                    MapThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                },
                                contentDescription = "Tema Mappa"
                            )
                        }
                        DropdownMenu(
                            expanded = showThemeMenu,
                            onDismissRequest = { showThemeMenu = false }
                        ) {
                            MapThemeMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    onClick = { mapThemeMode = mode; showThemeMenu = false },
                                    trailingIcon = if (mapThemeMode == mode) { { Icon(Icons.Default.Check, null) } } else null
                                )
                            }
                        }
                    }

                    // Menu Provider
                    Box {
                        IconButton(onClick = { showProviderMenu = true }) {
                            Icon(Icons.Default.Layers, contentDescription = "Stile Mappa")
                        }
                        DropdownMenu(
                            expanded = showProviderMenu,
                            onDismissRequest = { showProviderMenu = false }
                        ) {
                            MapProvider.entries.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.label) },
                                    onClick = { mapProvider = provider; showProviderMenu = false },
                                    trailingIcon = if (mapProvider == provider) { { Icon(Icons.Default.Check, null) } } else null
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            // ── MAPPA ──────────────────────────────────
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        mapViewRef = this
                        setTileSource(currentTileSource)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        addMapListener(object : MapListener {
                            override fun onScroll(e: ScrollEvent?): Boolean {
                                currentCenter = GeoPoint(mapCenter.latitude, mapCenter.longitude)
                                mapInteractionCount++
                                return true
                            }
                            override fun onZoom(e: ZoomEvent?): Boolean {
                                currentCenter = GeoPoint(mapCenter.latitude, mapCenter.longitude)
                                mapInteractionCount++
                                return true
                            }
                        })
                    }
                },
                update = { mapView ->
                    // Aggiorna la sorgente tile istantaneamente
                    mapView.setTileSource(currentTileSource)
                    mapView.invalidate()
                }
            )

            // ── PIN CENTRALE ANIMATO ────────────────────
            Box(modifier = Modifier.align(Alignment.Center)) {
                // Ombra (disegnata sotto, perfettamente centrata allo zero)
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(width = 16.dp, height = 6.dp)
                        .scale(shadowScale)
                        .background(
                            Color.Black.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(50)
                        )
                )
                // Icona Pin (disegnata sopra, si sposta in alto per poggiare la punta al centro)
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = "Posizione",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = pinOffsetY.dp)
                        .size(48.dp)
                )
            }

            // ── CONTROLLI IN BASSO ──────────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 32.dp)
                    .fillMaxWidth()
            ) {
                // Pill coordinate eleganti centrate in alto
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                    shadowElevation = 2.dp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        String.format(
                            Locale.US, "%.5f, %.5f",
                            currentCenter.latitude, currentCenter.longitude
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Layout asimmetrico: ricerca a sinistra, CTA a destra
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Azione secondaria: input manuale
                    SmallFloatingActionButton(
                        onClick = { showManualInput = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Inserisci coordinate")
                    }

                    // CTA primaria grande ed espressiva
                    ExtendedFloatingActionButton(
                        onClick = { onConfirm(currentCenter.latitude, currentCenter.longitude) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(72.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Usa posizione",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── DIALOG INPUT MANUALE ────────────────────
            if (showManualInput) {
                var inputLat by remember { mutableStateOf("") }
                var inputLng by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showManualInput = false },
                    title = { Text("Vai alle Coordinate", fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(24.dp),
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = inputLat,
                                onValueChange = { inputLat = it },
                                label = { Text("Latitudine") },
                                leadingIcon = { Icon(Icons.Default.NorthEast, contentDescription = null) },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = inputLng,
                                onValueChange = { inputLng = it },
                                label = { Text("Longitudine") },
                                leadingIcon = { Icon(Icons.Default.SouthEast, contentDescription = null) },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val lat = inputLat.toDoubleOrNull()
                                val lng = inputLng.toDoubleOrNull()
                                if (lat != null && lng != null) {
                                    mapViewRef?.controller?.animateTo(GeoPoint(lat, lng))
                                    showManualInput = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Vai") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showManualInput = false }) { Text("Annulla") }
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// BOTTOM SHEET: Aggiungi Server
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddServerBottomSheet(
    onDismiss: () -> Unit,
    onConfirm: (label: String, url: String, email: String) -> Unit
) {
    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var labelInput  by remember { mutableStateOf("") }
    var urlInput    by remember { mutableStateOf("https://") }
    var emailInput  by remember { mutableStateOf("") }
    val isValid     = labelInput.isNotBlank() && urlInput.length > 10 && emailInput.contains("@")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        dragHandle       = { SheetDragHandle() },
        containerColor   = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        val visible = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible.value = true }

        AnimatedVisibility(
            visible = visible.value,
            enter   = fadeIn(tween(220)) + slideInVertically(
                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
            ) { it / 5 }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 16.dp)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            ) {
                Surface(
                    shape    = RoundedCornerShape(14.dp),
                    color    = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AddLink,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Aggiungi Server", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    "Dopo aver aggiunto il server, fai il login per collegare il tuo account.",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExpressiveTextField(
                        value         = labelInput,
                        onValueChange = { labelInput = it },
                        label         = "Nome (es. Casa, Lavoro)",
                        icon          = Icons.Default.Label
                    )
                    ExpressiveTextField(
                        value         = urlInput,
                        onValueChange = { urlInput = it },
                        label         = "URL Server",
                        icon          = Icons.Default.Storage
                    )
                    ExpressiveTextField(
                        value         = emailInput,
                        onValueChange = { emailInput = it },
                        label         = "Email account",
                        icon          = Icons.Default.Email
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Annulla", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick  = { if (isValid) onConfirm(labelInput.trim(), urlInput.trim(), emailInput.trim()) },
                        enabled  = isValid,
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        Text("Aggiungi e Accedi", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────
// SERVER PROFILE CARD
// ─────────────────────────────────────────────
@Composable
private fun ServerProfileCard(
    profile: ServerProfile,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val isLoggedIn = !profile.token.isNullOrBlank()

    val cardColor by animateColorAsState(
        targetValue   = if (isLoggedIn)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else
            MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(400),
        label         = "card_color"
    )

    ElevatedCard(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Header ────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape    = RoundedCornerShape(14.dp),
                    color    = if (isLoggedIn)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState  = isLoggedIn,
                            transitionSpec = {
                                scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn() togetherWith
                                        scaleOut() + fadeOut()
                            },
                            label = "cloud_icon"
                        ) { loggedIn ->
                            Icon(
                                if (loggedIn) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint     = if (loggedIn)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        profile.label,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        profile.serverUrl,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                BouncyIconButton(onClick = onRename) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Rinomina",
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                BouncyIconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Rimuovi",
                        tint     = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Status bar ────────────────────────────
            AnimatedContent(
                targetState  = isLoggedIn,
                transitionSpec = {
                    slideInVertically { it / 2 } + fadeIn() togetherWith
                            slideOutVertically { -it / 2 } + fadeOut()
                },
                label = "status_bar"
            ) { loggedIn ->
                Surface(
                    color = if (loggedIn)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer,
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier              = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier          = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (loggedIn) Icons.Default.Person else Icons.Default.PersonOff,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint     = if (loggedIn)
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                else
                                    MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (loggedIn) "Connesso come ${profile.email}"
                                else "Non connesso (${profile.email})",
                                style    = MaterialTheme.typography.bodySmall,
                                color    = if (loggedIn)
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                else
                                    MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 1
                            )
                        }

                        if (loggedIn) {
                            Row {
                                TextButton(
                                    onClick         = onDeleteAccount,
                                    contentPadding  = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        "Elimina account",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                TextButton(
                                    onClick        = onLogout,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        "Esci",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        } else {
                            TextButton(
                                onClick        = onLogin,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("Accedi", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// DIALOG: Rinomina Profilo
// ─────────────────────────────────────────────
@Composable
private fun RenameProfileDialog(
    currentLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newLabel by remember { mutableStateOf(currentLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon             = { Icon(Icons.Default.Edit, contentDescription = null) },
        title            = { Text("Rinomina Server", fontWeight = FontWeight.Bold) },
        shape            = RoundedCornerShape(24.dp),
        text             = {
            ExpressiveTextField(
                value         = newLabel,
                onValueChange = { newLabel = it },
                label         = "Nuovo nome",
                icon          = Icons.Default.Label
            )
        },
        confirmButton    = {
            Button(
                onClick  = { if (newLabel.isNotBlank()) onConfirm(newLabel.trim()) },
                enabled  = newLabel.isNotBlank(),
                shape    = RoundedCornerShape(12.dp)
            ) { Text("Salva") }
        },
        dismissButton    = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

// ─────────────────────────────────────────────
// DIALOG: Elimina Account
// ─────────────────────────────────────────────
@Composable
private fun DeleteAccountDialog(
    profile: ServerProfile,
    onDismiss: () -> Unit,
    onConfirm: (password: String, onResult: (Boolean, String) -> Unit) -> Unit
) {
    var passwordInput by remember { mutableStateOf("") }
    var isLoading     by remember { mutableStateOf(false) }
    var errorMsg      by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        icon             = {
            Icon(
                Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title            = { Text("Elimina Account", fontWeight = FontWeight.Bold) },
        shape            = RoundedCornerShape(24.dp),
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape    = RoundedCornerShape(14.dp),
                    color    = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        "Stai per eliminare l'account su '${profile.label}'. " +
                                "Tutti i veicoli di cui sei l'unico proprietario verranno cancellati. " +
                                "Verrai rimosso da tutti i veicoli condivisi.",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                OutlinedTextField(
                    value                  = passwordInput,
                    onValueChange          = { passwordInput = it; errorMsg = null },
                    label                  = { Text("Conferma con la tua password") },
                    leadingIcon            = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation   = PasswordVisualTransformation(),
                    singleLine             = true,
                    modifier               = Modifier.fillMaxWidth(),
                    isError                = errorMsg != null,
                    shape                  = RoundedCornerShape(14.dp),
                    supportingText         = errorMsg?.let { err ->
                        { Text(err, color = MaterialTheme.colorScheme.error) }
                    }
                )
            }
        },
        confirmButton    = {
            Button(
                onClick  = {
                    isLoading = true
                    onConfirm(passwordInput) { success, message ->
                        isLoading = false
                        if (!success) errorMsg = message
                    }
                },
                enabled  = passwordInput.isNotBlank() && !isLoading,
                colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape    = RoundedCornerShape(12.dp)
            ) {
                AnimatedContent(targetState = isLoading, label = "loading_btn") { loading ->
                    if (loading) CircularProgressIndicator(
                        modifier    = Modifier.size(18.dp),
                        color       = MaterialTheme.colorScheme.onError,
                        strokeWidth = 2.dp
                    )
                    else Text("Elimina Account")
                }
            }
        },
        dismissButton    = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Annulla") }
        }
    )
}

// ─────────────────────────────────────────────
// DIALOG: Rimuovi Profilo
// ─────────────────────────────────────────────
@Composable
private fun RemoveProfileDialog(
    profile: ServerProfile,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon             = {
            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        title            = { Text("Rimuovi Server", fontWeight = FontWeight.Bold) },
        shape            = RoundedCornerShape(24.dp),
        text             = {
            Text(
                "Rimuovere \"${profile.label}\" (${profile.serverUrl})? " +
                        "I veicoli associati verranno rimossi anche dal dispositivo.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton    = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape   = RoundedCornerShape(12.dp)
            ) { Text("Rimuovi") }
        },
        dismissButton    = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

// ─────────────────────────────────────────────
// Section Header
// ─────────────────────────────────────────────
@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint     = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text          = title,
            style         = MaterialTheme.typography.labelSmall,
            color         = MaterialTheme.colorScheme.primary,
            fontWeight    = FontWeight.Bold,
            letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp)
        )
    }
}

// ─────────────────────────────────────────────
// Settings Card wrapper
// ─────────────────────────────────────────────
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

// ─────────────────────────────────────────────
// Animated Empty State
// ─────────────────────────────────────────────
@Composable
private fun AnimatedEmptyState(icon: ImageVector, title: String, subtitle: String) {
    val pulse = rememberInfiniteTransition(label = "empty_pulse")
    val pulseScale by pulse.animateFloat(
        initialValue  = 0.95f,
        targetValue   = 1.05f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "pulse"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier            = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape    = RoundedCornerShape(20.dp),
                color    = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(64.dp).scale(pulseScale)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint     = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────────────
// ConnectedSegmentControl
// ─────────────────────────────────────────────
@Composable
private fun ConnectedSegmentControl(
    options: List<Triple<String, String, ImageVector>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label, icon) ->
            val isSelected = selected == value
            val isFirst    = index == 0
            val isLast     = index == options.lastIndex
            val cornerFull  = 50.dp
            val cornerSmall = 4.dp

            val bgColor by animateColorAsState(
                targetValue   = if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                animationSpec = tween(220),
                label         = "seg_bg_$index"
            )
            val contentColor by animateColorAsState(
                targetValue   = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(220),
                label         = "seg_fg_$index"
            )
            val itemScale by animateFloatAsState(
                targetValue   = if (isSelected) 1.04f else 1f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                label         = "seg_scale_$index"
            )

            Surface(
                onClick  = { onSelect(value) },
                modifier = Modifier
                    .weight(1f)
                    .scale(itemScale)
                    .then(if (!isLast) Modifier.padding(end = 2.dp) else Modifier),
                shape    = RoundedCornerShape(
                    topStart    = if (isFirst) cornerFull else cornerSmall,
                    bottomStart = if (isFirst) cornerFull else cornerSmall,
                    topEnd      = if (isLast)  cornerFull else cornerSmall,
                    bottomEnd   = if (isLast)  cornerFull else cornerSmall
                ),
                color = bgColor
            ) {
                Row(
                    modifier              = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp), tint = contentColor)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        label,
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color      = contentColor
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Shared: Expressive TextField
// ─────────────────────────────────────────────
@Composable
private fun ExpressiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isError: Boolean = false
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label) },
        leadingIcon   = { Icon(icon, contentDescription = null) },
        singleLine    = true,
        modifier      = Modifier.fillMaxWidth(),
        isError       = isError,
        shape         = RoundedCornerShape(14.dp)
    )
}

// ─────────────────────────────────────────────
// Drag handle condiviso
// ─────────────────────────────────────────────
@Composable
private fun SheetDragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 14.dp, bottom = 6.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
    )
}

// ─────────────────────────────────────────────
// Bouncy Icon Button
// ─────────────────────────────────────────────
@Composable
private fun BouncyIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.78f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label         = "bouncy_icon"
    )
    IconButton(
        onClick  = { pressed = true; onClick() },
        modifier = Modifier.scale(scale)
    ) { content() }
    LaunchedEffect(pressed) {
        if (pressed) { kotlinx.coroutines.delay(150); pressed = false }
    }
}

@Composable
fun MadeWithLoveFooter() {
    var isTriggered by remember { mutableStateOf(false) }

    // Anima da 0 a 1 quando viene triggerato
    val mergeProgress by animateFloatAsState(
        targetValue = if (isTriggered) 1f else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "merge_progress"
    )

    // Cuore che pulsa (inizia solo quando l'animazione di fusione è quasi finita)
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat_transition")
    val heartPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = if (mergeProgress > 0.8f) {
            infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1200
                    1f at 0             // Inizio battito
                    1.3f at 150         // TUM
                    1f at 300           // (pausa brevissima)
                    1.3f at 450         // TUM
                    1f at 600           // Rilascio
                    1f at 1200          // Pausa lunga prima del prossimo battito
                },
                repeatMode = RepeatMode.Restart
            )
        } else {
            infiniteRepeatable(tween(100)) // Fallback invisibile
        },
        label = "heart_pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "App fatta con ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Il box magico dove avviene la trasformazione
        Box(contentAlignment = Alignment.Center, modifier = Modifier.width(32.dp)) {
            if (mergeProgress < 0.5f) {
                // Fase 1: Il < e il 3 si avvicinano e ruotano
                val phase1 = mergeProgress * 2f // normalizza da 0 a 1
                val offset = phase1 * 8f // si spostano verso il centro
                val rotation = phase1 * 45f // ruotano leggermente

                Row {
                    Text(
                        "<",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .offset(x = offset.dp)
                            .rotate(rotation)
                    )
                    Text(
                        "3",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .offset(x = (-offset).dp)
                            .rotate(-rotation)
                    )
                }
            } else {
                // Fase 2: Appare l'icona Material e inizia a pulsare
                val phase2 = (mergeProgress - 0.5f) * 2f // normalizza da 0 a 1
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Cuore",
                    tint = Color(0xFFE63946), // Un bel rosso acceso!
                    modifier = Modifier
                        .size(18.dp)
                        .scale(phase2 * heartPulse) // Appare gradualmente, poi batte
                )
            }
        }

        Text(
            " da ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Il tuo nome (Clickable per avviare la magia!)
        Text(
            "marco morosi",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { isTriggered = true }
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}