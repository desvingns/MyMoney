package com.kshavrin.mymoney.macrobenchmark

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

internal const val TARGET_PACKAGE = "com.kshavrin.mymoney"
private const val TIMEOUT_MILLIS = 10_000L
private const val DASHBOARD_PROBE_MILLIS = 1_000L
private const val SUMMARY_ENTRY_Y_FRACTION = 3
private val legacyBalancePattern = Pattern.compile("^(Balance|Баланс)(?:\\s|$).*")
private val freeBalancePattern =
    Pattern.compile(
        "^(Free balance|Свободный баланс)$",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE,
    )
private val dashboardEmptyPattern =
    Pattern.compile(
        "^(No expenses this period|За период нет расходов)$",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE,
    )
private val dashboardStatPattern =
    Pattern.compile(
        "^.*(Income|Expenses|Доходы|Расходы)\\s+.*\\d.*$",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE,
    )
private val readyBalancePattern =
    Pattern.compile("^-?\\d[\\d\\s.,]*(?:\\s*\\p{Sc}|\\s+(?:USD|EUR|RUB))$")
private val getStartedPattern = Pattern.compile("^(Get started|Начать)$")
private val nextPattern = Pattern.compile("^(Next|Далее)$")
private val skipPattern = Pattern.compile("^(Skip|Пропустить)$")
private val transactionsSurfacePattern =
    Pattern.compile(
        "^(Transactions|Транзакции|All operations|Все операции|No operations for the period)$",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE,
    )

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
    awaitDashboard()
    device.click(device.displayWidth / 2, device.displayHeight / SUMMARY_ENTRY_Y_FRACTION)
    check(device.wait(Until.hasObject(By.text(transactionsSurfacePattern)), TIMEOUT_MILLIS)) {
        "Transactions surface did not render"
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
    repeat(5) {
        if (waitForDashboard(DASHBOARD_PROBE_MILLIS)) return

        val getStarted = device.wait(Until.findObject(By.text(getStartedPattern)), DASHBOARD_PROBE_MILLIS)
        if (getStarted != null) {
            getStarted.click()
            device.waitForIdle()
            if (waitForDashboard(TIMEOUT_MILLIS)) return
        }

        val skip = device.wait(Until.findObject(By.text(skipPattern)), DASHBOARD_PROBE_MILLIS)
        if (skip != null) {
            skip.click()
            device.waitForIdle()
            if (waitForDashboard(TIMEOUT_MILLIS)) return
        }

        val next = device.wait(Until.findObject(By.text(nextPattern)), TIMEOUT_MILLIS)
        if (next == null) {
            if (waitForDashboard(DASHBOARD_PROBE_MILLIS)) return
            error("Onboarding action button was not found")
        }
        next.click()
        device.waitForIdle()
    }
    check(waitForDashboard(TIMEOUT_MILLIS)) {
        "Onboarding did not reach dashboard"
    }
}

private fun MacrobenchmarkScope.waitForDashboard(timeoutMillis: Long): Boolean =
    waitUntil(timeoutMillis) { hasDashboardSignal() }

private fun MacrobenchmarkScope.hasDashboardSignal(): Boolean =
    device.hasObject(By.text(legacyBalancePattern)) ||
        device.hasObject(By.text(freeBalancePattern)) ||
        device.hasObject(By.text(readyBalancePattern)) ||
        device.hasObject(By.text(dashboardStatPattern)) ||
        device.hasObject(By.text(dashboardEmptyPattern))

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
