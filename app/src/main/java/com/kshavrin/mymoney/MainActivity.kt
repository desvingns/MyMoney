package com.kshavrin.mymoney

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
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
import com.kshavrin.mymoney.core.ui.sound.SoundKey
import com.kshavrin.mymoney.core.ui.sound.SoundPlayer
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.lockscreen.overlay.LockController
import com.kshavrin.mymoney.feature.lockscreen.overlay.LockOverlay
import com.kshavrin.mymoney.navigation.Destinations
import com.kshavrin.mymoney.navigation.MyMoneyNavHost
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { !lockController.isResolved.value }
        lockController.observeProcessLifecycle()
        lifecycleScope.launch {
            lockController.biometricLockEnabled.collect { enabled ->
                if (enabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        enableEdgeToEdge()
        val shortcutDestination = resolveShortcutDestination(intent)
        setContent {
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val locked by lockController.shouldShowLock.collectAsStateWithLifecycle()
            MyMoneyTheme(themeMode = themeMode) {
                CompositionLocalProvider(
                    LocalSoundPlayer provides lazySoundPlayer,
                    LocalHapticPlayer provides lazyHapticPlayer,
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

private class LazySoundPlayer(
    private val delegate: Lazy<SoundPlayer>,
) : SoundPlayer {
    override fun play(key: SoundKey) = delegate.get().play(key)
}

private class LazyHapticPlayer(
    private val delegate: Lazy<HapticPlayer>,
) : HapticPlayer {
    override fun fire(kind: HapticKind) = delegate.get().fire(kind)
}
