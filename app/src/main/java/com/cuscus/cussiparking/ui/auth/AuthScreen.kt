/*
 * AuthScreen.kt - Material 3 Expressive Rewrite
 * Fully animated, bouncy, expressive - all original functionality preserved.
 */
package com.cuscus.cussiparking.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.cuscus.cussiparking.R

// ─────────────────────────────────────────────
// Spring specs
// ─────────────────────────────────────────────
private val bouncySpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
)
private val gentleSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

// ─────────────────────────────────────────────
// AUTH SCREEN
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateBack: (() -> Unit)? = null
) {
    val isOffline by viewModel.settingsManager.isOfflineMode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val profile = viewModel.targetProfile

    var email by remember { mutableStateOf(profile?.email ?: "") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = profile?.label,
                        transitionSpec = {
                            slideInVertically { -it } + fadeIn() togetherWith
                                    slideOutVertically { it } + fadeOut()
                        },
                        label = "topbar_title"
                    ) { label ->
                        Column {
                            Text(
                                stringResource(R.string.app_name),
                                fontWeight = FontWeight.Bold
                            )
                            if (label != null) {
                                Text(
                                    "Accedi a $label",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        BouncyIconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                        }
                    }
                },
                actions = {
                    BouncyIconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Impostazioni")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ── App icon / hero ────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn()
            ) {
                val pulseAnim = rememberInfiniteTransition(label = "hero_pulse")
                val pulseScale by pulseAnim.animateFloat(
                    initialValue = 0.97f,
                    targetValue = 1.03f,
                    animationSpec = infiniteRepeatable(
                        tween(2000, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse
                    ),
                    label = "pulse"
                )

                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(88.dp)
                        .scale(pulseScale)
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
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Server badge ───────────────────────────────────
            AnimatedVisibility(
                visible = profile != null && visible,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(),
            ) {
                if (profile != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    profile.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    profile.serverUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // ── Offline toggle ─────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
                ) + fadeIn()
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedContent(
                                targetState = isOffline,
                                transitionSpec = {
                                    scaleIn(bouncySpring) + fadeIn() togetherWith scaleOut() + fadeOut()
                                },
                                label = "offline_icon"
                            ) { offline ->
                                Icon(
                                    if (offline) Icons.Default.WifiOff else Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = if (offline)
                                        MaterialTheme.colorScheme.secondary
                                    else
                                        MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    stringResource(if (isOffline) R.string.offline_mode else R.string.online_mode),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                AnimatedContent(
                                    targetState = isOffline,
                                    transitionSpec = {
                                        slideInVertically { -it } + fadeIn() togetherWith
                                                slideOutVertically { it } + fadeOut()
                                    },
                                    label = "offline_sub"
                                ) { offline ->
                                    Text(
                                        if (offline) "Nessun server necessario" else "Connessione al server attiva",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Switch(
                            checked = !isOffline,
                            onCheckedChange = { viewModel.settingsManager.setOfflineMode(!it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Login fields (animated in/out) ─────────────────
            AnimatedVisibility(
                visible = !isOffline,
                enter = expandVertically(
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
                ) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ExpressiveTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = stringResource(R.string.email),
                        icon = Icons.Default.Email
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ExpressiveTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.password),
                        icon = Icons.Default.Lock,
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        onTogglePasswordVisibility = { passwordVisible = !passwordVisible }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Error message ──────────────────────────────────
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                errorMessage?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                msg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // ── Primary CTA button ─────────────────────────────
            val btnScale by animateFloatAsState(
                targetValue = if (isLoading) 0.96f else 1f,
                animationSpec = bouncySpring,
                label = "btn_scale"
            )
            Button(
                onClick = {
                    if (isOffline) onLoginSuccess()
                    else viewModel.performLogin(email, password, onLoginSuccess)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(btnScale),
                enabled = !isLoading,
                shape = RoundedCornerShape(18.dp)
            ) {
                AnimatedContent(
                    targetState = isLoading to isOffline,
                    transitionSpec = {
                        scaleIn(bouncySpring) + fadeIn() togetherWith scaleOut() + fadeOut()
                    },
                    label = "btn_content"
                ) { (loading, offline) ->
                    if (loading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (offline) Icons.Default.PhoneAndroid else Icons.Default.Login,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(if (offline) R.string.start_local else R.string.login),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── Register link ──────────────────────────────────
            AnimatedVisibility(
                visible = !isOffline && !isLoading,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                TextButton(
                    onClick = onNavigateToRegister,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        stringResource(R.string.dont_have_account),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// REGISTER SCREEN
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val profile = viewModel.targetProfile

    var email by remember { mutableStateOf(profile?.email ?: "") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    // Staggered entrance
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Per-field validation states
    val usernameError = username.isNotEmpty() && (username.length < 3 || username.length > 20)
    val passwordMismatch = confirmPassword.isNotEmpty() && password != confirmPassword

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                        if (profile != null) {
                            Text(
                                "Registrati su ${profile.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    BouncyIconButton(onClick = onNavigateToLogin) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Login")
                    }
                },
                actions = {
                    BouncyIconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Impostazioni")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Hero icon ──────────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(
                    animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)
                ) + fadeIn()
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Server badge ───────────────────────────────────
            AnimatedVisibility(
                visible = profile != null && visible,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
                ) + fadeIn()
            ) {
                if (profile != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "${profile.label} — ${profile.serverUrl}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ── Form fields with staggered animation ───────────
            val fields = listOf(
                FormField(
                    value = email,
                    onValueChange = { email = it },
                    label = stringResource(R.string.email),
                    icon = Icons.Default.Email
                ),
                FormField(
                    value = username,
                    onValueChange = { username = it.trim() },
                    label = "Username",
                    icon = Icons.Default.AlternateEmail,
                    supportingText = if (usernameError) "3-20 caratteri, lettere/numeri/underscore"
                    else "Lettere, numeri, underscore. 3-20 caratteri.",
                    isError = usernameError
                ),
                FormField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.password),
                    icon = Icons.Default.Lock,
                    isPassword = true,
                    passwordVisible = passwordVisible,
                    onTogglePasswordVisibility = { passwordVisible = !passwordVisible }
                ),
                FormField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = stringResource(R.string.confirm_password),
                    icon = Icons.Default.Lock,
                    isPassword = true,
                    passwordVisible = confirmPasswordVisible,
                    onTogglePasswordVisibility = { confirmPasswordVisible = !confirmPasswordVisible },
                    isError = passwordMismatch,
                    supportingText = if (passwordMismatch) "Le password non coincidono" else null
                )
            )

            fields.forEachIndexed { index, field ->
                val delayMs = 60 * index
                var fieldVisible by remember { mutableStateOf(false) }
                LaunchedEffect(visible) {
                    if (visible) {
                        kotlinx.coroutines.delay(delayMs.toLong())
                        fieldVisible = true
                    }
                }
                AnimatedVisibility(
                    visible = fieldVisible,
                    enter = slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
                    ) + fadeIn()
                ) {
                    Column {
                        ExpressiveTextField(
                            value = field.value,
                            onValueChange = field.onValueChange,
                            label = field.label,
                            icon = field.icon,
                            isPassword = field.isPassword,
                            passwordVisible = field.passwordVisible,
                            onTogglePasswordVisibility = field.onTogglePasswordVisibility,
                            isError = field.isError,
                            supportingText = field.supportingText
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Error banner ───────────────────────────────────
            val displayError = localError ?: errorMessage
            AnimatedVisibility(
                visible = displayError != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                displayError?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                msg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // ── Register button ────────────────────────────────
            val passwordsMismatchText = stringResource(R.string.passwords_do_not_match)
            val btnScale by animateFloatAsState(
                targetValue = if (isLoading) 0.96f else 1f,
                animationSpec = bouncySpring,
                label = "reg_btn_scale"
            )
            Button(
                onClick = {
                    localError = null
                    when {
                        password != confirmPassword -> localError = passwordsMismatchText
                        username.length < 3 -> localError = "Username troppo corto (min 3 caratteri)"
                        email.isNotBlank() && password.isNotBlank() ->
                            viewModel.performRegister(email, password, username, onRegisterSuccess)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(btnScale),
                enabled = !isLoading && !usernameError && !passwordMismatch,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                AnimatedContent(
                    targetState = isLoading,
                    transitionSpec = {
                        scaleIn(bouncySpring) + fadeIn() togetherWith scaleOut() + fadeOut()
                    },
                    label = "reg_btn_content"
                ) { loading ->
                    if (loading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.HowToReg,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.register),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Back to login ──────────────────────────────────
            TextButton(
                onClick = onNavigateToLogin,
                enabled = !isLoading
            ) {
                Text(
                    stringResource(R.string.already_have_account),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Shared: Expressive TextField with password toggle
// ─────────────────────────────────────────────
@Composable
private fun ExpressiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = if (isPassword && onTogglePasswordVisibility != null) {
            {
                BouncyIconButton(onClick = onTogglePasswordVisibility) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Nascondi" else "Mostra"
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible)
            PasswordVisualTransformation()
        else
            VisualTransformation.None,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        isError = isError,
        shape = RoundedCornerShape(16.dp),
        supportingText = supportingText?.let { { Text(it) } }
    )
}

// ─────────────────────────────────────────────
// Shared: Bouncy Icon Button
// ─────────────────────────────────────────────
@Composable
private fun BouncyIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.78f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label = "bouncy_icon"
    )
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(150)
            pressed = false
        }
    }
    IconButton(
        onClick = { pressed = true; onClick() },
        modifier = Modifier.scale(scale)
    ) {
        content()
    }
}

// ─────────────────────────────────────────────
// Internal data class for form field config
// ─────────────────────────────────────────────
private data class FormField(
    val value: String,
    val onValueChange: (String) -> Unit,
    val label: String,
    val icon: ImageVector,
    val isPassword: Boolean = false,
    val passwordVisible: Boolean = false,
    val onTogglePasswordVisibility: (() -> Unit)? = null,
    val isError: Boolean = false,
    val supportingText: String? = null
)