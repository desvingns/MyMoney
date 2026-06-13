package com.kshavrin.mymoney.macrobenchmark

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

internal const val TARGET_PACKAGE = "com.kshavrin.mymoney"
private const val DASHBOARD_TITLE = "MyMoney"
private const val TIMEOUT_MILLIS = 10_000L
private const val DASHBOARD_PROBE_MILLIS = 1_000L
private val balancePattern = Pattern.compile("^(Balance|Баланс)(?:\\s|$).*")
private val readyBalancePattern = Pattern.compile("^(Balance|Баланс)(?:\\s|$).*\\d.*")
private val getStartedPattern = Pattern.compile("^(Get started|Начать)$")
private val nextPattern = Pattern.compile("^(Next|Далее)$")
private val skipPattern = Pattern.compile("^(Skip|Пропустить)$")
private val transactionsTitlePattern = Pattern.compile("^(Transactions|Транзакции)$")

internal fun MacrobenchmarkScope.launchDashboard() {
    startActivityAndWait()
    when (awaitLaunchScreen()) {
        LaunchScreen.Dashboard -> return
        LaunchScreen.Onboarding -> completeOnboarding()
        LaunchScreen.Missing -> error("Neither dashboard nor onboarding appeared")
    }
    awaitDashboard()
}

internal fun MacrobenchmarkScope.awaitDashboard() {
    check(waitForDashboard(TIMEOUT_MILLIS)) {
        "Dashboard did not render"
    }
}

internal fun MacrobenchmarkScope.openTransactionsList() {
    check(waitForReadyBalance(TIMEOUT_MILLIS)) {
        "Dashboard balance was not rendered"
    }
    val balance =
        checkNotNull(
            device.findObject(By.text(readyBalancePattern)),
        ) {
            "Dashboard balance was not rendered"
        }
    balance.click()
    check(device.wait(Until.hasObject(By.text(transactionsTitlePattern)), TIMEOUT_MILLIS)) {
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

private fun MacrobenchmarkScope.awaitLaunchScreen(): LaunchScreen {
    var launchScreen = LaunchScreen.Missing
    val appeared =
        waitUntil(TIMEOUT_MILLIS) {
            when {
                hasDashboardSignal() -> {
                    launchScreen = LaunchScreen.Dashboard
                    true
                }
                hasOnboardingSignal() -> {
                    launchScreen = LaunchScreen.Onboarding
                    true
                }
                else -> false
            }
        }
    return if (appeared) launchScreen else LaunchScreen.Missing
}

private fun MacrobenchmarkScope.completeOnboarding() {
    val skip = device.wait(Until.findObject(By.text(skipPattern)), DASHBOARD_PROBE_MILLIS)
    if (skip != null) {
        skip.click()
        device.waitForIdle()
        if (waitForDashboard(TIMEOUT_MILLIS)) return
    }

    repeat(3) {
        val next =
            checkNotNull(device.wait(Until.findObject(By.text(nextPattern)), TIMEOUT_MILLIS)) {
                "Onboarding Next button was not found"
            }
        next.click()
        device.waitForIdle()
    }
    val getStarted =
        checkNotNull(
            device.wait(Until.findObject(By.text(getStartedPattern)), TIMEOUT_MILLIS),
        ) {
            "Onboarding completion button was not found"
        }
    getStarted.click()
}

private fun MacrobenchmarkScope.waitForDashboard(timeoutMillis: Long): Boolean =
    waitUntil(timeoutMillis) { hasDashboardSignal() }

private fun MacrobenchmarkScope.waitForReadyBalance(timeoutMillis: Long): Boolean =
    waitUntil(timeoutMillis) { device.hasObject(By.text(readyBalancePattern)) }

private fun MacrobenchmarkScope.hasDashboardSignal(): Boolean =
    device.hasObject(By.text(DASHBOARD_TITLE)) || device.hasObject(By.text(balancePattern))

private fun MacrobenchmarkScope.hasOnboardingSignal(): Boolean =
    device.hasObject(By.text(nextPattern)) ||
        device.hasObject(By.text(skipPattern)) ||
        device.hasObject(By.text(getStartedPattern))

private fun waitUntil(
    timeoutMillis: Long,
    condition: () -> Boolean,
): Boolean {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    while (SystemClock.uptimeMillis() < deadline) {
        if (condition()) return true
        SystemClock.sleep(100)
    }
    return condition()
}

private enum class LaunchScreen {
    Dashboard,
    Onboarding,
    Missing,
}
