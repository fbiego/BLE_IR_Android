package com.fbiego.bleir.app

import android.content.Context
import android.content.SharedPreferences
import androidx.multidex.MultiDexApplication
import com.fbiego.bleir.BuildConfig
import timber.log.Timber

class MainApplication : MultiDexApplication() {

    companion object {
        const val PREF_ADDRESS = "PREF_ADDRESS"
        const val PREF_LAYOUT = "layout_file"
        lateinit var sharedPrefs: SharedPreferences
        const val DEFAULT_ADDRESS = "00:00:00:00:00:00"
    }

    override fun onCreate() {
        super.onCreate()

        sharedPrefs = getSharedPreferences(BuildConfig.APPLICATION_ID, MODE_PRIVATE)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}