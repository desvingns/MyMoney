package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.designsystem.confetti.MonefyConfetti
import com.kshavrin.mymoney.core.designsystem.donut.MonefyDonutChart
import com.kshavrin.mymoney.core.designsystem.pill.MonefyBalancePill
import com.kshavrin.mymoney.core.ui.feedback.LocalHapticPlayer
import com.kshavrin.mymoney.core.ui.feedback.LocalSoundPlayer
import com.kshavrin.mymoney.core.ui.haptic.HapticKind
import com.kshavrin.mymoney.core.ui.sound.SoundKey
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.dashboard.components.LeftDrawerContent
import com.kshavrin.mymoney.feature.dashboard.components.PeriodStrip
import com.kshavrin.mymoney.feature.dashboard.components.RightDrawerContent
import com.kshavrin.mymoney.feature.dashboard.components.TwoFabLayout
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardRoute(
    onAction: (DashboardAction) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action -> onAction(action) }
    }

    DashboardContent(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    state: DashboardState,
    onEvent: (DashboardEvent) -> Unit,
) {
    val leftDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val rightDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val soundPlayer = LocalSoundPlayer.current
    val hapticPlayer = LocalHapticPlayer.current

    LaunchedEffect(state.leftDrawerOpen) {
        if (state.leftDrawerOpen) leftDrawerState.open() else leftDrawerState.close()
    }
    LaunchedEffect(state.rightDrawerOpen) {
        if (state.rightDrawerOpen) rightDrawerState.open() else rightDrawerState.close()
    }
    LaunchedEffect(state.showConfetti) {
        if (state.showConfetti) {
            soundPlayer.play(SoundKey.MILESTONE)
            hapticPlayer.fire(HapticKind.SUCCESS_SHIMMER)
        }
    }

    ModalNavigationDrawer(
        drawerState = leftDrawerState,
        drawerContent = {
            ModalDrawerSheet {
                LeftDrawerContent(state = state, onEvent = onEvent)
            }
        },
    ) {
        ModalNavigationDrawer(
            drawerState = rightDrawerState,
            drawerContent = {
                ModalDrawerSheet {
                    RightDrawerContent(onEvent = onEvent)
                }
            },
            gesturesEnabled = false,
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(text = stringResource(R.string.dashboard_title)) },
                        navigationIcon = {
                            IconButton(onClick = {
                                hapticPlayer.fire(HapticKind.MEDIUM)
                                scope.launch { leftDrawerState.open() }
                            }) {
                                Icon(
                                    Icons.Filled.Menu,
                                    contentDescription = stringResource(R.string.dashboard_menu),
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { onEvent(DashboardEvent.TransferClicked) }) {
                                Icon(
                                    Icons.Filled.SwapHoriz,
                                    contentDescription = stringResource(R.string.dashboard_transfer),
                                )
                            }
                            IconButton(onClick = { onEvent(DashboardEvent.SearchClicked) }) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = stringResource(R.string.dashboard_search),
                                )
                            }
                            IconButton(onClick = {
                                hapticPlayer.fire(HapticKind.MEDIUM)
                                scope.launch { rightDrawerState.open() }
                            }) {
                                Icon(
                                    Icons.Filled.Menu,
                                    contentDescription = stringResource(R.string.dashboard_settings_menu),
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PeriodStrip(
                            currentPeriod = state.period,
                            onPeriodChange = { p ->
                                soundPlayer.play(SoundKey.SWIPE)
                                hapticPlayer.fire(HapticKind.SOFT)
                                onEvent(DashboardEvent.PeriodChanged(p))
                            },
                        )
                        Spacer(modifier = Modifier.height(Spacing.m))

                        val balanceText = formatBalance(state)
                        MonefyBalancePill(
                            text = balanceText,
                            isPositive = (state.balanceSnapshot?.net?.amount?.signum() ?: 1) >= 0,
                            onClick = { onEvent(DashboardEvent.BalanceCardClicked) },
                        )
                        Spacer(modifier = Modifier.height(Spacing.l))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .padding(horizontal = Spacing.xl),
                        ) {
                            MonefyDonutChart(
                                income = state.balanceSnapshot?.income?.amount ?: BigDecimal.ZERO,
                                expense = state.balanceSnapshot?.expense?.amount ?: BigDecimal.ZERO,
                                slices = state.slices,
                                modifier = Modifier.fillMaxSize(),
                                onSliceClick = { slice ->
                                    onEvent(DashboardEvent.SliceClicked(slice.categoryId))
                                },
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.xl))

                        TwoFabLayout(
                            onMinusClick = {
                                hapticPlayer.fire(HapticKind.MEDIUM)
                                onEvent(DashboardEvent.MinusFabClicked)
                            },
                            onPlusClick = {
                                hapticPlayer.fire(HapticKind.MEDIUM)
                                onEvent(DashboardEvent.PlusFabClicked)
                            },
                            onTransferClick = { onEvent(DashboardEvent.TransferClicked) },
                        )
                    }

                    MonefyConfetti(
                        show = state.showConfetti,
                        onFinished = { onEvent(DashboardEvent.ConfettiAcknowledged) },
                    )
                }
            }
        }
    }
}

private fun formatBalance(state: DashboardState): String {
    val net = state.balanceSnapshot?.net ?: return "—"
    val symbol = state.currentCurrency?.symbol ?: ""
    return "$symbol${net.amount.toPlainString()}"
}
