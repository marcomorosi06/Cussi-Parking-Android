package com.cuscus.cussiparking.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.cuscus.cussiparking.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.cuscus.cussiparking.CussiParkingApplication
import com.cuscus.cussiparking.data.ServerProfile
import com.cuscus.cussiparking.data.SettingsManager
import com.cuscus.cussiparking.network.Vehicle
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.toArgb
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.isSystemInDarkTheme

import androidx.core.graphics.drawable.DrawableCompat
import com.cuscus.cussiparking.ui.settings.MapProvider
import com.cuscus.cussiparking.ui.settings.MapThemeMode
import org.osmdroid.tileprovider.tilesource.ITileSource
import android.annotation.SuppressLint
import androidx.compose.foundation.shape.CircleShape

private fun createMaterialMarkerDrawable(
    context: android.content.Context,
    colorArgb: Int
): Drawable {
    // 1. Proporzioni slanciate (più alto che largo)
    val width = 100
    val height = 160 // Allungato verticalmente
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 2. Disegniamo il corpo del pin (Goccia geometricamente perfetta)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = Paint.Style.FILL
    }

    val path = Path().apply {
        // Creiamo un arco che forma la "testa" rotonda del pin.
        // Parte dal lato in basso a sinistra (150 gradi) e fa il giro
        // fin sopra, arrivando in basso a destra (spazzando 240 gradi).
        addArc(
            RectF(0f, 0f, width.toFloat(), width.toFloat()),
            150f,
            240f
        )
        // Linea dritta che scende verso la punta estrema in basso
        lineTo(width / 2f, height.toFloat())
        // Chiude il tracciato tornando al punto di partenza dell'arco
        close()
    }
    canvas.drawPath(path, paint)

    // 3. Disegniamo il cerchio bianco centrale
    val circleCenterY = width / 2f
    val innerCircleRadius = width * 0.32f
    val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(width / 2f, circleCenterY, innerCircleRadius, whitePaint)

    // 4. Aggiungiamo l'icona dell'auto al centro
    // Cerca l'icona vettoriale XML nel tuo progetto (assicurati di averne creata una)
    val iconId = context.resources.getIdentifier("ic_directions_car", "drawable", context.packageName)

    // Fallback all'icona di sistema se non trova la tua
    val fallbackId = if (iconId != 0) iconId else android.R.drawable.ic_menu_directions

    ContextCompat.getDrawable(context, fallbackId)?.let { drawable ->
        // Coloriamo l'icona con lo stesso colore del pin per un look integrato
        DrawableCompat.setTint(drawable, colorArgb)

        // Calcoliamo le dimensioni per centrarla nel buco bianco
        val iconSize = (innerCircleRadius * 1.5f).toInt()
        val left = (width / 2f - iconSize / 2f).toInt()
        val top = (circleCenterY - iconSize / 2f).toInt()

        drawable.setBounds(left, top, left + iconSize, top + iconSize)
        drawable.draw(canvas)
    }

    return BitmapDrawable(context.resources, bitmap)
}

// ─────────────────────────────────────────────
// Spring presets
// ─────────────────────────────────────────────
private val bouncySpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness    = Spring.StiffnessMedium
)

private val gentleSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness    = Spring.StiffnessMediumLow
)

// ─────────────────────────────────────────────
// HOME SCREEN
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onLogout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMembers: (vehicleServerId: Int, vehicleName: String, profileId: String) -> Unit,
    onNavigateToTriggers: (vehicleId: Int, vehicleName: String) -> Unit,
    onNavigateToLogs: (Int, String, Boolean) -> Unit
) {
    val context         = LocalContext.current
    val app             = context.applicationContext as CussiParkingApplication
    val settingsManager = app.settingsManager
    var vehicleToDelete by remember { mutableStateOf<Vehicle?>(null) }

    val vehicles            by viewModel.vehicles.collectAsState()
    val isLoading           by viewModel.isLoading.collectAsState()
    val errorMessage        by viewModel.errorMessage.collectAsState()
    val isUnreachable       by viewModel.isServerUnreachable.collectAsState()
    val unreachableProfiles by viewModel.unreachableProfiles.collectAsState()
    val isOffline           by settingsManager.isOfflineMode.collectAsState()
    val profiles            by settingsManager.profiles.collectAsState()
    val loggedProfiles      = profiles.filter { !it.token.isNullOrBlank() }

    var vehicleForMap        by remember { mutableStateOf<Vehicle?>(null) }
    var showAddDialog        by remember { mutableStateOf(false) }
    var showJoinByCodeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = context.packageName
        viewModel.fetchLocations()
    }

    BackHandler(enabled = vehicleForMap != null) { vehicleForMap = null }

    Crossfade(targetState = vehicleForMap, label = "mapvslist") { mapVehicle ->
        if (mapVehicle != null) {
            MapSelectionOverlay(
                vehicle         = mapVehicle,
                otherVehicles   = vehicles,
                settingsManager = settingsManager,
                onSave          = { lat, lng ->
                    viewModel.parkVehicle(mapVehicle.id, lat, lng)
                    vehicleForMap = null
                },
                onCancel = { vehicleForMap = null }
            )
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(stringResource(R.string.i_miei_veicoli), fontWeight = FontWeight.Bold)
                                AnimatedVisibility(
                                    visible = isLoading,
                                    enter   = fadeIn() + expandVertically(),
                                    exit    = fadeOut() + shrinkVertically()
                                ) {
                                    LinearWavyProgressIndicator(
                                        modifier = Modifier
                                            .width(160.dp)
                                            .padding(top = 2.dp)
                                    )
                                }
                                if (!isLoading && vehicles.isNotEmpty()) {
                                    Text(
                                        if (vehicles.size == 1) stringResource(R.string.veicolo_singolo, vehicles.size) else stringResource(R.string.veicoli_multiplo, vehicles.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                    )
                                }
                            }
                        },
                        actions = {
                            if (loggedProfiles.isNotEmpty()) {
                                BouncyIconButton(onClick = { showJoinByCodeDialog = true }) {
                                    Icon(Icons.Default.VpnKey, contentDescription = stringResource(R.string.unisciti_con_codice))
                                }
                            }
                            BouncyIconButton(onClick = onNavigateToSettings) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.impostazioni))
                            }
                            BouncyIconButton(onClick = { viewModel.fetchLocations() }) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.aggiorna))
                            }
                        }
                    )
                },
                floatingActionButton = {
                    val fabScale by animateFloatAsState(
                        targetValue   = if (isLoading) 0.9f else 1f,
                        animationSpec = bouncySpring,
                        label         = "fabscale"
                    )
                    ExtendedFloatingActionButton(
                        onClick        = { showAddDialog = true },
                        icon           = { Icon(Icons.Default.Add, contentDescription = null) },
                        text           = { Text(stringResource(R.string.aggiungi), fontWeight = FontWeight.SemiBold) },
                        modifier       = Modifier.scale(fabScale),
                        shape          = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor   = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // ── Status banners ─────────────────────────
                    if (isOffline) {
                        StatusBanner(
                            icon    = Icons.Default.WifiOff,
                            message = stringResource(R.string.modalita_offline_desc),
                            bgColor = MaterialTheme.colorScheme.secondaryContainer,
                            fgColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (isUnreachable && !isOffline) {
                        StatusBanner(
                            icon    = Icons.Default.CloudOff,
                            message = if (unreachableProfiles.size == 1)
                                stringResource(R.string.server_non_raggiungibile_singolo, unreachableProfiles.first())
                            else
                                stringResource(R.string.server_non_raggiungibili_multiplo, unreachableProfiles.joinToString(", ")),
                            bgColor = MaterialTheme.colorScheme.errorContainer,
                            fgColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    // ── Main content ───────────────────────────
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when {
                            isLoading && vehicles.isEmpty() -> ExpressiveLoadingState()

                            vehicles.isEmpty() -> ExpressiveEmptyState(
                                loggedProfiles = loggedProfiles,
                                onAddVehicle   = { showAddDialog = true }
                            )

                            else -> {
                                LazyColumn(
                                    contentPadding = PaddingValues(
                                        start  = 16.dp,
                                        top    = 12.dp,
                                        end    = 16.dp,
                                        bottom = 100.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(vehicles, key = { it.id }) { vehicle ->
                                        VehicleCard(
                                            vehicle          = vehicle,
                                            showServerBadge  = loggedProfiles.size > 1,
                                            onParkGps        = { lat, lng -> viewModel.parkVehicle(vehicle.id, lat, lng) },
                                            onParkMap        = { vehicleForMap = vehicle },
                                            onDelete         = { vehicleToDelete = vehicle },
                                            onManageMembers  = {
                                                vehicle.serverId?.let { sid ->
                                                    val pid = vehicle.serverProfileId ?: return@let
                                                    onNavigateToMembers(sid, vehicle.name, pid)
                                                }
                                            },
                                            onManageTriggers = { onNavigateToTriggers(vehicle.id, vehicle.name) },
                                            onLogsClick = {
                                                if (vehicle.serverId != null) {
                                                    onNavigateToLogs(vehicle.serverId!!, vehicle.serverProfileId ?: "", vehicle.role == "owner")
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        errorMessage?.let { msg ->
                            Snackbar(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(msg, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── DIALOGS ────────────────────────────────────────────────
    if (showAddDialog) {
        AddVehicleBottomSheet(
            isOffline      = isOffline,
            loggedProfiles = loggedProfiles,
            onDismiss      = { showAddDialog = false },
            onConfirm      = { name, forceLocal, profileId ->
                viewModel.addVehicle(name, forceLocalOnly = forceLocal, profileId = profileId)
                showAddDialog = false
            }
        )
    }

    if (showJoinByCodeDialog) {
        val joinResult by viewModel.joinResult.collectAsState()
        JoinByCodeBottomSheet(
            loggedProfiles = loggedProfiles,
            isLoading      = isLoading,
            joinResult     = joinResult,
            onDismiss      = { showJoinByCodeDialog = false },
            onJoin         = { code, profileId -> viewModel.joinWithCode(code, profileId) },
            onJoinConsumed = {
                showJoinByCodeDialog = false
                viewModel.clearJoinResult()
                viewModel.fetchLocations()
            }
        )
    }
    vehicleToDelete?.let { vehicle ->
        AlertDialog(
            onDismissRequest = { vehicleToDelete = null },
            icon = {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.elimina_veicolo_titolo),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.elimina_veicolo_descrizione, vehicle.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            shape = RoundedCornerShape(24.dp), // Stile coerente con il resto dell'app
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteVehicle(vehicle.id)
                        vehicleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.elimina), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { vehicleToDelete = null }
                ) {
                    Text(stringResource(R.string.annulla), fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────
// Expressive full-screen loading state
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveLoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ContainedLoadingIndicator()
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            stringResource(R.string.caricamento_veicoli),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────
// Expressive empty state
// ─────────────────────────────────────────────
@Composable
private fun ExpressiveEmptyState(
    loggedProfiles: List<ServerProfile>,
    onAddVehicle: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "emptypulse")
    val pulseScale by pulse.animateFloat(
        initialValue  = 0.93f,
        targetValue   = 1.07f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulsescale"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape    = RoundedCornerShape(28.dp),
            color    = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(88.dp).scale(pulseScale)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.nessun_veicolo),
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (loggedProfiles.isEmpty())
                "Vai nelle Impostazioni per collegare un server."
            else
                "Tocca + per aggiungere il tuo primo veicolo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(onClick = onAddVehicle, shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.aggiungi_veicolo), fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────────────────────────
// Status banner
// ─────────────────────────────────────────────
@Composable
private fun StatusBanner(
    icon: ImageVector, message: String, bgColor: Color, fgColor: Color
) {
    Surface(color = bgColor, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = fgColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                message,
                style      = MaterialTheme.typography.bodySmall,
                color      = fgColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─────────────────────────────────────────────
// VEHICLE CARD — struttura rinnovata
// Layout: header compatto → posizione → azioni
// ─────────────────────────────────────────────
@Composable
fun VehicleCard(
    vehicle: com.cuscus.cussiparking.network.Vehicle,
    showServerBadge: Boolean,
    onParkGps: (Double, Double) -> Unit,
    onParkMap: () -> Unit,
    onDelete: () -> Unit,
    onManageMembers: () -> Unit,
    onManageTriggers: () -> Unit,
    onLogsClick: () -> Unit
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val isOwner = vehicle.role == "owner"
    val isLocalOnly = vehicle.serverId == null
    var showGpsConfirmDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            try {
                @SuppressLint("MissingPermission")
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) onParkGps(loc.latitude, loc.longitude)
                }
            } catch (_: SecurityException) { }
        }
    }

    val syncInfo: SyncInfo = when {
        isLocalOnly -> SyncInfo(
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Default.PhoneAndroid,
            label = stringResource(R.string.solo_locale)
        )
        vehicle.syncState == 1 -> SyncInfo(
            iconTint = Color(0xFF2E7D32),
            icon = Icons.Default.CloudDone,
            label = stringResource(R.string.sincronizzato)
        )
        vehicle.syncState == 2 -> SyncInfo(
            iconTint = Color(0xFFF57F17),
            icon = Icons.Default.CloudUpload,
            label = stringResource(R.string.in_attesa)
        )
        else -> SyncInfo(
            iconTint = MaterialTheme.colorScheme.error,
            icon = Icons.Default.CloudOff,
            label = stringResource(R.string.non_sincronizzato)
        )
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vehicle.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = syncInfo.icon,
                            contentDescription = null,
                            tint = syncInfo.iconTint,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = syncInfo.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (showServerBadge && !vehicle.serverLabel.isNullOrBlank()) {
                            Text(
                                text = " • ${vehicle.serverLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onManageTriggers) {
                        Icon(
                            Icons.Default.AutoMode,
                            contentDescription = stringResource(R.string.trigger),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

            Box(modifier = Modifier.padding(16.dp)) {
                if (vehicle.lat != null && vehicle.lng != null) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            val updatedByStr = vehicle.lastUpdatedBy?.let { stringResource(R.string.aggiornato_da, it) } ?: stringResource(R.string.posizione_registrata)
                            val updatedAtStr = vehicle.updatedAt?.let {
                                val fmt = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
                                stringResource(R.string.aggiornato_il, fmt.format(Date(it * 1000)))
                            } ?: ""

                            Text(
                                text = "$updatedByStr $updatedAtStr",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                val uri = Uri.parse("geo:${vehicle.lat},${vehicle.lng}?q=${vehicle.lat},${vehicle.lng}(${Uri.encode(vehicle.name)})")
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                catch (_: Exception) { Toast.makeText(context, context.getString(R.string.app_non_trovata), Toast.LENGTH_SHORT).show() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.raggiungi_il_veicolo), fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.nessuna_posizione_registrata),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (ok) {
                                showGpsConfirmDialog = true
                            } else {
                                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.usa_gps))
                    }

                    OutlinedButton(
                        onClick = onParkMap,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.mappa))
                    }
                }

                Row {
                    if (!isLocalOnly) {
                        IconButton(onClick = onLogsClick) {
                            Icon(Icons.Default.History, contentDescription = stringResource(R.string.log_posizioni), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onManageMembers) {
                            Icon(Icons.Default.Group, contentDescription = stringResource(R.string.membri), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (isOwner || isLocalOnly) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.elimina), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    if (showGpsConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showGpsConfirmDialog = false },
            title = { Text(stringResource(R.string.conferma_gps_titolo)) },
            text = { Text(stringResource(R.string.conferma_gps_testo)) },
            confirmButton = {
                Button(onClick = {
                    showGpsConfirmDialog = false
                    @SuppressLint("MissingPermission")
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                        if (loc != null) onParkGps(loc.latitude, loc.longitude)
                    }
                }) {
                    Text(stringResource(R.string.conferma))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGpsConfirmDialog = false }) {
                    Text(stringResource(R.string.annulla))
                }
            }
        )
    }
}

// Helper data class
private data class SyncInfo(
    val iconTint: Color,
    val icon: ImageVector,
    val label: String
)

// ─────────────────────────────────────────────
// StatusChip
// ─────────────────────────────────────────────
@Composable
private fun StatusChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    background: Color
) {
    Surface(
        color  = background,
        shape  = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint     = tint
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
}

// ─────────────────────────────────────────────
// BOTTOM SHEET HELPER — ingresso/uscita animati
// Wrap comune per entrambi i bottom sheet.
// Usa slideInVertically con spring + fadeIn.
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimatedBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState       = sheetState,
        dragHandle       = {
            // Drag handle personalizzato — pill più morbida
            Box(
                modifier = Modifier
                    .padding(top = 14.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        // Ingresso: slide dal basso + fade combinati
        sheetMaxWidth  = BottomSheetDefaults.SheetMaxWidth
    ) {
        // Contenuto animato: scala + fade su apertura
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
            Column(content = content)
        }
    }
}

// ─────────────────────────────────────────────
// BOTTOM SHEET: Aggiungi veicolo
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleBottomSheet(
    isOffline: Boolean,
    loggedProfiles: List<ServerProfile>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, forceLocal: Boolean, profileId: String?) -> Unit
) {
    val sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newVehicleName   by remember { mutableStateOf("") }
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var destinationChosen by remember { mutableStateOf(false) }

    val isOnlineAvailable = !isOffline && loggedProfiles.isNotEmpty()
    val canConfirm        = newVehicleName.isNotBlank() && (!isOnlineAvailable || destinationChosen)

    AnimatedBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 16.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        ) {
            // Icona + titolo
            Surface(
                shape  = RoundedCornerShape(14.dp),
                color  = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.aggiungi_veicolo),
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            Text(
                stringResource(R.string.scegli_un_nome_e_dove_salvare_il_ve),
                style  = MaterialTheme.typography.bodyMedium,
                color  = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value         = newVehicleName,
                onValueChange = { newVehicleName = it },
                label         = { Text(stringResource(R.string.nome_veicolo)) },
                leadingIcon   = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(16.dp)
            )

            if (isOnlineAvailable) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    stringResource(R.string.dove_salvare),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))

                DestinationRow(
                    selected = destinationChosen && selectedProfileId == null,
                    onClick  = { selectedProfileId = null; destinationChosen = true },
                    title    = stringResource(R.string.solo_su_questo_dispositivo),
                    subtitle = stringResource(R.string.non_visibile_agli_altri)
                )
                loggedProfiles.forEach { profile ->
                    DestinationRow(
                        selected = destinationChosen && selectedProfileId == profile.id,
                        onClick  = { selectedProfileId = profile.id; destinationChosen = true },
                        title    = "Server: ${profile.label}",
                        subtitle = profile.email
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape  = RoundedCornerShape(14.dp),
                    color  = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier          = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            if (isOffline) stringResource(R.string.modalita_offline_locale)
                            else stringResource(R.string.nessun_server_locale),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Row(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.annulla), fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        val forceLocal = !destinationChosen || isOffline || loggedProfiles.isEmpty()
                        onConfirm(newVehicleName, forceLocal, selectedProfileId)
                    },
                    enabled = canConfirm,
                    shape   = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.salva_veicolo), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────
// Destination radio row (animated selection)
// ─────────────────────────────────────────────
@Composable
private fun DestinationRow(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    subtitle: String
) {
    val rowScale by animateFloatAsState(
        targetValue   = if (selected) 1.02f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label         = "destscale"
    )
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(12.dp),
        color    = if (selected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        else
            MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .scale(rowScale)
            .padding(vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    title,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// BOTTOM SHEET: Unisciti con codice
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinByCodeBottomSheet(
    loggedProfiles: List<ServerProfile>,
    isLoading: Boolean,
    joinResult: Any?,
    onDismiss: () -> Unit,
    onJoin: (code: String, profileId: String) -> Unit,
    onJoinConsumed: () -> Unit
) {
    val sheetState      = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var codeInput       by remember { mutableStateOf("") }
    var selectedProfile by remember { mutableStateOf(loggedProfiles.firstOrNull()) }

    LaunchedEffect(joinResult) { if (joinResult != null) onJoinConsumed() }

    AnimatedBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 16.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        ) {
            // Icona + titolo
            Surface(
                shape  = RoundedCornerShape(14.dp),
                color  = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.VpnKey,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.unisciti_con_codice),
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            Text(
                stringResource(R.string.inserisci_il_codice_condiviso_dal_p),
                style  = MaterialTheme.typography.bodyMedium,
                color  = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value         = codeInput,
                onValueChange = { codeInput = it.uppercase().trim() },
                label         = { Text(stringResource(R.string.codice_invito)) },
                leadingIcon   = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(16.dp)
            )

            if (loggedProfiles.size > 1) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    stringResource(R.string.su_quale_server),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                loggedProfiles.forEach { profile ->
                    DestinationRow(
                        selected = selectedProfile?.id == profile.id,
                        onClick  = { selectedProfile = profile },
                        title    = profile.label,
                        subtitle = profile.email
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Row(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.annulla), fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick  = { selectedProfile?.let { onJoin(codeInput, it.id) } },
                    enabled  = codeInput.isNotBlank() && selectedProfile != null && !isLoading,
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    AnimatedContent(
                        targetState = isLoading,
                        label       = "joinbtn",
                        transitionSpec = {
                            fadeIn(tween(160)) togetherWith fadeOut(tween(160))
                        }
                    ) { loading ->
                        if (loading) CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp),
                            color       = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        else Text(stringResource(R.string.unisciti), fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────
// MAP SELECTION OVERLAY — invariato
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSelectionOverlay(
    vehicle: com.cuscus.cussiparking.network.Vehicle,
    otherVehicles: List<com.cuscus.cussiparking.network.Vehicle>,
    settingsManager: com.cuscus.cussiparking.data.SettingsManager,
    onSave: (Double, Double) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val mapBehavior by settingsManager.mapBehavior.collectAsState()
    val customLat by settingsManager.customLat.collectAsState()
    val customLng by settingsManager.customLng.collectAsState()

    var currentMapCenter by remember { mutableStateOf(GeoPoint(41.8902, 12.4922)) }
    var mapViewReference by remember { mutableStateOf<MapView?>(null) }
    var showManualInput by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val markerColorArgb = MaterialTheme.colorScheme.tertiary.toArgb()

    var mapProvider by remember { mutableStateOf(MapProvider.CARTO) }
    var mapThemeMode by remember { mutableStateOf(MapThemeMode.LIGHT) }
    var showProviderMenu by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }

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
        label = "pinbouncey"
    )

    val shadowScale by animateFloatAsState(
        targetValue = if (isMapMoving) 0.6f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "shadowscale"
    )

    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkMap = when (mapThemeMode) {
        MapThemeMode.SYSTEM -> isSystemDark
        MapThemeMode.LIGHT -> false
        MapThemeMode.DARK -> true
    }

    val currentTileSource: ITileSource? = remember(mapProvider, isDarkMap) {
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

    LaunchedEffect(mapViewReference) {
        mapViewReference ?: return@LaunchedEffect
        when (mapBehavior) {
            "vehicle" -> if (vehicle.lat != null && vehicle.lng != null) {
                val p = GeoPoint(vehicle.lat, vehicle.lng); currentMapCenter = p
                mapViewReference?.controller?.setCenter(p)
            }
            "custom" -> {
                val p = GeoPoint(customLat, customLng); currentMapCenter = p
                mapViewReference?.controller?.setCenter(p)
            }
            "gps" -> {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    @SuppressLint("MissingPermission")
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                        if (loc != null) {
                            val p = GeoPoint(loc.latitude, loc.longitude); currentMapCenter = p
                            mapViewReference?.controller?.setCenter(p)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dove_veicolo, vehicle.name), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.annulla))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showThemeMenu = true }) {
                            Icon(
                                imageVector = when (mapThemeMode) {
                                    MapThemeMode.LIGHT -> Icons.Default.LightMode
                                    MapThemeMode.DARK -> Icons.Default.DarkMode
                                    MapThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                },
                                contentDescription = stringResource(R.string.tema_mappa)
                            )
                        }
                        DropdownMenu(
                            expanded = showThemeMenu,
                            onDismissRequest = { showThemeMenu = false }
                        ) {
                            MapThemeMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(id = mode.labelResId)) },
                                    onClick = { mapThemeMode = mode; showThemeMenu = false },
                                    trailingIcon = if (mapThemeMode == mode) { { Icon(Icons.Default.Check, null) } } else null
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(onClick = { showProviderMenu = true }) {
                            Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.stile_mappa))
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
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        mapViewReference = this
                        setTileSource(currentTileSource)
                        setMultiTouchControls(true)
                        controller.setZoom(18.0)

                        addMapListener(object : MapListener {
                            override fun onScroll(e: ScrollEvent?): Boolean {
                                currentMapCenter = GeoPoint(mapCenter.latitude, mapCenter.longitude)
                                mapInteractionCount++
                                return true
                            }
                            override fun onZoom(e: ZoomEvent?): Boolean {
                                currentMapCenter = GeoPoint(mapCenter.latitude, mapCenter.longitude)
                                mapInteractionCount++
                                return true
                            }
                        })
                    }
                },
                update = { mapView ->
                    mapView.setTileSource(currentTileSource)
                    mapView.overlays.clear()

                    val modernMarkerIcon = createMaterialMarkerDrawable(context, markerColorArgb)

                    otherVehicles.forEach { v ->
                        if (v.lat != null && v.lng != null) {
                            Marker(mapView).also { m ->
                                m.position = GeoPoint(v.lat, v.lng)
                                m.title = v.name
                                m.icon = modernMarkerIcon
                                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                mapView.overlays.add(m)
                            }
                        }
                    }
                    mapView.invalidate()
                }
            )

            Box(
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(16.dp, 6.dp)
                        .offset(y = 4.dp)
                        .scale(shadowScale)
                        .background(Color.Black.copy(alpha = 0.2f), shape = RoundedCornerShape(50))
                )

                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = stringResource(R.string.puntatore),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = pinOffsetY.dp)
                        .size(48.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 32.dp)
                    .fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                    shadowElevation = 2.dp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        String.format(Locale.US, "%.5f, %.5f", currentMapCenter.latitude, currentMapCenter.longitude),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SmallFloatingActionButton(
                            onClick = { showManualInput = true },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.coordinate_manuali))
                        }

                        SmallFloatingActionButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    @SuppressLint("MissingPermission")
                                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                                        if (loc != null) mapViewReference?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                                    }
                                } else {
                                    Toast.makeText(context, "Permesso posizione necessario", Toast.LENGTH_SHORT).show()
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.centra_su_di_me))
                        }
                    }

                    ExtendedFloatingActionButton(
                        onClick = { onSave(currentMapCenter.latitude, currentMapCenter.longitude) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(72.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.conferma), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (showManualInput) {
                var inputLat by remember { mutableStateOf("") }
                var inputLng by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showManualInput = false },
                    title = { Text(stringResource(R.string.vai_alle_coordinate), fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(24.dp),
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = inputLat,
                                onValueChange = { inputLat = it },
                                label = { Text(stringResource(R.string.latitudine)) },
                                leadingIcon = { Icon(Icons.Default.NorthEast, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = inputLng,
                                onValueChange = { inputLng = it },
                                label = { Text(stringResource(R.string.longitudine)) },
                                leadingIcon = { Icon(Icons.Default.SouthEast, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                    mapViewReference?.controller?.animateTo(GeoPoint(lat, lng))
                                    showManualInput = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(stringResource(R.string.vai)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showManualInput = false }) { Text(stringResource(R.string.annulla)) }
                    }
                )
            }
        }
    }
}
// ─────────────────────────────────────────────
// Bouncy Icon Button (shared)
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