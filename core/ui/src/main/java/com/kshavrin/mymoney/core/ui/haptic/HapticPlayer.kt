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
            val vibrator = this.vibrator ?: return
            val selection =
                HapticEffectSelector.select(
                    sdkInt = Build.VERSION.SDK_INT,
                    hapticsEnabled = hapticEnabled.value,
                    hasVibrator = vibrator.hasVibrator(),
                    kind = kind,
                )
            toVibrationEffect(selection)?.let(vibrator::vibrate)
        }

        private fun toVibrationEffect(selection: HapticSelection): VibrationEffect? =
            when (selection) {
                HapticSelection.None -> null
                is HapticSelection.LegacyOneShot ->
                    VibrationEffect.createOneShot(selection.durationMillis, selection.amplitude)
                is HapticSelection.LegacyWaveform ->
                    VibrationEffect.createWaveform(
                        selection.timings.toLongArray(),
                        selection.amplitudes.toIntArray(),
                        NO_REPEAT,
                    )
                is HapticSelection.Composition -> compose(selection.steps)
                is HapticSelection.Celebratory -> compose(listOf(selection.step))
            }

        private fun compose(steps: List<HapticStep>): VibrationEffect? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
            val composition = VibrationEffect.startComposition()
            steps.forEach { step ->
                val primitive =
                    when (step.primitive) {
                        HapticPrimitive.CLICK -> VibrationEffect.Composition.PRIMITIVE_CLICK
                        HapticPrimitive.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK
                        HapticPrimitive.THUD -> VibrationEffect.Composition.PRIMITIVE_THUD
                        HapticPrimitive.SPIN ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                VibrationEffect.Composition.PRIMITIVE_SPIN
                            } else {
                                VibrationEffect.Composition.PRIMITIVE_TICK
                            }
                    }
                if (step.delayMillis > 0) {
                    composition.addPrimitive(primitive, step.scale, step.delayMillis)
                } else {
                    composition.addPrimitive(primitive, step.scale)
                }
            }
            return composition.compose()
        }

        private fun resolveVibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

        private companion object {
            const val NO_REPEAT = -1
        }
    }
