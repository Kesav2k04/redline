package dev.kesav.redline

import android.app.Activity
import android.app.Application
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Entitlement state, read from RevenueCat rather than kept as a local boolean.
 *
 * The identifier below is the single place the paywall and the dashboard have to agree,
 * which is why it is named here and nowhere else.
 */
object Billing {

    const val ENTITLEMENT = "full_report"

    private const val TAG = "Billing"

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _offering = MutableStateFlow<Offering?>(null)
    val offering: StateFlow<Offering?> = _offering.asStateFlow()

    /** False when no key is configured, so a clean clone still builds and runs. */
    var configured: Boolean = false
        private set

    fun start(app: Application, apiKey: String) {
        if (apiKey.isBlank()) {
            Log.i(TAG, "No RevenueCat key configured; the paywall will stay locked.")
            return
        }

        Purchases.logLevel = LogLevel.WARN
        Purchases.configure(PurchasesConfiguration.Builder(app, apiKey).build())
        configured = true
    }

    /**
     * Reads entitlement state before anything is drawn, so the report is never shown
     * and then snatched back.
     */
    suspend fun refresh() {
        if (!configured) return
        runCatching { Purchases.sharedInstance.awaitCustomerInfo() }
            .onSuccess { apply(it) }
            .onFailure { Log.w(TAG, "Could not read entitlements: ${it.message}") }
    }

    suspend fun loadOffering() {
        if (!configured) return
        runCatching { Purchases.sharedInstance.awaitOfferings() }
            .onSuccess { _offering.value = it.current }
            .onFailure { Log.w(TAG, "Could not load offerings: ${it.message}") }
    }

    /** Returns null when the buyer cancelled, which is not an error worth showing. */
    suspend fun purchase(activity: Activity, pkg: Package): String? {
        if (!configured) return "Purchases are not configured in this build."

        return runCatching {
            val params = PurchaseParams.Builder(activity, pkg).build()
            apply(Purchases.sharedInstance.awaitPurchase(params).customerInfo)
            null
        }.getOrElse { error ->
            if (error is com.revenuecat.purchases.PurchasesTransactionException && error.userCancelled) {
                null
            } else {
                error.message ?: "The purchase did not complete."
            }
        }
    }

    /**
     * Reinstalling loses local state but not the purchase. Almost nobody writes this
     * path, and it is the one a buyer needs most.
     */
    suspend fun restore(): String? {
        if (!configured) return "Purchases are not configured in this build."

        return runCatching {
            apply(Purchases.sharedInstance.awaitRestore())
            if (_unlocked.value) null else "Nothing to restore on this account."
        }.getOrElse { it.message ?: "Restore did not complete." }
    }

    private fun apply(info: CustomerInfo) {
        _unlocked.value = info.entitlements[ENTITLEMENT]?.isActive == true
    }
}
