package com.mariomanhique.drip

import android.app.Application
import com.mariomanhique.drip.di.initKoin
import com.mariomanhique.drip.auth.AuthService
import com.mariomanhique.drip.data.Backend
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext

class DripApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@DripApplication)
            modules(module {
                single { AndroidAuthService(androidContext()) } bind AuthService::class
                single { AndroidBackend(androidContext()) } bind Backend::class
            })
        }
    }
}
