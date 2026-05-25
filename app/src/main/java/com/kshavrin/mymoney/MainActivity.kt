package com.kshavrin.mymoney

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.lockscreen.overlay.LockController
import com.kshavrin.mymoney.feature.lockscreen.overlay.LockOverlay
import com.kshavrin.mymoney.navigation.Destinations
import com.kshavrin.mymoney.navigation.MyMoneyNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val themeViewModel: AppThemeViewModel by viewModels()

    @Inject
    lateinit var lockController: LockController

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        lockController.observeProcessLifecycle()
        enableEdgeToEdge()
        val shortcutDestination = resolveShortcutDestination(intent)
        setContent {
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val locked by lockController.shouldShowLock.collectAsStateWithLifecycle()
            MyMoneyTheme(themeMode = themeMode) {
                Box {
                    MyMoneyNavHost(shortcutDestination = shortcutDestination)
                    if (locked) {
                        LockOverlay(onUnlocked = lockController::markUnlocked)
                    }
                }
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
