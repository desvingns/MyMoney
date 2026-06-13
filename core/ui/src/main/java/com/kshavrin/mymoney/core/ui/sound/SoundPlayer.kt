package com.kshavrin.mymoney.core.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.kshavrin.mymoney.core.common.di.DefaultDispatcher
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class SoundKey {
    KEYPAD_TAP,
    SAVE_OK,
    SWIPE,
    DELETE,
    MILESTONE,
    ERROR,
}

interface SoundPlayer {
    fun play(key: SoundKey)
}

@Singleton
class SoundPoolImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val appSettingsRepository: AppSettingsRepository,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) : SoundPlayer {
        // The six aesthetic clips ship via a content pipeline and are resolved by name
        // at runtime so the build stays green with zero committed assets. Expected raw
        // names: tap, kaching, swipe, pop, confetti, buzz — optionally under a sound-pack
        // prefix from Remote-Config `aesthetic_sound_pack` (default / minimal / none).
        private val packPrefix = ""

        private val soundPool: SoundPool =
            SoundPool
                .Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                ).build()

        // Populated asynchronously off the cold-start path. Until prefetch completes a
        // missing key simply degrades to silence (see play()).
        private val soundIds = ConcurrentHashMap<SoundKey, Int>()

        private val soundEnabled = MutableStateFlow(true)
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)

        init {
            appSettingsRepository.settings
                .onEach { soundEnabled.value = it.soundEnabled }
                .launchIn(scope)
            scope.launch { loadAll() }
        }

        override fun play(key: SoundKey) {
            if (!soundEnabled.value) return
            val id = soundIds[key] ?: return
            if (id == 0) return
            soundPool.play(id, VOLUME, VOLUME, PRIORITY, NO_LOOP, NORMAL_RATE)
        }

        private fun loadAll() {
            SoundKey.entries.forEach { key ->
                soundIds[key] = loadByName(assetNameFor(key))
            }
        }

        private fun loadByName(name: String): Int {
            val resId = context.resources.getIdentifier(packPrefix + name, "raw", context.packageName)
            return if (resId == 0) 0 else soundPool.load(context, resId, PRIORITY)
        }

        private fun assetNameFor(key: SoundKey): String =
            when (key) {
                SoundKey.KEYPAD_TAP -> "tap"
                SoundKey.SAVE_OK -> "kaching"
                SoundKey.SWIPE -> "swipe"
                SoundKey.DELETE -> "pop"
                SoundKey.MILESTONE -> "confetti"
                SoundKey.ERROR -> "buzz"
            }

        private companion object {
            const val MAX_STREAMS = 4
            const val PRIORITY = 1
            const val NO_LOOP = 0
            const val NORMAL_RATE = 1.0f
            const val VOLUME = 1.0f
        }
    }
