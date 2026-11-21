package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CartItem
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentStatus
import com.example.data.model.Product
import com.example.data.model.StoreSettings
import com.example.payment.GatewayResult
import com.example.payment.PaymentGatewayManager
import com.example.ui.components.QuantityStepper
import com.example.ui.components.formatInr
import com.example.ui.components.productImageRes
import com.example.ui.theme.StatusSuccess
import com.example.ui.viewmodel.CartSummary
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartCheckoutScreen(
    cartSummary: CartSummary,
    storeSettings: StoreSettings,
    defaultCustomerName: String,
    isSubmittingOrder: Boolean,
    onIncrement: (Product) -> Unit,
    onDecrement: (String) -> Unit,
    onPlaceOrder: (
        customerName: String,
        customerPhone: String,
        deliveryAddress: String,
        paymentMethod: PaymentMethod,
        paymentStatus: PaymentStatus,
        paymentReference: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) -> Unit,
    onBrowseShop: () -> Unit,
    onViewOrders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var customerName by remember(defaultCustomerName) { mutableStateOf(defaultCustomerName) }
    var customerPhone by remember { mutableStateOf("") }
    var deliveryAddress by remember { mutableStateOf("") }
    var selectedPayment by remember { mutableStateOf(PaymentMethod.UPI) }
    var formError by remember { mutableStateOf<String?>(null) }
    var showGatewaySheet by remember { mutableStateOf(false) }
    var completedOrderId by remember { mutableStateOf<String?>(null) }
    var completedPaymentRef by remember { mutableStateOf("") }

    if (completedOrderId != null) {
        OrderSuccessView(
            orderId = completedOrderId!!,
            paymentRef = completedPaymentRef,
            storePhone = storeSettings.contactPhone,
            onViewOrders = {
                completedOrderId = null
                onViewOrders()
            },
            onContinueShopping = {
                completedOrderId = null
                onBrowseShop()
            }
        )
        return
    }

    if (cartSummary.items.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.ShoppingBag,
                    contentDescription = "Empty Cart",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Your shopping basket is empty",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Add fresh groceries, snacks, or daily household essentials from Debiganj Mart.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onBrowseShop,
                    modifier = Modifier.testTag("empty_cart_browse_button")
                ) {
                    Text("Browse Store Products")
                }
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // Free delivery banner
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val progress = (cartSummary.subtotal / storeSettings.freeDeliveryAbove.coerceAtLeast(1.0))
                        .toFloat().coerceIn(0f, 1f)
                    Text(
                        text = if (cartSummary.freeDeliveryRemaining <= 0.0) {
                            "Yay! You unlocked FREE delivery from ${storeSettings.storeName}"
                        } else {
                            "Add ${formatInr(cartSummary.freeDeliveryRemaining)} more for FREE delivery"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }
            }
        }

        // Cart Items
        items(cartSummary.items, key = { it.product.id }) { cartItem ->
            CartItemRow(
                cartItem = cartItem,
                onIncrement = { onIncrement(cartItem.product) },
                onDecrement = { onDecrement(cartItem.product.id) }
            )
        }

        // Delivery Details Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Delivery Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checkout_name_input")
                    )
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { input ->
                            customerPhone = input.filter { it.isDigit() }.take(12)
                        },
                        label = { Text("Mobile Number (for delivery call)") },
                        placeholder = { Text("10-digit mobile number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checkout_phone_input")
                    )
                    OutlinedTextField(
                        value = deliveryAddress,
                        onValueChange = { deliveryAddress = it },
                        label = { Text("Delivery Address") },
                        placeholder = { Text("e.g., Near Debiganj Bazar, Goalpokher, Uttar Dinajpur") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checkout_address_input")
                    )
                }
            }
        }

        // Payment Method Selector
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Secure Payment Gateway",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = StatusSuccess.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Encrypted",
                                    tint = StatusSuccess,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "SSL Secure",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    PaymentOptionRow(
                        selected = selectedPayment == PaymentMethod.UPI,
                        icon = Icons.Default.QrCode2,
                        title = "UPI Instant Pay",
                        subtitle = "GPay, PhonePe, Paytm, BHIM (${storeSettings.merchantUpiId})",
                        onClick = { selectedPayment = PaymentMethod.UPI },
                        testTag = "payment_option_upi"
                    )
                    PaymentOptionRow(
                        selected = selectedPayment == PaymentMethod.CARD_GATEWAY,
                        icon = Icons.Default.CreditCard,
                        title = "Debit / Credit Card Gateway",
                        subtitle = if (PaymentGatewayManager.isTestGatewayMode()) {
                            "Encrypted Gateway (Test Mode • Configure key in Secrets)"
                        } else {
                            "Visa, Mastercard, RuPay • Instant Authorization"
                        },
                        onClick = { selectedPayment = PaymentMethod.CARD_GATEWAY },
                        testTag = "payment_option_card"
                    )
                    PaymentOptionRow(
                        selected = selectedPayment == PaymentMethod.COD,
                        icon = Icons.Default.AccountBalanceWallet,
                        title = "Cash on Delivery",
                        subtitle = "Pay cash or UPI QR when items reach your doorstep",
                        onClick = { selectedPayment = PaymentMethod.COD },
                        testTag = "payment_option_cod"
                    )
                }
            }
        }

        // Bill Summary + CTA
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Bill Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    BillRow("Item Total", formatInr(cartSummary.subtotal))
                    BillRow(
                        "Delivery Fee",
                        if (cartSummary.deliveryFee == 0.0) "FREE" else formatInr(cartSummary.deliveryFee)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Grand Total",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatInr(cartSummary.grandTotal),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (formError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formError ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            formError = null
                            when {
                                customerName.trim().length < 2 ->
                                    formError = "Please enter your full name."
                                customerPhone.trim().length < 10 ->
                                    formError = "Please enter a valid 10-digit phone number."
                                deliveryAddress.trim().length < 5 ->
                                    formError = "Please enter your full delivery address in Debiganj / Goalpokher."
                                selectedPayment == PaymentMethod.COD -> {
                                    val codRef = "COD-${UUID.randomUUID().toString().take(8).uppercase()}"
                                    onPlaceOrder(
                                        customerName,
                                        customerPhone,
                                        deliveryAddress,
                                        PaymentMethod.COD,
                                        PaymentStatus.PENDING_COD,
                                        codRef,
                                        { orderId ->
                                            completedPaymentRef = codRef
                                            completedOrderId = orderId
                                        },
                                        { err -> formError = err }
                                    )
                                }
                                else -> showGatewaySheet = true
                            }
                        },
                        enabled = !isSubmittingOrder && storeSettings.storeOpen,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("proceed_to_pay_button")
                    ) {
                        if (isSubmittingOrder) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = when {
                                    !storeSettings.storeOpen -> "Store Currently Closed"
                                    selectedPayment == PaymentMethod.COD -> "Place COD Order • ${formatInr(cartSummary.grandTotal)}"
                                    else -> "Pay Securely • ${formatInr(cartSummary.grandTotal)}"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showGatewaySheet) {
        SecurePaymentGatewaySheet(
            paymentMethod = selectedPayment,
            amountInr = cartSummary.grandTotal,
            storeSettings = storeSettings,
            isSubmittingOrder = isSubmittingOrder,
            onDismiss = { showGatewaySheet = false },
            onLaunchUpiApp = { txnRef ->
                PaymentGatewayManager.launchUpiAppIntent(
                    context = context,
                    merchantUpiId = storeSettings.merchantUpiId,
                    merchantName = storeSettings.storeName,
                    amountInr = cartSummary.grandTotal,
                    txnRef = txnRef
                )
            },
            onConfirmGatewayPayment = { txnRef ->
                onPlaceOrder(
                    customerName,
                    customerPhone,
                    deliveryAddress,
                    selectedPayment,
                    PaymentStatus.PAID,
                    txnRef,
                    { orderId ->
                        showGatewaySheet = false
                        completedPaymentRef = txnRef
                        completedOrderId = orderId
                    },
                    { err ->
                        showGatewaySheet = false
                        formError = err
                    }
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurePaymentGatewaySheet(
    paymentMethod: PaymentMethod,
    amountInr: Double,
    storeSettings: StoreSettings,
    isSubmittingOrder: Boolean,
    onDismiss: () -> Unit,
    onLaunchUpiApp: (String) -> Boolean,
    onConfirmGatewayPayment: (String) -> Unit,
) {
    var upiIdInput by remember { mutableStateOf("") }
    var upiAppLaunched by remember { mutableStateOf(false) }
    var cardNumber by remember { mutableStateOf("") }
    var cardExpiry by remember { mutableStateOf("") }
    var cardCvv by remember { mutableStateOf("") }
    var cardHolder by remember { mutableStateOf("") }
    var gatewayError by remember { mutableStateOf<String?>(null) }
    val upiTxnRef = remember { "DM-${UUID.randomUUID().toString().take(8).uppercase()}" }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.checkout_secure_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusSuccess,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Pay ${formatInr(amountInr)} to ${storeSettings.storeName}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Secure Gateway",
                    tint = StatusSuccess
                )
            }

            if (paymentMethod == PaymentMethod.UPI) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Merchant UPI: ${storeSettings.merchantUpiId}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Ref: $upiTxnRef • Instant verification",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        gatewayError = null
                        val launched = onLaunchUpiApp(upiTxnRef)
                        if (launched) {
                            upiAppLaunched = true
                        } else {
                            gatewayError = "No UPI app installed on this device. Verify your UPI ID below instead."
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("launch_upi_app_button")
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open GPay / PhonePe / Paytm")
                }

                if (upiAppLaunched) {
                    Button(
                        onClick = { onConfirmGatewayPayment(upiTxnRef) },
                        enabled = !isSubmittingOrder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("I Completed Payment in UPI App")
                    }
                }

                HorizontalDivider()
                Text(
                    text = "Or pay via UPI ID verification:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = upiIdInput,
                    onValueChange = { upiIdInput = it },
                    label = { Text("Your UPI ID") },
                    placeholder = { Text("username@okicici or mobile@ybl") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gateway_upi_id_input")
                )

                if (gatewayError != null) {
                    Text(
                        text = gatewayError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        gatewayError = null
                        when (val res = PaymentGatewayManager.verifyUpiIdAndProcess(upiIdInput, amountInr)) {
                            is GatewayResult.Success -> onConfirmGatewayPayment(res.transactionRef)
                            is GatewayResult.Failure -> gatewayError = res.reason
                        }
                    },
                    enabled = !isSubmittingOrder,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("confirm_upi_gateway_button")
                ) {
                    Text("Verify & Pay ${formatInr(amountInr)}")
                }
            } else {
                // Card Gateway
                OutlinedTextField(
                    value = cardHolder,
                    onValueChange = { cardHolder = it },
                    label = { Text("Cardholder Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gateway_card_holder_input")
                )
                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = { input ->
                        cardNumber = input.filter { it.isDigit() }.take(16)
                    },
                    label = { Text("Card Number") },
                    placeholder = { Text("16-digit Visa / Mastercard / RuPay") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gateway_card_number_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = cardExpiry,
                        onValueChange = { cardExpiry = it.take(5) },
                        label = { Text("Expiry (MM/YY)") },
                        placeholder = { Text("12/28") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("gateway_card_expiry_input")
                    )
                    OutlinedTextField(
                        value = cardCvv,
                        onValueChange = { input ->
                            cardCvv = input.filter { it.isDigit() }.take(4)
                        },
                        label = { Text("CVV") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("gateway_card_cvv_input")
                    )
                }

                if (gatewayError != null) {
                    Text(
                        text = gatewayError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        gatewayError = null
                        val res = PaymentGatewayManager.processCardGatewayPayment(
                            cardNumberRaw = cardNumber,
                            expiryRaw = cardExpiry,
                            cvvRaw = cardCvv,
                            cardHolderName = cardHolder,
                            amountInr = amountInr
                        )
                        when (res) {
                            is GatewayResult.Success -> {
                                // Immediately scrub sensitive card fields from local UI state
                                cardNumber = ""
                                cardExpiry = ""
                                cardCvv = ""
                                onConfirmGatewayPayment(res.transactionRef)
                            }
                            is GatewayResult.Failure -> gatewayError = res.reason
                        }
                    },
                    enabled = !isSubmittingOrder,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("confirm_card_gateway_button")
                ) {
                    Text("Authorize & Pay ${formatInr(amountInr)}")
                }
            }
        }
    }
}

@Composable
private fun CartItemRow(
    cartItem: CartItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = productImageRes(cartItem.product.imageKey)),
                contentDescription = cartItem.product.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cartItem.product.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${cartItem.product.unit} • ${formatInr(cartItem.product.price)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatInr(cartItem.lineTotal),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            QuantityStepper(
                quantity = cartItem.quantity,
                onDecrement = onDecrement,
                onIncrement = onIncrement
            )
        }
    }
}

@Composable
private fun PaymentOptionRow(
    selected: Boolean,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BillRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OrderSuccessView(
    orderId: String,
    paymentRef: String,
    storePhone: String,
    onViewOrders: () -> Unit,
    onContinueShopping: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Order Confirmed",
                    tint = StatusSuccess,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Order Placed!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Order #${orderId.takeLast(6).uppercase()} • Ref: $paymentRef",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "We will send you live order notifications as Debiganj Mart packs and dispatches your items. Need help? Call $storePhone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onViewOrders,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("order_success_track_button")
                ) {
                    Text("Track Order Status")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onContinueShopping,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue Shopping")
                }
            }
        }
    }
}
