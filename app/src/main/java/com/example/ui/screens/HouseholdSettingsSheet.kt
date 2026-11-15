package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Household
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.StartTabPreference
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdSettingsSheet(
    household: Household,
    currentUserId: String,
    currentUserName: String,
    isSavingIdentity: Boolean,
    useCustomWeights: Boolean,
    useDynamicColor: Boolean,
    themeMode: AppThemeMode,
    startTab: StartTabPreference,
    onSaveIdentity: (householdName: String, memberDisplayName: String) -> Unit,
    onToggleCustomWeights: (Boolean) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onSelectStartTab: (StartTabPreference) -> Unit,
    onUpdateAisleOrder: (List<String>) -> Unit,
    onSaveWeights: (Map<String, Double>) -> Unit,
    onLeaveHousehold: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var copiedNotice by remember { mutableStateOf(false) }
    var showAisleOrderSheet by remember { mutableStateOf(false) }

    var houseNameDraft by remember(household.id, household.name) { mutableStateOf(household.name) }
    var memberNameDraft by remember(household.id, currentUserName) { mutableStateOf(currentUserName) }
    var identitySavedNotice by remember(household.name, currentUserName) { mutableStateOf(false) }

    val hasIdentityChanges = houseNameDraft.trim() != household.name.trim() ||
        memberNameDraft.trim() != currentUserName.trim()
    val isIdentityValid = houseNameDraft.trim().isNotEmpty() && memberNameDraft.trim().isNotEmpty()

    var weightsDraft by remember(household.members) {
        mutableStateOf(
            household.memberUids.associateWith { uid ->
                household.members[uid]?.weight ?: 1.0
            }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Ustawienia domu",
                style = MaterialTheme.typography.headlineSmall
            )

            // Section 1: Household & Profile Identity Customization
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Nazwa domu i Twój profil",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    OutlinedTextField(
                        value = houseNameDraft,
                        onValueChange = {
                            houseNameDraft = it.take(60)
                            identitySavedNotice = false
                        },
                        label = { Text("Nazwa domu") },
                        placeholder = { Text("np. Nasze Mieszkanie") },
                        leadingIcon = {
                            Icon(Icons.Filled.Home, contentDescription = null)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_house_name_input")
                    )

                    OutlinedTextField(
                        value = memberNameDraft,
                        onValueChange = {
                            memberNameDraft = it.take(60)
                            identitySavedNotice = false
                        },
                        label = { Text("Twoje wyświetlane imię") },
                        placeholder = { Text("np. Ania") },
                        leadingIcon = {
                            Icon(Icons.Filled.Person, contentDescription = null)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_member_name_input")
                    )

                    AnimatedVisibility(visible = identitySavedNotice) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Zaktualizowano dane domu i profilu",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            onSaveIdentity(houseNameDraft, memberNameDraft)
                            identitySavedNotice = true
                        },
                        enabled = !isSavingIdentity && hasIdentityChanges && isIdentityValid,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_save_identity_button")
                    ) {
                        Text(if (isSavingIdentity) "Zapisywanie..." else "Zapisz nazwę i profil")
                    }
                }
            }

            // Section 2: Invite code & Share sheet
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Kod zaproszenia dla domowników",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = household.inviteCode,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                letterSpacing = 4.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("Kod HouseSync", household.inviteCode)
                                )
                                copiedNotice = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Kopiuj kod",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    if (copiedNotice) {
                        Text(
                            text = "Skopiowano kod ${household.inviteCode} do schowka",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Button(
                        onClick = {
                            val shareText = "Dołącz do naszego domu „${household.name}” w HouseSync! " +
                                "Wpisz kod: ${household.inviteCode}"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(
                                Intent.createChooser(intent, "Wyślij zaproszenie do HouseSync")
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("share_invite_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Udostępnij zaproszenie")
                    }
                }
            }

            // Section 3: Members & optional split weights
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Domownicy (${household.memberUids.size})",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Własne proporcje rozliczeń", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Domyślnie równy podział 1/N; włącz, aby ustawić np. 60/40",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useCustomWeights,
                        onCheckedChange = onToggleCustomWeights
                    )
                }

                household.memberUids.forEach { uid ->
                    val name = household.memberName(uid)
                    val weight = weightsDraft[uid] ?: 1.0
                    val totalWeight = weightsDraft.values.sum().coerceAtLeast(1.0)
                    val pct = ((weight / totalWeight) * 100.0).roundToInt()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name.take(1).uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (uid == currentUserId) "$name (Ty)" else name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            if (useCustomWeights) {
                                Slider(
                                    value = weight.toFloat(),
                                    onValueChange = { newVal ->
                                        weightsDraft = weightsDraft + (uid to newVal.toDouble())
                                    },
                                    valueRange = 0.5f..3.0f,
                                    steps = 4
                                )
                            }
                        }
                        if (useCustomWeights) {
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (useCustomWeights) {
                    FilledTonalButton(
                        onClick = { onSaveWeights(weightsDraft) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Zapisz proporcje podziału")
                    }
                }
            }

            HorizontalDivider()

            // Section 4: App Appearance & Navigation Customization
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Personalizacja aplikacji",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Motyw kolorów",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AppThemeMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { onSelectThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = idx,
                                    count = AppThemeMode.entries.size
                                ),
                                modifier = Modifier.testTag("theme_mode_${mode.key}")
                            ) {
                                Text(mode.labelPl, maxLines = 1)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Material You (Dynamic Color)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Dopasuj kolory do tapety systemu Android 12+",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useDynamicColor,
                        onCheckedChange = onToggleDynamicColor,
                        modifier = Modifier.testTag("dynamic_color_switch")
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Domyślny ekran po uruchomieniu",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        StartTabPreference.entries.forEachIndexed { idx, tab ->
                            SegmentedButton(
                                selected = startTab == tab,
                                onClick = { onSelectStartTab(tab) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = idx,
                                    count = StartTabPreference.entries.size
                                ),
                                modifier = Modifier.testTag("start_tab_${tab.key}")
                            ) {
                                Text(tab.labelPl, maxLines = 1)
                            }
                        }
                    }
                }

                Card(
                    onClick = { showAisleOrderSheet = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_aisle_order_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kolejność alejek w sklepie",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = "Dopasuj układ kategorii do swojego sklepu",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (currentUserId != household.ownerId) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onLeaveHousehold()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Opuść dom")
                    }
                }
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onSignOut()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Wyloguj")
                }
            }
        }
    }

    if (showAisleOrderSheet) {
        AisleOrderBottomSheet(
            currentOrder = household.orderedAisles(),
            onDismiss = { showAisleOrderSheet = false },
            onSaveOrder = { newOrder ->
                onUpdateAisleOrder(newOrder.map { it.key })
                showAisleOrderSheet = false
            }
        )
    }
}
