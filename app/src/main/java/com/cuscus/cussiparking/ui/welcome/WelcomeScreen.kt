package com.cuscus.cussiparking.ui.welcome

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.cuscus.cussiparking.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cuscus.cussiparking.data.SettingsManager
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WelcomeScreen(
    settingsManager: SettingsManager,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // Ora abbiamo 10 pagine totali (aggiunta configurazione Server)
    val totalPages = 9
    val pagerState = rememberPagerState(pageCount = { totalPages })

    // ── Permission helpers ─────────────────────────────────────────────────
    fun hasPerm(perm: String) = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    fun hasFineLoc()   = hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasBgLoc()     = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) hasPerm(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else true
    fun hasBt()        = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) hasPerm(Manifest.permission.BLUETOOTH_CONNECT) else true
    fun hasNotif()     = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) hasPerm(Manifest.permission.POST_NOTIFICATIONS) else true
    fun hasBattOpt()   = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    // ── Permission launchers ───────────────────────────────────────────────
    var permRefreshKey by remember { mutableStateOf(0) }
    val fineLocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permRefreshKey++ }
    val bgLocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permRefreshKey++ }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permRefreshKey++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permRefreshKey++ }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { permRefreshKey++ }

    @Suppress("UNUSED_EXPRESSION")
    permRefreshKey

    fun advance() {
        if (pagerState.currentPage == totalPages - 1) onFinish()
        else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
    }

    // ── UI ─────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val pageOffset = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction).absoluteValue
            Box(
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = 1f - pageOffset.coerceIn(0f, 1f) * 0.4f
                    scaleX = 1f - pageOffset.coerceIn(0f, 1f) * 0.05f
                    scaleY = 1f - pageOffset.coerceIn(0f, 1f) * 0.05f
                }
            ) {
                when (page) {
                    0 -> HeroPage(onNext = ::advance)
                    1 -> HowItWorksPage(onNext = ::advance)
                    2 -> PrivateServerPage(onNext = ::advance)
                    3 -> SetupModePage(settingsManager = settingsManager, onNext = ::advance)

                    4 -> PermissionPage(
                        icon = Icons.Default.MyLocation, iconTint = MaterialTheme.colorScheme.primary, badgeColor = MaterialTheme.colorScheme.errorContainer, badgeText = stringResource(R.string.richiesto),
                        title = stringResource(R.string.posizione_precisa), subtitle = stringResource(R.string.posizione_precisa_sub), granted = hasFineLoc(),
                        rationale = stringResource(R.string.posizione_precisa_rationale),
                        details = listOf(
                            PermDetail(Icons.Default.GpsFixed, stringResource(R.string.gps_preciso_veicolo)),
                            PermDetail(Icons.Default.LocationOn, stringResource(R.string.posizione_primo_piano))
                        ),
                        buttonLabel = stringResource(R.string.concedi_posizione), onNext = ::advance,
                        onGrant = { fineLocLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
                    )

                    5 -> PermissionPage(
                        icon = Icons.Default.LocationOn, iconTint = MaterialTheme.colorScheme.tertiary, badgeColor = MaterialTheme.colorScheme.errorContainer, badgeText = stringResource(R.string.critico_trigger),
                        title = stringResource(R.string.posizione_sempre), subtitle = stringResource(R.string.posizione_sempre_sub), granted = hasBgLoc(),
                        rationale = stringResource(R.string.posizione_sempre_rationale),
                        details = listOf(
                            PermDetail(Icons.Default.Bluetooth, stringResource(R.string.trigger_bt_desc)),
                            PermDetail(Icons.Default.Nfc, stringResource(R.string.trigger_nfc_desc)),
                            PermDetail(Icons.Default.BatterySaver, stringResource(R.string.zero_impatto_batteria))
                        ),
                        buttonLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) stringResource(R.string.apri_impostazioni) else stringResource(R.string.concedi_posizione_sempre),
                        extraNote = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) stringResource(R.string.vai_in_autorizzazioni) else null,
                        onNext = ::advance,
                        onGrant = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", context.packageName, null) }
                                context.startActivity(intent)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                bgLocLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        }
                    )

                    6 -> CombinedPermissionPage(
                        onGrantBt = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) btLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT)) },
                        onGrantNotif = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        hasBt = hasBt(), hasNotif = hasNotif(), onNext = ::advance
                    )

                    7 -> BatteryOptimizationPage(
                        isIgnored = hasBattOpt(),
                        onGrant = {
                            @SuppressLint("BatteryLife")
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                            batteryLauncher.launch(intent)
                        },
                        onNext = ::advance
                    )

                    8 -> AllDonePage(hasFineLoc = hasFineLoc(), hasBgLoc = hasBgLoc(), hasBt = hasBt(), hasNotif = hasNotif(), hasBattOpt = hasBattOpt(), onFinish = onFinish)
                }
            }
        }

        // ── Floating Bottom Navigation ──────────────────────────────────────────
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), MaterialTheme.colorScheme.surface)))
                .padding(horizontal = 32.dp, vertical = 28.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = pagerState.currentPage > 0,
                    enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)
                ) {
                    FilledIconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.indietro), tint = MaterialTheme.colorScheme.onSurface) }
                }
                if (pagerState.currentPage == 0) Spacer(Modifier.width(48.dp))

                PageDots(pagerState = pagerState, totalPages = totalPages)

                // Mostra "Salta" solo sulle info e nascondilo sulla Configurazione Server (3) e Permissions
                AnimatedVisibility(visible = pagerState.currentPage in 1..2 || pagerState.currentPage == 4) {
                    TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(5) } }, shape = RoundedCornerShape(100.dp)) {
                        Text(stringResource(R.string.salta), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }
                if (pagerState.currentPage !in 1..2 && pagerState.currentPage != 4) Spacer(Modifier.width(64.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────
// Page 0 — HERO
// ─────────────────────────────────────────────
@Composable
private fun HeroPage(onNext: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // ── Animazione di Levitazione (Floating) ──
    val infiniteTransition = rememberInfiniteTransition(label = "heroanim")
    val floatOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -16f, // Fluttua verso l'alto di 16dp
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floaty"
    )

    // Un leggero bagliore che respira dietro il logo
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowscale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // ── Area Logo ─────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(800)) + scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
                // Bagliore sfumato (Niente bordi rigidi)
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(glowScale)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Il tuo logo che fluttua
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = stringResource(R.string.logo_cussiparking),
                    modifier = Modifier
                        .size(140.dp)
                        .offset(y = floatOffsetY.dp), // Applica la levitazione
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.tertiary)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── Area Testo ────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(800, delayMillis = 200)) + slideInVertically(tween(800, delayMillis = 200)) { it / 3 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.cussiparking),
                    style = MaterialTheme.typography.displayMedium, // Massiccio ma senza esagerare
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface, // Colore solido e pulito
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.il_tuo_parcheggio_intelligentennon_),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp
                )
            }
        }

        Spacer(Modifier.height(64.dp))

        // ── Area Bottone ──────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(800, delayMillis = 400)) + slideInVertically(tween(800, delayMillis = 400)) { it / 2 }
        ) {
            Button(
                onClick = onNext,
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.iniziamo), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────
// Page 1 & 2 — Info Pages
// ─────────────────────────────────────────────
// ─────────────────────────────────────────────
// Page 1 — COME FUNZIONA
// ─────────────────────────────────────────────
@Composable
private fun HowItWorksPage(onNext: () -> Unit) {
    InfoPage(
        eyebrow = stringResource(R.string.come_funziona),
        title = stringResource(R.string.il_tuo_parcheggio_regole),
        subtitle = stringResource(R.string.flessibilita_totale),
        steps = listOf(
            InfoStep(
                icon = Icons.Default.SaveAs,
                color = MaterialTheme.colorScheme.primary,
                heading = stringResource(R.string.salva_la_posizione),
                body = stringResource(R.string.salva_la_posizione_desc)
            ),
            InfoStep(
                icon = Icons.Default.Navigation,
                color = MaterialTheme.colorScheme.secondary,
                heading = stringResource(R.string.ritrova_il_veicolo),
                body = stringResource(R.string.ritrova_il_veicolo_desc)
            ),
            InfoStep(
                icon = Icons.Default.AutoMode,
                color = MaterialTheme.colorScheme.tertiary,
                heading = stringResource(R.string.tutto_in_automatico),
                body = stringResource(R.string.tutto_in_automatico_desc)
            )
        ),
        onNext = onNext
    )
}

// ─────────────────────────────────────────────
// Page 2 — SERVER PRIVATO & GITHUB
// ─────────────────────────────────────────────
@Composable
private fun PrivateServerPage(onNext: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 32.dp)
            .padding(top = 64.dp, bottom = 140.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(stringResource(R.string.selfhosted), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.privatonsicuro_tuo), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.nessun_cloud_di_terze_parti_i_tuoi_), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))

        val steps = listOf(
            InfoStep(
                icon = Icons.Default.Dns,
                color = MaterialTheme.colorScheme.primary,
                heading = stringResource(R.string.hostalo_dove_vuoi),
                body = stringResource(R.string.hostalo_dove_vuoi_desc)
            ),
            InfoStep(
                icon = Icons.Default.Group,
                color = MaterialTheme.colorScheme.secondary,
                heading = stringResource(R.string.per_la_tua_famiglia),
                body = stringResource(R.string.per_la_tua_famiglia_desc)
            ),
            InfoStep(
                icon = Icons.Default.Code,
                color = MaterialTheme.colorScheme.tertiary,
                heading = stringResource(R.string.open_source_100),
                body = stringResource(R.string.open_source_100_desc)
            )
        )

        steps.forEachIndexed { index, step ->
            InfoStepCard(step, index)
            if (index < steps.lastIndex) Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.height(32.dp))

        // Pulsante GitHub
        OutlinedButton(
            onClick = {
                // INSERISCI QUI IL TUO VERO LINK GITHUB
                uriHandler.openUri("https://github.com/marcomorosi06/Cussi-Parking-Server")
            },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.scarica_il_server), fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))

        Button(onClick = onNext, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text(stringResource(R.string.prossimo), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(20.dp))
        }
    }
}
@Composable
private fun MultiProfilePage(onNext: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    val scrollState = rememberScrollState()
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 32.dp).padding(top = 64.dp, bottom = 140.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(stringResource(R.string.il_tuo_server), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.privatonsicuro_tuo), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.nessun_cloud_di_terze_parti_i_tuoi_), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))

        val steps = listOf(
            InfoStep(Icons.Default.Dns, MaterialTheme.colorScheme.primary, stringResource(R.string.hostalo_dove_ti_pare), stringResource(R.string.hostalo_dove_ti_pare_desc)),
            InfoStep(Icons.Default.Login, MaterialTheme.colorScheme.secondary, stringResource(R.string.tutto_dall_app), stringResource(R.string.tutto_dall_app_desc)),
            InfoStep(Icons.Default.Code, MaterialTheme.colorScheme.tertiary, stringResource(R.string.open_source_100), stringResource(R.string.open_source_famiglia_desc))
        )

        steps.forEachIndexed { index, step ->
            InfoStepCard(step, index)
            if (index < steps.lastIndex) Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.height(32.dp))

        // Pulsante GitHub
        OutlinedButton(
            onClick = {
                // INSERISCI QUI IL LINK AL TUO REPO GITHUB!
                uriHandler.openUri("https://github.com/")
            },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.vedi_il_server_su_github), fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))

        Button(onClick = onNext, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text(stringResource(R.string.prossimo), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(20.dp))
        }
    }
}

// ─────────────────────────────────────────────
// Page 3 — SETUP SERVER & OFFLINE
// ─────────────────────────────────────────────
@Composable
private fun SetupModePage(settingsManager: SettingsManager, onNext: () -> Unit) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    var isCloudSelected by remember { mutableStateOf<Boolean?>(null) } // null = nessuna scelta

    var serverLabel by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("https://") }
    var showError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 32.dp).padding(top = 64.dp, bottom = 140.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(stringResource(R.string.iniziamo), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.scegli_lanmodalit), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.vuoi_connetterti_subito_al_tuo_serv), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

        Spacer(Modifier.height(40.dp))

        // Card Offline (Configura in seguito)
        ModeSelectionCard(
            icon = Icons.Default.PhoneAndroid,
            title = stringResource(R.string.configura_in_seguito),
            subtitle = stringResource(R.string.configura_in_seguito_desc),
            selected = isCloudSelected == false,
            onClick = { isCloudSelected = false }
        )

        Spacer(Modifier.height(16.dp))

        // Card Cloud (Collega Server)
        ModeSelectionCard(
            icon = Icons.Default.Storage,
            title = stringResource(R.string.collega_server),
            subtitle = stringResource(R.string.collega_server_desc),
            selected = isCloudSelected == true,
            onClick = { isCloudSelected = true }
        )

        // Campi input Server
        AnimatedVisibility(visible = isCloudSelected == true, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(modifier = Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = serverLabel, onValueChange = { serverLabel = it },
                    label = { Text(stringResource(R.string.nome_server_es_casa_lavoro)) },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = serverUrl, onValueChange = { serverUrl = it; showError = false },
                    label = { Text(stringResource(R.string.indirizzo_url_del_server)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), isError = showError
                )
                if (showError) {
                    Text(stringResource(R.string.inserisci_un_url_valido_per_continu), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        // Pulsante visibile solo se è stata fatta una scelta
        AnimatedVisibility(visible = isCloudSelected != null) {
            Button(
                onClick = {
                    if (isCloudSelected == false) {
                        settingsManager.setOfflineMode(true)
                        onNext()
                    } else {
                        if (serverUrl.length > 8) {
                            settingsManager.setOfflineMode(false)
                            // Aggiunge il profilo in attesa di login. Mette una mail vuota temporanea.
                            if (settingsManager.profiles.value.none { it.serverUrl == serverUrl }) {
                                settingsManager.addProfile(label = serverLabel.ifBlank { context.getString(R.string.cloud) }, serverUrl = serverUrl.trim(), email = context.getString(R.string.da_configurare))
                            }
                            onNext()
                        } else {
                            showError = true
                        }
                    }
                },
                shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (isCloudSelected == true) stringResource(R.string.salva_server) else stringResource(R.string.prosegui_offline), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ModeSelectionCard(icon: ImageVector, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick, shape = RoundedCornerShape(24.dp), color = bgColor,
        border = BorderStroke(2.dp, borderColor), modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = contentColor)
            }
        }
    }
}

// ─────────────────────────────────────────────
// Page 4 — Triggers Overview (Senza Wi-Fi)
// ─────────────────────────────────────────────
@Composable
private fun TriggersOverviewPage(onNext: () -> Unit) {
    InfoPage(
        eyebrow = stringResource(R.string.automazione), title = stringResource(R.string.app_lavora_per_te), subtitle = stringResource(R.string.scendi_auto_salva_sola),
        steps = listOf(
            InfoStep(Icons.Default.Bluetooth, MaterialTheme.colorScheme.secondary, stringResource(R.string.bluetooth_audio), stringResource(R.string.bluetooth_audio_desc)),
            InfoStep(Icons.Default.Nfc, MaterialTheme.colorScheme.tertiary, stringResource(R.string.tag_nfc_fisico), stringResource(R.string.tag_nfc_fisico_desc))
        ),
        onNext = onNext
    )
}

// ─────────────────────────────────────────────
// Shared Composables & Remaining Pages
// ─────────────────────────────────────────────
data class PermDetail(val icon: ImageVector, val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionPage(
    icon: ImageVector, iconTint: Color, badgeColor: Color, badgeText: String, title: String, subtitle: String,
    granted: Boolean, rationale: String, details: List<PermDetail>, buttonLabel: String, extraNote: String? = null,
    onGrant: () -> Unit, onNext: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 32.dp).padding(top = 64.dp, bottom = 140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopEnd, modifier = Modifier.size(130.dp)) {
            Surface(shape = RoundedCornerShape(36.dp), color = iconTint.copy(alpha = 0.12f), modifier = Modifier.size(120.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(64.dp)) }
            }
            Surface(shape = RoundedCornerShape(100.dp), color = badgeColor, shadowElevation = 8.dp, modifier = Modifier.offset(x = 8.dp, y = (-8).dp)) {
                Text(badgeText.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PermStatusChip(granted = granted)
        Spacer(Modifier.height(32.dp))

        Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                details.forEach { detail ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(detail.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(detail.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(rationale, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
        if (extraNote != null) {
            Spacer(Modifier.height(16.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(extraNote, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
        AnimatedVisibility(visible = !granted) {
            Button(onClick = onGrant, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(buttonLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onNext, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = if (granted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)) {
            Text(if (granted) stringResource(R.string.continua) else stringResource(R.string.piu_tardi), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (granted) { Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun CombinedPermissionPage(onGrantBt: () -> Unit, onGrantNotif: () -> Unit, hasBt: Boolean, hasNotif: Boolean, onNext: () -> Unit) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 32.dp).padding(top = 64.dp, bottom = 140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy((-16).dp)) {
            DoubleIcon(Icons.Default.Bluetooth, MaterialTheme.colorScheme.secondary)
            DoubleIcon(Icons.Default.Notifications, MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.dettagli_finali), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.rendi_i_trigger_impeccabili), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))

        PermCard(icon = Icons.Default.Bluetooth, iconTint = MaterialTheme.colorScheme.secondary, title = stringResource(R.string.bluetooth_android12), description = stringResource(R.string.bluetooth_rationale), granted = hasBt, buttonLabel = stringResource(R.string.sblocca), onGrant = onGrantBt)
        Spacer(Modifier.height(16.dp))
        PermCard(icon = Icons.Default.Notifications, iconTint = MaterialTheme.colorScheme.primary, title = stringResource(R.string.notifiche), description = stringResource(R.string.notifiche_rationale), granted = hasNotif, buttonLabel = stringResource(R.string.sblocca), onGrant = onGrantNotif)

        Spacer(Modifier.height(40.dp))
        Button(onClick = onNext, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = if (hasBt && hasNotif) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = if (hasBt && hasNotif) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)) {
            Text(if (hasBt && hasNotif) stringResource(R.string.continua) else stringResource(R.string.prosegui), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun BatteryOptimizationPage(isIgnored: Boolean, onGrant: () -> Unit, onNext: () -> Unit) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 32.dp).padding(top = 64.dp, bottom = 140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(120.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.BatteryAlert, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(64.dp)) }
        }
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.libera_lapp), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.evita_che_android_blocchi_i_trigger), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PermStatusChip(granted = isIgnored)
        Spacer(Modifier.height(32.dp))

        Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BatteryRow(Icons.Default.NightShelter, stringResource(R.string.doze_addormenta))
                BatteryRow(Icons.Default.Warning, stringResource(R.string.blocca_trigger_bt))
                BatteryRow(Icons.Default.CheckCircle, stringResource(R.string.rimuovi_ottimizzazione_h24))
            }
        }
        Spacer(Modifier.height(40.dp))
        AnimatedVisibility(visible = !isIgnored) {
            Column {
                Button(onClick = onGrant, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                    Icon(Icons.Default.BatterySaver, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.disattiva_ottimizzazione), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        Button(onClick = onNext, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isIgnored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = if (isIgnored) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)) {
            Text(if (isIgnored) stringResource(R.string.continua) else stringResource(R.string.piu_tardi), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AllDonePage(hasFineLoc: Boolean, hasBgLoc: Boolean, hasBt: Boolean, hasNotif: Boolean, hasBattOpt: Boolean, onFinish: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "donespin")
    val rot by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing)), label = "rot")
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp).padding(top = 80.dp, bottom = 140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
            Box(modifier = Modifier.size(160.dp).graphicsLayer { rotationZ = rot }.clip(CircleShape).background(Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.primaryContainer))))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(120.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(64.dp)) }
            }
        }
        Spacer(Modifier.height(40.dp))
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(600)) + slideInVertically { it / 3 }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.sei_a_bordo), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.tutto_configurato_alla_perfezione), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(40.dp))
        Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryRow(stringResource(R.string.gps_preciso), Icons.Default.GpsFixed, hasFineLoc)
                SummaryRow(stringResource(R.string.gps_background), Icons.Default.LocationOn, hasBgLoc)
                SummaryRow(stringResource(R.string.bluetooth), Icons.Default.Bluetooth, hasBt)
                SummaryRow(stringResource(R.string.notifiche), Icons.Default.Notifications, hasNotif)
                SummaryRow(stringResource(R.string.batteria_ottimizzata), Icons.Default.BatterySaver, hasBattOpt)
            }
        }
        Spacer(Modifier.height(48.dp))
        Button(onClick = onFinish, shape = RoundedCornerShape(100.dp), modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Icon(Icons.Default.DirectionsCar, null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.inizia_a_parcheggiare), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────
// Generic/Shared UI
// ─────────────────────────────────────────────
data class InfoStep(val icon: ImageVector, val color: Color, val heading: String, val body: String)

@Composable
private fun InfoPage(eyebrow: String, title: String, subtitle: String, steps: List<InfoStep>, onNext: () -> Unit) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 32.dp).padding(top = 64.dp, bottom = 140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(48.dp))

        steps.forEachIndexed { index, step ->
            InfoStepCard(step, index)
            if (index < steps.lastIndex) Spacer(Modifier.height(16.dp))
        }
        Spacer(Modifier.height(48.dp))
        Button(onClick = onNext, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text(stringResource(R.string.prossimo), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun InfoStepCard(step: InfoStep, index: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay((index * 120).toLong()); visible = true }
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(400)) + slideInHorizontally(tween(400)) { -40 }) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(step.color.copy(alpha = 0.15f))) {
                    Icon(step.icon, null, tint = step.color, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(step.heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(step.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun PermStatusChip(granted: Boolean) {
    Surface(shape = RoundedCornerShape(100.dp), color = if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)) {
        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (granted) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
            Text(if (granted) stringResource(R.string.permesso_attivo) else stringResource(R.string.permesso_richiesto), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun PermCard(icon: ImageVector, iconTint: Color, title: String, description: String, granted: Boolean, buttonLabel: String, onGrant: () -> Unit) {
    Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(20.dp)).background(iconTint.copy(alpha = 0.15f))) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    PermStatusChip(granted = granted)
                }
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
            AnimatedVisibility(visible = !granted) {
                Button(onClick = onGrant, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(buttonLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BatteryRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp).padding(top = 2.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp)
    }
}

@Composable
private fun SummaryRow(label: String, icon: ImageVector, granted: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }
        Icon(if (granted) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun DoubleIcon(icon: ImageVector, tint: Color) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f))) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(48.dp))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PageDots(pagerState: PagerState, totalPages: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(totalPages) { index ->
            val selected = pagerState.currentPage == index
            val widthAnim by animateDpAsState(targetValue = if (selected) 32.dp else 8.dp, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "dotwidth")
            Box(modifier = Modifier.height(8.dp).width(widthAnim).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
        }
    }
}