package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentStatus
import com.example.data.model.Product
import com.example.data.model.StoreCatalogDefaults
import com.example.data.model.StoreOrder
import com.example.data.model.StoreSettings
import com.example.data.model.UiState
import com.example.payment.PaymentGatewayManager
import com.example.ui.components.CallStorePill
import com.example.ui.components.OrderStatusChip
import com.example.ui.components.PriceDisplay
import com.example.ui.components.dialStorePhone
import com.example.ui.components.formatInr
import com.example.ui.components.productImageRes
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess

@Composable
fun ControlPanelOrStoreScreen(
    isAdmin: Boolean,
    userEmail: String?,
    storeSettings: StoreSettings,
    adminProductsState: UiState<List<Product>>,
    adminOrdersState: UiState<List<StoreOrder>>,
    onSaveProduct: (Product, () -> Unit) -> Unit,
    onQuickUpdatePrice: (Product, Double, Double, () -> Unit) -> Unit,
    onDeleteProduct: (Product) -> Unit,
    onSeedCatalog: () -> Unit,
    onUpdateOrderStatus: (StoreOrder, OrderStatus, String, String) -> Unit,
    onSaveStoreSettings: (StoreSettings) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isAdmin) {
        CustomerStoreInfoView(
            userEmail = userEmail,
            storeSettings = storeSettings,
            onSignOut = onSignOut,
            modifier = modifier
        )
        return
    }

    var selectedSubTab by remember { mutableIntStateOf(0) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var priceEditingProduct by remember { mutableStateOf<Product?>(null) }
    var deletingProduct by remember { mutableStateOf<Product?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = "Owner Control Panel",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Owner Control Panel",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = userEmail ?: StoreCatalogDefaults.OWNER_ADMIN_EMAIL,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(
                onClick = onSignOut,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("admin_sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Sign out",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sign Out", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                modifier = Modifier.testTag("admin_tab_products")
            ) {
                Text("Products & Prices")
            }
            SegmentedButton(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                modifier = Modifier.testTag("admin_tab_orders")
            ) {
                Text("Orders")
            }
            SegmentedButton(
                selected = selectedSubTab == 2,
                onClick = { selectedSubTab = 2 },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                modifier = Modifier.testTag("admin_tab_settings")
            ) {
                Text("Store")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedSubTab) {
            0 -> AdminCatalogTab(
                productsState = adminProductsState,
                onAddClick = { showCreateDialog = true },
                onQuickPriceClick = { priceEditingProduct = it },
                onEditClick = { editingProduct = it },
                onDeleteClick = { deletingProduct = it },
                onSeedCatalog = onSeedCatalog
            )

            1 -> AdminOrdersTab(
                ordersState = adminOrdersState,
                onUpdateOrderStatus = onUpdateOrderStatus
            )

            2 -> AdminStoreSettingsTab(
                settings = storeSettings,
                onSaveSettings = onSaveStoreSettings
            )
        }
    }

    if (showCreateDialog) {
        ProductEditorDialog(
            initial = Product(),
            onDismiss = { showCreateDialog = false },
            onSave = { updated ->
                onSaveProduct(updated) { showCreateDialog = false }
            }
        )
    }

    editingProduct?.let { product ->
        ProductEditorDialog(
            initial = product,
            onDismiss = { editingProduct = null },
            onSave = { updated ->
                onSaveProduct(updated) { editingProduct = null }
            }
        )
    }

    priceEditingProduct?.let { product ->
        QuickPriceDialog(
            product = product,
            onDismiss = { priceEditingProduct = null },
            onSave = { newPrice, newMrp ->
                onQuickUpdatePrice(product, newPrice, newMrp) { priceEditingProduct = null }
            }
        )
    }

    deletingProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { deletingProduct = null },
            title = { Text("Remove ${product.name}?") },
            text = { Text("This permanently deletes the product from Debiganj Mart.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProduct(product)
                        deletingProduct = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDanger)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProduct = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AdminCatalogTab(
    productsState: UiState<List<Product>>,
    onAddClick: () -> Unit,
    onQuickPriceClick: (Product) -> Unit,
    onEditClick: (Product) -> Unit,
    onDeleteClick: (Product) -> Unit,
    onSeedCatalog: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tap Price or Edit to update any item",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("add_product_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Product")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (productsState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Error -> Text(productsState.message, color = MaterialTheme.colorScheme.error)
            is UiState.Success -> {
                val products = productsState.data
                if (products.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No products in catalog", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onSeedCatalog) {
                                Text("Seed Starter General Store Items")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(products, key = { it.id }) { product ->
                            AdminProductCard(
                                product = product,
                                onQuickPriceClick = { onQuickPriceClick(product) },
                                onEditClick = { onEditClick(product) },
                                onDeleteClick = { onDeleteClick(product) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminProductCard(
    product: Product,
    onQuickPriceClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = productImageRes(product.imageKey)),
                    contentDescription = product.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            color = if (product.available) StatusSuccess.copy(alpha = 0.14f) else StatusDanger.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = if (product.available) "Live" else "Hidden",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (product.available) StatusSuccess else StatusDanger,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${product.category} • ${product.unit} • Stock: ${product.stockCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    PriceDisplay(price = product.price, mrp = product.mrp)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onQuickPriceClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("change_price_${product.id}")
                ) {
                    Icon(Icons.Default.CurrencyRupee, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Change Price")
                }
                Button(
                    onClick = onEditClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("edit_product_${product.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit Product")
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.testTag("delete_product_${product.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete product",
                        tint = StatusDanger
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPriceDialog(
    product: Product,
    onDismiss: () -> Unit,
    onSave: (Double, Double) -> Unit,
) {
    var priceText by remember { mutableStateOf(product.price.toInt().toString()) }
    var mrpText by remember { mutableStateOf(product.mrp.toInt().toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Price: ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Selling Price (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.testTag("quick_price_input")
                )
                OutlinedTextField(
                    value = mrpText,
                    onValueChange = { mrpText = it },
                    label = { Text("MRP (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.testTag("quick_mrp_input")
                )
                if (errorText != null) {
                    Text(errorText ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = priceText.toDoubleOrNull()
                    val m = mrpText.toDoubleOrNull() ?: p
                    if (p == null || p < 0.0 || p > 1000000.0) {
                        errorText = "Enter a valid selling price"
                    } else if (m == null || m < 0.0 || m > 1000000.0) {
                        errorText = "Enter a valid MRP"
                    } else {
                        onSave(p, m)
                    }
                },
                modifier = Modifier.testTag("save_quick_price_button")
            ) {
                Text("Update Price")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductEditorDialog(
    initial: Product,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    var category by remember {
        mutableStateOf(initial.category.ifBlank { "Groceries & Staples" })
    }
    var unit by remember { mutableStateOf(initial.unit.ifBlank { "1 kg" }) }
    var priceText by remember { mutableStateOf(if (initial.price > 0) initial.price.toString() else "") }
    var mrpText by remember { mutableStateOf(if (initial.mrp > 0) initial.mrp.toString() else "") }
    var stockText by remember { mutableStateOf(initial.stockCount.toString()) }
    var available by remember { mutableStateOf(initial.available) }
    var imageKey by remember { mutableStateOf(initial.imageKey.ifBlank { "groceries" }) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "Add New Product" else "Edit Product") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("editor_product_name")
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit / Weight (e.g., 1 kg, 500 ml)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Price (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("editor_product_price")
                    )
                    OutlinedTextField(
                        value = mrpText,
                        onValueChange = { mrpText = it },
                        label = { Text("MRP (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = stockText,
                    onValueChange = { stockText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Stock Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StoreCatalogDefaults.CATEGORIES.drop(1).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text("Image Style", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StoreCatalogDefaults.IMAGE_KEYS.forEach { (key, label) ->
                        FilterChip(
                            selected = imageKey == key,
                            onClick = { imageKey = key },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Visible to Customers", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = available, onCheckedChange = { available = it })
                }
                if (errorText != null) {
                    Text(errorText ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = priceText.toDoubleOrNull()
                    val m = mrpText.toDoubleOrNull() ?: p
                    val stock = stockText.toLongOrNull() ?: 0L
                    when {
                        name.trim().isEmpty() -> errorText = "Enter product name"
                        unit.trim().isEmpty() -> errorText = "Enter unit/weight"
                        p == null || p < 0.0 -> errorText = "Enter valid price"
                        m == null || m < 0.0 -> errorText = "Enter valid MRP"
                        else -> onSave(
                            initial.copy(
                                name = name.trim(),
                                description = description.trim(),
                                category = category,
                                unit = unit.trim(),
                                price = p,
                                mrp = m.coerceAtLeast(p),
                                stockCount = stock,
                                available = available,
                                imageKey = imageKey
                            )
                        )
                    }
                },
                modifier = Modifier.testTag("save_product_button")
            ) {
                Text("Save Product")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminOrdersTab(
    ordersState: UiState<List<StoreOrder>>,
    onUpdateOrderStatus: (StoreOrder, OrderStatus, String, String) -> Unit,
) {
    val context = LocalContext.current
    when (ordersState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> Text(ordersState.message, color = MaterialTheme.colorScheme.error)
        is UiState.Success -> {
            val orders = ordersState.data
            if (orders.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No customer orders yet", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(orders, key = { it.id }) { order ->
                        val status = OrderStatus.fromRaw(order.status)
                        var note by remember(order.id, order.ownerNote) { mutableStateOf(order.ownerNote) }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#${order.id.takeLast(6).uppercase()} • ${order.customerName}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    OrderStatusChip(status = status)
                                }
                                Text(
                                    text = order.deliveryAddress,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${formatInr(order.totalAmount)} • ${PaymentMethod.fromRaw(order.paymentMethod).displayLabel} (${PaymentStatus.fromRaw(order.paymentStatus).displayLabel})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    CallStorePill(
                                        phone = order.customerPhone,
                                        onClick = { dialStorePhone(context, order.customerPhone) }
                                    )
                                }

                                order.items.forEach { item ->
                                    Text(
                                        text = "• ${item.quantity}x ${item.name} (${item.unit})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                OutlinedTextField(
                                    value = note,
                                    onValueChange = { note = it },
                                    label = { Text("Customer Update Note (sent in notification)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        OrderStatus.CONFIRMED,
                                        OrderStatus.PACKED,
                                        OrderStatus.OUT_FOR_DELIVERY,
                                        OrderStatus.DELIVERED,
                                        OrderStatus.CANCELLED
                                    ).forEach { targetStatus ->
                                        val isCurrent = targetStatus == status
                                        FilterChip(
                                            selected = isCurrent,
                                            onClick = {
                                                val payStatus = if (targetStatus == OrderStatus.DELIVERED && order.paymentStatus == PaymentStatus.PENDING_COD.wireValue) {
                                                    PaymentStatus.PAID.wireValue
                                                } else order.paymentStatus
                                                onUpdateOrderStatus(order, targetStatus, payStatus, note)
                                            },
                                            label = { Text(targetStatus.displayLabel, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminStoreSettingsTab(
    settings: StoreSettings,
    onSaveSettings: (StoreSettings) -> Unit,
) {
    var storeName by remember(settings) { mutableStateOf(settings.storeName) }
    var address by remember(settings) { mutableStateOf(settings.address) }
    var contactPhone by remember(settings) { mutableStateOf(settings.contactPhone) }
    var merchantUpi by remember(settings) { mutableStateOf(settings.merchantUpiId) }
    var deliveryFee by remember(settings) { mutableStateOf(settings.deliveryFee.toInt().toString()) }
    var freeThreshold by remember(settings) { mutableStateOf(settings.freeDeliveryAbove.toInt().toString()) }
    var storeOpen by remember(settings) { mutableStateOf(settings.storeOpen) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Store Profile & Gateway Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("Store Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Store Address") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { contactPhone = it.filter { ch -> ch.isDigit() }.take(12) },
                    label = { Text("Contact Phone") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = merchantUpi,
                    onValueChange = { merchantUpi = it },
                    label = { Text("Merchant UPI ID (for Payments)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = deliveryFee,
                        onValueChange = { deliveryFee = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Delivery Fee (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = freeThreshold,
                        onValueChange = { freeThreshold = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Free Above (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Store Accepting Orders", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = storeOpen, onCheckedChange = { storeOpen = it })
                }

                HorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = StatusSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (PaymentGatewayManager.isTestGatewayMode()) {
                            "Payment Gateway: Test/UPI Mode (set PAYMENT_GATEWAY_KEY_ID in Secrets for live key)"
                        } else {
                            "Payment Gateway Key active from BuildConfig"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        onSaveSettings(
                            settings.copy(
                                storeName = storeName.trim().ifBlank { StoreSettings.DEFAULT_STORE_NAME },
                                address = address.trim().ifBlank { StoreSettings.DEFAULT_STORE_ADDRESS },
                                contactPhone = contactPhone.trim().ifBlank { StoreSettings.DEFAULT_STORE_PHONE },
                                merchantUpiId = merchantUpi.trim().ifBlank { StoreSettings.DEFAULT_UPI_ID },
                                deliveryFee = deliveryFee.toDoubleOrNull() ?: 20.0,
                                freeDeliveryAbove = freeThreshold.toDoubleOrNull() ?: 499.0,
                                storeOpen = storeOpen
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_store_settings_button")
                ) {
                    Text("Save Store & Gateway Settings")
                }
            }
        }
    }
}

@Composable
private fun CustomerStoreInfoView(
    userEmail: String?,
    storeSettings: StoreSettings,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = storeSettings.storeName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (storeSettings.storeOpen) "Open for Orders" else "Currently Closed",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (storeSettings.storeOpen) StatusSuccess else StatusDanger
                        )
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(storeSettings.address, style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("+91 ${storeSettings.contactPhone}", style = MaterialTheme.typography.bodyMedium)
                    }
                    CallStorePill(
                        phone = storeSettings.contactPhone,
                        onClick = { dialStorePhone(context, storeSettings.contactPhone) }
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Your Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Signed in as ${userEmail ?: "Google User"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Owner Control Panel is restricted to the store owner account (${StoreCatalogDefaults.OWNER_ADMIN_EMAIL}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out / Switch Account")
                }
            }
        }
    }
}
