package com.kshavrin.mymoney.core.network

interface ConnectivityChecker {
    fun isOnline(): Boolean
}
