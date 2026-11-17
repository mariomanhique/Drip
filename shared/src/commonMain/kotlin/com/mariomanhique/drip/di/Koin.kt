package com.mariomanhique.drip.di

import com.mariomanhique.drip.auth.AuthService
import com.mariomanhique.drip.data.DripRepository
import com.mariomanhique.drip.ui.DripViewModel
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val dataModule = module {
    single { DripRepository() }
}

val viewModelModule = module {
    viewModel { DripViewModel(get(), get()) }
}

val appModules = listOf(dataModule, viewModelModule)

/** [auth] is the platform AuthService; Android registers its own via [config]. */
fun initKoin(auth: AuthService? = null, config: KoinAppDeclaration? = null): KoinApplication = startKoin {
    config?.invoke(this)
    if (auth != null) modules(module { single<AuthService> { auth } })
    modules(appModules)
}
