package com.cuscus.cussiparking.ui.members

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuscus.cussiparking.network.Member

// ─────────────────────────────────────────────
// MEMBERS SCREEN
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleMembersScreen(
    viewModel: VehicleMembersViewModel,
    onNavigateBack: () -> Unit
) {
    val context          = LocalContext.current
    val members          by viewModel.members.collectAsState()
    val isLoading        by viewModel.isLoading.collectAsState()
    val feedbackMessage  by viewModel.feedbackMessage.collectAsState()
    val inviteCode       by viewModel.inviteCode.collectAsState()
    val isOwner          by viewModel.isCurrentUserOwner.collectAsState()

    var showAddMemberDialog   by remember { mutableStateOf(false) }
    var showInviteCodeDialog  by remember { mutableStateOf(false) }
    var memberToDelete        by remember { mutableStateOf<Member?>(null) }
    var memberToChangeRole    by remember { mutableStateOf<Member?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearFeedback() }
    }
    LaunchedEffect(inviteCode) {
        if (inviteCode != null) showInviteCodeDialog = true
    }

    val owners         = members.filter { it.role == "owner" }
    val regularMembers = members.filter { it.role == "member" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gestione Membri", fontWeight = FontWeight.Bold)
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
                },
                actions = {
                    BouncyIconButton(onClick = { viewModel.fetchMembers() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aggiorna")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (isOwner) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(
                        onClick        = { viewModel.generateInviteCode() },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor   = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape          = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Genera codice invito")
                    }
                    ExtendedFloatingActionButton(
                        onClick        = { showAddMemberDialog = true },
                        icon           = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                        text           = { Text("Aggiungi", fontWeight = FontWeight.SemiBold) },
                        shape          = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor   = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // ── Loading iniziale ───────────────────────
                isLoading && members.isEmpty() -> {
                    Column(
                        modifier                = Modifier.align(Alignment.Center),
                        horizontalAlignment     = Alignment.CenterHorizontally,
                        verticalArrangement     = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            "Caricamento membri…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ── Empty state ────────────────────────────
                members.isEmpty() -> {
                    val pulse = rememberInfiniteTransition(label = "members_pulse")
                    val pulseScale by pulse.animateFloat(
                        initialValue  = 0.93f,
                        targetValue   = 1.07f,
                        animationSpec = infiniteRepeatable(
                            tween(1800, easing = FastOutSlowInEasing),
                            RepeatMode.Reverse
                        ),
                        label = "pulse_scale"
                    )
                    Column(
                        modifier            = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape    = RoundedCornerShape(20.dp),
                            color    = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(80.dp).scale(pulseScale)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint     = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        Text(
                            "Solo tu hai accesso a questo veicolo.",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign  = TextAlign.Center
                        )
                        if (isOwner) Text(
                            "Usa il tasto + per aggiungere qualcuno.",
                            style     = MaterialTheme.typography.bodyMedium,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // ── Lista membri ───────────────────────────
                else -> {
                    LazyColumn(
                        contentPadding      = PaddingValues(
                            start  = 16.dp,
                            top    = 12.dp,
                            end    = 16.dp,
                            bottom = 100.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier            = Modifier.fillMaxSize()
                    ) {
                        // Contatore accessi
                        item {
                            Text(
                                "${members.size} ${if (members.size == 1) "persona ha" else "persone hanno"} accesso",
                                style    = MaterialTheme.typography.bodyMedium,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        if (owners.isNotEmpty()) {
                            item {
                                SectionLabel(
                                    text  = "Proprietari",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(owners, key = { it.username }) { member ->
                                MemberCard(
                                    member         = member,
                                    isCurrentUser  = member.isMe,
                                    isOwner        = isOwner,
                                    onRemove       = { memberToDelete = member },
                                    onChangeRole   = { memberToChangeRole = member }
                                )
                            }
                        }

                        if (regularMembers.isNotEmpty()) {
                            item {
                                SectionLabel(
                                    text     = "Membri",
                                    color    = MaterialTheme.colorScheme.secondary,
                                    topPad   = if (owners.isNotEmpty()) 12.dp else 0.dp
                                )
                            }
                            items(regularMembers, key = { it.username }) { member ->
                                MemberCard(
                                    member         = member,
                                    isCurrentUser  = member.isMe,
                                    isOwner        = isOwner,
                                    onRemove       = { memberToDelete = member },
                                    onChangeRole   = { memberToChangeRole = member }
                                )
                            }
                        }
                    }
                }
            }

            // Refresh indicator sovrapposto
            if (isLoading && members.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }

    // ── Bottom sheet: aggiungi membro ─────────────
    if (showAddMemberDialog) {
        AddMemberBottomSheet(
            isLoading = isLoading,
            onDismiss = { showAddMemberDialog = false },
            onConfirm = { username ->
                viewModel.addMember(username) { showAddMemberDialog = false }
            }
        )
    }

    // ── Bottom sheet: codice invito ───────────────
    if (showInviteCodeDialog && inviteCode != null) {
        InviteCodeBottomSheet(
            inviteCode  = inviteCode!!,
            vehicleName = viewModel.vehicleName,
            serverUrl   = viewModel.serverUrl,
            context     = context,
            onDismiss   = { showInviteCodeDialog = false; viewModel.clearInviteCode() }
        )
    }

    // ── Dialog: rimozione membro ──────────────────
    memberToDelete?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToDelete = null },
            icon             = { Icon(Icons.Default.PersonRemove, null, tint = MaterialTheme.colorScheme.error) },
            title            = { Text("Rimuovi Membro", fontWeight = FontWeight.Bold) },
            text             = {
                Text("Rimuovere @${member.username} da ${viewModel.vehicleName}? Non potrà più vedere questo veicolo.")
            },
            confirmButton    = {
                Button(
                    onClick = { viewModel.removeMember(member); memberToDelete = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape   = RoundedCornerShape(12.dp)
                ) { Text("Rimuovi") }
            },
            dismissButton    = {
                TextButton(onClick = { memberToDelete = null }) { Text("Annulla") }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ── Dialog: cambio ruolo ──────────────────────
    memberToChangeRole?.let { member ->
        val isCurrentlyOwner = member.role == "owner"
        val newRole          = if (isCurrentlyOwner) "member" else "owner"
        val actionLabel      = if (isCurrentlyOwner) "Retrocedi a Membro" else "Promuovi a Proprietario"

        AlertDialog(
            onDismissRequest = { memberToChangeRole = null },
            icon             = {
                Icon(
                    if (isCurrentlyOwner) Icons.Default.PersonRemove else Icons.Default.Shield,
                    null,
                    tint = if (isCurrentlyOwner) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            },
            title            = { Text(actionLabel, fontWeight = FontWeight.Bold) },
            text             = {
                Text(
                    if (isCurrentlyOwner)
                        "@${member.username} non sarà più proprietario di ${viewModel.vehicleName} e diventerà un membro ordinario."
                    else
                        "@${member.username} diventerà proprietario di ${viewModel.vehicleName} e potrà aggiungere e rimuovere altri membri."
                )
            },
            confirmButton    = {
                Button(
                    onClick = { viewModel.changeRole(member, newRole); memberToChangeRole = null },
                    colors  = if (isCurrentlyOwner)
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    else
                        ButtonDefaults.buttonColors(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(actionLabel) }
            },
            dismissButton    = {
                TextButton(onClick = { memberToChangeRole = null }) { Text("Annulla") }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

// ─────────────────────────────────────────────
// BOTTOM SHEET: Aggiungi membro via username
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemberBottomSheet(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input       by remember { mutableStateOf("") }

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
                            Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Aggiungi Membro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    "Inserisci l'username dell'utente da aggiungere a ${"\u201c"}${"\u201d"}.",
                    style  = MaterialTheme.typography.bodyMedium,
                    color  = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )
                OutlinedTextField(
                    value         = input,
                    onValueChange = { input = it.trim() },
                    label         = { Text("Username") },
                    leadingIcon   = { Icon(Icons.Default.AlternateEmail, null) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Annulla", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick  = { if (input.isNotBlank()) onConfirm(input) },
                        enabled  = input.isNotBlank() && !isLoading,
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        AnimatedContent(
                            targetState  = isLoading,
                            label        = "add_btn",
                            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) }
                        ) { loading ->
                            if (loading) CircularProgressIndicator(
                                modifier    = Modifier.size(18.dp),
                                color       = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            else Text("Aggiungi", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────
// BOTTOM SHEET: Codice invito generato
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteCodeBottomSheet(
    inviteCode: String,
    vehicleName: String,
    serverUrl: String,
    context: Context,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val shareText = buildString {
        appendLine("Ti invito a seguire $vehicleName su CussiParking!")
        appendLine()
        appendLine("1. Apri l'app e collegati al server: $serverUrl")
        appendLine("2. Usa il codice invito: $inviteCode")
        appendLine()
        append("Il codice è valido 24 ore e può essere usato una sola volta.")
    }

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
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 16.dp)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icona
                Surface(
                    shape    = RoundedCornerShape(14.dp),
                    color    = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Codice Invito", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    "Valido 24 ore, monouso. Condividilo con chi vuoi aggiungere.",
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                // Codice grande
                Surface(
                    color    = MaterialTheme.colorScheme.primaryContainer,
                    shape    = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text      = inviteCode,
                        style     = MaterialTheme.typography.displaySmall.copy(
                            fontFamily    = FontFamily.Monospace,
                            letterSpacing = 6.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier.padding(vertical = 24.dp, horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Info server
                Surface(
                    color    = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier          = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Storage,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Da usare su: $serverUrl",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottoni copia + condividi
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick  = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("Codice Invito", inviteCode))
                            Toast.makeText(context, "Codice copiato!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copia", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick  = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                putExtra(Intent.EXTRA_SUBJECT, "Codice invito CussiParking")
                            }
                            context.startActivity(Intent.createChooser(intent, "Condividi codice invito"))
                        },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Condividi", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick  = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Chiudi", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────
// MEMBER CARD — rinnovata
// ─────────────────────────────────────────────
@Composable
private fun MemberCard(
    member: Member,
    isCurrentUser: Boolean,
    isOwner: Boolean,
    onRemove: () -> Unit,
    onChangeRole: () -> Unit
) {
    val memberIsOwner    = member.role == "owner"
    val canActOnMember   = isOwner && !isCurrentUser

    val accentColor      = if (memberIsOwner) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.secondary
    val containerColor   = if (memberIsOwner) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.secondaryContainer
    val onContainerColor = if (memberIsOwner) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSecondaryContainer

    ElevatedCard(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
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
            // Avatar con iniziale
            Box(
                modifier        = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    member.username.first().uppercaseChar().toString(),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = onContainerColor
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "@${member.username}",
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isCurrentUser) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "tu",
                                style    = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color    = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Badge ruolo
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor.copy(alpha = 0.10f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            if (memberIsOwner) Icons.Default.Shield else Icons.Default.Person,
                            null,
                            modifier = Modifier.size(10.dp),
                            tint     = accentColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (memberIsOwner) "Proprietario" else "Membro",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor
                        )
                    }
                }
            }

            // Azioni (solo owner su altri utenti)
            if (canActOnMember) {
                BouncyIconButton(onClick = onChangeRole) {
                    Icon(
                        if (memberIsOwner) Icons.Default.ArrowDownward else Icons.Default.Shield,
                        contentDescription = if (memberIsOwner) "Retrocedi a membro" else "Promuovi a proprietario",
                        tint = if (memberIsOwner) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary
                    )
                }
                AnimatedVisibility(
                    visible = !memberIsOwner,
                    enter   = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit    = scaleOut() + fadeOut()
                ) {
                    BouncyIconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.PersonRemove,
                            contentDescription = "Rimuovi ${member.username}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Section label
// ─────────────────────────────────────────────
@Composable
private fun SectionLabel(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    topPad: androidx.compose.ui.unit.Dp = 8.dp
) {
    Text(
        text.uppercase(),
        style      = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color      = color,
        modifier   = Modifier.padding(top = topPad, bottom = 2.dp)
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