package com.kshavrin.mymoney.core.ui.di

import com.kshavrin.mymoney.core.ui.haptic.HapticPlayer
import com.kshavrin.mymoney.core.ui.haptic.HapticPlayerImpl
import com.kshavrin.mymoney.core.ui.sound.SoundPlayer
import com.kshavrin.mymoney.core.ui.sound.SoundPoolImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AestheticsModule {
    @Binds
    @Singleton
    abstract fun bindSoundPlayer(impl: SoundPoolImpl): SoundPlayer

    @Binds
    @Singleton
    abstract fun bindHapticPlayer(impl: HapticPlayerImpl): HapticPlayer
}
