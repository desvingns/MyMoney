package com.kshavrin.mymoney.core.ads.admob

import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.kshavrin.mymoney.core.ads.AdAvailability
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import javax.inject.Inject

class AdErrorMapper
    @Inject
    constructor() {
        fun map(error: LoadAdError): AdAvailability = map(error.code, error.message)

        fun map(
            errorCode: Int,
            errorMessage: String? = null,
        ): AdAvailability =
            when (errorCode) {
                AdRequest.ERROR_CODE_NO_FILL -> AdAvailability.NoFill
                AdRequest.ERROR_CODE_NETWORK_ERROR -> AdAvailability.Offline
                else -> {
                    IllegalStateException(
                        "Unexpected AdMob rewarded error code=$errorCode message=${errorMessage.orEmpty()}",
                    ).reportToSentry()
                    AdAvailability.NoFill
                }
            }

        fun isNoFill(errorCode: Int): Boolean = errorCode == AdRequest.ERROR_CODE_NO_FILL
    }
