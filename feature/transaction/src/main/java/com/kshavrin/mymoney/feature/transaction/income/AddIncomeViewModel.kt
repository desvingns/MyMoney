package com.kshavrin.mymoney.feature.transaction.income

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.calculator.CalculatorEngine
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.designsystem.keypad.toCalculator
import com.kshavrin.mymoney.core.designsystem.keypad.toDesignsystem
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.feature.transaction.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class AddIncomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val categoryRepository: CategoryRepository,
    private val appSettingsRepository: AppSettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val engine = CalculatorEngine()

    private val _state = MutableStateFlow(AddIncomeState())
    val state: StateFlow<AddIncomeState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<AddIncomeAction>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val actions: SharedFlow<AddIncomeAction> = _actions.asSharedFlow()

    init {
        loadInitialContext()
        observeCategories()
    }

    private fun loadInitialContext() {
        viewModelScope.launch {
            val settings = appSettingsRepository.settings.first()
            val accounts = accountRepository.observeActive().first()
            val currencies = currencyRepository.observeActive().first()
            val defaultAccount = if (settings.defaultAccountId >= 0) {
                accounts.firstOrNull { it.id == settings.defaultAccountId }
            } else null
            val activeAccount = defaultAccount ?: accounts.firstOrNull()
            val activeCurrency = activeAccount?.let { acc ->
                currencies.firstOrNull { it.id == acc.currencyId }
            }
            _state.value = _state.value.copy(
                accounts = accounts,
                currencies = currencies,
                account = activeAccount,
                currency = activeCurrency,
            )
        }
    }

    private fun observeCategories() {
        viewModelScope.launch {
            categoryRepository.observeAll().collect { all ->
                _state.value = _state.value.copy(
                    categories = all
                        .filter { it.kind == CategoryKind.Income && !it.isArchived }
                        .sortedBy { it.sortOrder },
                )
            }
        }
    }

    fun onEvent(event: AddIncomeEvent) {
        when (event) {
            is AddIncomeEvent.KeypadDigit -> {
                engine.inputDigit(event.d)
                syncFromEngine()
            }
            is AddIncomeEvent.KeypadOperator -> {
                engine.inputOperator(event.op.toCalculator())
                syncFromEngine()
            }
            AddIncomeEvent.KeypadDot -> {
                engine.inputDot()
                syncFromEngine()
            }
            AddIncomeEvent.KeypadBackspace -> {
                engine.backspace()
                syncFromEngine()
            }
            AddIncomeEvent.KeypadEquals -> {
                engine.equals()
                syncFromEngine()
            }
            is AddIncomeEvent.NoteChanged ->
                _state.value = _state.value.copy(note = event.text, errorBannerRes = null)
            is AddIncomeEvent.DateChanged ->
                _state.value = _state.value.copy(occurredAt = event.date)
            is AddIncomeEvent.AccountChanged -> {
                val acc = _state.value.accounts.firstOrNull { it.id == event.accountId } ?: return
                val cur = _state.value.currencies.firstOrNull { it.id == acc.currencyId }
                _state.value = _state.value.copy(account = acc, currency = cur)
            }
            AddIncomeEvent.SelectCategoryClicked -> onSelectCategoryClicked()
            AddIncomeEvent.BackToAmount ->
                _state.value = _state.value.copy(categoryStep = false, errorBannerRes = null)
            AddIncomeEvent.AddCategoryClicked -> emit(AddIncomeAction.NavigateToCreateCategory)
            is AddIncomeEvent.CategoryPicked -> onCategoryPicked(event.categoryId)
            AddIncomeEvent.SaveClicked -> save()
            AddIncomeEvent.SwapMode -> emit(AddIncomeAction.NavigateToExpenseForm)
            AddIncomeEvent.BackClicked -> emit(AddIncomeAction.NavigateBack)
            AddIncomeEvent.DismissError ->
                _state.value = _state.value.copy(errorBannerRes = null)
        }
    }

    private fun syncFromEngine() {
        _state.value = _state.value.copy(
            amount = engine.currentValue,
            amountInput = engine.display,
            expression = engine.expression,
            pendingOperator = engine.pendingOp.toDesignsystem(),
        )
    }

    private fun onSelectCategoryClicked() {
        val s = _state.value
        if (s.amount <= BigDecimal.ZERO) {
            _state.value = s.copy(errorBannerRes = R.string.error_enter_amount_first)
            return
        }
        _state.value = s.copy(categoryStep = true, errorBannerRes = null)
    }

    private fun onCategoryPicked(categoryId: Long) {
        if (_state.value.isSaving) return
        val s = _state.value
        if (s.amount <= BigDecimal.ZERO) {
            _state.value = s.copy(
                categoryStep = false,
                errorBannerRes = R.string.error_enter_amount_first,
            )
            return
        }
        val account = s.account ?: return
        val currency = s.currency ?: return
        _state.value = s.copy(isSaving = true)
        viewModelScope.launch {
            val category = try {
                categoryRepository.findById(categoryId)
            } catch (t: Throwable) {
                t.reportToSentry()
                _state.value = _state.value.copy(
                    isSaving = false,
                    errorBannerRes = R.string.error_save_failed,
                )
                return@launch
            }
            if (category == null) {
                _state.value = _state.value.copy(isSaving = false)
                return@launch
            }
            val saveState = s.copy(category = category)
            _state.value = _state.value.copy(category = category, errorBannerRes = null)
            persist(saveState, category, account, currency)
        }
    }

    private fun save() {
        if (_state.value.isSaving) return
        val s = _state.value
        if (s.amount <= BigDecimal.ZERO) {
            _state.value = s.copy(errorBannerRes = R.string.error_enter_amount_first)
            return
        }
        val category = s.category
        if (category == null) {
            _state.value = s.copy(errorBannerRes = R.string.error_choose_category_first)
            return
        }
        val account = s.account ?: return
        val currency = s.currency ?: return
        _state.value = s.copy(isSaving = true)
        viewModelScope.launch {
            persist(s, category, account, currency)
        }
    }

    private suspend fun persist(
        s: AddIncomeState,
        category: Category,
        account: Account,
        currency: Currency,
    ) {
        try {
            val now = Instant.now()
            val tx = Transaction(
                id = 0L,
                kind = TransactionKind.Income,
                amount = s.amount,
                currencyId = currency.id,
                accountId = account.id,
                categoryId = category.id,
                note = s.note.takeIf { it.isNotBlank() },
                occurredAt = s.occurredAt.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                toAccountId = null,
                toAmount = null,
                exchangeRate = null,
            )
            transactionRepository.upsert(tx)
            _state.value = _state.value.copy(
                isSaving = false,
                savedSignal = _state.value.savedSignal + 1,
            )
            _actions.emit(AddIncomeAction.NavigateBack)
        } catch (t: Throwable) {
            t.reportToSentry()
            _state.value = _state.value.copy(
                isSaving = false,
                errorBannerRes = R.string.error_save_failed,
            )
        }
    }

    private fun emit(action: AddIncomeAction) {
        viewModelScope.launch { _actions.emit(action) }
    }

    companion object {
        const val KEY_CREATED_CATEGORY_ID = "createdCategoryId"
    }
}
