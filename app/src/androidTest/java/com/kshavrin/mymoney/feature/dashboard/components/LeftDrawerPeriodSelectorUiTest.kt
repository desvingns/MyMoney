package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.AllAccountsFoldMode
import com.kshavrin.mymoney.feature.dashboard.DashboardEvent
import com.kshavrin.mymoney.feature.dashboard.DashboardSelection
import com.kshavrin.mymoney.feature.dashboard.DashboardState
import com.kshavrin.mymoney.feature.dashboard.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class LeftDrawerPeriodSelectorUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(TEST_TIME_ZONE_ID))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `interval row expands inline start and end controls with disabled apply`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                LeftDrawerContent(
                    state = DashboardState(period = Period.All, isLoading = false),
                    onEvent = {},
                    onPickDateRangeClick = {},
                )
            }
        }

        periodRow(R.string.period_date_range).performClick()

        composeTestRule
            .onNodeWithText(
                targetString(
                    R.string.period_interval_value,
                    targetString(R.string.period_interval_start),
                    targetString(R.string.period_interval_not_selected),
                ),
            ).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                targetString(
                    R.string.period_interval_value,
                    targetString(R.string.period_interval_end),
                    targetString(R.string.period_interval_not_selected),
                ),
            ).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.period_apply))
            .assertIsNotEnabled()
    }

    @Test
    fun `interval date controls gate invalid range and emit valid interval without dismissing drawer`() {
        val selectedEvents = mutableListOf<DashboardEvent>()
        val start = LocalDate.now().withDayOfMonth(20)
        val invalidEnd = LocalDate.now().withDayOfMonth(10)
        val validEnd = LocalDate.now().withDayOfMonth(25)

        composeTestRule.setContent {
            MyMoneyTheme {
                LeftDrawerContent(
                    state = DashboardState(period = Period.All, isLoading = false),
                    onEvent = { selectedEvents += it },
                    onPickDateRangeClick = {},
                )
            }
        }

        periodRow(R.string.period_date_range).performClick()
        selectIntervalEndpoint(
            contentDescription =
                targetString(
                    R.string.period_interval_start_cd,
                    targetString(R.string.period_interval_not_selected),
                ),
            date = start,
        )
        selectIntervalEndpoint(
            contentDescription =
                targetString(
                    R.string.period_interval_end_cd,
                    targetString(R.string.period_interval_not_selected),
                ),
            date = invalidEnd,
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.period_interval_invalid))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.period_apply))
            .assertIsNotEnabled()

        selectIntervalEndpoint(
            contentDescription = targetString(R.string.period_interval_end_cd, mediumDateLabel(invalidEnd)),
            date = validEnd,
        )
        composeTestRule
            .onNodeWithText(targetString(R.string.period_apply))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(DashboardEvent.PeriodChanged(Period.Interval(start, validEnd))),
                selectedEvents,
            )
        }
        periodRow(R.string.period_date_range).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                targetString(R.string.period_interval_value, targetString(R.string.period_interval_start), mediumDateLabel(start)),
            ).assertIsDisplayed()
    }

    @Test
    fun `pick a date invokes parent callback without emitting a period event`() {
        val selectedEvents = mutableListOf<DashboardEvent>()
        var callbackCount = 0

        composeTestRule.setContent {
            MyMoneyTheme {
                LeftDrawerContent(
                    state = DashboardState(period = Period.All, isLoading = false),
                    onEvent = { selectedEvents += it },
                    onPickDateRangeClick = { callbackCount += 1 },
                )
            }
        }

        periodRow(R.string.period_pick_a_date).performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, callbackCount)
            assertTrue(selectedEvents.isEmpty())
        }
    }

    @Test
    fun `ordinary period buttons remain present and emit each corresponding period in order`() {
        val currentDate = LocalDate.now()
        val selectedEvents = mutableListOf<DashboardEvent>()
        val expectedSelections =
            listOf(
                R.string.period_day to DashboardEvent.PeriodChanged(Period.Day(currentDate)),
                R.string.period_week to DashboardEvent.PeriodChanged(Period.Week(currentDate.with(DayOfWeek.MONDAY))),
                R.string.period_month to DashboardEvent.PeriodChanged(Period.Month(YearMonth.from(currentDate))),
                R.string.period_year to DashboardEvent.PeriodChanged(Period.Year(currentDate.year)),
                R.string.period_all to DashboardEvent.PeriodChanged(Period.All),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                LeftDrawerContent(
                    state = DashboardState(period = Period.All, isLoading = false),
                    onEvent = { selectedEvents += it },
                    onPickDateRangeClick = {},
                )
            }
        }

        composeTestRule
            .onAllNodesWithText(targetString(R.string.period_date_range))
            .assertCountEquals(1)
        composeTestRule
            .onAllNodesWithText(targetString(R.string.period_pick_a_date))
            .assertCountEquals(1)

        expectedSelections.forEach { (resourceId, _) ->
            composeTestRule
                .onNodeWithText(targetString(resourceId))
                .performClick()
        }

        composeTestRule.runOnIdle {
            assertEquals(expectedSelections.map { it.second }, selectedEvents)
        }
    }

    @Test
    fun `period row selection is mutually exclusive`() {
        var currentState by mutableStateOf(DashboardState(period = Period.All, isLoading = false))

        composeTestRule.setContent {
            MyMoneyTheme {
                LeftDrawerContent(
                    state = currentState,
                    onEvent = { event ->
                        if (event is DashboardEvent.PeriodChanged) {
                            currentState = currentState.copy(period = event.period)
                        }
                    },
                    onPickDateRangeClick = {},
                )
            }
        }

        periodRow(R.string.period_all).assertIsSelected()
        periodRow(R.string.period_day).performClick()
        composeTestRule.runOnIdle {
            periodRow(R.string.period_day).assertIsSelected()
            periodRow(R.string.period_all).assertIsNotSelected()
            periodRow(R.string.period_week).assertIsNotSelected()
            periodRow(R.string.period_month).assertIsNotSelected()
            periodRow(R.string.period_year).assertIsNotSelected()
            periodRow(R.string.period_date_range).assertIsNotSelected()
            periodRow(R.string.period_pick_a_date).assertIsNotSelected()
        }
    }

    @Test
    fun `period rows are ordered day week month year all interval pick a date`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                LeftDrawerContent(
                    state = DashboardState(period = Period.All, isLoading = false),
                    onEvent = {},
                    onPickDateRangeClick = {},
                )
            }
        }

        val rows =
            listOf(
                R.string.period_day,
                R.string.period_week,
                R.string.period_month,
                R.string.period_year,
                R.string.period_all,
                R.string.period_date_range,
                R.string.period_pick_a_date,
            ).map { periodRow(it).fetchSemanticsNode().boundsInRoot.top }

        assertTrue(rows.zipWithNext().all { (current, next) -> current < next })
    }

    @Test
    fun `compact drawer can scroll to date range row with long currency name`() {
        val serbianDinar =
            currency(
                code = "RSD",
                symbol = "RSD",
                name = "Serbian dinar",
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                Box(
                    modifier =
                        Modifier
                            .width(360.dp)
                            .height(360.dp),
                ) {
                    LeftDrawerContent(
                        state =
                            DashboardState(
                                period = Period.All,
                                currencies = listOf(serbianDinar),
                                dashboardSelection = DashboardSelection.AllAccounts(AllAccountsFoldMode.ConvertTo(serbianDinar)),
                                isLoading = false,
                        ),
                        onEvent = {},
                        onPickDateRangeClick = {},
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.period_date_range))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `separate mode keeps the account toggle and reveals individual accounts`() {
        val euro = currency(code = "EUR", symbol = "€", name = "Euro")
        val cash =
            account(
                id = 42L,
                name = "Cash wallet",
                currencyId = euro.id,
            )
        var selectedEvent: DashboardEvent? = null

        composeTestRule.setContent {
            MyMoneyTheme {
                LeftDrawerContent(
                    state =
                        DashboardState(
                            period = Period.All,
                            accounts = listOf(cash),
                            currencies = listOf(euro),
                            dashboardSelection = DashboardSelection.AllAccounts(AllAccountsFoldMode.Separate),
                            isLoading = false,
                        ),
                        onEvent = { selectedEvent = it },
                        onPickDateRangeClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.left_drawer_separate_currencies))
            .assertIsDisplayed()
            .performClick()

        composeTestRule
            .onNodeWithText(cash.name)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(DashboardEvent.AccountSelected(cash.id), selectedEvent)
        }
    }

    private fun account(
        id: Long,
        name: String,
        currencyId: Long,
    ) = Account(
        id = id,
        name = name,
        currencyId = currencyId,
        initialBalance = java.math.BigDecimal.ZERO,
        type = com.kshavrin.mymoney.core.domain.model.AccountType.Cash,
        colorHex = "#FF0000",
        iconKey = "wallet",
        isDefault = false,
        sortOrder = 0,
        createdAt = java.time.Instant.EPOCH,
        updatedAt = java.time.Instant.EPOCH,
        isArchived = false,
    )

    private fun periodRow(resourceId: Int) =
        composeTestRule.onNodeWithContentDescription(
            targetString(R.string.dashboard_drawer_option_cd, targetString(resourceId)),
        )

    private fun selectIntervalEndpoint(
        contentDescription: String,
        date: LocalDate,
    ) {
        composeTestRule.onNodeWithContentDescription(contentDescription).performClick()
        composeTestRule.onNodeWithText(datePickerLabel(date)).performClick()
        composeTestRule.onNodeWithText(targetString(R.string.period_apply)).performClick()
    }

    private fun targetString(
        resourceId: Int,
        vararg formatArgs: Any,
    ): String =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getString(resourceId, *formatArgs)

    private fun mediumDateLabel(date: LocalDate): String {
        val locale =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.configuration.locales[0]
        return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }

    private fun datePickerLabel(date: LocalDate): String {
        val locale =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.configuration.locales[0]
        return date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale))
    }

    private fun currency(
        code: String,
        symbol: String,
        name: String,
    ) = Currency(
        id = 1L,
        code = code,
        symbol = symbol,
        name = name,
        decimalDigits = 2,
        isActive = true,
        sortOrder = 0,
    )

    companion object {
        private const val TEST_TIME_ZONE_ID = "America/New_York"
    }
}
