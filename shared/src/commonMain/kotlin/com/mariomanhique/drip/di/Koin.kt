package com.mariomanhique.drip.di

import com.mariomanhique.drip.data.DripRepository
import com.mariomanhique.drip.ui.DripViewModel
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val dataModule = module {
    single { DripRepository() }
}

val viewModelModule = module {
    viewModelOf(::DripViewModel)
}

val appModules = listOf(dataModule, viewModelModule)

fun initKoin(config: KoinAppDeclaration? = null): KoinApplication = startKoin {
    config?.invoke(this)
    modules(appModules)
}
