package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.Household
import com.example.domain.BudgetStatus
import com.example.domain.CategoryBudgetProgress
import com.example.domain.ExpenseBalanceCalculator
import com.example.domain.ExpenseCategorySuggester
import com.example.domain.MonthlyBudgetSummary
import com.example.ui.viewmodel.ExpenseScreenData
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpensesScreen(
    household: Household,
    currentUserId: String,
    useCustomWeights: Boolean,
    expenseState: UiState<ExpenseScreenData>,
    errorMessage: String?,
    onAddExpense: (amountInput: String, description: String, paidByUid: String, category: ExpenseCategory) -> Unit,
    onDeleteExpense: (String) -> Unit,
    onSettleUp: () -> Unit,
    onSaveMonthlyBudget: (monthlyLimitGrosze: Long?, categoryBudgetsGrosze: Map<String, Long>) -> Unit,
    onClearError: () -> Unit
) {
    var amountInput by rememberSaveable { mutableStateOf("") }
    var descriptionInput by rememberSaveable { mutableStateOf("") }
    var manualCategoryKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPayerUid by rememberSaveable(currentUserId, household.id) {
        mutableStateOf(
            if (currentUserId in household.memberUids) currentUserId
            else household.memberUids.firstOrNull().orEmpty()
        )
    }
    var showBudgetSheet by remember { mutableStateOf(false) }

    val selectedCategory = remember(descriptionInput, manualCategoryKey) {
        manualCategoryKey?.let { ExpenseCategory.fromKey(it) }
            ?: ExpenseCategorySuggester.suggestCategory(descriptionInput)
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
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
                            contentDescription = "Zamknij błąd",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            when (expenseState) {
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
                            text = expenseState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is UiState.Success -> {
                    val data = expenseState.data
                    val summary = data.balanceSummary

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Single prominent net balance card at the top
                        item(key = "balance_hero", contentType = "balance_hero") {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("net_balance_card"),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (summary.isSettled) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (summary.isSettled) {
                                                Icons.Filled.CheckCircle
                                            } else {
                                                Icons.Filled.AccountBalanceWallet
                                            },
                                            contentDescription = null,
                                            tint = if (summary.isSettled) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            }
                                        )
                                        Text(
                                            text = if (useCustomWeights) {
                                                "Saldo netto (wg wag domu)"
                                            } else {
                                                "Saldo netto (podział równy)"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (summary.isSettled) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            }
                                        )
                                    }

                                    Text(
                                        text = summary.primaryHeadlinePl,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (summary.isSettled) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        }
                                    )

                                    if (summary.transfers.size > 1) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            summary.transfers.forEach { t ->
                                                Text(
                                                    text = "• ${t.fromName} → ${t.toName}: ${ExpenseBalanceCalculator.formatPln(t.amountGrosze)}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Suma okresu: ${ExpenseBalanceCalculator.formatPln(summary.totalCycleGrosze)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (summary.isSettled) {
                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            } else {
                                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                            }
                                        )

                                        if (!summary.isSettled) {
                                            Button(
                                                onClick = onSettleUp,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondary,
                                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                                ),
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.testTag("settle_expenses_button")
                                            ) {
                                                Text("Wyrównaj rachunki")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Monthly budget overview & planning entry point
                        item(key = "monthly_budget_card", contentType = "monthly_budget") {
                            MonthlyBudgetCard(
                                budget = data.monthlyBudget,
                                onOpenPlanner = { showBudgetSheet = true }
                            )
                        }

                        // Quick expense form with category chip selector
                        item(key = "quick_expense_form", contentType = "quick_expense_form") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Dodaj wspólny wydatek",
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = amountInput,
                                            onValueChange = { raw ->
                                                val filtered = raw.filter { it.isDigit() || it == ',' || it == '.' }
                                                amountInput = filtered.take(10)
                                            },
                                            label = { Text("Kwota (zł)") },
                                            placeholder = { Text("148,00") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Decimal,
                                                imeAction = ImeAction.Next
                                            ),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(0.42f)
                                                .testTag("expense_amount_input")
                                        )

                                        OutlinedTextField(
                                            value = descriptionInput,
                                            onValueChange = { descriptionInput = it.take(80) },
                                            label = { Text("Opis") },
                                            placeholder = { Text("np. Biedronka") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                capitalization = KeyboardCapitalization.Sentences,
                                                imeAction = ImeAction.Done
                                            ),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(0.58f)
                                                .testTag("expense_desc_input")
                                        )
                                    }

                                    Text(
                                        text = "Kategoria budżetu",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ExpenseCategory.entries.forEach { cat ->
                                            FilterChip(
                                                selected = selectedCategory == cat,
                                                onClick = { manualCategoryKey = cat.key },
                                                label = { Text(cat.shortNamePl) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = expenseCategoryIcon(cat),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                },
                                                modifier = Modifier.testTag("expense_cat_chip_${cat.key}")
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Kto zapłacił?",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        household.memberUids.forEach { uid ->
                                            FilterChip(
                                                selected = selectedPayerUid == uid,
                                                onClick = { selectedPayerUid = uid },
                                                label = { Text(household.memberName(uid)) }
                                            )
                                        }
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            onAddExpense(amountInput, descriptionInput, selectedPayerUid, selectedCategory)
                                            if (ExpenseBalanceCalculator.parsePlnToGrosze(amountInput) != null &&
                                                descriptionInput.isNotBlank()
                                            ) {
                                                amountInput = ""
                                                descriptionInput = ""
                                                manualCategoryKey = null
                                            }
                                        },
                                        enabled = amountInput.isNotBlank() && descriptionInput.isNotBlank(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("add_expense_button"),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Dodaj wydatek")
                                    }
                                }
                            }
                        }

                        item(key = "expenses_list_header", contentType = "section_header") {
                            Text(
                                text = "Wydatki w bieżącym cyklu (${data.activeCycleExpenses.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        if (data.activeCycleExpenses.isEmpty()) {
                            item(key = "expenses_empty", contentType = "empty_state") {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(18.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Payments,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Brak nierozliczonych wydatków. Wpisz kwotę i opis powyżej, gdy ktoś zapłaci za zakupy lub rachunki.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(
                                items = data.activeCycleExpenses,
                                key = { it.id },
                                contentType = { "expense_row" }
                            ) { expense ->
                                ExpenseRowCard(
                                    expense = expense,
                                    onDelete = { onDeleteExpense(expense.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBudgetSheet) {
        BudgetPlannerSheet(
            currentMonthlyLimitGrosze = household.monthlyBudgetGrosze,
            currentCategoryBudgetsGrosze = household.categoryBudgetsGrosze,
            onDismiss = { showBudgetSheet = false },
            onSave = { totalLimit, catBudgets ->
                onSaveMonthlyBudget(totalLimit, catBudgets)
                showBudgetSheet = false
            }
        )
    }
}

@Composable
private fun MonthlyBudgetCard(
    budget: MonthlyBudgetSummary,
    onOpenPlanner: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("monthly_budget_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Savings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Budżet: ${budget.monthLabelPl}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Niezależny od wyrównania rachunków",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(
                    onClick = onOpenPlanner,
                    modifier = Modifier.testTag("edit_budget_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (budget.hasAnyBudgetConfigured) "Edytuj" else "Zaplanuj")
                }
            }

            if (!budget.hasAnyBudgetConfigured) {
                Text(
                    text = "Ustal wspólny limit na miesiąc oraz opcjonalne limity dla kategorii (np. Zakupy, Rachunki), aby kontrolować domowe wydatki.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (budget.totalSpentGrosze > 0L) {
                    Text(
                        text = "Wydano w tym miesiącu: ${ExpenseBalanceCalculator.formatPln(budget.totalSpentGrosze)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                FilledTonalButton(
                    onClick = onOpenPlanner,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_budget_planner_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Zaplanuj budżet miesiąca")
                }
            } else {
                val totalLimit = budget.totalLimitGrosze
                if (totalLimit != null) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = budget.progressFraction,
                        label = "monthly_budget_progress"
                    )
                    val progressColor = when (budget.status) {
                        BudgetStatus.EXCEEDED -> MaterialTheme.colorScheme.error
                        BudgetStatus.WARNING_80 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    }
                    val trackColor = when (budget.status) {
                        BudgetStatus.EXCEEDED -> MaterialTheme.colorScheme.errorContainer
                        BudgetStatus.WARNING_80 -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = ExpenseBalanceCalculator.formatPln(budget.totalSpentGrosze),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = progressColor
                            )
                            Text(
                                text = "z limitu ${ExpenseBalanceCalculator.formatPln(totalLimit)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = trackColor
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (budget.status == BudgetStatus.WARNING_80 || budget.status == BudgetStatus.EXCEEDED) {
                                    Icon(
                                        imageVector = Icons.Filled.WarningAmber,
                                        contentDescription = null,
                                        tint = progressColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                val rem = budget.remainingGrosze ?: 0L
                                val statusLabel = when (budget.status) {
                                    BudgetStatus.EXCEEDED -> "Przekroczono o ${ExpenseBalanceCalculator.formatPln(-rem)}"
                                    BudgetStatus.WARNING_80 -> "${budget.percentUsed}% • Zostało ${ExpenseBalanceCalculator.formatPln(rem)}"
                                    else -> "${budget.percentUsed}% • Zostało ${ExpenseBalanceCalculator.formatPln(rem)}"
                                }
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = progressColor
                                )
                            }
                        }
                    }

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        color = progressColor,
                        trackColor = trackColor,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Text(
                        text = "Wydano w tym miesiącu: ${ExpenseBalanceCalculator.formatPln(budget.totalSpentGrosze)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (budget.categoryBreakdown.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Kategorie w tym miesiącu",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        budget.categoryBreakdown.forEach { catProgress ->
                            CategoryBudgetRow(catProgress = catProgress)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBudgetRow(catProgress: CategoryBudgetProgress) {
    val barColor = when (catProgress.status) {
        BudgetStatus.EXCEEDED -> MaterialTheme.colorScheme.error
        BudgetStatus.WARNING_80 -> MaterialTheme.colorScheme.secondary
        BudgetStatus.SAFE -> MaterialTheme.colorScheme.primary
        BudgetStatus.UNCONFIGURED -> MaterialTheme.colorScheme.outline
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = expenseCategoryIcon(catProgress.category),
                    contentDescription = null,
                    tint = barColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = catProgress.category.displayNamePl,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            val amountText = if (catProgress.limitGrosze != null) {
                "${ExpenseBalanceCalculator.formatPln(catProgress.spentGrosze)} / ${ExpenseBalanceCalculator.formatPln(catProgress.limitGrosze)}"
            } else {
                ExpenseBalanceCalculator.formatPln(catProgress.spentGrosze)
            }
            Text(
                text = amountText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (catProgress.status == BudgetStatus.EXCEEDED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
        if (catProgress.limitGrosze != null) {
            val anim by animateFloatAsState(
                targetValue = catProgress.progressFraction,
                label = "cat_progress_${catProgress.category.key}"
            )
            LinearProgressIndicator(
                progress = { anim },
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetPlannerSheet(
    currentMonthlyLimitGrosze: Long?,
    currentCategoryBudgetsGrosze: Map<String, Long>,
    onDismiss: () -> Unit,
    onSave: (monthlyLimitGrosze: Long?, categoryBudgetsGrosze: Map<String, Long>) -> Unit
) {
    fun formatInitialPln(grosze: Long?): String {
        if (grosze == null || grosze <= 0L) return ""
        return if (grosze % 100L == 0L) (grosze / 100L).toString()
        else String.format(java.util.Locale.US, "%.2f", grosze / 100.0)
    }

    var totalLimitText by rememberSaveable { mutableStateOf(formatInitialPln(currentMonthlyLimitGrosze)) }
    val categoryInputs = remember {
        mutableStateMapOf<String, String>().apply {
            ExpenseCategory.entries.forEach { cat ->
                put(cat.key, formatInitialPln(currentCategoryBudgetsGrosze[cat.key]))
            }
        }
    }

    val totalLimitGrosze = ExpenseBalanceCalculator.parsePlnToGrosze(totalLimitText)
    val categoryGroszeMap = ExpenseCategory.entries.mapNotNull { cat ->
        val g = ExpenseBalanceCalculator.parsePlnToGrosze(categoryInputs[cat.key].orEmpty())
        if (g != null && g > 0L) cat.key to g else null
    }.toMap()
    val sumCategoryGrosze = categoryGroszeMap.values.sum()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Planowanie budżetu miesiąca",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Ustal wspólny limit domu na miesiąc oraz opcjonalne limity dla kategorii. Zostaw puste, aby wyłączyć limit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = totalLimitText,
                onValueChange = { raw ->
                    totalLimitText = raw.filter { it.isDigit() || it == ',' || it == '.' }.take(10)
                },
                label = { Text("Łączny limit miesięczny (zł)") },
                placeholder = { Text("np. 4000") },
                leadingIcon = { Icon(Icons.Filled.Savings, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("budget_total_limit_input")
            )

            Text(
                text = "Limity kategorii (opcjonalnie)",
                style = MaterialTheme.typography.titleSmall
            )

            ExpenseCategory.entries.forEach { cat ->
                OutlinedTextField(
                    value = categoryInputs[cat.key].orEmpty(),
                    onValueChange = { raw ->
                        categoryInputs[cat.key] = raw.filter { it.isDigit() || it == ',' || it == '.' }.take(10)
                    },
                    label = { Text(cat.displayNamePl) },
                    placeholder = { Text("Bez limitu") },
                    leadingIcon = {
                        Icon(
                            imageVector = expenseCategoryIcon(cat),
                            contentDescription = null
                        )
                    },
                    suffix = { Text("zł") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("budget_cat_input_${cat.key}")
                )
            }

            if (sumCategoryGrosze > 0L) {
                val hintColor = if (totalLimitGrosze != null && sumCategoryGrosze > totalLimitGrosze) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = if (totalLimitGrosze != null && sumCategoryGrosze > totalLimitGrosze) {
                        "Suma limitów kategorii (${ExpenseBalanceCalculator.formatPln(sumCategoryGrosze)}) przekracza limit ogólny (${ExpenseBalanceCalculator.formatPln(totalLimitGrosze)})."
                    } else {
                        "Suma limitów kategorii: ${ExpenseBalanceCalculator.formatPln(sumCategoryGrosze)}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = hintColor
                )
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
                    onClick = { onSave(totalLimitGrosze, categoryGroszeMap) },
                    modifier = Modifier.testTag("save_budget_button")
                ) {
                    Text("Zapisz budżet")
                }
            }
        }
    }
}

@Composable
private fun ExpenseRowCard(
    expense: Expense,
    onDelete: () -> Unit
) {
    val cat = expense.categoryEnum()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("expense_row_${expense.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = expenseCategoryIcon(cat),
                    contentDescription = cat.displayNamePl,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.description,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Zapłacił(a): ${expense.paidByName} • ${cat.shortNamePl}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = ExpenseBalanceCalculator.formatPln(expense.amountGrosze),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Usuń wydatek",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun expenseCategoryIcon(category: ExpenseCategory): ImageVector {
    return when (category) {
        ExpenseCategory.GROCERIES -> Icons.Filled.ShoppingCart
        ExpenseCategory.BILLS -> Icons.Filled.Home
        ExpenseCategory.FOOD_OUT -> Icons.Filled.Restaurant
        ExpenseCategory.TRANSPORT -> Icons.Filled.DirectionsCar
        ExpenseCategory.HOME_HEALTH -> Icons.Filled.LocalHospital
        ExpenseCategory.OTHER -> Icons.Filled.Category
    }
}
