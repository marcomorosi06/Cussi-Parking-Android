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
import com.cuscus.cussiparking.R
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
    val totalPages = 10
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
                        icon = Icons.Default.MyLocation, iconTint = MaterialTheme.colorScheme.primary, badgeColor = MaterialTheme.colorScheme.errorContainer, badgeText = "Richiesto",
                        title = "Posizione precisa", subtitle = "Per sapere dove hai parcheggiato", granted = hasFineLoc(),
                        rationale = "L'app usa il GPS per salvare il punto esatto in cui hai parcheggiato. Senza questo permesso non è possibile registrare né visualizzare le posizioni dei veicoli.",
                        details = listOf(
                            PermDetail(Icons.Default.GpsFixed, "GPS preciso per la posizione del veicolo"),
                            PermDetail(Icons.Default.LocationOn, "Posizione in primo piano quando l'app è aperta")
                        ),
                        buttonLabel = "Concedi posizione", onNext = ::advance,
                        onGrant = { fineLocLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
                    )

                    5 -> PermissionPage(
                        icon = Icons.Default.LocationOn, iconTint = MaterialTheme.colorScheme.tertiary, badgeColor = MaterialTheme.colorScheme.errorContainer, badgeText = "Critico per i trigger",
                        title = "Posizione sempre", subtitle = "Trigger automatici in background", granted = hasBgLoc(),
                        rationale = "Questo è il permesso più importante per i trigger automatici.\n\nQuando ti disconnetti dall'auto via Bluetooth, il sistema deve poter raccogliere la tua posizione GPS anche con l'app chiusa.\n\nSu Android 11+ devi andare nelle Impostazioni di sistema e scegliere \"Consenti sempre\".",
                        details = listOf(
                            PermDetail(Icons.Default.Bluetooth, "Trigger BT: salva alla disconnessione"),
                            PermDetail(Icons.Default.Nfc, "Trigger NFC: salva col tag"),
                            PermDetail(Icons.Default.BatterySaver, "Zero impatto costante sulla batteria")
                        ),
                        buttonLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "Apri impostazioni" else "Concedi posizione sempre",
                        extraNote = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "Vai in Autorizzazioni → Posizione → Consenti sempre" else null,
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
                    ) { Icon(Icons.Default.ArrowBack, contentDescription = "Indietro", tint = MaterialTheme.colorScheme.onSurface) }
                }
                if (pagerState.currentPage == 0) Spacer(Modifier.width(48.dp))

                PageDots(pagerState = pagerState, totalPages = totalPages)

                // Mostra "Salta" solo sulle info e nascondilo sulla Configurazione Server (3) e Permissions
                AnimatedVisibility(visible = pagerState.currentPage in 1..2 || pagerState.currentPage == 4) {
                    TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(5) } }, shape = RoundedCornerShape(100.dp)) {
                        Text("Salta", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
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
    val infiniteTransition = rememberInfiniteTransition(label = "hero_anim")
    val floatOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -16f, // Fluttua verso l'alto di 16dp
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_y"
    )

    // Un leggero bagliore che respira dietro il logo
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
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
                    contentDescription = "Logo CussiParking",
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
                    text = "CussiParking",
                    style = MaterialTheme.typography.displayMedium, // Massiccio ma senza esagerare
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface, // Colore solido e pulito
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Il tuo parcheggio intelligente.\nNon perdere mai più l'auto.",
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
                Text("Iniziamo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
        eyebrow = "Come funziona",
        title = "Il tuo parcheggio,\nle tue regole",
        subtitle = "Flessibilità totale per salvare e ritrovare l'auto.",
        steps = listOf(
            InfoStep(
                icon = Icons.Default.SaveAs,
                color = MaterialTheme.colorScheme.primary,
                heading = "Salva la posizione",
                body = "Usa il GPS sul momento, seleziona il punto sulla mappa se ti sei scordato di salvarla da fuori, oppure usa un tag NFC o il Bluetooth."
            ),
            InfoStep(
                icon = Icons.Default.Navigation,
                color = MaterialTheme.colorScheme.secondary,
                heading = "Ritrova il veicolo",
                body = "Un pulsante dedicato apre la tua app di navigazione predefinita (Maps, Waze) per farti guidare dritto alle coordinate salvate."
            ),
            InfoStep(
                icon = Icons.Default.AutoMode,
                color = MaterialTheme.colorScheme.tertiary,
                heading = "Tutto in automatico",
                body = "Rileva la disconnessione del Bluetooth dell'auto o tocca un tag NFC per registrare il parcheggio senza nemmeno aprire l'app."
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 32.dp)
            .padding(top = 64.dp, bottom = 140.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text("SELF-HOSTED", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Privato,\nsicuro, tuo.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Nessun cloud di terze parti. I tuoi dati restano sotto il tuo totale controllo.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))

        val steps = listOf(
            InfoStep(
                icon = Icons.Default.Dns,
                color = MaterialTheme.colorScheme.primary,
                heading = "Hostalo dove vuoi",
                body = "Il server è leggerissimo: puoi farlo girare su qualsiasi computer Linux, persino su un Raspberry Pi Zero W."
            ),
            InfoStep(
                icon = Icons.Default.Group,
                color = MaterialTheme.colorScheme.secondary,
                heading = "Per la tua famiglia",
                body = "Condividi i veicoli con chi vuoi. Creazione account e login si fanno direttamente dall'app, senza pannelli web."
            ),
            InfoStep(
                icon = Icons.Default.Code,
                color = MaterialTheme.colorScheme.tertiary,
                heading = "100% Open Source",
                body = "Codice trasparente. Scarica il server da GitHub e configuralo in pochi minuti."
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
            Text("Scarica il Server", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))

        Button(onClick = onNext, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text("Prossimo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(20.dp))
        }
    }
}
@Composable
private fun MultiProfilePage(onNext: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 32.dp).padding(top = 64.dp, bottom = 140.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text("IL TUO SERVER", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Privato,\nsicuro, tuo.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Nessun cloud di terze parti. I tuoi dati restano sotto il tuo totale controllo.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))

        val steps = listOf(
            InfoStep(Icons.Default.Dns, MaterialTheme.colorScheme.primary, "Hostalo dove ti pare", "Il server è privato e leggerissimo: puoi farlo girare persino su un Raspberry Pi Zero W."),
            InfoStep(Icons.Default.Login, MaterialTheme.colorScheme.secondary, "Tutto dall'app", "Non serve alcun pannello web. La registrazione degli account e il login si fanno direttamente da qui."),
            InfoStep(Icons.Default.Code, MaterialTheme.colorScheme.tertiary, "100% Open Source", "Codice trasparente. Scarica il server da GitHub, configuralo e condividi le auto con la famiglia.")
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
            Text("Vedi il Server su GitHub", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))

        Button(onClick = onNext, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text("Prossimo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
    var isCloudSelected by remember { mutableStateOf<Boolean?>(null) } // null = nessuna scelta

    var serverLabel by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("https://") }
    var showError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 32.dp).padding(top = 64.dp, bottom = 140.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text("INIZIAMO", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Scegli la\nModalità", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Vuoi connetterti subito al tuo server o usare l'app offline?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

        Spacer(Modifier.height(40.dp))

        // Card Offline (Configura in seguito)
        ModeSelectionCard(
            icon = Icons.Default.PhoneAndroid,
            title = "Configura in seguito",
            subtitle = "Usa la modalità Offline. Potrai aggiungere un server dalle Impostazioni in qualsiasi momento.",
            selected = isCloudSelected == false,
            onClick = { isCloudSelected = false }
        )

        Spacer(Modifier.height(16.dp))

        // Card Cloud (Collega Server)
        ModeSelectionCard(
            icon = Icons.Default.Storage,
            title = "Collega Server",
            subtitle = "Sincronizza e condividi. Inserisci l'URL ora, farai login o registrazione appena finito il tutorial.",
            selected = isCloudSelected == true,
            onClick = { isCloudSelected = true }
        )

        // Campi input Server
        AnimatedVisibility(visible = isCloudSelected == true, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(modifier = Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = serverLabel, onValueChange = { serverLabel = it },
                    label = { Text("Nome Server (es. Casa, Lavoro)") },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = serverUrl, onValueChange = { serverUrl = it; showError = false },
                    label = { Text("Indirizzo URL del Server") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), isError = showError
                )
                if (showError) {
                    Text("Inserisci un URL valido per continuare.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
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
                                settingsManager.addProfile(label = serverLabel.ifBlank { "Cloud" }, serverUrl = serverUrl.trim(), email = "Da configurare")
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
                Text(if (isCloudSelected == true) "Salva Server" else "Prosegui Offline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
        eyebrow = "Automazione", title = "L'app lavora\nper te", subtitle = "Scendi dall'auto, la posizione si salva da sola.",
        steps = listOf(
            InfoStep(Icons.Default.Bluetooth, MaterialTheme.colorScheme.secondary, "Bluetooth Audio", "Spegni l'autoradio e l'app capisce che sei arrivato."),
            InfoStep(Icons.Default.Nfc, MaterialTheme.colorScheme.tertiary, "Tag NFC Fisico", "Un tap col telefono sul cruscotto e il gioco è fatto.")
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
            Text(if (granted) "Continua" else "Più tardi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (granted) { Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun CombinedPermissionPage(onGrantBt: () -> Unit, onGrantNotif: () -> Unit, hasBt: Boolean, hasNotif: Boolean, onNext: () -> Unit) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 32.dp).padding(top = 64.dp, bottom = 140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy((-16).dp)) {
            DoubleIcon(Icons.Default.Bluetooth, MaterialTheme.colorScheme.secondary)
            DoubleIcon(Icons.Default.Notifications, MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(32.dp))
        Text("Dettagli Finali", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Rendi i trigger impeccabili", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))

        PermCard(icon = Icons.Default.Bluetooth, iconTint = MaterialTheme.colorScheme.secondary, title = "Bluetooth (Android 12+)", description = "Necessario per leggere l'autoradio e configurare i trigger BT.", granted = hasBt, buttonLabel = "Sblocca", onGrant = onGrantBt)
        Spacer(Modifier.height(16.dp))
        PermCard(icon = Icons.Default.Notifications, iconTint = MaterialTheme.colorScheme.primary, title = "Notifiche", description = "Serve a mostrare la notifica di sistema obbligatoria quando l'app rileva un parcheggio.", granted = hasNotif, buttonLabel = "Sblocca", onGrant = onGrantNotif)

        Spacer(Modifier.height(40.dp))
        Button(onClick = onNext, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = if (hasBt && hasNotif) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = if (hasBt && hasNotif) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)) {
            Text(if (hasBt && hasNotif) "Continua" else "Prosegui", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun BatteryOptimizationPage(isIgnored: Boolean, onGrant: () -> Unit, onNext: () -> Unit) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 32.dp).padding(top = 64.dp, bottom = 140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(120.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.BatteryAlert, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(64.dp)) }
        }
        Spacer(Modifier.height(32.dp))
        Text("Libera l'App", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Evita che Android blocchi i trigger", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PermStatusChip(granted = isIgnored)
        Spacer(Modifier.height(32.dp))

        Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BatteryRow(Icons.Default.NightShelter, "Di notte Android entra in \"Doze\" e addormenta le app.")
                BatteryRow(Icons.Default.Warning, "Questo bloccherebbe i trigger Bluetooth.")
                BatteryRow(Icons.Default.CheckCircle, "Rimuovi l'ottimizzazione per avere trigger affidabili h24.")
            }
        }
        Spacer(Modifier.height(40.dp))
        AnimatedVisibility(visible = !isIgnored) {
            Column {
                Button(onClick = onGrant, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                    Icon(Icons.Default.BatterySaver, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Disattiva ottimizzazione", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        Button(onClick = onNext, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isIgnored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = if (isIgnored) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)) {
            Text(if (isIgnored) "Continua" else "Più tardi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AllDonePage(hasFineLoc: Boolean, hasBgLoc: Boolean, hasBt: Boolean, hasNotif: Boolean, hasBattOpt: Boolean, onFinish: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "done_spin")
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
                Text("Sei a Bordo!", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text("Tutto configurato alla perfezione.", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(40.dp))
        Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryRow("GPS Preciso", Icons.Default.GpsFixed, hasFineLoc)
                SummaryRow("GPS in Background", Icons.Default.LocationOn, hasBgLoc)
                SummaryRow("Bluetooth", Icons.Default.Bluetooth, hasBt)
                SummaryRow("Notifiche", Icons.Default.Notifications, hasNotif)
                SummaryRow("Batteria Ottimizzata", Icons.Default.BatterySaver, hasBattOpt)
            }
        }
        Spacer(Modifier.height(48.dp))
        Button(onClick = onFinish, shape = RoundedCornerShape(100.dp), modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Icon(Icons.Default.DirectionsCar, null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text("Inizia a parcheggiare", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
            Text("Prossimo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            Text(if (granted) "Permesso Attivo" else "Permesso Richiesto", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer)
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
            val widthAnim by animateDpAsState(targetValue = if (selected) 32.dp else 8.dp, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "dot_width")
            Box(modifier = Modifier.height(8.dp).width(widthAnim).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
        }
    }
}