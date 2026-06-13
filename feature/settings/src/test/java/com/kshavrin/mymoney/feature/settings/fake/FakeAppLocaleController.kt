package com.kshavrin.mymoney.feature.settings.fake

import com.kshavrin.mymoney.feature.settings.language.AppLocaleController

class FakeAppLocaleController : AppLocaleController {
    var applyCount: Int = 0
        private set

    var lastAppliedTag: String? = null
        private set

    private var captured: Boolean = false

    override fun apply(languageTag: String?) {
        applyCount += 1
        lastAppliedTag = languageTag
        captured = true
    }

    fun wasApplied(): Boolean = captured
}
