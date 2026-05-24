package com.kshavrin.mymoney.feature.settings.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

interface AppLocaleController {
    fun apply(languageTag: String?)
}

@Singleton
class AppCompatLocaleController @Inject constructor() : AppLocaleController {
    override fun apply(languageTag: String?) {
        val locales = if (languageTag.isNullOrEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppLocaleControllerModule {

    @Binds
    @Singleton
    abstract fun bindAppLocaleController(impl: AppCompatLocaleController): AppLocaleController
}
