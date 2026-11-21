package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CartItem
import com.example.data.model.OrderLineItem
import com.example.data.model.OrderNotification
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentStatus
import com.example.data.model.Product
import com.example.data.model.StoreOrder
import com.example.data.model.StoreSettings
import com.example.data.model.UiState
import com.example.data.repository.StoreRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CartSummary(
    val items: List<CartItem> = emptyList(),
    val totalCount: Int = 0,
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val grandTotal: Double = 0.0,
    val freeDeliveryRemaining: Double = 0.0,
)

data class OrderAlertEvent(
    val notificationKey: String,
    val title: String,
    val message: String,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StoreViewModel(
    private val repository: StoreRepository,
    val currentUserId: String,
    val userEmail: String?,
    val isEmailVerified: Boolean,
    val displayName: String?,
) : ViewModel() {

    val isAdmin: StateFlow<Boolean> = repository
        .observeIsAdmin(currentUserId, userEmail, isEmailVerified)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = false
        )

    val storeSettings: StateFlow<StoreSettings> = repository
        .observeStoreSettings()
        .catch { error ->
            unwrapFirestoreError(error, "store_settings")
            emit(StoreSettings())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = StoreSettings()
        )

    val availableProductsState: StateFlow<UiState<List<Product>>> = repository
        .observeAvailableProducts()
        .map<List<Product>, UiState<List<Product>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error, "products")
            emit(UiState.Error(root.message ?: "Failed to load products"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val adminProductsState: StateFlow<UiState<List<Product>>> = isAdmin
        .flatMapLatest { admin ->
            if (!admin) flowOf(UiState.Success(emptyList()))
            else repository.observeAllProducts()
                .map<List<Product>, UiState<List<Product>>> { UiState.Success(it) }
                .catch { error ->
                    val root = unwrapFirestoreError(error, "admin_products")
                    emit(UiState.Error(root.message ?: "Failed to load catalog"))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val userOrdersState: StateFlow<UiState<List<StoreOrder>>> = repository
        .observeUserOrders(currentUserId)
        .map<List<StoreOrder>, UiState<List<StoreOrder>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error, "user_orders")
            emit(UiState.Error(root.message ?: "Failed to load your orders"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val adminOrdersState: StateFlow<UiState<List<StoreOrder>>> = isAdmin
        .flatMapLatest { admin ->
            if (!admin) flowOf(UiState.Success(emptyList()))
            else repository.observeAllOrders()
                .map<List<StoreOrder>, UiState<List<StoreOrder>>> { UiState.Success(it) }
                .catch { error ->
                    val root = unwrapFirestoreError(error, "admin_orders")
                    emit(UiState.Error(root.message ?: "Failed to load store orders"))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val notificationsState: StateFlow<UiState<List<OrderNotification>>> = repository
        .observeUserNotifications(currentUserId)
        .map<List<OrderNotification>, UiState<List<OrderNotification>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error, "notifications")
            emit(UiState.Error(root.message ?: "Failed to load alerts"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _cartQuantities = MutableStateFlow<Map<String, Int>>(emptyMap())

    val filteredProductsState: StateFlow<UiState<List<Product>>> = combine(
        availableProductsState,
        _searchQuery,
        _selectedCategory
    ) { state, query, category ->
        when (state) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> state
            is UiState.Success -> {
                val q = query.trim().lowercase()
                val filtered = state.data.filter { product ->
                    val catMatch = category == "All" || product.category.equals(category, ignoreCase = true)
                    val qMatch = q.isEmpty() ||
                        product.name.lowercase().contains(q) ||
                        product.description.lowercase().contains(q) ||
                        product.category.lowercase().contains(q)
                    catMatch && qMatch
                }
                UiState.Success(filtered)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    val cartSummary: StateFlow<CartSummary> = combine(
        availableProductsState,
        _cartQuantities,
        storeSettings
    ) { productsState, qtyMap, settings ->
        val products = (productsState as? UiState.Success)?.data.orEmpty().associateBy { it.id }
        val items = qtyMap.entries.mapNotNull { (productId, qty) ->
            val product = products[productId]
            if (product != null && qty > 0 && product.stockCount > 0) {
                CartItem(product, qty.coerceAtMost(product.stockCount.toInt()))
            } else null
        }
        val subtotal = items.sumOf { it.lineTotal }
        val totalCount = items.sumOf { it.quantity }
        val deliveryFee = when {
            items.isEmpty() -> 0.0
            subtotal >= settings.freeDeliveryAbove -> 0.0
            else -> settings.deliveryFee
        }
        val remaining = (settings.freeDeliveryAbove - subtotal).coerceAtLeast(0.0)
        CartSummary(
            items = items,
            totalCount = totalCount,
            subtotal = subtotal,
            deliveryFee = deliveryFee,
            grandTotal = subtotal + deliveryFee,
            freeDeliveryRemaining = remaining
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = CartSummary()
    )

    private val _orderAlertEvents = MutableSharedFlow<OrderAlertEvent>(extraBufferCapacity = 16)
    val orderAlertEvents: SharedFlow<OrderAlertEvent> = _orderAlertEvents.asSharedFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private val _isSubmittingOrder = MutableStateFlow(false)
    val isSubmittingOrder: StateFlow<Boolean> = _isSubmittingOrder.asStateFlow()

    private var seededAttempted = false
    private val knownOrderStatuses = mutableMapOf<String, String>()
    private var initialOrdersSnapshotLoaded = false

    init {
        // Auto-seed starter catalog when the Store Owner signs in and catalog is empty
        viewModelScope.launch {
            combine(isAdmin, adminProductsState) { admin, products ->
                admin to products
            }.collect { (admin, state) ->
                if (admin && !seededAttempted && state is UiState.Success && state.data.isEmpty()) {
                    seededAttempted = true
                    val result = repository.seedStarterCatalogIfEmpty()
                    result.onSuccess { count ->
                        if (count > 0) {
                            _snackbarMessages.tryEmit("Loaded $count starter general store products")
                        }
                    }
                }
            }
        }

        // Detect live order status transitions for push-style local notifications
        viewModelScope.launch {
            userOrdersState.collect { state ->
                if (state is UiState.Success) {
                    val orders = state.data
                    if (!initialOrdersSnapshotLoaded) {
                        orders.forEach { knownOrderStatuses[it.id] = it.status }
                        initialOrdersSnapshotLoaded = true
                    } else {
                        orders.forEach { order ->
                            val previous = knownOrderStatuses[order.id]
                            if (previous != null && previous != order.status) {
                                val statusEnum = OrderStatus.fromRaw(order.status)
                                val shortId = order.id.takeLast(6).uppercase()
                                _orderAlertEvents.tryEmit(
                                    OrderAlertEvent(
                                        notificationKey = "${order.id}_${order.status}",
                                        title = "Debiganj Mart • ${statusEnum.displayLabel}",
                                        message = "Order #$shortId is now ${statusEnum.displayLabel.lowercase()}." +
                                            if (order.ownerNote.isNotBlank()) " ${order.ownerNote}" else ""
                                    )
                                )
                            }
                            knownOrderStatuses[order.id] = order.status
                        }
                    }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun incrementCart(product: Product) {
        if (product.stockCount <= 0L || !product.available) {
            _snackbarMessages.tryEmit("${product.name} is currently out of stock")
            return
        }
        val current = _cartQuantities.value[product.id] ?: 0
        if (current >= product.stockCount) {
            _snackbarMessages.tryEmit("Only ${product.stockCount} available in stock")
            return
        }
        _cartQuantities.value = _cartQuantities.value.toMutableMap().apply {
            put(product.id, current + 1)
        }
    }

    fun decrementCart(productId: String) {
        val current = _cartQuantities.value[productId] ?: 0
        _cartQuantities.value = _cartQuantities.value.toMutableMap().apply {
            if (current <= 1) remove(productId) else put(productId, current - 1)
        }
    }

    fun clearCart() {
        _cartQuantities.value = emptyMap()
    }

    fun placeOrder(
        customerName: String,
        customerPhone: String,
        deliveryAddress: String,
        paymentMethod: PaymentMethod,
        paymentStatus: PaymentStatus,
        paymentReference: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val summary = cartSummary.value
        if (summary.items.isEmpty()) {
            onError("Your cart is empty")
            return
        }
        if (_isSubmittingOrder.value) return
        _isSubmittingOrder.value = true

        viewModelScope.launch {
            val order = StoreOrder(
                userId = currentUserId,
                customerName = customerName,
                customerPhone = customerPhone,
                deliveryAddress = deliveryAddress,
                items = summary.items.map { cartItem ->
                    OrderLineItem(
                        productId = cartItem.product.id,
                        name = cartItem.product.name,
                        unit = cartItem.product.unit,
                        price = cartItem.product.price,
                        quantity = cartItem.quantity.toLong()
                    )
                },
                subtotal = summary.subtotal,
                deliveryFee = summary.deliveryFee,
                totalAmount = summary.grandTotal,
                paymentMethod = paymentMethod.wireValue,
                paymentStatus = paymentStatus.wireValue,
                paymentReference = paymentReference,
                status = OrderStatus.PLACED.wireValue
            )

            val result = repository.placeOrder(order)
            _isSubmittingOrder.value = false
            result.onSuccess { orderId ->
                knownOrderStatuses[orderId] = OrderStatus.PLACED.wireValue
                clearCart()
                val shortId = orderId.takeLast(6).uppercase()
                _orderAlertEvents.tryEmit(
                    OrderAlertEvent(
                        notificationKey = "${orderId}_PLACED",
                        title = "Order Confirmed (#$shortId)",
                        message = "Debiganj Mart received your order of ₹${summary.grandTotal.toInt()}."
                    )
                )
                onSuccess(orderId)
            }.onFailure { err ->
                onError(err.message ?: "Failed to place order. Please try again.")
            }
        }
    }

    fun cancelOrder(orderId: String) {
        viewModelScope.launch {
            repository.cancelUserOrder(orderId)
                .onSuccess { _snackbarMessages.tryEmit("Order cancelled") }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Could not cancel order") }
        }
    }

    fun markNotificationSeen(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationSeen(currentUserId, notificationId)
        }
    }

    // --- Owner Control Panel Actions ---

    fun seedStarterCatalog() {
        viewModelScope.launch {
            repository.seedStarterCatalogIfEmpty()
                .onSuccess { count ->
                    if (count > 0) _snackbarMessages.tryEmit("Added $count starter products")
                    else _snackbarMessages.tryEmit("Catalog already has products")
                }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Failed to seed catalog") }
        }
    }

    fun saveProduct(product: Product, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveProduct(product)
                .onSuccess {
                    _snackbarMessages.tryEmit(
                        if (product.id.isBlank()) "Product added to Debiganj Mart" else "Updated ${product.name}"
                    )
                    onDone()
                }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Failed to save product") }
        }
    }

    fun quickUpdatePrice(product: Product, newPrice: Double, newMrp: Double, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.quickUpdateProductPrice(product.id, newPrice, newMrp.coerceAtLeast(newPrice))
                .onSuccess {
                    _snackbarMessages.tryEmit("Price updated for ${product.name}: ₹${newPrice.toInt()}")
                    onDone()
                }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Failed to update price") }
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product.id)
                .onSuccess { _snackbarMessages.tryEmit("Removed ${product.name}") }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Failed to delete product") }
        }
    }

    fun updateOrderStatus(
        order: StoreOrder,
        newStatus: OrderStatus,
        paymentStatus: String,
        ownerNote: String,
    ) {
        viewModelScope.launch {
            repository.updateOrderStatus(order, newStatus, paymentStatus, ownerNote)
                .onSuccess { _snackbarMessages.tryEmit("Order updated to ${newStatus.displayLabel}") }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Failed to update order") }
        }
    }

    fun saveStoreSettings(settings: StoreSettings) {
        viewModelScope.launch {
            repository.updateStoreSettings(settings)
                .onSuccess { _snackbarMessages.tryEmit("Store details & gateway settings saved") }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Failed to save store settings") }
        }
    }

    private fun unwrapFirestoreError(error: Throwable, tag: String): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Stream error in $tag", root)
        return root
    }

    private companion object {
        const val TAG = "StoreViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
