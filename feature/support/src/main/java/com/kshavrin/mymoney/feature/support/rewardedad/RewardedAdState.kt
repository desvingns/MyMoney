package com.kshavrin.mymoney.feature.support.rewardedad

data class RewardedAdState(
    val status: RewardedAdStatus = RewardedAdStatus.Loading,
    val reward: RewardProgress? = null,
)

data class RewardProgress(
    val progress: Int,
    val required: Int,
    val plusActive: Boolean,
)

sealed interface RewardedAdStatus {
    data object Loading : RewardedAdStatus

    data object Unauthenticated : RewardedAdStatus

    data object Ready : RewardedAdStatus

    data object NoFill : RewardedAdStatus

    data object RegionUnavailable : RewardedAdStatus

    data object Offline : RewardedAdStatus

    data object Unavailable : RewardedAdStatus

    data object AwaitingConfirmation : RewardedAdStatus

    data object ConfirmationTimeout : RewardedAdStatus
}
