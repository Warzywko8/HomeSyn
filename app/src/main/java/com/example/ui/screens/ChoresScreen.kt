package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChoreIconOption
import com.example.data.model.ChoreResetMode
import com.example.data.model.ChoreTile
import com.example.ui.viewmodel.ChoreTileUiModel
import com.example.ui.viewmodel.UiState

@Composable
fun ChoresScreen(
    choresState: UiState<List<ChoreTileUiModel>>,
    errorMessage: String?,
    onToggleChore: (ChoreTileUiModel) -> Unit,
    onAddChore: (title: String, icon: ChoreIconOption, mode: ChoreResetMode, hours: List<Long>, pin: Boolean) -> Unit,
    onUpdateChore: (chore: ChoreTile, title: String, icon: ChoreIconOption, mode: ChoreResetMode, hours: List<Long>, pin: Boolean) -> Unit,
    onUpdatePinnedChores: (Set<String>) -> Unit,
    onDeleteChore: (String) -> Unit,
    onClearError: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var editingChore by remember { mutableStateOf<ChoreTile?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showWidgetPinSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            if (errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearError) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Zamknij komunikat",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            when (choresState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = choresState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is UiState.Success -> {
                    val tiles = choresState.data
                    val pinnedCount = tiles.count { it.tile.isPinnedToWidget }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tapnij kafelek, aby odznaczyć",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AssistChip(
                            onClick = { showWidgetPinSheet = true },
                            label = { Text("Widżet: $pinnedCount/2") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                trailingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = null,
                            modifier = Modifier.testTag("widget_pin_picker_button")
                        )
                    }

                    if (tiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Brak kafelków statusu",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        text = "Dodaj pierwszy mikro-obowiązek (np. „Pies nakarmiony”), aby domownicy nie musieli pytać na czacie.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 156.dp),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 92.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = tiles,
                                key = { it.tile.id },
                                contentType = { "chore_tile" }
                            ) { model ->
                                ChoreGridCard(
                                    model = model,
                                    onTap = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleChore(model)
                                    },
                                    onEdit = { editingChore = model.tile }
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_chore_fab")
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Dodaj obowiązek")
        }
    }

    if (showCreateDialog) {
        ChoreEditorDialog(
            existing = null,
            onDismiss = { showCreateDialog = false },
            onSave = { title, icon, mode, hours, pin ->
                onAddChore(title, icon, mode, hours, pin)
                showCreateDialog = false
            },
            onDelete = null
        )
    }

    editingChore?.let { target ->
        ChoreEditorDialog(
            existing = target,
            onDismiss = { editingChore = null },
            onSave = { title, icon, mode, hours, pin ->
                onUpdateChore(target, title, icon, mode, hours, pin)
                editingChore = null
            },
            onDelete = {
                onDeleteChore(target.id)
                editingChore = null
            }
        )
    }

    if (showWidgetPinSheet) {
        val currentTiles = (choresState as? UiState.Success)?.data.orEmpty()
        WidgetPinPickerSheet(
            tiles = currentTiles,
            onDismiss = { showWidgetPinSheet = false },
            onSave = { selectedIds ->
                onUpdatePinnedChores(selectedIds)
                showWidgetPinSheet = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChoreGridCard(
    model: ChoreTileUiModel,
    onTap: () -> Unit,
    onEdit: () -> Unit
) {
    val isDone = model.status.isDoneForCurrentPeriod
    val containerColor by animateColorAsState(
        targetValue = if (isDone) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        label = "chore_card_bg"
    )
    val contentColor = if (isDone) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = onEdit
            )
            .testTag("chore_tile_${model.tile.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDone) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = choreIcon(model.tile.icon()),
                        contentDescription = null,
                        tint = if (isDone) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (model.tile.isPinnedToWidget) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Przypięty do widżetu",
                            tint = contentColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edytuj kafelek",
                            tint = contentColor.copy(alpha = 0.65f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = model.tile.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isDone) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = model.status.statusTextPl,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDone) {
                            contentColor.copy(alpha = 0.9f)
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "${model.tile.icon().labelPl} • ${model.status.scheduleHintPl}",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoreEditorDialog(
    existing: ChoreTile?,
    onDismiss: () -> Unit,
    onSave: (String, ChoreIconOption, ChoreResetMode, List<Long>, Boolean) -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var selectedIcon by remember { mutableStateOf(existing?.icon() ?: ChoreIconOption.OTHER) }
    var resetMode by remember { mutableStateOf(existing?.mode() ?: ChoreResetMode.DAILY_MIDNIGHT) }
    var selectedHours by remember {
        mutableStateOf(
            existing?.resetHours?.takeIf { it.isNotEmpty() } ?: listOf(8L, 18L)
        )
    }
    var pinToWidget by remember { mutableStateOf(existing?.isPinnedToWidget ?: false) }

    val availableHours = listOf(7L, 8L, 12L, 16L, 18L, 20L, 22L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existing == null) "Nowy mikro-obowiązek" else "Edytuj obowiązek")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(50) },
                    label = { Text("Nazwa obowiązku") },
                    placeholder = { Text("np. Kuwteta wyczyszczona") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chore_title_input")
                )

                Text("Kategoria", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChoreIconOption.entries.forEach { option ->
                        FilterChip(
                            selected = selectedIcon == option,
                            onClick = { selectedIcon = option },
                            label = { Text(option.labelPl) },
                            leadingIcon = {
                                Icon(
                                    imageVector = choreIcon(option),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                Text("Automatyczny reset", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoreResetMode.entries.forEach { mode ->
                        FilterChip(
                            selected = resetMode == mode,
                            onClick = { resetMode = mode },
                            label = { Text(mode.labelPl) }
                        )
                    }
                }

                if (resetMode == ChoreResetMode.SCHEDULED_HOURS) {
                    Text("Godziny resetu", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableHours.forEach { h ->
                            val isSelected = h in selectedHours
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedHours = if (isSelected && selectedHours.size > 1) {
                                        selectedHours - h
                                    } else if (!isSelected) {
                                        (selectedHours + h).sorted()
                                    } else {
                                        selectedHours
                                    }
                                },
                                label = { Text(String.format("%02d:00", h)) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Przypnij do widżetu", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Widżet mieści maks. 2 obowiązki",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = pinToWidget,
                        onCheckedChange = { pinToWidget = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, selectedIcon, resetMode, selectedHours, pinToWidget) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_chore_button")
            ) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Usuń obowiązek",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Anuluj")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetPinPickerSheet(
    tiles: List<ChoreTileUiModel>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var selectedIds by remember(tiles) {
        mutableStateOf(
            tiles.filter { it.tile.isPinnedToWidget }
                .map { it.tile.id }
                .take(2)
                .toSet()
        )
    }
    var limitHint by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Obowiązki na widżecie",
                    style = MaterialTheme.typography.titleLarge
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${selectedIds.size}/2",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Wybierz maksymalnie 2 obowiązki, które wyświetlą się na ekranie głównym telefonu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (limitHint || selectedIds.size >= 2) {
                Text(
                    text = if (limitHint) {
                        "Możesz wybrać maksymalnie 2 obowiązki. Odznacz jeden, aby wybrać inny."
                    } else {
                        "Wybrano maksimum (2/2). Odznacz jeden kafelek, aby zamienić."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (limitHint) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            if (tiles.isEmpty()) {
                Text(
                    text = "Brak obowiązków. Dodaj najpierw kafelek przyciskiem +.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tiles.forEach { model ->
                        val isChecked = model.tile.id in selectedIds
                        val toggleSelection = {
                            if (isChecked) {
                                selectedIds = selectedIds - model.tile.id
                                limitHint = false
                            } else if (selectedIds.size < 2) {
                                selectedIds = selectedIds + model.tile.id
                                limitHint = false
                            } else {
                                limitHint = true
                            }
                        }

                        Card(
                            onClick = toggleSelection,
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChecked) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("widget_pick_chore_${model.tile.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isChecked) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = choreIcon(model.tile.icon()),
                                        contentDescription = null,
                                        tint = if (isChecked) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.tile.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = model.status.scheduleHintPl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }

                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { toggleSelection() }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Anuluj")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSave(selectedIds) },
                    modifier = Modifier.testTag("save_widget_pins_button")
                ) {
                    Text("Zapisz widżet")
                }
            }
        }
    }
}

private fun choreIcon(option: ChoreIconOption): ImageVector {
    return when (option) {
        ChoreIconOption.KITCHEN -> Icons.Filled.Kitchen
        ChoreIconOption.CLEANING -> Icons.Filled.CleaningServices
        ChoreIconOption.LAUNDRY -> Icons.Filled.LocalLaundryService
        ChoreIconOption.PETS -> Icons.Filled.Pets
        ChoreIconOption.PLANTS -> Icons.Filled.Yard
        ChoreIconOption.HOME -> Icons.Filled.Home
        ChoreIconOption.OTHER -> Icons.Filled.Category
    }
}
