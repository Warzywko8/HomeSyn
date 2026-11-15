package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EggAlt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Household
import com.example.data.model.ShoppingAisle
import com.example.data.model.ShoppingItem
import com.example.domain.PolishShoppingCategorizer
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    household: Household,
    itemsState: UiState<List<ShoppingItem>>,
    isOnline: Boolean,
    errorMessage: String?,
    onAddItem: (String) -> Unit,
    onTogglePurchased: (ShoppingItem) -> Unit,
    onChangeCategory: (ShoppingItem, ShoppingAisle) -> Unit,
    onDeleteItem: (ShoppingItem) -> Unit,
    onClearPurchased: (List<ShoppingItem>) -> Unit,
    onUpdateAisleOrder: (List<String>) -> Unit,
    onClearError: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var quickInput by rememberSaveable { mutableStateOf("") }
    var showPurchasedSection by rememberSaveable { mutableStateOf(false) }
    var showAisleOrderSheet by remember { mutableStateOf(false) }

    val parsedPreview = remember(quickInput) {
        if (quickInput.isBlank()) null else PolishShoppingCategorizer.parseAndCategorize(quickInput)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
        ) {
            AnimatedVisibility(visible = !isOnline) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Tryb offline — zmiany zsynchronizują się automatycznie po powrocie zasięgu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            if (errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
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
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Zamknij błąd",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Sub-header row with active count & aisle order button
            val itemsList = (itemsState as? UiState.Success)?.data.orEmpty()
            val activeItems = remember(itemsList) { itemsList.filter { !it.isPurchased } }
            val purchasedItems = remember(itemsList) { itemsList.filter { it.isPurchased } }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (activeItems.isEmpty()) {
                        "Brak aktywnych produktów"
                    } else {
                        "Do kupienia: ${activeItems.size}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AssistChip(
                    onClick = { showAisleOrderSheet = true },
                    label = { Text("Kolejność alejek") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.SwapVert,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("edit_aisle_order_button")
                )
            }

            when (itemsState) {
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
                            text = itemsState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is UiState.Success -> {
                    val orderedAisles = remember(household.aisleOrder) { household.orderedAisles() }
                    val groupedActive = remember(activeItems, orderedAisles) {
                        orderedAisles.mapNotNull { aisle ->
                            val group = activeItems.filter { it.aisle() == aisle }
                            if (group.isEmpty()) null else aisle to group
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (activeItems.isEmpty()) {
                            item(key = "empty_shopping", contentType = "empty_state") {
                                EmptyShoppingCard()
                            }
                        } else {
                            groupedActive.forEach { (aisle, groupItems) ->
                                item(key = "header_${aisle.key}", contentType = "aisle_header") {
                                    AisleHeaderRow(aisle = aisle, count = groupItems.size)
                                }
                                items(
                                    items = groupItems,
                                    key = { "active_${it.id}" },
                                    contentType = { "active_item" }
                                ) { item ->
                                    ShoppingItemRow(
                                        item = item,
                                        onToggle = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onTogglePurchased(item)
                                        },
                                        onChangeCategory = { newAisle -> onChangeCategory(item, newAisle) },
                                        onDelete = { onDeleteItem(item) }
                                    )
                                }
                            }
                        }

                        if (purchasedItems.isNotEmpty()) {
                            item(key = "purchased_toggle_header", contentType = "purchased_header") {
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { showPurchasedSection = !showPurchasedSection }
                                        .testTag("toggle_purchased_section"),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (showPurchasedSection) {
                                                    Icons.Filled.ExpandLess
                                                } else {
                                                    Icons.Filled.ExpandMore
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Ostatnio kupione (${purchasedItems.size}/10)",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (showPurchasedSection) {
                                            TextButton(
                                                onClick = { onClearPurchased(purchasedItems) },
                                                modifier = Modifier.testTag("clear_purchased_button")
                                            ) {
                                                Text("Wyczyść")
                                            }
                                        }
                                    }
                                }
                            }

                            if (showPurchasedSection) {
                                items(
                                    items = purchasedItems,
                                    key = { "purchased_${it.id}" },
                                    contentType = { "purchased_item" }
                                ) { item ->
                                    PurchasedItemRow(
                                        item = item,
                                        onRestore = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onTogglePurchased(item)
                                        },
                                        onDelete = { onDeleteItem(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Thumb-friendly bottom quick-add bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnimatedVisibility(
                    visible = parsedPreview != null && parsedPreview.name.isNotBlank(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    if (parsedPreview != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = aisleIcon(parsedPreview.category),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Alejka: ${parsedPreview.category.displayNamePl}" +
                                    if (parsedPreview.quantityText.isNotBlank()) {
                                        " • Ilość: ${parsedPreview.quantityText}"
                                    } else "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = quickInput,
                        onValueChange = { quickInput = it.take(80) },
                        placeholder = { Text("Dodaj produkt, np. mleko 2l...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (quickInput.isNotBlank()) {
                                    onAddItem(quickInput)
                                    quickInput = ""
                                }
                            }
                        ),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shopping_quick_input")
                    )

                    FilledIconButton(
                        onClick = {
                            if (quickInput.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAddItem(quickInput)
                                quickInput = ""
                            }
                        },
                        enabled = quickInput.isNotBlank(),
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("shopping_add_button"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Dodaj produkt"
                        )
                    }
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

@Composable
private fun EmptyShoppingCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ShoppingBasket,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "Wszystko kupione!",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Wpisz na dole np. „banany 1 kg” albo „chleb żytni”, a aplikacja sama dobierze alejkę w sklepie.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AisleHeaderRow(aisle: ShoppingAisle, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = aisleIcon(aisle),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = aisle.displayNamePl,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "($count)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingItem,
    onToggle: () -> Unit,
    onChangeCategory: (ShoppingAisle) -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shopping_item_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isPurchased,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("check_shopping_${item.id}")
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.addedByName.isNotBlank()) {
                    Text(
                        text = "Dodał(a): ${item.addedByName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (item.quantityText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = item.quantityText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Zmień kategorię lub usuń",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    ShoppingAisle.entries.forEach { aisle ->
                        DropdownMenuItem(
                            text = { Text(aisle.displayNamePl) },
                            leadingIcon = { Icon(aisleIcon(aisle), contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onChangeCategory(aisle)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Usuń z listy", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PurchasedItemRow(
    item: ShoppingItem,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onRestore,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("purchased_item_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = true,
                onCheckedChange = { onRestore() },
                modifier = Modifier.testTag("uncheck_shopping_${item.id}")
            )
            Text(
                text = buildString {
                    append(item.name)
                    if (item.quantityText.isNotBlank()) {
                        append(" (${item.quantityText})")
                    }
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = TextDecoration.LineThrough
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Usuń z historii",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AisleOrderBottomSheet(
    currentOrder: List<ShoppingAisle>,
    onDismiss: () -> Unit,
    onSaveOrder: (List<ShoppingAisle>) -> Unit
) {
    var workingOrder by remember(currentOrder) { mutableStateOf(currentOrder) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Kolejność alejek w Twoim sklepie",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Ustaw kategorie tak, jak idziesz przez sklep, aby nie wracać między działami.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))

            workingOrder.forEachIndexed { index, aisle ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = aisleIcon(aisle),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = aisle.displayNamePl,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val mutable = workingOrder.toMutableList()
                                    val item = mutable.removeAt(index)
                                    mutable.add(index - 1, item)
                                    workingOrder = mutable
                                }
                            },
                            enabled = index > 0
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Przesuń wyżej")
                        }
                        IconButton(
                            onClick = {
                                if (index < workingOrder.lastIndex) {
                                    val mutable = workingOrder.toMutableList()
                                    val item = mutable.removeAt(index)
                                    mutable.add(index + 1, item)
                                    workingOrder = mutable
                                }
                            },
                            enabled = index < workingOrder.lastIndex
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Przesuń niżej")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Anuluj")
                }
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Button(
                    onClick = { onSaveOrder(workingOrder) },
                    modifier = Modifier.testTag("save_aisle_order_button")
                ) {
                    Text("Zapisz kolejność")
                }
            }
        }
    }
}

private fun aisleIcon(aisle: ShoppingAisle): ImageVector {
    return when (aisle) {
        ShoppingAisle.VEG_FRUIT -> Icons.Filled.Eco
        ShoppingAisle.BAKERY -> Icons.Filled.BakeryDining
        ShoppingAisle.DAIRY -> Icons.Filled.EggAlt
        ShoppingAisle.MEAT_FISH -> Icons.Filled.Restaurant
        ShoppingAisle.DRY_GOODS -> Icons.Filled.Inventory2
        ShoppingAisle.HOME_CHEM -> Icons.Filled.CleaningServices
        ShoppingAisle.OTHER -> Icons.Filled.ShoppingBasket
    }
}
