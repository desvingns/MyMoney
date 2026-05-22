package com.kshavrin.mymoney

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.navigation.Destinations
import com.kshavrin.mymoney.navigation.MyMoneyNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val shortcutDestination = resolveShortcutDestination(intent)
        setContent {
            MyMoneyTheme {
                MyMoneyNavHost(shortcutDestination = shortcutDestination)
            }
        }
    }

    private fun resolveShortcutDestination(intent: Intent?): String? =
        when (intent?.getStringExtra(EXTRA_SHORTCUT_ID)) {
            SHORTCUT_ADD_EXPENSE -> Destinations.ADD_EXPENSE
            SHORTCUT_ADD_INCOME -> Destinations.ADD_INCOME
            SHORTCUT_TRANSFER -> Destinations.TRANSFER
            else -> null
        }

    private companion object {
        const val EXTRA_SHORTCUT_ID = "shortcut_id"
        const val SHORTCUT_ADD_EXPENSE = "add_expense"
        const val SHORTCUT_ADD_INCOME = "add_income"
        const val SHORTCUT_TRANSFER = "transfer"
    }
}
