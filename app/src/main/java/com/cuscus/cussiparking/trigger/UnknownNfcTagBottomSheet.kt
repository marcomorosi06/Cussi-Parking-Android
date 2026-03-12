package com.cuscus.cussiparking.ui.triggers

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cuscus.cussiparking.local.TriggerDao
import com.cuscus.cussiparking.local.TriggerEntity
import com.cuscus.cussiparking.local.VehicleEntity
import com.cuscus.cussiparking.trigger.ParkingLocationWorker
import com.cuscus.cussiparking.trigger.UnknownNfcTagData
import kotlinx.coroutines.launch

/**
 * Bottom sheet per associare un tag NFC sconosciuto a un veicolo esistente.
 *
 * IMPORTANTE — ciclo di vita del dismiss:
 * [onDismiss] viene chiamato dal ModalBottomSheet stesso quando l'animazione
 * di chiusura è completata (sia per swipe che per pressione "Ignora"/conferma).
 * È in quel momento che chiamiamo UnknownNfcTagState.consume(), non prima,
 * così notify() rimane bloccato (pending != null) per tutta la durata
 * dell'animazione di chiusura e non può arrivare un secondo sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnknownNfcTagBottomSheet(
    tagData:    UnknownNfcTagData,
    vehicles:   List<VehicleEntity>,
    triggerDao: TriggerDao,
    onDismiss:  () -> Unit          // chiamato da MainActivity → UnknownNfcTagState.consume()
) {
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedVehicle by remember { mutableStateOf<VehicleEntity?>(null) }
    var locationMode    by remember { mutableStateOf(tagData.locationMode) }
    var saving          by remember { mutableStateOf(false) }
    var saved           by remember { mutableStateOf(false) }

    val tagLabel = tagData.tagVehicleName.ifBlank { "Tag NFC" }

    // Helper per chiudere il sheet con animazione, poi notificare il parent
    val closeSheet: () -> Unit = {
        scope.launch {
            sheetState.hide()  // aspetta animazione
            onDismiss()        // solo ora consume() viene chiamato
        }
    }

    ModalBottomSheet(
        // onDismissRequest scatta per swipe-down: usiamo closeSheet anche qui
        // così l'animazione completa prima del consume()
        onDismissRequest = { scope.launch { sheetState.hide(); onDismiss() } },
        sheetState       = sheetState,
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle       = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Intestazione ──────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pulse = rememberInfiniteTransition(label = "nfc_pulse_unk")
                val pulseScale by pulse.animateFloat(
                    initialValue  = 0.88f,
                    targetValue   = 1.12f,
                    animationSpec = infiniteRepeatable(
                        tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse
                    ),
                    label = "pulse"
                )
                Surface(
                    shape    = androidx.compose.foundation.shape.CircleShape,
                    color    = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(48.dp).scale(pulseScale)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Nfc, null,
                            tint     = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        "Tag NFC non associato",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "\"$tagLabel\" — a quale auto vuoi collegarlo?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // ── Lista veicoli ─────────────────────────────────────────────────
            if (vehicles.isEmpty()) {
                Surface(
                    color    = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape    = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier          = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DirectionsCar, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Nessun veicolo configurato. Aggiungine uno prima di associare un tag.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    "Seleziona veicolo",
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier            = Modifier.heightIn(max = 240.dp)
                ) {
                    items(vehicles, key = { it.id }) { vehicle ->
                        val isSelected = selectedVehicle?.id == vehicle.id
                        Surface(
                            onClick  = { selectedVehicle = vehicle },
                            color    = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape    = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier          = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(vehicle.icon, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    vehicle.name,
                                    modifier   = Modifier.weight(1f),
                                    style      = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color      = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter   = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                                    exit    = scaleOut() + fadeOut()
                                ) {
                                    Icon(Icons.Default.CheckCircle, null,
                                        tint     = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ── Modalità GPS ──────────────────────────────────────────────────
            Text(
                "Modalità posizione",
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LocationModeChip(
                    label    = "Ultima nota",
                    icon     = Icons.Default.GpsNotFixed,
                    selected = locationMode == "last_known",
                    modifier = Modifier.weight(1f),
                    onClick  = { locationMode = "last_known" }
                )
                LocationModeChip(
                    label    = "GPS preciso",
                    icon     = Icons.Default.GpsFixed,
                    selected = locationMode == "precise",
                    modifier = Modifier.weight(1f),
                    onClick  = { locationMode = "precise" }
                )
            }

            // ── Pulsanti ──────────────────────────────────────────────────────
            AnimatedContent(
                targetState = saved,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) },
                label = "save_state"
            ) { isSaved ->
                if (isSaved) {
                    Surface(
                        color    = MaterialTheme.colorScheme.primaryContainer,
                        shape    = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier              = Modifier.padding(16.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Posizione salvata per ${selectedVehicle?.name}!",
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color      = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            onClick  = closeSheet,
                            modifier = Modifier.weight(1f)
                        ) { Text("Ignora") }

                        Button(
                            onClick = {
                                val vehicle = selectedVehicle ?: return@Button
                                saving = true
                                scope.launch {
                                    // 1. Salva la mappatura: tagSourceVehicleId → veicolo locale.
                                    //    identifier è "nfc" fisso; la chiave di ricerca per le
                                    //    scansioni future è tagSourceVehicleId = tagData.tagVehicleId.
                                    triggerDao.insertTrigger(
                                        TriggerEntity(
                                            localVehicleId     = vehicle.id,
                                            vehicleName        = vehicle.name,
                                            type               = "nfc",
                                            identifier         = "nfc",
                                            label              = tagLabel,
                                            locationMode       = locationMode,
                                            tagSourceVehicleId = tagData.tagVehicleId,
                                            enabled            = true
                                        )
                                    )
                                    // 2. Salva immediatamente la posizione: il tag è stato
                                    //    appena avvicinato, è l'occasione giusta.
                                    enqueueLocationSave(context, vehicle.id, vehicle.name, locationMode)

                                    saving = false
                                    saved  = true
                                    // Breve feedback visivo, poi chiudi con animazione
                                    kotlinx.coroutines.delay(1200)
                                    closeSheet()
                                }
                            },
                            enabled  = selectedVehicle != null && !saving,
                            shape    = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(2f)
                        ) {
                            if (saving) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color       = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.AddCircle, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Associa e salva posizione", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun enqueueLocationSave(
    context:      Context,
    vehicleId:    Int,
    vehicleName:  String,
    locationMode: String
) {
    val inputData = Data.Builder()
        .putInt(ParkingLocationWorker.KEY_VEHICLE_ID, vehicleId)
        .putString(ParkingLocationWorker.KEY_VEHICLE_NAME, vehicleName)
        .putString(ParkingLocationWorker.KEY_LOCATION_MODE, locationMode)
        .putString(ParkingLocationWorker.KEY_TRIGGER_LABEL, "NFC")
        .build()
    WorkManager.getInstance(context)
        .enqueue(OneTimeWorkRequestBuilder<ParkingLocationWorker>().setInputData(inputData).build())
}

@Composable
private fun LocationModeChip(
    label:    String,
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick:  () -> Unit
) {
    Surface(
        onClick  = onClick,
        color    = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape    = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null,
                modifier = Modifier.size(16.dp),
                tint     = if (selected) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                label,
                style      = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color      = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}