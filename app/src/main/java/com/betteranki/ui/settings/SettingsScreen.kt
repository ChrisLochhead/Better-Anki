package com.betteranki.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betteranki.data.model.Deck
import com.betteranki.ui.theme.AppColors

private val LocalNumberFieldCommitSignal = compositionLocalOf { 0 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val editingSettings by viewModel.editingSettings.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var numberFieldCommitSignal by remember { mutableIntStateOf(0) }
    var pendingSave by remember { mutableStateOf(false) }

    LaunchedEffect(pendingSave, numberFieldCommitSignal) {
        if (!pendingSave) return@LaunchedEffect

        // Give NumberField composables a chance to coerce empty -> 0
        withFrameNanos { }
        withFrameNanos { }

        viewModel.saveSettings()
        viewModel.showSaveDialog()
        pendingSave = false
    }

    var showNotificationsSettingsPrompt by remember { mutableStateOf(false) }
    
    CompositionLocalProvider(LocalNumberFieldCommitSignal provides numberFieldCommitSignal) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Account (Firebase)
            Text(
                text = "Account",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (!uiState.firebaseConfigured) {
                Text(
                    text = "Firebase isn't configured yet (add google-services.json to enable sync).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (uiState.signedInEmail != null) {
                Text(
                    text = "Signed in as ${uiState.signedInEmail}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.signOut() },
                        enabled = !uiState.authBusy
                    ) {
                        Text("Sign out")
                    }
                }
            } else {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val icon = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                        val desc = if (passwordVisible) "Hide password" else "Show password"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(icon, contentDescription = desc)
                        }
                    }
                )
                if (uiState.authError != null) {
                    Text(
                        text = uiState.authError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
                if (uiState.authStatus != null) {
                    Text(
                        text = uiState.authStatus ?: "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.signIn(email, password) },
                        enabled = !uiState.authBusy && email.isNotBlank() && password.isNotBlank()
                    ) {
                        Text("Sign in")
                    }
                    OutlinedButton(
                        onClick = { viewModel.signUp(email, password) },
                        enabled = !uiState.authBusy && email.isNotBlank() && password.isNotBlank()
                    ) {
                        Text("Create account")
                    }
                }

                if (uiState.showForgotPassword) {
                    TextButton(
                        onClick = { viewModel.sendPasswordReset(email) },
                        enabled = !uiState.authBusy && email.isNotBlank()
                    ) {
                        Text("Forgot password?")
                    }
                }
                if (uiState.authBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Signing in…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-sync after each review",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Uploads progress automatically when you swipe",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.autoSyncAfterReview,
                    onCheckedChange = { viewModel.setAutoSyncAfterReview(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AppColors.Primary,
                        checkedTrackColor = AppColors.Primary.copy(alpha = 0.5f)
                    )
                )
            }

            Divider()

            // Preset Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Deck Selection
                DeckDropdown(
                    decks = uiState.availableDecks,
                    selectedDeckId = editingSettings.deckId,
                    onDeckSelected = { viewModel.updateDeckId(it) },
                    modifier = Modifier.weight(1f)
                )
                
                // Preset Selection
                if (uiState.availablePresets.isNotEmpty()) {
                    PresetDropdown(
                        presets = uiState.availablePresets.map { it.name to it.id },
                        selectedPresetId = uiState.selectedPresetId,
                        onPresetSelected = { viewModel.selectPreset(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Divider()
            
            // Daily Limits Section
            Text(
                text = "Daily Limits",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            NumberField(
                label = "New Cards Per Day",
                value = editingSettings.dailyNewCards,
                onValueChange = { viewModel.updateDailyNewCards(it) }
            )
            
            NumberField(
                label = "Maximum Reviews Per Day",
                value = editingSettings.dailyReviewLimit,
                onValueChange = { viewModel.updateDailyReviewLimit(it) }
            )
            
            Divider()
            
            // Spaced Repetition Intervals
            Text(
                text = "Review Intervals (minutes)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Set how long before a card appears again based on difficulty",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            NumberField(
                label = "Again (didn't know it)",
                value = editingSettings.againIntervalMinutes,
                onValueChange = { viewModel.updateAgainInterval(it) },
                helperText = "${formatInterval(editingSettings.againIntervalMinutes)}"
            )
            
            NumberField(
                label = "Hard (took a while)",
                value = editingSettings.hardIntervalMinutes,
                onValueChange = { viewModel.updateHardInterval(it) },
                helperText = "${formatInterval(editingSettings.hardIntervalMinutes)}"
            )
            
            NumberField(
                label = "Good (knew it well)",
                value = editingSettings.goodIntervalMinutes,
                onValueChange = { viewModel.updateGoodInterval(it) },
                helperText = "${formatInterval(editingSettings.goodIntervalMinutes)}"
            )
            
            NumberField(
                label = "Easy (instant recall)",
                value = editingSettings.easyIntervalMinutes,
                onValueChange = { viewModel.updateEasyInterval(it) },
                helperText = "${formatInterval(editingSettings.easyIntervalMinutes)}"
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Response Time Thresholds
            Text(
                text = "Response Time Thresholds (seconds)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "How quickly you need to flip the card to be considered well-remembered",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            NumberField(
                label = "Easy threshold (very confident)",
                value = editingSettings.easyThresholdSeconds,
                onValueChange = { viewModel.updateEasyThreshold(it) },
                helperText = "Less than ${editingSettings.easyThresholdSeconds}s = Easy"
            )
            
            NumberField(
                label = "Good threshold (remembered well)",
                value = editingSettings.goodThresholdSeconds,
                onValueChange = { viewModel.updateGoodThreshold(it) },
                helperText = "${editingSettings.easyThresholdSeconds}s - ${editingSettings.goodThresholdSeconds}s = Good"
            )
            
            Text(
                text = "Note: Times longer than ${editingSettings.goodThresholdSeconds}s are treated as Hard",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            
            // Leniency Mode Section
            Text(
                text = "Leniency Mode",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Prevents review pileup after missed days",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enable Leniency Mode",
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = editingSettings.leniencyModeEnabled,
                    onCheckedChange = { viewModel.updateLeniencyModeEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AppColors.Primary,
                        checkedTrackColor = AppColors.Primary.copy(alpha = 0.5f)
                    )
                )
            }
            
            if (editingSettings.leniencyModeEnabled) {
                NumberField(
                    label = "Max New Cards After Skipped Days",
                    value = editingSettings.maxNewCardsAfterSkip,
                    onValueChange = { viewModel.updateMaxNewCardsAfterSkip(it) },
                    helperText = "Caps new cards when you miss days studying"
                )
                
                NumberField(
                    label = "Max Review Cards",
                    value = editingSettings.maxReviewCards,
                    onValueChange = { viewModel.updateMaxReviewCards(it) },
                    helperText = "Maximum reviews per session"
                )
                
                NumberField(
                    label = "Daily Reviews Addable",
                    value = editingSettings.dailyReviewsAddable,
                    onValueChange = { viewModel.updateDailyReviewsAddable(it) },
                    helperText = "Max reviews that can pile up per missed day"
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            
            // Decay Mode Section
            Text(
                text = "Decay Mode",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Gradually reduce cards after extended inactivity",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enable Decay Mode",
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = editingSettings.decayModeEnabled,
                    onCheckedChange = { viewModel.updateDecayModeEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AppColors.CardLearning,
                        checkedTrackColor = AppColors.CardLearning.copy(alpha = 0.5f)
                    )
                )
            }
            
            if (editingSettings.decayModeEnabled) {
                NumberField(
                    label = "Days Before Decay Starts",
                    value = editingSettings.decayStartDays,
                    onValueChange = { viewModel.updateDecayStartDays(it.coerceAtLeast(1)) },
                    helperText = "Inactivity days before card reduction begins"
                )
                
                NumberField(
                    label = "Cards Reduced Per Day",
                    value = editingSettings.decayRatePerDay,
                    onValueChange = { viewModel.updateDecayRatePerDay(it.coerceAtLeast(1)) },
                    helperText = "How many fewer cards each day after decay starts"
                )
                
                NumberField(
                    label = "Minimum Cards Floor",
                    value = editingSettings.decayMinCards,
                    onValueChange = { viewModel.updateDecayMinCards(it.coerceAtLeast(1)) },
                    helperText = "Cards won't go below this number"
                )
                
                // Show decay preview
                val previewText = buildString {
                    append("Preview: After ${editingSettings.decayStartDays} days inactive, ")
                    val daysToReachFloor = ((editingSettings.dailyNewCards - editingSettings.decayMinCards) / editingSettings.decayRatePerDay.coerceAtLeast(1))
                    append("new cards decay from ${editingSettings.dailyNewCards} to ${editingSettings.decayMinCards} over ~$daysToReachFloor days")
                }
                Text(
                    text = previewText,
                    fontSize = 11.sp,
                    color = AppColors.CardLearning,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            
            // Notification Settings Section
            Text(
                text = "Notifications",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Get reminded to study on specific days",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Permission launcher for Android 13+
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    viewModel.updateNotificationsEnabled(true)
                } else {
                    // Permission denied (possibly "Don't ask again"); guide user to settings.
                    showNotificationsSettingsPrompt = true
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enable Notifications",
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = editingSettings.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                viewModel.updateNotificationsEnabled(true)
                            } else {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            viewModel.updateNotificationsEnabled(enabled)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AppColors.Secondary,
                        checkedTrackColor = AppColors.Secondary.copy(alpha = 0.5f)
                    )
                )
            }

            if (showNotificationsSettingsPrompt) {
                AlertDialog(
                    onDismissRequest = { showNotificationsSettingsPrompt = false },
                    title = { Text("Enable notifications") },
                    text = {
                        Text(
                            "Notifications permission is off. Enable it in app settings to receive reminders."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showNotificationsSettingsPrompt = false
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", context.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        ) { Text("Open settings") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showNotificationsSettingsPrompt = false }) { Text("Cancel") }
                    },
                    shape = RoundedCornerShape(2.dp)
                )
            }
            
            if (editingSettings.notificationsEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Notification Days",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                
                val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dayNames.forEachIndexed { index, dayName ->
                        val dayNumber = index + 1 // 1 = Monday, 7 = Sunday
                        val isSelected = editingSettings.notificationDays.contains(dayNumber)
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(
                                    if (isSelected) AppColors.Secondary.copy(alpha = 0.55f)
                                    else AppColors.DarkSurfaceVariant,
                                    RoundedCornerShape(4.dp)
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) AppColors.Secondary else AppColors.Border,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    val newDays = if (isSelected) {
                                        editingSettings.notificationDays - dayNumber
                                    } else {
                                        editingSettings.notificationDays + dayNumber
                                    }
                                    viewModel.updateNotificationDays(newDays)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayName.take(1),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) AppColors.DarkBackground else AppColors.TextSecondary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Time picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Notification Time",
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NumberField(
                            label = "Hour",
                            value = editingSettings.notificationHour,
                            onValueChange = { viewModel.updateNotificationHour(it.coerceIn(0, 23)) },
                            modifier = Modifier.width(80.dp),
                            showZeroAsEmpty = true
                        )
                        Text(":", fontWeight = FontWeight.Bold)
                        NumberField(
                            label = "Min",
                            value = editingSettings.notificationMinute,
                            onValueChange = { viewModel.updateNotificationMinute(it.coerceIn(0, 59)) },
                            modifier = Modifier.width(80.dp),
                            showZeroAsEmpty = true
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            
            // OCR Translation Settings Section
            Text(
                text = "OCR Translation",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Default languages for photo card translation",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Language dropdowns
            val commonLanguages = mapOf(
                "ja" to "Japanese",
                "en" to "English",
                "es" to "Spanish",
                "fr" to "French",
                "de" to "German",
                "it" to "Italian",
                "pt" to "Portuguese",
                "ru" to "Russian",
                "zh" to "Chinese",
                "ko" to "Korean",
                "ar" to "Arabic",
                "hi" to "Hindi"
            )
            
            var showSourceLanguageMenu by remember { mutableStateOf(false) }
            var showTargetLanguageMenu by remember { mutableStateOf(false) }
            
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Source Language
                Column {
                    Text(
                        text = "Source Language (Text in Photo)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box {
                        OutlinedButton(
                            onClick = { showSourceLanguageMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Text(
                                commonLanguages[editingSettings.ocrSourceLanguage] 
                                    ?: editingSettings.ocrSourceLanguage
                            )
                        }
                        DropdownMenu(
                            expanded = showSourceLanguageMenu,
                            onDismissRequest = { showSourceLanguageMenu = false }
                        ) {
                            commonLanguages.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        viewModel.updateOcrSourceLanguage(code)
                                        showSourceLanguageMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Target Language
                Column {
                    Text(
                        text = "Target Language (Translation)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box {
                        OutlinedButton(
                            onClick = { showTargetLanguageMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Text(
                                commonLanguages[editingSettings.ocrTargetLanguage] 
                                    ?: editingSettings.ocrTargetLanguage
                            )
                        }
                        DropdownMenu(
                            expanded = showTargetLanguageMenu,
                            onDismissRequest = { showTargetLanguageMenu = false }
                        ) {
                            commonLanguages.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        viewModel.updateOcrTargetLanguage(code)
                                        showTargetLanguageMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Save Button
            Button(
                onClick = {
                    numberFieldCommitSignal += 1
                    pendingSave = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(2.dp)
            ) {
                Text("Save Settings")
            }
        }
    }
    }
    
    // Save as Preset Dialog
    if (uiState.showSaveDialog) {
        SavePresetDialog(
            availablePresets = uiState.availablePresets.map { it.name to it.id },
            onDismiss = { viewModel.dismissSaveDialog() },
            onSave = { name, overwriteId ->
                if (name.isNotBlank()) {
                    viewModel.saveAsPreset(name, overwriteId)
                } else {
                    viewModel.dismissSaveDialog()
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDropdown(
    decks: List<Deck>,
    selectedDeckId: Long,
    onDeckSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDeck = decks.find { it.id == selectedDeckId }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedDeck?.name ?: "All Decks",
            onValueChange = {},
            readOnly = true,
            label = { Text("Deck") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("All Decks") },
                onClick = {
                    onDeckSelected(-1L)
                    expanded = false
                }
            )
            decks.forEach { deck ->
                DropdownMenuItem(
                    text = { Text(deck.name) },
                    onClick = {
                        onDeckSelected(deck.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetDropdown(
    presets: List<Pair<String, Long>>,
    selectedPresetId: Long?,
    onPresetSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedPreset = presets.find { it.second == selectedPresetId }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedPreset?.first ?: "Load Preset",
            onValueChange = {},
            readOnly = true,
            label = { Text("Preset") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            presets.forEach { (name, id) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onPresetSelected(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun NumberField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    helperText: String? = null,
    modifier: Modifier = Modifier,
    showZeroAsEmpty: Boolean = false
) {
    var textValue by remember { mutableStateOf("") }
    val commitSignal = LocalNumberFieldCommitSignal.current

    LaunchedEffect(value, showZeroAsEmpty) {
        if (showZeroAsEmpty && value == 0) {
            // Only show empty for 0 if the user hasn't explicitly typed "0".
            if (textValue != "0") textValue = ""
        } else {
            val currentParsed = textValue.toIntOrNull()
            if (currentParsed != value) {
                textValue = value.toString()
            }
        }
    }

    LaunchedEffect(commitSignal) {
        if (commitSignal == 0) return@LaunchedEffect
        if (textValue.isEmpty()) {
            onValueChange(0)
            textValue = "0"
        }
    }
    
    Column(modifier = modifier) {
        OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                textValue = newValue
                // Allow empty string without calling onValueChange immediately
                // Only update if there's a valid number
                newValue.toIntOrNull()?.let { onValueChange(it) }
            },
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(2.dp)
        )
        if (helperText != null) {
            Text(
                text = helperText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavePresetDialog(
    availablePresets: List<Pair<String, Long>>,
    onDismiss: () -> Unit,
    onSave: (String, Long?) -> Unit
) {
    var presetName by remember { mutableStateOf("") }
    var overwriteExpanded by remember { mutableStateOf(false) }
    var overwritePresetId by remember { mutableStateOf<Long?>(null) }
    val overwritePreset = availablePresets.find { it.second == overwritePresetId }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as Preset?") },
        text = {
            Column {
                Text("Give your preset a name to save these settings for later use.")
                Spacer(modifier = Modifier.height(16.dp))

                // Overwrite dropdown (optional)
                ExposedDropdownMenuBox(
                    expanded = overwriteExpanded,
                    onExpandedChange = { overwriteExpanded = it }
                ) {
                    OutlinedTextField(
                        value = overwritePreset?.first ?: "Create new preset",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Overwrite") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = overwriteExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(2.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = overwriteExpanded,
                        onDismissRequest = { overwriteExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Create new preset") },
                            onClick = {
                                overwritePresetId = null
                                overwriteExpanded = false
                            }
                        )
                        availablePresets.forEach { (name, id) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    overwritePresetId = id
                                    presetName = name
                                    overwriteExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Preset Name") },
                    placeholder = { Text("e.g., Intensive Review") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(2.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(presetName, overwritePresetId) }) {
                Text("Save as Preset")
            }
        },
        dismissButton = {
            TextButton(onClick = { onSave("", null) }) {
                Text("Save Without Preset")
            }
        },
        shape = RoundedCornerShape(2.dp)
    )
}

fun formatInterval(minutes: Int): String {
    return when {
        minutes < 60 -> "$minutes min"
        minutes < 1440 -> "${minutes / 60} hour${if (minutes / 60 > 1) "s" else ""}"
        else -> "${minutes / 1440} day${if (minutes / 1440 > 1) "s" else ""}"
    }
}
