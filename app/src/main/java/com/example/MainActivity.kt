package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.AuthScreen
import com.example.auth.signOut
import com.example.data.model.UiState
import com.example.data.repository.StoreRepository
import com.example.notifications.OrderNotificationHelper
import com.example.ui.screens.CartCheckoutScreen
import com.example.ui.screens.ControlPanelOrStoreScreen
import com.example.ui.screens.OrdersNotificationsScreen
import com.example.ui.screens.ShopScreen
import com.example.ui.theme.DebiganjMartTheme
import com.example.ui.viewmodel.StoreViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_SHOP = "shop"
const val ROUTE_CART = "cart"
const val ROUTE_ORDERS = "orders"
const val ROUTE_CONTROL_PANEL = "control_panel"

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int = 0,
    val testTag: String,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        OrderNotificationHelper.ensureChannel(this)
        setContent {
            DebiganjMartTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        val uid = user.uid
        key(uid) {
            DebiganjMartShell(
                currentUserId = uid,
                userEmail = user.email,
                isEmailVerified = user.isEmailVerified,
                displayName = user.displayName
            )
        }
    }
}

@Composable
private fun DebiganjMartShell(
    currentUserId: String,
    userEmail: String?,
    isEmailVerified: Boolean,
    displayName: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: StoreViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                StoreViewModel(
                    repository = StoreRepository(db),
                    currentUserId = currentUserId,
                    userEmail = userEmail,
                    isEmailVerified = isEmailVerified,
                    displayName = displayName
                )
            }
        }
    )

    var hasNotifPermission by remember {
        mutableStateOf(OrderNotificationHelper.hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.orderAlertEvents.collect { event ->
            OrderNotificationHelper.showOrderUpdateNotification(
                context = context,
                notificationKey = event.notificationKey,
                title = event.title,
                message = event.message
            )
        }
    }

    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val filteredProductsState by viewModel.filteredProductsState.collectAsStateWithLifecycle()
    val adminProductsState by viewModel.adminProductsState.collectAsStateWithLifecycle()
    val userOrdersState by viewModel.userOrdersState.collectAsStateWithLifecycle()
    val adminOrdersState by viewModel.adminOrdersState.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val cartSummary by viewModel.cartSummary.collectAsStateWithLifecycle()
    val isSubmittingOrder by viewModel.isSubmittingOrder.collectAsStateWithLifecycle()

    val unreadAlerts = (notificationsState as? UiState.Success)?.data?.count { !it.seen } ?: 0

    val destinations = listOf(
        NavDestination(
            route = ROUTE_SHOP,
            label = stringResource(R.string.nav_shop),
            selectedIcon = Icons.Filled.Storefront,
            unselectedIcon = Icons.Outlined.Storefront,
            testTag = "nav_shop_item"
        ),
        NavDestination(
            route = ROUTE_CART,
            label = stringResource(R.string.nav_cart),
            selectedIcon = Icons.Filled.ShoppingCart,
            unselectedIcon = Icons.Outlined.ShoppingCart,
            badgeCount = cartSummary.totalCount,
            testTag = "nav_cart_item"
        ),
        NavDestination(
            route = ROUTE_ORDERS,
            label = stringResource(R.string.nav_orders),
            selectedIcon = Icons.Filled.ReceiptLong,
            unselectedIcon = Icons.Outlined.ReceiptLong,
            badgeCount = unreadAlerts,
            testTag = "nav_orders_item"
        ),
        NavDestination(
            route = ROUTE_CONTROL_PANEL,
            label = if (isAdmin) stringResource(R.string.nav_control_panel) else stringResource(R.string.nav_store_info),
            selectedIcon = Icons.Filled.AdminPanelSettings,
            unselectedIcon = Icons.Outlined.AdminPanelSettings,
            testTag = "nav_control_panel_item"
        )
    )

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_SHOP

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (dest.badgeCount > 0) {
                                                Badge { Text(dest.badgeCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    }
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag(dest.testTag)
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
                if (useRail) {
                    NavigationRail {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (dest.badgeCount > 0) {
                                                Badge { Text(dest.badgeCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    }
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_SHOP,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_SHOP) {
                        ShopScreen(
                            productsState = filteredProductsState,
                            storeSettings = storeSettings,
                            searchQuery = searchQuery,
                            selectedCategory = selectedCategory,
                            cartSummary = cartSummary,
                            isAdmin = isAdmin,
                            onSearchChange = viewModel::updateSearchQuery,
                            onSelectCategory = viewModel::selectCategory,
                            onIncrementCart = viewModel::incrementCart,
                            onDecrementCart = viewModel::decrementCart,
                            onNavigateToCart = { navigateTo(ROUTE_CART) },
                            onSeedCatalog = viewModel::seedStarterCatalog
                        )
                    }
                    composable(ROUTE_CART) {
                        CartCheckoutScreen(
                            cartSummary = cartSummary,
                            storeSettings = storeSettings,
                            defaultCustomerName = displayName.orEmpty(),
                            isSubmittingOrder = isSubmittingOrder,
                            onIncrement = viewModel::incrementCart,
                            onDecrement = viewModel::decrementCart,
                            onPlaceOrder = viewModel::placeOrder,
                            onBrowseShop = { navigateTo(ROUTE_SHOP) },
                            onViewOrders = { navigateTo(ROUTE_ORDERS) }
                        )
                    }
                    composable(ROUTE_ORDERS) {
                        OrdersNotificationsScreen(
                            ordersState = userOrdersState,
                            notificationsState = notificationsState,
                            hasNotificationPermission = hasNotifPermission,
                            onRequestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onCancelOrder = viewModel::cancelOrder,
                            onMarkNotificationSeen = viewModel::markNotificationSeen
                        )
                    }
                    composable(ROUTE_CONTROL_PANEL) {
                        ControlPanelOrStoreScreen(
                            isAdmin = isAdmin,
                            userEmail = userEmail,
                            storeSettings = storeSettings,
                            adminProductsState = adminProductsState,
                            adminOrdersState = adminOrdersState,
                            onSaveProduct = viewModel::saveProduct,
                            onQuickUpdatePrice = viewModel::quickUpdatePrice,
                            onDeleteProduct = viewModel::deleteProduct,
                            onSeedCatalog = viewModel::seedStarterCatalog,
                            onUpdateOrderStatus = viewModel::updateOrderStatus,
                            onSaveStoreSettings = viewModel::saveStoreSettings,
                            onSignOut = {
                                signOut(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
