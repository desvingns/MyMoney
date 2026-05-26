package com.kshavrin.mymoney.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

internal const val TARGET_PACKAGE = "com.kshavrin.mymoney"
private const val DASHBOARD_TITLE = "MyMoney"
private const val TRANSACTIONS_TITLE = "Transactions"
private const val TIMEOUT_MILLIS = 10_000L
private const val DASHBOARD_PROBE_MILLIS = 1_000L
private val amountPattern = Pattern.compile(".*\\d+(?:[.,]\\d+)?\\s*.*")

internal fun MacrobenchmarkScope.launchDashboard() {
    startActivityAndWait()
    if (device.wait(Until.hasObject(By.text(DASHBOARD_TITLE)), DASHBOARD_PROBE_MILLIS)) {
        return
    }

    check(device.wait(Until.hasObject(By.text("Next")), TIMEOUT_MILLIS)) {
        "Neither dashboard nor onboarding appeared"
    }
    repeat(3) {
        val next = checkNotNull(device.wait(Until.findObject(By.text("Next")), TIMEOUT_MILLIS)) {
            "Onboarding Next button was not found"
        }
        next.click()
        device.waitForIdle()
    }
    val getStarted = checkNotNull(
        device.wait(Until.findObject(By.text("Get started")), TIMEOUT_MILLIS),
    ) {
        "Onboarding completion button was not found"
    }
    getStarted.click()
    awaitDashboard()
}

internal fun MacrobenchmarkScope.awaitDashboard() {
    check(device.wait(Until.hasObject(By.text(DASHBOARD_TITLE)), TIMEOUT_MILLIS)) {
        "Dashboard did not render"
    }
}

internal fun MacrobenchmarkScope.openTransactionsList() {
    val balance = checkNotNull(
        device.wait(Until.findObject(By.text(amountPattern)), TIMEOUT_MILLIS),
    ) {
        "Dashboard balance was not rendered"
    }
    balance.click()
    check(device.wait(Until.hasObject(By.text(TRANSACTIONS_TITLE)), TIMEOUT_MILLIS)) {
        "Transactions list did not render"
    }
}

internal fun MacrobenchmarkScope.scrollTransactionsList() {
    val centerX = device.displayWidth / 2
    val startY = device.displayHeight * 3 / 4
    val endY = device.displayHeight / 3
    repeat(4) {
        device.swipe(centerX, startY, centerX, endY, 12)
        device.waitForIdle()
    }
}
