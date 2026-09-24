package dev.kesav.redline

import android.app.Activity
import android.app.Application
import android.provider.Settings
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import java.security.MessageDigest
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

    /**
     * A firmware bug shipped this same value on a batch of early devices, so it
     * identifies nothing and has to be refused.
     */
    private const val SHARED_BAD_ANDROID_ID = "9774d56d682e549c"

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _offering = MutableStateFlow<Offering?>(null)
    val offering: StateFlow<Offering?> = _offering.asStateFlow()

    /**
     * False until the first entitlement read has finished, whatever it found.
     *
     * Without this there is no way to tell "not entitled" from "not asked yet", and the
     * report would draw itself redacted with a buy button and then rearrange under the
     * reader a second later.
     */
    private val _known = MutableStateFlow(false)
    val known: StateFlow<Boolean> = _known.asStateFlow()

    /** False when no key is configured, so a clean clone still builds and runs. */
    var configured: Boolean = false
        private set

    fun start(app: Application, apiKey: String) {
        if (apiKey.isBlank()) {
            Log.i(TAG, "No RevenueCat key configured; the paywall will stay locked.")
            return
        }

        Purchases.logLevel = LogLevel.WARN

        val config = PurchasesConfiguration.Builder(app, apiKey)
            // Without this the SDK mints a fresh anonymous id on every install, and a
            // reinstall then has nothing to restore against.
            .appUserID(purchaseId(androidId(app)))
            // The app asks who you are nowhere else, so it should not start here.
            .automaticDeviceIdentifierCollectionEnabled(false)
            .build()

        Purchases.configure(config)
        // Entitlement changes the app did not ask for, a restore finishing late or a
        // refund, arrive here instead of waiting for the next launch.
        Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener(::apply)
        configured = true
    }

    private fun androidId(app: Application): String? =
        runCatching {
            Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()

    /**
     * Purchases are keyed to the device rather than to an account, because there are no
     * accounts and a restore has to survive an uninstall.
     *
     * `ANDROID_ID` is the only value with the right lifetime: it is scoped to the signing
     * key, it needs no permission, and it outlives the app's own storage. It is hashed
     * with a salt before it goes anywhere, so what reaches RevenueCat is a stable hash,
     * never the raw ID. Returning null hands the SDK back its anonymous behaviour, which
     * costs restore but never crashes.
     */
    internal fun purchaseId(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == SHARED_BAD_ANDROID_ID) return null

        val digest = MessageDigest.getInstance("SHA-256")
            .digest("redline:v1:$raw".toByteArray(Charsets.UTF_8))

        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    /**
     * Reads entitlement state. This is a network call, so it finishes after the first
     * frame is already on screen; [known] is what the UI waits on rather than guessing
     * from [unlocked] alone.
     */
    suspend fun refresh() {
        if (!configured) {
            _known.value = true
            return
        }
        runCatching { Purchases.sharedInstance.awaitCustomerInfo() }
            .onSuccess { apply(it) }
            .onFailure { Log.w(TAG, "Could not read entitlements: ${it.message}") }
        _known.value = true
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

            // awaitRestore replays purchase history from the store, and a store can
            // answer truthfully with nothing while the entitlement is already attached
            // to this user. Telling an entitled buyer they own nothing is the worst
            // outcome available here, so ask the other question before saying it.
            if (!_unlocked.value) {
                runCatching { Purchases.sharedInstance.awaitCustomerInfo() }.onSuccess(::apply)
            }

            if (_unlocked.value) null else "Nothing to restore on this account."
        }.getOrElse { it.message ?: "Restore did not complete." }
    }

    private fun apply(info: CustomerInfo) {
        _unlocked.value = info.entitlements[ENTITLEMENT]?.isActive == true
    }
}
