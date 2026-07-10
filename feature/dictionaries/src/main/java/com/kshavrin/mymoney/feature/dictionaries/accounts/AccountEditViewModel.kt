package com.kshavrin.mymoney.feature.dictionaries.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.toMoneyScale
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AccountEditViewModel
    @Inject
    constructor(
        private val accountRepository: AccountRepository,
        private val currencyRepository: CurrencyRepository,
        private val transactionRepository: TransactionRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val accountId: Long = savedStateHandle.get<Long>("id") ?: -1L

        private val _state = MutableStateFlow(AccountEditState(isCreateMode = accountId == -1L))
        val state: StateFlow<AccountEditState> = _state.asStateFlow()

        private val _actions = MutableSharedFlow<AccountEditAction>(extraBufferCapacity = 4)
        val actions: SharedFlow<AccountEditAction> = _actions.asSharedFlow()

        init {
            viewModelScope.launch {
                val activeCurrencies = currencyRepository.observeActive().first()
                _state.value = _state.value.copy(availableCurrencies = activeCurrencies)
                if (accountId != -1L) {
                    accountRepository.findById(accountId)?.let { existing ->
                        _state.value =
                            _state.value.copy(
                                name = existing.name,
                                currencyId = existing.currencyId,
                                initialBalanceText = MoneyFormatter.formatInput(existing.initialBalance, Locale.getDefault()),
                                type = existing.type,
                                colorHex = existing.colorHex,
                                iconKey = existing.iconKey,
                                isDefault = existing.isDefault,
                                sortOrder = existing.sortOrder,
                                isCreateMode = false,
                                createdAt = existing.createdAt,
                            )
                    }
                } else if (_state.value.currencyId == -1L) {
                    activeCurrencies.firstOrNull()?.let { c ->
                        _state.value = _state.value.copy(currencyId = c.id)
                    }
                }
            }
        }

        fun onEvent(event: AccountEditEvent) {
            when (event) {
                is AccountEditEvent.NameChanged ->
                    _state.value = _state.value.copy(name = event.value, errorMessage = null)
                is AccountEditEvent.CurrencyChanged ->
                    _state.value = _state.value.copy(currencyId = event.id)
                is AccountEditEvent.InitialBalanceChanged ->
                    _state.value = _state.value.copy(initialBalanceText = event.value, errorMessage = null)
                is AccountEditEvent.TypeChanged ->
                    _state.value = _state.value.copy(type = event.value)
                is AccountEditEvent.ColorChanged ->
                    _state.value = _state.value.copy(colorHex = event.value)
                is AccountEditEvent.IconChanged ->
                    _state.value = _state.value.copy(iconKey = event.value)
                is AccountEditEvent.IsDefaultChanged ->
                    _state.value = _state.value.copy(isDefault = event.value)
                AccountEditEvent.SaveClicked -> save()
                AccountEditEvent.BackClicked ->
                    viewModelScope.launch { _actions.emit(AccountEditAction.NavigateBack) }
                AccountEditEvent.DeleteClicked -> attemptDelete()
                AccountEditEvent.BlockedDeleteDismissed ->
                    _state.value = _state.value.copy(blockedDeleteCount = null)
            }
        }

        private fun save() {
            if (_state.value.isSaving) return
            val s = _state.value
            if (s.name.isBlank() || s.name.length > 32) {
                _state.value = s.copy(errorMessage = "name_required")
                return
            }
            if (s.currencyId == -1L) {
                _state.value = s.copy(errorMessage = "currency_required")
                return
            }
            val currency =
                s.availableCurrencies.firstOrNull { it.id == s.currencyId }
                    ?: run {
                        _state.value = s.copy(errorMessage = "currency_required")
                        return
                    }
            val parsedBalance =
                s.initialBalanceText
                    .trim()
                    .replace(',', '.')
                    .toBigDecimalOrNull()
                    ?.toMoneyScale(currency)
            if (parsedBalance == null) {
                _state.value = s.copy(errorMessage = "balance_format")
                return
            }
            _state.value = s.copy(isSaving = true)
            viewModelScope.launch {
                try {
                    val now = Instant.now()
                    val savedId =
                        accountRepository.upsert(
                            Account(
                                id = if (accountId == -1L) 0L else accountId,
                                name = s.name,
                                currencyId = s.currencyId,
                                initialBalance = parsedBalance,
                                type = s.type,
                                colorHex = s.colorHex,
                                iconKey = s.iconKey,
                                isDefault = s.isDefault,
                                sortOrder = s.sortOrder,
                                createdAt = s.createdAt ?: now,
                                updatedAt = now,
                                isArchived = false,
                            ),
                        )
                    if (s.isDefault) {
                        accountRepository.setDefault(savedId)
                    }
                    _state.value = _state.value.copy(isSaving = false)
                    _actions.emit(AccountEditAction.NavigateBack)
                } catch (e: IllegalArgumentException) {
                    _state.value =
                        _state.value.copy(
                            isSaving = false,
                            errorMessage = e.message ?: "save_failed",
                        )
                } finally {
                    _state.value = _state.value.copy(isSaving = false)
                }
            }
        }

        private fun attemptDelete() {
            if (accountId == -1L) return
            viewModelScope.launch {
                val count = transactionRepository.countByAccount(accountId)
                if (count > 0) {
                    _state.value = _state.value.copy(blockedDeleteCount = count)
                } else {
                    accountRepository.archive(accountId)
                    _actions.emit(AccountEditAction.NavigateBack)
                }
            }
        }
    }

data class AccountEditState(
    val isCreateMode: Boolean = true,
    val name: String = "",
    val currencyId: Long = -1L,
    val initialBalanceText: String = "0",
    val type: AccountType = AccountType.Cash,
    val colorHex: String = "#7AC794",
    val iconKey: String = "ic_account_cash",
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    val availableCurrencies: List<Currency> = emptyList(),
    val blockedDeleteCount: Int? = null,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val createdAt: Instant? = null,
)

sealed interface AccountEditEvent {
    data class NameChanged(
        val value: String,
    ) : AccountEditEvent

    data class CurrencyChanged(
        val id: Long,
    ) : AccountEditEvent

    data class InitialBalanceChanged(
        val value: String,
    ) : AccountEditEvent

    data class TypeChanged(
        val value: AccountType,
    ) : AccountEditEvent

    data class ColorChanged(
        val value: String,
    ) : AccountEditEvent

    data class IconChanged(
        val value: String,
    ) : AccountEditEvent

    data class IsDefaultChanged(
        val value: Boolean,
    ) : AccountEditEvent

    data object SaveClicked : AccountEditEvent

    data object BackClicked : AccountEditEvent

    data object DeleteClicked : AccountEditEvent

    data object BlockedDeleteDismissed : AccountEditEvent
}

sealed interface AccountEditAction {
    data object NavigateBack : AccountEditAction
}
