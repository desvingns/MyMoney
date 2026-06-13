package com.kshavrin.mymoney.core.ui.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.kshavrin.mymoney.core.common.di.DefaultDispatcher
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

enum class HapticKind {
    SOFT,
    MEDIUM,
    HEAVY,
    WARNING,
    SUCCESS_SHIMMER,
}

interface HapticPlayer {
    fun fire(kind: HapticKind)
}

@Singleton
class HapticPlayerImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
        appSettingsRepository: AppSettingsRepository,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) : HapticPlayer {
        private val vibrator: Vibrator? = resolveVibrator(context)

        private val hapticEnabled = MutableStateFlow(true)
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)

        init {
            appSettingsRepository.settings
                .onEach { hapticEnabled.value = it.hapticEnabled }
                .launchIn(scope)
        }

        override fun fire(kind: HapticKind) {
            if (!hapticEnabled.value) return
            val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
            composition(kind)?.let(vibrator::vibrate)
        }

        private fun composition(kind: HapticKind): VibrationEffect? {
            val builder = VibrationEffect.startComposition()
            return when (kind) {
                HapticKind.SOFT ->
                    builder.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, SOFT_SCALE).compose()
                HapticKind.MEDIUM ->
                    builder.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, FULL_SCALE).compose()
                HapticKind.HEAVY ->
                    builder.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, FULL_SCALE).compose()
                HapticKind.WARNING ->
                    builder
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, FULL_SCALE)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, FULL_SCALE, GAP_MILLIS)
                        .compose()
                HapticKind.SUCCESS_SHIMMER -> shimmer(builder)
            }
        }

        // TDD §6.9 names a PRIMITIVE_SHIMMER effect, but no such constant exists in the platform.
        // PRIMITIVE_SPIN is the closest celebratory primitive (API 33+); API 31–32 falls back to TICK×3.
        private fun shimmer(builder: VibrationEffect.Composition): VibrationEffect =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                builder.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, FULL_SCALE).compose()
            } else {
                builder
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, FULL_SCALE)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, FULL_SCALE, GAP_MILLIS)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, FULL_SCALE, GAP_MILLIS)
                    .compose()
            }

        private fun resolveVibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

        private companion object {
            const val SOFT_SCALE = 0.5f
            const val FULL_SCALE = 1.0f
            const val GAP_MILLIS = 40
        }
    }
