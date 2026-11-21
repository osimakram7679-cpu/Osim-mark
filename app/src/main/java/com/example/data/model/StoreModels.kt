package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val unit: String = "",
    val price: Double = 0.0,
    val mrp: Double = 0.0,
    val stockCount: Long = 0L,
    val available: Boolean = true,
    val imageKey: String = "groceries",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val discountPercent: Int
        get() = if (mrp > price && mrp > 0.0) {
            (((mrp - price) / mrp) * 100.0).toInt().coerceIn(0, 99)
        } else 0
}

@Keep
@IgnoreExtraProperties
data class StoreSettings(
    @DocumentId val id: String = "",
    val storeName: String = DEFAULT_STORE_NAME,
    val address: String = DEFAULT_STORE_ADDRESS,
    val contactPhone: String = DEFAULT_STORE_PHONE,
    val merchantUpiId: String = DEFAULT_UPI_ID,
    val deliveryFee: Double = 20.0,
    val freeDeliveryAbove: Double = 499.0,
    val storeOpen: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    companion object {
        const val DEFAULT_STORE_NAME = "Debiganj Mart"
        const val DEFAULT_STORE_ADDRESS = "Debiganj, Goalpokher, Uttar Dinajpur"
        const val DEFAULT_STORE_PHONE = "7679100108"
        const val DEFAULT_UPI_ID = "7679100108@upi"
    }
}

@Keep
@IgnoreExtraProperties
data class OrderLineItem(
    val productId: String = "",
    val name: String = "",
    val unit: String = "",
    val price: Double = 0.0,
    val quantity: Long = 1L,
) {
    val lineTotal: Double
        get() = price * quantity
}

@Keep
@IgnoreExtraProperties
data class StoreOrder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val deliveryAddress: String = "",
    val items: List<OrderLineItem> = emptyList(),
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val totalAmount: Double = 0.0,
    val paymentMethod: String = PaymentMethod.UPI.wireValue,
    val paymentStatus: String = PaymentStatus.PAID.wireValue,
    val paymentReference: String = "",
    val status: String = OrderStatus.PLACED.wireValue,
    val ownerNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class OrderNotification(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val title: String = "",
    val message: String = "",
    val status: String = "",
    val seen: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class CartItem(
    val product: Product,
    val quantity: Int,
) {
    val lineTotal: Double get() = product.price * quantity
}

enum class OrderStatus(val wireValue: String, val displayLabel: String) {
    PLACED("PLACED", "Order Placed"),
    CONFIRMED("CONFIRMED", "Confirmed"),
    PACKED("PACKED", "Packed"),
    OUT_FOR_DELIVERY("OUT_FOR_DELIVERY", "Out for Delivery"),
    DELIVERED("DELIVERED", "Delivered"),
    CANCELLED("CANCELLED", "Cancelled");

    companion object {
        fun fromRaw(raw: String): OrderStatus =
            entries.firstOrNull { it.wireValue == raw } ?: PLACED
    }
}

enum class PaymentMethod(val wireValue: String, val displayLabel: String) {
    UPI("UPI", "UPI Instant Pay"),
    CARD_GATEWAY("CARD_GATEWAY", "Card / NetBanking Gateway"),
    COD("COD", "Cash on Delivery");

    companion object {
        fun fromRaw(raw: String): PaymentMethod =
            entries.firstOrNull { it.wireValue == raw } ?: COD
    }
}

enum class PaymentStatus(val wireValue: String, val displayLabel: String) {
    PAID("PAID", "Paid"),
    PENDING_COD("PENDING_COD", "Pay on Delivery"),
    REFUNDED("REFUNDED", "Refunded");

    companion object {
        fun fromRaw(raw: String): PaymentStatus =
            entries.firstOrNull { it.wireValue == raw } ?: PENDING_COD
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

object StoreCatalogDefaults {
    const val OWNER_ADMIN_EMAIL = "osimakram90644@gmail.com"

    val CATEGORIES = listOf(
        "All",
        "Groceries & Staples",
        "Snacks & Drinks",
        "Dairy & Fresh",
        "Personal Care",
        "Household"
    )

    val IMAGE_KEYS = listOf(
        "groceries" to "Groceries & Grains",
        "snacks" to "Snacks & Tea",
        "household" to "Personal & Home"
    )

    val STARTER_PRODUCTS = listOf(
        Product(
            id = "basmati_rice_5kg",
            name = "Royal Basmati Rice",
            description = "Long-grain aromatic aged basmati rice ideal for biryani and everyday meals.",
            category = "Groceries & Staples",
            unit = "5 kg",
            price = 460.0,
            mrp = 520.0,
            stockCount = 28,
            available = true,
            imageKey = "groceries"
        ),
        Product(
            id = "mustard_oil_1l",
            name = "Kachi Ghani Mustard Oil",
            description = "Pure cold-pressed pungent mustard oil sourced from Bengal farms.",
            category = "Groceries & Staples",
            unit = "1 L",
            price = 165.0,
            mrp = 190.0,
            stockCount = 40,
            available = true,
            imageKey = "groceries"
        ),
        Product(
            id = "chakki_atta_5kg",
            name = "Chakki Fresh Whole Wheat Atta",
            description = "Stone-ground whole wheat flour for soft rotis and parathas.",
            category = "Groceries & Staples",
            unit = "5 kg",
            price = 215.0,
            mrp = 245.0,
            stockCount = 35,
            available = true,
            imageKey = "groceries"
        ),
        Product(
            id = "masoor_dal_1kg",
            name = "Desi Red Masoor Dal",
            description = "Unpolished protein-rich split red lentils, cleaned and ready to cook.",
            category = "Groceries & Staples",
            unit = "1 kg",
            price = 112.0,
            mrp = 130.0,
            stockCount = 45,
            available = true,
            imageKey = "groceries"
        ),
        Product(
            id = "turmeric_powder_200g",
            name = "Haldi Turmeric Powder",
            description = "Rich golden curcumin-rich turmeric powder for daily cooking.",
            category = "Groceries & Staples",
            unit = "200 g",
            price = 58.0,
            mrp = 70.0,
            stockCount = 50,
            available = true,
            imageKey = "groceries"
        ),
        Product(
            id = "assam_ctc_tea_250g",
            name = "Premium Assam CTC Leaf Tea",
            description = "Strong, malty morning chai blend with rich aroma and color.",
            category = "Snacks & Drinks",
            unit = "250 g",
            price = 135.0,
            mrp = 155.0,
            stockCount = 32,
            available = true,
            imageKey = "snacks"
        ),
        Product(
            id = "butter_biscuits_pack",
            name = "Golden Butter Crunch Biscuits",
            description = "Crispy baked tea-time biscuits family value pack.",
            category = "Snacks & Drinks",
            unit = "400 g",
            price = 65.0,
            mrp = 80.0,
            stockCount = 60,
            available = true,
            imageKey = "snacks"
        ),
        Product(
            id = "bhujia_namkeen_400g",
            name = "Spicy Masala Bhujia Sev",
            description = "Crispy spiced gram-flour namkeen snack for evening tea.",
            category = "Snacks & Drinks",
            unit = "400 g",
            price = 92.0,
            mrp = 110.0,
            stockCount = 38,
            available = true,
            imageKey = "snacks"
        ),
        Product(
            id = "fresh_toned_milk_1l",
            name = "Fresh Pasteurized Toned Milk",
            description = "Farm-fresh chilled milk packet delivered daily to Debiganj.",
            category = "Dairy & Fresh",
            unit = "1 L",
            price = 56.0,
            mrp = 60.0,
            stockCount = 24,
            available = true,
            imageKey = "snacks"
        ),
        Product(
            id = "cow_desi_ghee_500ml",
            name = "Pure Cow Desi Ghee",
            description = "Granular aromatic clarified butter for traditional cooking and sweets.",
            category = "Dairy & Fresh",
            unit = "500 ml",
            price = 345.0,
            mrp = 390.0,
            stockCount = 18,
            available = true,
            imageKey = "groceries"
        ),
        Product(
            id = "neem_turmeric_soap_3pk",
            name = "Herbal Neem & Tulsi Bath Soap",
            description = "Gentle antibacterial herbal soap bar value pack of 3.",
            category = "Personal Care",
            unit = "3 x 100 g",
            price = 99.0,
            mrp = 120.0,
            stockCount = 44,
            available = true,
            imageKey = "household"
        ),
        Product(
            id = "liquid_detergent_1l",
            name = "Fresh Wash Liquid Detergent",
            description = "Tough stain removal with gentle fabric care for bucket and machine wash.",
            category = "Household",
            unit = "1 L",
            price = 175.0,
            mrp = 210.0,
            stockCount = 22,
            available = true,
            imageKey = "household"
        )
    )
}
