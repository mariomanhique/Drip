package com.mariomanhique.drip

import android.app.Application
import com.mariomanhique.drip.di.initKoin
import org.koin.android.ext.koin.androidContext

class DripApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@DripApplication) }
    }
}
