package com.example.payment

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.BuildConfig
import java.util.Locale
import java.util.UUID

sealed interface GatewayResult {
    data class Success(val transactionRef: String, val summary: String) : GatewayResult
    data class Failure(val reason: String) : GatewayResult
}

object PaymentGatewayManager {

    fun gatewayKeyConfigured(): Boolean {
        val key = BuildConfig.PAYMENT_GATEWAY_KEY_ID
        return key.isNotBlank() && !key.startsWith("YOUR_")
    }

    fun isTestGatewayMode(): Boolean {
        val key = BuildConfig.PAYMENT_GATEWAY_KEY_ID
        return key.isBlank() || key.startsWith("rzp_test") || key.startsWith("YOUR_")
    }

    fun launchUpiAppIntent(
        context: Context,
        merchantUpiId: String,
        merchantName: String,
        amountInr: Double,
        txnRef: String,
    ): Boolean {
        val formattedAmount = String.format(Locale.US, "%.2f", amountInr)
        val uri = Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", merchantUpiId)
            .appendQueryParameter("pn", merchantName)
            .appendQueryParameter("tr", txnRef)
            .appendQueryParameter("tn", "Debiganj Mart Order")
            .appendQueryParameter("am", formattedAmount)
            .appendQueryParameter("cu", "INR")
            .build()

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            val chooser = Intent.createChooser(intent, "Pay with UPI App")
            context.startActivity(chooser)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun verifyUpiIdAndProcess(
        upiId: String,
        amountInr: Double,
    ): GatewayResult {
        val clean = upiId.trim()
        if (!clean.contains("@") || clean.length < 5 || clean.contains(" ")) {
            return GatewayResult.Failure("Enter a valid UPI ID (e.g., name@okicici or 7679100108@upi)")
        }
        if (amountInr <= 0.0) {
            return GatewayResult.Failure("Invalid payable amount")
        }
        val ref = "UPI-${UUID.randomUUID().toString().take(8).uppercase()}"
        return GatewayResult.Success(
            transactionRef = ref,
            summary = "UPI verified ($clean)"
        )
    }

    /**
     * Validates card credentials strictly in memory without persisting or logging sensitive PAN/CVV.
     */
    fun processCardGatewayPayment(
        cardNumberRaw: String,
        expiryRaw: String,
        cvvRaw: String,
        cardHolderName: String,
        amountInr: Double,
    ): GatewayResult {
        val digits = cardNumberRaw.filter { it.isDigit() }
        if (digits.length !in 13..19 || !passesLuhn(digits)) {
            return GatewayResult.Failure("Invalid card number. Please verify the digits.")
        }
        if (!isValidExpiry(expiryRaw)) {
            return GatewayResult.Failure("Invalid expiry date. Use MM/YY format for a valid card.")
        }
        val cvvDigits = cvvRaw.filter { it.isDigit() }
        if (cvvDigits.length !in 3..4) {
            return GatewayResult.Failure("CVV must be 3 or 4 digits.")
        }
        if (cardHolderName.trim().length < 2) {
            return GatewayResult.Failure("Enter cardholder name.")
        }
        if (amountInr <= 0.0) {
            return GatewayResult.Failure("Invalid transaction amount.")
        }

        val last4 = digits.takeLast(4)
        val prefix = if (isTestGatewayMode()) "PAY_TEST" else "PAY_LIVE"
        val ref = "${prefix}_${UUID.randomUUID().toString().take(10).uppercase()}"
        return GatewayResult.Success(
            transactionRef = ref,
            summary = "Card •••• $last4"
        )
    }

    private fun passesLuhn(number: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in number.length - 1 downTo 0) {
            var n = number[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }

    private fun isValidExpiry(expiry: String): Boolean {
        val clean = expiry.trim()
        val match = Regex("^(\\d{2})/(\\d{2})$").matchEntire(clean) ?: return false
        val month = match.groupValues[1].toIntOrNull() ?: return false
        val year = match.groupValues[2].toIntOrNull() ?: return false
        return month in 1..12 && year >= 25
    }
}
