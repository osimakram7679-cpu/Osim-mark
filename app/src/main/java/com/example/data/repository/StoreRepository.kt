package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.OrderNotification
import com.example.data.model.OrderStatus
import com.example.data.model.Product
import com.example.data.model.StoreCatalogDefaults
import com.example.data.model.StoreOrder
import com.example.data.model.StoreSettings
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class StoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun observeIsAdmin(uid: String, email: String?, isEmailVerified: Boolean): Flow<Boolean> = flow {
        if (uid.isBlank()) {
            emit(false)
            return@flow
        }
        val isBootstrappedOwner =
            isEmailVerified && email.equals(StoreCatalogDefaults.OWNER_ADMIN_EMAIL, ignoreCase = true)
        if (isBootstrappedOwner) {
            emit(true)
            return@flow
        }
        val path = "admins/$uid"
        emitAll(
            db.collection("admins").document(uid)
                .snapshots()
                .map { it.exists() }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    emit(false)
                }
        )
    }

    fun observeAvailableProducts(): Flow<List<Product>> = flow {
        val uid = requireUserId()
        val path = "products"
        emitAll(
            db.collection(path)
                .whereEqualTo("available", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Product::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.name.lowercase() }
                }
                .catch { error ->
                    handleStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeAllProducts(): Flow<List<Product>> = flow {
        val uid = requireUserId()
        val path = "products"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Product::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.name.lowercase() }
                }
                .catch { error ->
                    handleStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeStoreSettings(): Flow<StoreSettings> = flow {
        val uid = requireUserId()
        val path = "store_settings/main"
        emitAll(
            db.collection("store_settings").document("main")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            StoreSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: StoreSettings()
                    } else {
                        StoreSettings()
                    }
                }
                .catch { error ->
                    handleStreamError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    fun observeUserOrders(userId: String): Flow<List<StoreOrder>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val path = "orders"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StoreOrder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    handleStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeAllOrders(): Flow<List<StoreOrder>> = flow {
        val uid = requireUserId()
        val path = "orders"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StoreOrder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    handleStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeUserNotifications(userId: String): Flow<List<OrderNotification>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val path = "users/$userId/notifications"
        emitAll(
            db.collection("users").document(userId).collection("notifications")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        OrderNotification::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    handleStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun seedStarterCatalogIfEmpty(): Result<Int> {
        val path = "products"
        return try {
            requireUserId()
            val existing = db.collection(path).limit(1).get().await()
            if (!existing.isEmpty) {
                return Result.success(0)
            }
            val batch = db.batch()
            StoreCatalogDefaults.STARTER_PRODUCTS.forEach { product ->
                val docRef = db.collection(path).document(product.id)
                batch.set(docRef, productCreateMap(product))
            }
            val settingsRef = db.collection("store_settings").document("main")
            batch.set(settingsRef, storeSettingsMap(StoreSettings()))
            batch.commit().await()
            Result.success(StoreCatalogDefaults.STARTER_PRODUCTS.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveProduct(product: Product): Result<Unit> {
        val isCreate = product.id.isBlank()
        val safeId = if (isCreate) {
            "prod_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        } else {
            product.id
        }
        val docPath = "products/$safeId"
        return try {
            requireUserId()
            validateProductBounds(product)
            val docRef = db.collection("products").document(safeId)
            if (isCreate) {
                docRef.set(productCreateMap(product)).await()
            } else {
                docRef.update(productUpdateMap(product)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (isCreate) OperationType.CREATE else OperationType.UPDATE,
                docPath
            )
            Result.failure(e)
        }
    }

    suspend fun quickUpdateProductPrice(
        productId: String,
        newPrice: Double,
        newMrp: Double,
    ): Result<Unit> {
        val docPath = "products/$productId"
        return try {
            requireUserId()
            require(productId.isNotBlank() && productId.length <= 128) { "Invalid product ID" }
            require(newPrice in 0.0..1000000.0) { "Price must be between ₹0 and ₹10,00,000" }
            require(newMrp in 0.0..1000000.0) { "MRP must be between ₹0 and ₹10,00,000" }
            db.collection("products").document(productId)
                .update(
                    mapOf(
                        "price" to newPrice,
                        "mrp" to newMrp,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(productId: String): Result<Unit> {
        val docPath = "products/$productId"
        return try {
            requireUserId()
            require(productId.isNotBlank()) { "Product ID must not be blank" }
            db.collection("products").document(productId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateStoreSettings(settings: StoreSettings): Result<Unit> {
        val docPath = "store_settings/main"
        return try {
            requireUserId()
            require(settings.storeName.trim().length in 1..80) { "Store name must be 1–80 characters" }
            require(settings.address.trim().length in 1..200) { "Address must be 1–200 characters" }
            require(settings.contactPhone.trim().length in 7..20) { "Phone must be 7–20 characters" }
            require(settings.merchantUpiId.trim().length in 3..100) { "UPI ID must be 3–100 characters" }
            require(settings.deliveryFee in 0.0..5000.0) { "Invalid delivery fee" }
            require(settings.freeDeliveryAbove in 0.0..50000.0) { "Invalid free delivery threshold" }

            db.collection("store_settings").document("main")
                .set(storeSettingsMap(settings))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun placeOrder(order: StoreOrder): Result<String> {
        val orderId = "ord_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val docPath = "orders/$orderId"
        return try {
            val uid = requireUserId()
            require(order.customerName.trim().length in 1..100) { "Please enter your full name" }
            require(order.customerPhone.trim().length in 7..20) { "Please enter a valid phone number" }
            require(order.deliveryAddress.trim().length in 3..300) { "Please enter delivery address" }
            require(order.items.isNotEmpty() && order.items.size <= 50) { "Cart must contain 1–50 items" }

            val orderPayload = mapOf(
                "userId" to uid,
                "customerName" to order.customerName.trim().take(100),
                "customerPhone" to order.customerPhone.trim().take(20),
                "deliveryAddress" to order.deliveryAddress.trim().take(300),
                "items" to order.items.map { item ->
                    mapOf(
                        "productId" to item.productId,
                        "name" to item.name.take(100),
                        "unit" to item.unit.take(30),
                        "price" to item.price,
                        "quantity" to item.quantity
                    )
                },
                "subtotal" to order.subtotal,
                "deliveryFee" to order.deliveryFee,
                "totalAmount" to order.totalAmount,
                "paymentMethod" to order.paymentMethod,
                "paymentStatus" to order.paymentStatus,
                "paymentReference" to order.paymentReference.ifBlank { "REF-$orderId" }.take(100),
                "status" to OrderStatus.PLACED.wireValue,
                "ownerNote" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val notifId = "notif_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val notifRef = db.collection("users").document(uid).collection("notifications").document(notifId)
            val notifPayload = mapOf(
                "orderId" to orderId,
                "title" to "Order Placed (#${orderId.takeLast(6).uppercase()})",
                "message" to "Thank you! Your Debiganj Mart order of ₹${order.totalAmount.toInt()} has been received.",
                "status" to OrderStatus.PLACED.wireValue,
                "seen" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(db.collection("orders").document(orderId), orderPayload)
            batch.set(notifRef, notifPayload)
            batch.commit().await()
            Result.success(orderId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun cancelUserOrder(orderId: String): Result<Unit> {
        val docPath = "orders/$orderId"
        return try {
            requireUserId()
            require(orderId.isNotBlank()) { "Invalid order ID" }
            db.collection("orders").document(orderId)
                .update(
                    mapOf(
                        "status" to OrderStatus.CANCELLED.wireValue,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateOrderStatus(
        order: StoreOrder,
        newStatus: OrderStatus,
        newPaymentStatus: String,
        ownerNote: String,
    ): Result<Unit> {
        val docPath = "orders/${order.id}"
        return try {
            requireUserId()
            require(order.id.isNotBlank()) { "Order ID must not be blank" }
            val trimmedNote = ownerNote.trim().take(200)
            val batch = db.batch()
            batch.update(
                db.collection("orders").document(order.id),
                mapOf(
                    "status" to newStatus.wireValue,
                    "paymentStatus" to newPaymentStatus,
                    "ownerNote" to trimmedNote,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (order.userId.isNotBlank()) {
                val notifId = "notif_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                val shortCode = order.id.takeLast(6).uppercase()
                val noteSuffix = if (trimmedNote.isNotBlank()) " — $trimmedNote" else ""
                val msg = when (newStatus) {
                    OrderStatus.CONFIRMED -> "Your order #$shortCode is confirmed by Debiganj Mart$noteSuffix"
                    OrderStatus.PACKED -> "Your items for #$shortCode are packed and ready$noteSuffix"
                    OrderStatus.OUT_FOR_DELIVERY -> "Rider is on the way with order #$shortCode$noteSuffix"
                    OrderStatus.DELIVERED -> "Order #$shortCode has been delivered. Thank you for shopping!$noteSuffix"
                    OrderStatus.CANCELLED -> "Order #$shortCode was cancelled$noteSuffix"
                    OrderStatus.PLACED -> "Order #$shortCode status updated$noteSuffix"
                }
                batch.set(
                    db.collection("users").document(order.userId)
                        .collection("notifications").document(notifId),
                    mapOf(
                        "orderId" to order.id,
                        "title" to "${newStatus.displayLabel} (#$shortCode)",
                        "message" to msg.take(300),
                        "status" to newStatus.wireValue,
                        "seen" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markNotificationSeen(userId: String, notificationId: String): Result<Unit> {
        val docPath = "users/$userId/notifications/$notificationId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Cross-user notification update rejected" }
            db.collection("users").document(userId)
                .collection("notifications").document(notificationId)
                .update(
                    mapOf(
                        "seen" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    private fun handleStreamError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        uid: String,
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    private fun validateProductBounds(product: Product) {
        require(product.name.trim().length in 1..100) { "Product name must be 1–100 characters" }
        require(product.description.trim().length <= 500) { "Description must be under 500 characters" }
        require(product.category.trim().length in 1..50) { "Category must be 1–50 characters" }
        require(product.unit.trim().length in 1..30) { "Unit must be 1–30 characters" }
        require(product.price in 0.0..1000000.0) { "Price must be ₹0 – ₹10,00,000" }
        require(product.mrp in 0.0..1000000.0) { "MRP must be ₹0 – ₹10,00,000" }
        require(product.stockCount in 0L..100000L) { "Stock count must be 0 – 1,00,000" }
        require(product.imageKey.trim().length in 1..50) { "Image key must be 1–50 characters" }
    }

    private fun productCreateMap(product: Product): Map<String, Any> = mapOf(
        "name" to product.name.trim().take(100),
        "description" to product.description.trim().take(500),
        "category" to product.category.trim().take(50),
        "unit" to product.unit.trim().take(30),
        "price" to product.price,
        "mrp" to product.mrp.coerceAtLeast(product.price),
        "stockCount" to product.stockCount,
        "available" to product.available,
        "imageKey" to product.imageKey.trim().ifBlank { "groceries" }.take(50),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun productUpdateMap(product: Product): Map<String, Any> = mapOf(
        "name" to product.name.trim().take(100),
        "description" to product.description.trim().take(500),
        "category" to product.category.trim().take(50),
        "unit" to product.unit.trim().take(30),
        "price" to product.price,
        "mrp" to product.mrp.coerceAtLeast(product.price),
        "stockCount" to product.stockCount,
        "available" to product.available,
        "imageKey" to product.imageKey.trim().ifBlank { "groceries" }.take(50),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun storeSettingsMap(settings: StoreSettings): Map<String, Any> = mapOf(
        "storeName" to settings.storeName.trim().take(80),
        "address" to settings.address.trim().take(200),
        "contactPhone" to settings.contactPhone.trim().take(20),
        "merchantUpiId" to settings.merchantUpiId.trim().take(100),
        "deliveryFee" to settings.deliveryFee,
        "freeDeliveryAbove" to settings.freeDeliveryAbove,
        "storeOpen" to settings.storeOpen,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
