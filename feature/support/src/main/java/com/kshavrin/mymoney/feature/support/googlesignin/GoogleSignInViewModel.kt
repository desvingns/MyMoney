package com.kshavrin.mymoney.feature.support.googlesignin

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.network.shared.GoogleIdTokenProvider
import com.kshavrin.mymoney.core.network.shared.SignInWithGoogleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class GoogleSignInStatus {
    Idle,
    Loading,
    Success,
    Error,
}

data class GoogleSignInUiState(
    val status: GoogleSignInStatus = GoogleSignInStatus.Idle,
)

@HiltViewModel
class GoogleSignInViewModel
    @Inject
    constructor(
        private val googleIdTokenProvider: GoogleIdTokenProvider,
        private val signInWithGoogle: SignInWithGoogleUseCase,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _state = MutableStateFlow(GoogleSignInUiState())
        val state: StateFlow<GoogleSignInUiState> = _state.asStateFlow()

        private var signInJob: Job? = null

        fun signIn(activity: Activity) {
            if (signInJob?.isActive == true) return
            signInJob =
                viewModelScope.launch {
                    _state.value = _state.value.copy(status = GoogleSignInStatus.Loading)
                    try {
                        val token =
                            withContext(ioDispatcher) { googleIdTokenProvider.fetchIdToken(activity) }
                                .getOrElse {
                                    _state.value = _state.value.copy(status = GoogleSignInStatus.Error)
                                    return@launch
                                }
                        val session =
                            withContext(ioDispatcher) {
                                signInWithGoogle(token.idToken, token.nonce)
                            }
                        _state.value =
                            _state.value.copy(
                                status =
                                    if (session.isSuccess) {
                                        GoogleSignInStatus.Success
                                    } else {
                                        session.exceptionOrNull()?.reportToSentry()
                                        GoogleSignInStatus.Error
                                    },
                            )
                    } catch (throwable: Throwable) {
                        if (throwable is CancellationException) throw throwable
                        throwable.reportToSentry()
                        _state.value = _state.value.copy(status = GoogleSignInStatus.Error)
                    }
                }
        }
    }
