package com.kshavrin.mymoney

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.kshavrin.mymoney.core.ui.feedback.LocalHapticPlayer
import com.kshavrin.mymoney.core.ui.feedback.LocalSoundPlayer
import com.kshavrin.mymoney.core.ui.haptic.HapticKind
import com.kshavrin.mymoney.core.ui.haptic.HapticPlayer
import com.kshavrin.mymoney.core.ui.restart.EXTRA_RESET_HAD_FAILURES
import com.kshavrin.mymoney.core.ui.sound.SoundKey
import com.kshavrin.mymoney.core.ui.sound.SoundPlayer
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.core.ui.window.LocalSecureWindowController
import com.kshavrin.mymoney.core.ui.window.SecureWindowController
import com.kshavrin.mymoney.core.ui.window.SecureWindowSource
import com.kshavrin.mymoney.feature.lockscreen.overlay.LockController
import com.kshavrin.mymoney.feature.lockscreen.overlay.LockOverlay
import com.kshavrin.mymoney.navigation.MyMoneyNavHost
import com.kshavrin.mymoney.navigation.ShortcutDestination
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val themeViewModel: AppThemeViewModel by viewModels()

    @Inject
    lateinit var lockController: LockController

    @Inject
    lateinit var soundPlayer: Lazy<SoundPlayer>

    @Inject
    lateinit var hapticPlayer: Lazy<HapticPlayer>

    private val lazySoundPlayer: SoundPlayer by lazy { LazySoundPlayer(soundPlayer) }
    private val lazyHapticPlayer: HapticPlayer by lazy { LazyHapticPlayer(hapticPlayer) }
    private val initialWindowSecurityApplied = AtomicBoolean(false)
    private lateinit var secureWindowController: SecureWindowController

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        secureWindowController = SecureWindowController(window)
        splashScreen.setKeepOnScreenCondition {
            !lockController.isResolved.value || !initialWindowSecurityApplied.get()
        }
        lockController.observeProcessLifecycle()
        lifecycleScope.launch {
            lockController.appContentSecurityState.collect { securityState ->
                if (securityState == null) return@collect
                secureWindowController.setSecure(
                    SecureWindowSource.AppContent,
                    securityState.shouldSecure,
                )
                initialWindowSecurityApplied.set(true)
            }
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            showFactoryResetFailureIfNeeded()
        }
        val shortcutDestination = resolveShortcutDestination(intent)
        setContent {
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val locked by lockController.shouldShowLock.collectAsStateWithLifecycle()
            MyMoneyTheme(themeMode = themeMode) {
                CompositionLocalProvider(
                    LocalSoundPlayer provides lazySoundPlayer,
                    LocalHapticPlayer provides lazyHapticPlayer,
                    LocalSecureWindowController provides secureWindowController,
                ) {
                    Box {
                        MyMoneyNavHost(shortcutDestination = shortcutDestination)
                        if (locked) {
                            LockOverlay(onUnlocked = lockController::markUnlocked)
                        }
                    }
                }
            }
        }
    }

    private fun showFactoryResetFailureIfNeeded() {
        if (!intent.getBooleanExtra(EXTRA_RESET_HAD_FAILURES, false)) return
        intent.removeExtra(EXTRA_RESET_HAD_FAILURES)
        Toast
            .makeText(
                this,
                com.kshavrin.mymoney.feature.settings.R.string.factory_reset_error,
                Toast.LENGTH_LONG,
            ).show()
    }

    private fun resolveShortcutDestination(intent: Intent?): ShortcutDestination? =
        when (intent?.getStringExtra(EXTRA_SHORTCUT_ID)) {
            SHORTCUT_ADD_EXPENSE -> ShortcutDestination.AddExpense
            SHORTCUT_ADD_INCOME -> ShortcutDestination.AddIncome
            SHORTCUT_TRANSFER -> ShortcutDestination.Transfer
            else -> null
        }

    private companion object {
        const val EXTRA_SHORTCUT_ID = "shortcut_id"
        const val SHORTCUT_ADD_EXPENSE = "add_expense"
        const val SHORTCUT_ADD_INCOME = "add_income"
        const val SHORTCUT_TRANSFER = "transfer"
    }
}

internal class LazySoundPlayer(
    private val delegate: Lazy<SoundPlayer>,
) : SoundPlayer {
    override fun play(key: SoundKey) = delegate.get().play(key)
}

internal class LazyHapticPlayer(
    private val delegate: Lazy<HapticPlayer>,
) : HapticPlayer {
    override fun fire(kind: HapticKind) = delegate.get().fire(kind)
}
