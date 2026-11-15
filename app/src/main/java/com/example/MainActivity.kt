package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.firebase.AuthHelper
import com.example.data.firebase.ChoreRepository
import com.example.data.firebase.ExpenseRepository
import com.example.data.firebase.HouseholdRepository
import com.example.data.firebase.ShoppingRepository
import com.example.data.firebase.authStateFlow
import com.example.data.model.Household
import com.example.data.network.observeNetworkAvailability
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.AppPreferencesStore
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.StartTabPreference
import com.example.data.widget.WidgetSnapshotStore
import com.example.ui.screens.ChoresScreen
import com.example.ui.screens.ExpensesScreen
import com.example.ui.screens.HouseholdSettingsSheet
import com.example.ui.screens.PairingOnboardingScreen
import com.example.ui.screens.ShoppingScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.theme.DomSyncTheme
import com.example.ui.viewmodel.ChoreViewModel
import com.example.ui.viewmodel.ExpenseViewModel
import com.example.ui.viewmodel.HouseholdRouteState
import com.example.ui.viewmodel.HouseholdShellViewModel
import com.example.ui.viewmodel.ShoppingViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object ShoppingRoute

@Serializable
data object ChoresRoute

@Serializable
data object ExpensesRoute

private data class NavTab(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DomSyncRootApp()
        }
    }
}

@Composable
fun DomSyncRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsStore = remember(context) { AppPreferencesStore(context.applicationContext) }
    val preferencesOrNull by prefsStore.preferencesFlow.collectAsStateWithLifecycle(initialValue = null)

    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val preferences = preferencesOrNull
    if (preferences == null) {
        DomSyncTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = false) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
        }
        return
    }

    val darkTheme = when (preferences.themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    DomSyncTheme(
        darkTheme = darkTheme,
        dynamicColor = preferences.useDynamicColor
    ) {
        val user = currentUser
        if (user == null) {
            SignInScreen(
                onAuthSuccess = { /* AuthStateListener updates currentUser reactively */ }
            )
        } else {
            key(user.uid) {
                AuthenticatedDomSyncShell(
                    currentUserId = user.uid,
                    authDisplayName = user.displayName ?: "Domownik",
                    authPhotoUrl = user.photoUrl?.toString(),
                    prefsStore = prefsStore,
                    preferences = preferences,
                    onToggleDynamicColor = { enabled ->
                        scope.launch { prefsStore.setDynamicColor(enabled) }
                    },
                    onSelectThemeMode = { mode ->
                        scope.launch { prefsStore.setThemeMode(mode) }
                    },
                    onSelectStartTab = { tab ->
                        scope.launch { prefsStore.setStartTab(tab) }
                    },
                    onToggleCustomWeights = { enabled ->
                        scope.launch { prefsStore.setUseCustomWeights(enabled) }
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedDomSyncShell(
    currentUserId: String,
    authDisplayName: String,
    authPhotoUrl: String?,
    prefsStore: AppPreferencesStore,
    preferences: AppPreferences,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onSelectStartTab: (StartTabPreference) -> Unit,
    onToggleCustomWeights: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val shellViewModel: HouseholdShellViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                HouseholdShellViewModel(
                    householdRepo = HouseholdRepository(db),
                    currentUserId = currentUserId,
                    authDisplayName = authDisplayName,
                    authPhotoUrl = authPhotoUrl,
                    prefsStore = prefsStore
                )
            }
        }
    )

    val routeState by shellViewModel.routeState.collectAsStateWithLifecycle()
    val isBusy by shellViewModel.isBusy.collectAsStateWithLifecycle()
    val actionError by shellViewModel.actionError.collectAsStateWithLifecycle()

    val onSignOut: () -> Unit = {
        AuthHelper.signOut(credentialManager = credentialManager, scope = scope)
    }

    when (val state = routeState) {
        is HouseholdRouteState.Loading -> {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        is HouseholdRouteState.Error -> {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        is HouseholdRouteState.NeedsPairing -> {
            PairingOnboardingScreen(
                defaultDisplayName = authDisplayName,
                isBusy = isBusy,
                errorMessage = actionError,
                onCreateHousehold = { houseName, memberName ->
                    shellViewModel.createHousehold(houseName, memberName)
                },
                onJoinHousehold = { code, memberName ->
                    shellViewModel.joinHousehold(code, memberName)
                },
                onSignOut = onSignOut,
                onClearError = shellViewModel::clearError
            )
        }
        is HouseholdRouteState.Ready -> {
            // Key strictly on household.id so settling expenses never recreates listeners or NavHost
            key(state.household.id) {
                HouseholdMainContent(
                    household = state.household,
                    currentUserId = currentUserId,
                    currentUserName = state.userDisplayName,
                    isSavingIdentity = isBusy,
                    preferences = preferences,
                    onSaveIdentity = { houseName, memberName ->
                        shellViewModel.updateIdentitySettings(state.household, houseName, memberName)
                    },
                    onToggleCustomWeights = onToggleCustomWeights,
                    onToggleDynamicColor = onToggleDynamicColor,
                    onSelectThemeMode = onSelectThemeMode,
                    onSelectStartTab = onSelectStartTab,
                    onUpdateAisleOrder = { newOrder ->
                        shellViewModel.updateAisleOrder(state.household.id, newOrder)
                    },
                    onSaveWeights = { weights ->
                        shellViewModel.updateMemberWeights(state.household, weights)
                    },
                    onSaveMonthlyBudget = { monthlyLimit, categoryLimits ->
                        shellViewModel.updateMonthlyBudget(state.household.id, monthlyLimit, categoryLimits)
                    },
                    onLeaveHousehold = { shellViewModel.leaveHousehold(state.household) },
                    onSignOut = onSignOut
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HouseholdMainContent(
    household: Household,
    currentUserId: String,
    currentUserName: String,
    isSavingIdentity: Boolean,
    preferences: AppPreferences,
    onSaveIdentity: (householdName: String, memberDisplayName: String) -> Unit,
    onToggleCustomWeights: (Boolean) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onSelectStartTab: (StartTabPreference) -> Unit,
    onUpdateAisleOrder: (List<String>) -> Unit,
    onSaveWeights: (Map<String, Double>) -> Unit,
    onSaveMonthlyBudget: (monthlyLimitGrosze: Long?, categoryBudgetsGrosze: Map<String, Long>) -> Unit,
    onLeaveHousehold: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val isOnline by remember(context) { observeNetworkAvailability(context) }
        .collectAsStateWithLifecycle(initialValue = true)

    val shoppingViewModel: ShoppingViewModel = viewModel(
        key = "shop_${currentUserId}_${household.id}",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                ShoppingViewModel(
                    shoppingRepo = ShoppingRepository(db),
                    snapshotStore = WidgetSnapshotStore(app),
                    appContext = app,
                    household = household,
                    currentUserId = currentUserId,
                    currentUserName = currentUserName
                )
            }
        }
    )

    val choreViewModel: ChoreViewModel = viewModel(
        key = "chore_${currentUserId}_${household.id}",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                ChoreViewModel(
                    choreRepo = ChoreRepository(db),
                    snapshotStore = WidgetSnapshotStore(app),
                    appContext = app,
                    household = household,
                    currentUserId = currentUserId,
                    currentUserName = currentUserName
                )
            }
        }
    )

    val expenseViewModel: ExpenseViewModel = viewModel(
        key = "exp_${currentUserId}_${household.id}",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                ExpenseViewModel(
                    expenseRepo = ExpenseRepository(db),
                    initialHousehold = household,
                    currentUserId = currentUserId
                )
            }
        }
    )

    LaunchedEffect(household) {
        expenseViewModel.updateHousehold(household)
    }
    LaunchedEffect(preferences.useCustomWeights) {
        expenseViewModel.setUseCustomWeights(preferences.useCustomWeights)
    }

    val shoppingItemsState by shoppingViewModel.itemsState.collectAsStateWithLifecycle()
    val shoppingError by shoppingViewModel.errorMessage.collectAsStateWithLifecycle()
    val choresState by choreViewModel.choresState.collectAsStateWithLifecycle()
    val choresError by choreViewModel.errorMessage.collectAsStateWithLifecycle()
    val expenseState by expenseViewModel.expenseState.collectAsStateWithLifecycle()
    val expenseError by expenseViewModel.errorMessage.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    var showSettingsSheet by remember { mutableStateOf(false) }

    val initialRoute: Any = remember {
        when (preferences.startTab) {
            StartTabPreference.SHOPPING -> ShoppingRoute
            StartTabPreference.CHORES -> ChoresRoute
            StartTabPreference.EXPENSES -> ExpensesRoute
        }
    }

    val tabs = remember {
        listOf(
            NavTab(
                route = ShoppingRoute,
                label = "Zakupy",
                selectedIcon = Icons.Filled.ShoppingCart,
                unselectedIcon = Icons.Outlined.ShoppingCart,
                testTag = "nav_tab_shopping"
            ),
            NavTab(
                route = ChoresRoute,
                label = "Obowiązki",
                selectedIcon = Icons.Filled.CheckBox,
                unselectedIcon = Icons.Outlined.CheckBox,
                testTag = "nav_tab_chores"
            ),
            NavTab(
                route = ExpensesRoute,
                label = "Rozliczenia",
                selectedIcon = Icons.Filled.AccountBalanceWallet,
                unselectedIcon = Icons.Outlined.AccountBalanceWallet,
                testTag = "nav_tab_expenses"
            )
        )
    }

    val navigateToTab: (Any) -> Unit = remember(navController) {
        { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = household.name,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Kod: ${household.inviteCode} • ${household.memberUids.size} os.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.testTag("open_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Ustawienia domu"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = initialRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<ShoppingRoute> {
                        ShoppingScreen(
                            household = household,
                            itemsState = shoppingItemsState,
                            isOnline = isOnline,
                            errorMessage = shoppingError,
                            onAddItem = shoppingViewModel::addItem,
                            onTogglePurchased = shoppingViewModel::togglePurchased,
                            onChangeCategory = shoppingViewModel::changeCategory,
                            onDeleteItem = shoppingViewModel::deleteItem,
                            onClearPurchased = shoppingViewModel::clearPurchased,
                            onUpdateAisleOrder = onUpdateAisleOrder,
                            onClearError = shoppingViewModel::clearError
                        )
                    }
                    composable<ChoresRoute> {
                        ChoresScreen(
                            choresState = choresState,
                            errorMessage = choresError,
                            onToggleChore = choreViewModel::toggleChore,
                            onAddChore = choreViewModel::addChore,
                            onUpdateChore = choreViewModel::updateChoreConfig,
                            onUpdatePinnedChores = choreViewModel::updatePinnedChores,
                            onDeleteChore = choreViewModel::deleteChore,
                            onClearError = choreViewModel::clearError
                        )
                    }
                    composable<ExpensesRoute> {
                        ExpensesScreen(
                            household = household,
                            currentUserId = currentUserId,
                            useCustomWeights = preferences.useCustomWeights,
                            expenseState = expenseState,
                            errorMessage = expenseError,
                            onAddExpense = expenseViewModel::addExpense,
                            onDeleteExpense = expenseViewModel::deleteExpense,
                            onSettleUp = expenseViewModel::settleUp,
                            onSaveMonthlyBudget = onSaveMonthlyBudget,
                            onClearError = expenseViewModel::clearError
                        )
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        HouseholdSettingsSheet(
            household = household,
            currentUserId = currentUserId,
            currentUserName = currentUserName,
            isSavingIdentity = isSavingIdentity,
            useCustomWeights = preferences.useCustomWeights,
            useDynamicColor = preferences.useDynamicColor,
            themeMode = preferences.themeMode,
            startTab = preferences.startTab,
            onSaveIdentity = onSaveIdentity,
            onToggleCustomWeights = onToggleCustomWeights,
            onToggleDynamicColor = onToggleDynamicColor,
            onSelectThemeMode = onSelectThemeMode,
            onSelectStartTab = onSelectStartTab,
            onUpdateAisleOrder = onUpdateAisleOrder,
            onSaveWeights = onSaveWeights,
            onLeaveHousehold = onLeaveHousehold,
            onSignOut = onSignOut,
            onDismiss = { showSettingsSheet = false }
        )
    }
}
