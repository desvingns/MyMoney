package com.kshavrin.mymoney.core.network.shared

import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton

interface AuthSessionLifecycle {
    fun addInvalidationListener(listener: () -> Unit)

    fun invalidate()
}

@Singleton
class DefaultAuthSessionLifecycle
    @Inject
    constructor() : AuthSessionLifecycle {
        private val listeners = CopyOnWriteArraySet<() -> Unit>()

        override fun addInvalidationListener(listener: () -> Unit) {
            listeners += listener
        }

        override fun invalidate() {
            listeners.forEach { it() }
        }
    }

object NoOpAuthSessionLifecycle : AuthSessionLifecycle {
    override fun addInvalidationListener(listener: () -> Unit) = Unit

    override fun invalidate() = Unit
}
