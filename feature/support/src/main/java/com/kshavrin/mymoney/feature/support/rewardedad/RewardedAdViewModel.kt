package com.kshavrin.mymoney.feature.support.rewardedad

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.ads.AdAvailability
import com.kshavrin.mymoney.core.ads.AdGateway
import com.kshavrin.mymoney.core.ads.AdLoadResult
import com.kshavrin.mymoney.core.ads.AdShowResult
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.domain.ads.AdRewardState
import com.kshavrin.mymoney.core.domain.ads.ConfirmationOutcome
import com.kshavrin.mymoney.core.domain.usecase.ObserveAdRewardStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class RewardedAdViewModel
    @Inject
    constructor(
        private val observeAdRewardState: ObserveAdRewardStateUseCase,
        private val adGateway: AdGateway,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _state = MutableStateFlow(RewardedAdState())
        val state: StateFlow<RewardedAdState> = _state.asStateFlow()

        private var loadJob: Job? = null
        private var watchJob: Job? = null

        fun onBlockShown(activity: Activity) {
            if (loadJob?.isActive == true || watchJob?.isActive == true) return
            loadBlock(activity)
        }

        fun onRetry(activity: Activity) {
            if (loadJob?.isActive == true || watchJob?.isActive == true) return
            loadBlock(activity)
        }

        fun onWatchAd(activity: Activity) {
            if (watchJob?.isActive == true || loadJob?.isActive == true) return
            if (_state.value.status != RewardedAdStatus.Ready) return
            watchJob =
                viewModelScope.launch {
                    try {
                        when (val result = withContext(ioDispatcher) { adGateway.showRewarded(activity) }) {
                            is AdShowResult.Dismissed ->
                                if (result.rewardEarned) {
                                    confirmReward()
                                } else {
                                    loadAvailability(activity)
                                }

                            AdShowResult.Unauthenticated ->
                                _state.value = _state.value.copy(status = RewardedAdStatus.Unauthenticated)

                            is AdShowResult.Unavailable ->
                                applyUnavailable(result.availability)
                        }
                    } catch (throwable: Throwable) {
                        if (throwable is CancellationException) throw throwable
                        throwable.reportToSentry()
                        _state.value = _state.value.copy(status = RewardedAdStatus.NoFill)
                    }
                }
        }

        private fun loadBlock(activity: Activity) {
            loadJob =
                viewModelScope.launch {
                    _state.value = _state.value.copy(status = RewardedAdStatus.Loading)
                    val rewardState = refreshRewardState()
                    if (rewardState == null) {
                        if (_state.value.status == RewardedAdStatus.Loading) {
                            _state.value = _state.value.copy(status = RewardedAdStatus.Unavailable)
                        }
                        return@launch
                    }
                    _state.value = _state.value.copy(reward = rewardState.toRewardProgress())
                    loadAvailability(activity)
                }
        }

        private suspend fun refreshRewardState(): AdRewardState? {
            val result = withContext(ioDispatcher) { observeAdRewardState.refresh() }
            return result.fold(
                onSuccess = { it },
                onFailure = { throwable ->
                    when ((throwable as? SyncException)?.syncError) {
                        SyncError.Auth ->
                            _state.value = _state.value.copy(status = RewardedAdStatus.Unauthenticated, reward = null)

                        SyncError.Network ->
                            _state.value = _state.value.copy(status = RewardedAdStatus.Offline)

                        else -> {
                            throwable.reportToSentry()
                            _state.value = _state.value.copy(status = RewardedAdStatus.Unavailable)
                        }
                    }
                    null
                },
            )
        }

        private suspend fun loadAvailability(activity: Activity) {
            val result =
                withTimeoutOrNull(AD_LOAD_TIMEOUT_MILLIS) {
                    withContext(ioDispatcher) {
                        adGateway.ensureConsent(activity)
                        adGateway.loadRewarded()
                    }
                }
            when (result) {
                null -> _state.value = _state.value.copy(status = RewardedAdStatus.NoFill)
                AdLoadResult.Loaded -> _state.value = _state.value.copy(status = RewardedAdStatus.Ready)
                AdLoadResult.Unauthenticated ->
                    _state.value = _state.value.copy(status = RewardedAdStatus.Unauthenticated, reward = null)

                is AdLoadResult.Unavailable -> applyUnavailable(result.availability)
            }
        }

        private suspend fun confirmReward() {
            val previous = observeAdRewardState.state.value
            if (previous == null) {
                _state.value = _state.value.copy(status = RewardedAdStatus.Unavailable)
                return
            }
            _state.value = _state.value.copy(status = RewardedAdStatus.AwaitingConfirmation)
            repeat(CONFIRMATION_MAX_RETRIES) {
                when (val outcome = withContext(ioDispatcher) { observeAdRewardState.awaitConfirmation(previous) }) {
                    is ConfirmationOutcome.ProgressIncreased -> {
                        _state.value =
                            _state.value.copy(
                                status = RewardedAdStatus.Ready,
                                reward = outcome.state.toRewardProgress(),
                            )
                        return
                    }

                    is ConfirmationOutcome.PlusGranted -> {
                        _state.value =
                            _state.value.copy(
                                status = RewardedAdStatus.Ready,
                                reward = outcome.state.toRewardProgress(),
                            )
                        return
                    }

                    ConfirmationOutcome.PendingConfirmation -> Unit
                }
            }
            _state.value = _state.value.copy(status = RewardedAdStatus.ConfirmationTimeout)
        }

        private fun applyUnavailable(availability: AdAvailability) {
            _state.value =
                _state.value.copy(
                    status =
                        when (availability) {
                            AdAvailability.NoFill -> RewardedAdStatus.NoFill
                            AdAvailability.RegionUnavailable -> RewardedAdStatus.RegionUnavailable
                            AdAvailability.Offline -> RewardedAdStatus.Offline
                            AdAvailability.Available -> RewardedAdStatus.Ready
                            AdAvailability.Loading -> RewardedAdStatus.NoFill
                            AdAvailability.ConsentRequired,
                            AdAvailability.Disabled,
                            -> RewardedAdStatus.Unavailable
                        },
                )
        }
    }

private fun AdRewardState.toRewardProgress(): RewardProgress =
    RewardProgress(
        progress = progress,
        required = required,
        plusActive = plusActive,
    )

private const val AD_LOAD_TIMEOUT_MILLIS = 25_000L
private const val CONFIRMATION_MAX_RETRIES = 10
