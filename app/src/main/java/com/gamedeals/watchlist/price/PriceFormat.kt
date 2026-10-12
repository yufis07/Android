package com.gamedeals.watchlist.price

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object PriceFormat {
    private val ISO_CODE = Regex("[A-Z]{3}")

    /** Pulls a currency symbol such as "$", "€", "R$" or "zł" out of a displayed price. */
    fun symbolFrom(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val s = text.replace(Regex("""[\d.,\s '-]"""), "").trim()
        return s.takeIf { it.isNotEmpty() && it.length <= 4 && !it.equals("free", ignoreCase = true) }
    }

    fun format(value: Double?, currency: String?): String {
        if (value == null) return "—"
        if (value == 0.0) return "Free"
        if (currency != null && ISO_CODE.matches(currency)) {
            runCatching {
                val nf = NumberFormat.getCurrencyInstance(Locale.getDefault())
                nf.currency = Currency.getInstance(currency)
                return nf.format(value)
            }
        }
        val num = String.format(Locale.getDefault(), "%.2f", value)
        return if (currency.isNullOrBlank()) num else currency + num
    }

    /** Value for an editable text field, e.g. 15.0 -> "15", 14.99 -> "14.99". */
    fun plain(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value)
}
