package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.ChoreRepository
import com.example.data.firebase.ExpenseRepository
import com.example.data.firebase.HouseholdRepository
import com.example.data.firebase.ShoppingRepository
import com.example.data.preferences.AppPreferencesStore
import com.example.data.model.ChoreIconOption
import com.example.data.model.ChoreResetMode
import com.example.data.model.ChoreTile
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.Household
import com.example.data.model.ShoppingAisle
import com.example.data.model.ShoppingItem
import com.example.data.model.UserProfile
import com.example.data.widget.PinnedChoreSnapshot
import com.example.data.widget.WidgetSnapshot
import com.example.data.widget.WidgetSnapshotStore
import com.example.domain.BalanceSummary
import com.example.domain.ChoreResetEvaluator
import com.example.domain.EvaluatedChoreStatus
import com.example.domain.ExpenseBalanceCalculator
import com.example.domain.MonthlyBudgetCalculator
import com.example.domain.MonthlyBudgetSummary
import com.example.domain.PolishShoppingCategorizer
import com.example.domain.ShoppingHistoryLimiter
import com.example.widget.DomSyncWidget
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Immutable
sealed interface HouseholdRouteState {
    data object Loading : HouseholdRouteState
    data object NeedsPairing : HouseholdRouteState
    data class Ready(val household: Household, val userDisplayName: String) : HouseholdRouteState
    data class Error(val message: String) : HouseholdRouteState
}

class HouseholdShellViewModel(
    private val householdRepo: HouseholdRepository,
    private val currentUserId: String,
    private val authDisplayName: String,
    private val authPhotoUrl: String?,
    private val prefsStore: AppPreferencesStore? = null
) : ViewModel() {

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _useCustomWeights = MutableStateFlow(false)
    val useCustomWeights: StateFlow<Boolean> = _useCustomWeights.asStateFlow()

    private val _useDynamicColor = MutableStateFlow(false)
    val useDynamicColor: StateFlow<Boolean> = _useDynamicColor.asStateFlow()

    val routeState: StateFlow<HouseholdRouteState> = combine(
        householdRepo.observeUserProfile(currentUserId),
        householdRepo.observeUserHouseholds(currentUserId)
    ) { profile, households ->
        val activeId = profile?.activeHouseholdId
        val selected = households.firstOrNull { it.id == activeId } ?: households.firstOrNull()
        if (selected == null) {
            HouseholdRouteState.NeedsPairing
        } else {
            val name = profile?.displayName?.takeIf { it.isNotBlank() }
                ?: selected.memberName(currentUserId).takeIf { it != "Domownik" }
                ?: authDisplayName.ifBlank { "Domownik" }
            HouseholdRouteState.Ready(selected, name)
        }
    }
        .catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(TAG, "Failed observing household shell", root)
            emit(HouseholdRouteState.Error(root.message ?: "Błąd połączenia z bazą danych."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = HouseholdRouteState.Loading
        )

    private var lastSyncedMemberSignature: String = ""

    init {
        viewModelScope.launch {
            householdRepo.ensureUserProfile(authDisplayName, authPhotoUrl)
        }

        // Propagate new memberUids to existing subcollection docs only when membership changes
        routeState.onEach { state ->
            if (state is HouseholdRouteState.Ready && state.household.memberUids.size > 1) {
                val sig = state.household.memberUids.sorted().joinToString(",")
                val fullSig = "${state.household.id}:$sig"
                if (fullSig != lastSyncedMemberSignature) {
                    val persistedSig = prefsStore?.getSyncedMembersSignature(state.household.id)
                    if (persistedSig != sig) {
                        householdRepo.syncMemberUidsAcrossSubcollections(state.household)
                        prefsStore?.setSyncedMembersSignature(state.household.id, sig)
                    }
                    lastSyncedMemberSignature = fullSig
                }
            }
        }.launchIn(viewModelScope)
    }

    fun clearError() {
        _actionError.value = null
    }

    fun setUseCustomWeights(enabled: Boolean) {
        _useCustomWeights.value = enabled
        viewModelScope.launch {
            prefsStore?.setUseCustomWeights(enabled)
        }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        _useDynamicColor.value = enabled
        viewModelScope.launch {
            prefsStore?.setDynamicColor(enabled)
        }
    }

    fun updateIdentitySettings(
        household: Household,
        householdName: String,
        memberDisplayName: String
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            _actionError.value = null
            householdRepo.updateIdentitySettings(household, householdName, memberDisplayName)
                .onFailure { _actionError.value = it.message ?: "Nie udało się zapisać zmian." }
            _isBusy.value = false
        }
    }

    fun createHousehold(householdName: String, memberDisplayName: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            _actionError.value = null
            val res = householdRepo.createHousehold(
                householdName = householdName,
                ownerDisplayName = memberDisplayName.ifBlank { authDisplayName },
                ownerPhotoUrl = authPhotoUrl
            )
            res.onFailure { _actionError.value = it.message ?: "Nie udało się utworzyć domu." }
            _isBusy.value = false
        }
    }

    fun joinHousehold(inviteCode: String, memberDisplayName: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            _actionError.value = null
            val res = householdRepo.joinHouseholdByCode(
                rawInviteCode = inviteCode,
                memberDisplayName = memberDisplayName.ifBlank { authDisplayName },
                memberPhotoUrl = authPhotoUrl
            )
            res.onFailure { _actionError.value = it.message ?: "Nie udało się dołączyć do domu." }
            _isBusy.value = false
        }
    }

    fun updateAisleOrder(householdId: String, newOrder: List<String>) {
        viewModelScope.launch {
            householdRepo.updateAisleOrder(householdId, newOrder)
                .onFailure { _actionError.value = it.message ?: "Błąd zapisu kolejności kategorii." }
        }
    }

    fun updateMemberWeights(household: Household, weights: Map<String, Double>) {
        viewModelScope.launch {
            householdRepo.updateMemberWeights(household, weights)
                .onFailure { _actionError.value = it.message ?: "Błąd zapisu proporcji podziału." }
        }
    }

    fun updateMonthlyBudget(
        householdId: String,
        monthlyBudgetGrosze: Long?,
        categoryBudgetsGrosze: Map<String, Long>
    ) {
        viewModelScope.launch {
            householdRepo.updateMonthlyBudget(householdId, monthlyBudgetGrosze, categoryBudgetsGrosze)
                .onFailure { _actionError.value = it.message ?: "Błąd zapisu planu budżetu." }
        }
    }

    fun leaveHousehold(household: Household) {
        viewModelScope.launch {
            _isBusy.value = true
            householdRepo.leaveHousehold(household)
                .onFailure { _actionError.value = it.message ?: "Nie można opuścić domu." }
            _isBusy.value = false
        }
    }

    private companion object {
        const val TAG = "HouseholdShellVM"
    }
}

class ShoppingViewModel(
    private val shoppingRepo: ShoppingRepository,
    private val snapshotStore: WidgetSnapshotStore,
    private val appContext: Context,
    private val household: Household,
    private val currentUserId: String,
    private val currentUserName: String
) : ViewModel() {

    private val optimisticAdded = MutableStateFlow<List<ShoppingItem>>(emptyList())
    private val optimisticPurchased = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val optimisticCategories = MutableStateFlow<Map<String, String>>(emptyMap())
    private val optimisticDeleted = MutableStateFlow<Set<String>>(emptySet())
    private val trimmedOverflowIds = mutableSetOf<String>()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val itemsState: StateFlow<UiState<List<ShoppingItem>>> = combine(
        shoppingRepo.observeShoppingItems(household.id, currentUserId)
            .onEach { remoteItems ->
                val overflow = ShoppingHistoryLimiter.overflowIdsToDelete(remoteItems)
                    .filter { it !in trimmedOverflowIds }
                if (overflow.isNotEmpty()) {
                    trimmedOverflowIds.addAll(overflow)
                    viewModelScope.launch {
                        shoppingRepo.trimPurchasedOverflow(household.id, overflow)
                            .onFailure { trimmedOverflowIds.removeAll(overflow.toSet()) }
                    }
                }
            },
        optimisticAdded,
        optimisticPurchased,
        optimisticCategories,
        optimisticDeleted
    ) { remoteItems, added, purchasedOverrides, categoryOverrides, deletedIds ->
        val remoteIds = remoteItems.map { it.id }.toSet()
        val pendingAdds = added.filter { it.id !in remoteIds }
        val mergedAll = (remoteItems + pendingAdds)
            .filter { it.id !in deletedIds }
            .map { item ->
                item.copy(
                    isPurchased = purchasedOverrides[item.id] ?: item.isPurchased,
                    category = categoryOverrides[item.id] ?: item.category
                )
            }

        val activeSorted = mergedAll
            .filter { !it.isPurchased }
            .sortedByDescending { it.createdAt ?: Timestamp.now() }

        val purchasedCapped = ShoppingHistoryLimiter.sortPurchasedNewestFirst(mergedAll)
            .take(ShoppingHistoryLimiter.MAX_RECENTLY_PURCHASED)

        UiState.Success(activeSorted + purchasedCapped) as UiState<List<ShoppingItem>>
    }
        .catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(TAG, "Error observing shopping items", root)
            emit(UiState.Error(root.message ?: "Błąd wczytywania listy zakupów."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    init {
        itemsState.onEach { state ->
            if (state is UiState.Success) {
                val activeCount = state.data.count { !it.isPurchased }
                updateWidgetShoppingCount(activeCount)
            }
        }.launchIn(viewModelScope)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun addItem(rawInput: String) {
        val parsed = PolishShoppingCategorizer.parseAndCategorize(rawInput)
        if (parsed.name.isBlank()) return

        val itemId = shoppingRepo.allocateItemId(household.id)
        val optimistic = ShoppingItem(
            id = itemId,
            householdId = household.id,
            memberUids = household.memberUids,
            name = parsed.name,
            quantityText = parsed.quantityText,
            category = parsed.category.key,
            isPurchased = false,
            addedByUid = currentUserId,
            addedByName = currentUserName,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        optimisticAdded.value = optimisticAdded.value + optimistic

        viewModelScope.launch {
            val result = shoppingRepo.addItem(household, rawInput, currentUserName, itemId = itemId)
            optimisticAdded.value = optimisticAdded.value.filterNot { it.id == itemId }
            result.onFailure { _errorMessage.value = it.message ?: "Nie udało się dodać produktu." }
        }
    }

    fun togglePurchased(item: ShoppingItem) {
        val target = !item.isPurchased
        val currentItems = (itemsState.value as? UiState.Success)?.data.orEmpty()
        val overflowIds = if (target) {
            ShoppingHistoryLimiter.overflowIdsToDelete(
                purchasedItems = currentItems,
                incomingItemId = item.id
            )
        } else {
            emptyList()
        }

        optimisticPurchased.value = optimisticPurchased.value + (item.id to target)
        if (overflowIds.isNotEmpty()) {
            optimisticDeleted.value = optimisticDeleted.value + overflowIds
            trimmedOverflowIds.addAll(overflowIds)
        }

        viewModelScope.launch {
            val result = shoppingRepo.togglePurchased(
                householdId = household.id,
                itemId = item.id,
                purchased = target,
                overflowIdsToDelete = overflowIds
            )
            if (optimisticPurchased.value[item.id] == target) {
                optimisticPurchased.value = optimisticPurchased.value - item.id
            }
            if (overflowIds.isNotEmpty()) {
                optimisticDeleted.value = optimisticDeleted.value - overflowIds.toSet()
                if (result.isFailure) {
                    trimmedOverflowIds.removeAll(overflowIds.toSet())
                }
            }
            result.onFailure { _errorMessage.value = it.message ?: "Nie udało się zaktualizować produktu." }
        }
    }

    fun changeCategory(item: ShoppingItem, aisle: ShoppingAisle) {
        optimisticCategories.value = optimisticCategories.value + (item.id to aisle.key)
        viewModelScope.launch {
            val result = shoppingRepo.updateCategory(household.id, item.id, aisle)
            if (optimisticCategories.value[item.id] == aisle.key) {
                optimisticCategories.value = optimisticCategories.value - item.id
            }
            result.onFailure { _errorMessage.value = it.message ?: "Nie udało się zmienić kategorii." }
        }
    }

    fun deleteItem(item: ShoppingItem) {
        optimisticDeleted.value = optimisticDeleted.value + item.id
        viewModelScope.launch {
            val result = shoppingRepo.deleteItem(household.id, item.id)
            optimisticDeleted.value = optimisticDeleted.value - item.id
            result.onFailure { _errorMessage.value = it.message ?: "Nie udało się usunąć produktu." }
        }
    }

    fun clearPurchased(purchasedItems: List<ShoppingItem>) {
        if (purchasedItems.isEmpty()) return
        val ids = purchasedItems.map { it.id }.toSet()
        optimisticDeleted.value = optimisticDeleted.value + ids
        viewModelScope.launch {
            val result = shoppingRepo.clearPurchasedItems(household.id, purchasedItems.filterNot { it.id.startsWith("opt_") })
            optimisticDeleted.value = optimisticDeleted.value - ids
            result.onFailure { _errorMessage.value = it.message ?: "Nie udało się wyczyścić historii." }
        }
    }

    private suspend fun updateWidgetShoppingCount(activeCount: Int) {
        val current = snapshotStore.getSnapshot()
        if (current.householdId != household.id ||
            current.householdName != household.name ||
            current.remainingShoppingCount != activeCount ||
            current.currentUserDisplayName != currentUserName
        ) {
            snapshotStore.saveSnapshot(
                current.copy(
                    householdId = household.id,
                    householdName = household.name,
                    remainingShoppingCount = activeCount,
                    currentUserDisplayName = currentUserName
                )
            )
            runCatching { DomSyncWidget().updateAll(appContext) }
        }
    }

    private companion object {
        const val TAG = "ShoppingVM"
    }
}

@Immutable
data class ChoreTileUiModel(
    val tile: ChoreTile,
    val status: EvaluatedChoreStatus
)

class ChoreViewModel(
    private val choreRepo: ChoreRepository,
    private val snapshotStore: WidgetSnapshotStore,
    private val appContext: Context,
    private val household: Household,
    private val currentUserId: String,
    private val currentUserName: String
) : ViewModel() {

    private data class OptimisticChoreCompletion(
        val isDone: Boolean,
        val timestamp: Timestamp?,
        val byName: String?
    )

    private val optimisticCompletions = MutableStateFlow<Map<String, OptimisticChoreCompletion>>(emptyMap())
    private val optimisticPinned = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val optimisticDeleted = MutableStateFlow<Set<String>>(emptySet())
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val minuteTicker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L)
        }
    }

    val choresState: StateFlow<UiState<List<ChoreTileUiModel>>> = combine(
        choreRepo.observeChores(household.id, currentUserId),
        optimisticCompletions,
        optimisticPinned,
        optimisticDeleted,
        minuteTicker
    ) { remoteChores, overrides, pinnedOverrides, deletedIds, nowMillis ->
        val models = remoteChores
            .filter { it.id !in deletedIds }
            .map { tile ->
                val override = overrides[tile.id]
                val pinned = pinnedOverrides[tile.id] ?: tile.isPinnedToWidget
                val effectiveTile = if (override != null) {
                    tile.copy(
                        isPinnedToWidget = pinned,
                        lastCompletedAt = if (override.isDone) override.timestamp else null,
                        lastCompletedByName = if (override.isDone) override.byName else null
                    )
                } else {
                    tile.copy(isPinnedToWidget = pinned)
                }
                ChoreTileUiModel(
                    tile = effectiveTile,
                    status = ChoreResetEvaluator.evaluate(effectiveTile, nowMillis)
                )
            }
            .sortedWith(
                compareByDescending<ChoreTileUiModel> { it.tile.isPinnedToWidget }
                    .thenBy { it.status.isDoneForCurrentPeriod }
                    .thenBy { it.tile.title }
            )
        UiState.Success(models) as UiState<List<ChoreTileUiModel>>
    }
        .catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(TAG, "Error observing chores", root)
            emit(UiState.Error(root.message ?: "Błąd wczytywania obowiązków."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    init {
        choresState.onEach { state ->
            if (state is UiState.Success) {
                syncWidgetPinnedChores(state.data)
            }
        }.launchIn(viewModelScope)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun toggleChore(model: ChoreTileUiModel) {
        val markDone = !model.status.isDoneForCurrentPeriod
        optimisticCompletions.value = optimisticCompletions.value + (
            model.tile.id to OptimisticChoreCompletion(
                isDone = markDone,
                timestamp = if (markDone) Timestamp.now() else null,
                byName = if (markDone) currentUserName else null
            )
            )

        viewModelScope.launch {
            val result = choreRepo.toggleCompletion(
                householdId = household.id,
                choreId = model.tile.id,
                completedByName = currentUserName,
                markDone = markDone
            )
            optimisticCompletions.value = optimisticCompletions.value - model.tile.id
            result.onFailure { _errorMessage.value = it.message ?: "Nie udało się zapisać statusu." }
        }
    }

    fun addChore(
        title: String,
        icon: ChoreIconOption,
        resetMode: ChoreResetMode,
        resetHours: List<Long>,
        pinToWidget: Boolean
    ) {
        viewModelScope.launch {
            val currentList = (choresState.value as? UiState.Success)?.data.orEmpty()
            val canPin = pinToWidget && currentList.count { it.tile.isPinnedToWidget } < 2
            choreRepo.addChore(household, title, icon, resetMode, resetHours, canPin)
                .onFailure { _errorMessage.value = it.message ?: "Nie udało się dodać obowiązku." }
        }
    }

    fun updateChoreConfig(
        chore: ChoreTile,
        title: String,
        icon: ChoreIconOption,
        resetMode: ChoreResetMode,
        resetHours: List<Long>,
        pinToWidget: Boolean
    ) {
        viewModelScope.launch {
            val currentList = (choresState.value as? UiState.Success)?.data.orEmpty()
            val otherPinnedCount = currentList.count { it.tile.isPinnedToWidget && it.tile.id != chore.id }
            if (pinToWidget && otherPinnedCount >= 2) {
                _errorMessage.value = "Do widżetu możesz przypiąć maksymalnie 2 obowiązki. Odepnij inny kafelek."
                return@launch
            }
            choreRepo.updateChoreConfig(household.id, chore.id, title, icon, resetMode, resetHours, pinToWidget)
                .onFailure { _errorMessage.value = it.message ?: "Nie udało się zaktualizować kafelka." }
        }
    }

    fun updatePinnedChores(pinnedIds: Set<String>) {
        val target = pinnedIds.take(2).toSet()
        val currentTiles = (choresState.value as? UiState.Success)?.data.orEmpty().map { it.tile }
        if (currentTiles.isEmpty()) return

        optimisticPinned.value = currentTiles.associate { it.id to (it.id in target) }
        viewModelScope.launch {
            val result = choreRepo.updatePinnedChores(household.id, currentTiles, target)
            optimisticPinned.value = emptyMap()
            result.onFailure { _errorMessage.value = it.message ?: "Nie udało się zaktualizować widżetu." }
        }
    }

    fun deleteChore(choreId: String) {
        optimisticDeleted.value = optimisticDeleted.value + choreId
        viewModelScope.launch {
            val res = choreRepo.deleteChore(household.id, choreId)
            optimisticDeleted.value = optimisticDeleted.value - choreId
            res.onFailure { _errorMessage.value = it.message ?: "Nie udało się usunąć obowiązku." }
        }
    }

    private suspend fun syncWidgetPinnedChores(models: List<ChoreTileUiModel>) {
        val pinned = models.filter { it.tile.isPinnedToWidget }.take(2)
        val snapshots = pinned.map { model ->
            PinnedChoreSnapshot(
                id = model.tile.id,
                title = model.tile.title,
                iconKey = model.tile.iconKey,
                isDone = model.status.isDoneForCurrentPeriod,
                statusText = model.status.statusTextPl
            )
        }

        val current = snapshotStore.getSnapshot()
        if (current.householdId != household.id ||
            current.householdName != household.name ||
            current.pinnedChores != snapshots ||
            current.currentUserDisplayName != currentUserName
        ) {
            snapshotStore.saveSnapshot(
                current.copy(
                    householdId = household.id,
                    householdName = household.name,
                    pinnedChores = snapshots,
                    currentUserDisplayName = currentUserName
                )
            )
            runCatching { DomSyncWidget().updateAll(appContext) }
        }
    }

    private companion object {
        const val TAG = "ChoreVM"
    }
}

@Immutable
data class ExpenseScreenData(
    val activeCycleExpenses: List<Expense>,
    val balanceSummary: BalanceSummary,
    val monthlyBudget: MonthlyBudgetSummary
)

class ExpenseViewModel(
    private val expenseRepo: ExpenseRepository,
    initialHousehold: Household,
    private val currentUserId: String
) : ViewModel() {

    private val householdFlow = MutableStateFlow(initialHousehold)
    private val optimisticAdded = MutableStateFlow<List<Expense>>(emptyList())
    private val optimisticDeleted = MutableStateFlow<Set<String>>(emptySet())
    private val useCustomWeights = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun updateHousehold(latest: Household) {
        if (householdFlow.value != latest) {
            householdFlow.value = latest
        }
    }

    fun setUseCustomWeights(enabled: Boolean) {
        useCustomWeights.value = enabled
    }

    val expenseState: StateFlow<UiState<ExpenseScreenData>> = combine(
        expenseRepo.observeExpenses(initialHousehold.id, currentUserId),
        householdFlow,
        optimisticAdded,
        optimisticDeleted,
        useCustomWeights
    ) { remoteExpenses, currentHousehold, added, deletedIds, customWeights ->
        val remoteIds = remoteExpenses.map { it.id }.toSet()
        val pending = added.filter { it.id !in remoteIds }
        val allVisible = (remoteExpenses + pending).filter { it.id !in deletedIds }
        val active = allVisible
            .filter { it.cycleNumber == currentHousehold.settlementCycle }
            .sortedByDescending { it.createdAt ?: Timestamp.now() }

        val summary = ExpenseBalanceCalculator.calculate(
            household = currentHousehold,
            activeCycleExpenses = active,
            useCustomWeights = customWeights
        )
        val monthlyBudget = MonthlyBudgetCalculator.calculate(
            household = currentHousehold,
            allExpenses = allVisible
        )
        UiState.Success(ExpenseScreenData(active, summary, monthlyBudget)) as UiState<ExpenseScreenData>
    }
        .catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(TAG, "Error observing expenses", root)
            emit(UiState.Error(root.message ?: "Błąd wczytywania rozliczeń."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun clearError() {
        _errorMessage.value = null
    }

    fun addExpense(
        amountInput: String,
        description: String,
        paidByUid: String,
        category: ExpenseCategory = ExpenseCategory.OTHER
    ) {
        val grosze = ExpenseBalanceCalculator.parsePlnToGrosze(amountInput)
        if (grosze == null) {
            _errorMessage.value = "Wpisz poprawną kwotę większą od 0 zł (np. 48,50)."
            return
        }
        val cleanDesc = description.trim()
        if (cleanDesc.isBlank()) {
            _errorMessage.value = "Podaj krótki opis wydatku (np. Biedronka)."
            return
        }
        val currentHousehold = householdFlow.value
        val paidByName = currentHousehold.memberName(paidByUid)
        val tempId = "opt_exp_${System.nanoTime()}"
        val optimistic = Expense(
            id = tempId,
            householdId = currentHousehold.id,
            memberUids = currentHousehold.memberUids,
            amountGrosze = grosze,
            description = cleanDesc,
            paidByUid = paidByUid,
            paidByName = paidByName,
            cycleNumber = currentHousehold.settlementCycle,
            category = category.key,
            createdByUid = currentUserId,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        optimisticAdded.value = optimisticAdded.value + optimistic

        viewModelScope.launch {
            val res = expenseRepo.addExpense(currentHousehold, grosze, cleanDesc, paidByUid, paidByName, category)
            optimisticAdded.value = optimisticAdded.value.filterNot { it.id == tempId }
            res.onFailure { _errorMessage.value = it.message ?: "Nie udało się zapisać wydatku." }
        }
    }

    fun deleteExpense(expenseId: String) {
        optimisticDeleted.value = optimisticDeleted.value + expenseId
        viewModelScope.launch {
            val res = if (expenseId.startsWith("opt_")) {
                Result.success(Unit)
            } else {
                expenseRepo.deleteExpense(householdFlow.value.id, expenseId)
            }
            optimisticDeleted.value = optimisticDeleted.value - expenseId
            res.onFailure { _errorMessage.value = it.message ?: "Nie udało się usunąć wydatku." }
        }
    }

    fun settleUp() {
        viewModelScope.launch {
            expenseRepo.settleCycle(householdFlow.value.id)
                .onFailure { _actionError(it) }
        }
    }

    private fun _actionError(t: Throwable) {
        _errorMessage.value = t.message ?: "Nie udało się wyrównać rachunków."
    }

    private companion object {
        const val TAG = "ExpenseVM"
    }
}

private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
    return generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
}
