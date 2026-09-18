package dev.kesav.redline

import android.app.Application

class RedlineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Billing.start(this, BuildConfig.REVENUECAT_API_KEY)
    }
}
