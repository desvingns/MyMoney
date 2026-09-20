package com.kshavrin.mymoney.feature.dashboard.tour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DashboardTourTest {

    @Test
    fun firstStepIsActionsInPanelPlaceholderUnpaused() {
        val first = DashboardTourReducer.first()
        assertEquals(TourStep.Actions, first.step)
        assertEquals(TourPhase.Panel, first.phase)
        assertEquals(false, first.paused)
    }

    @Test
    fun panelStepsEnterInButtonPhase() {
        assertEquals(TourPhase.Button, DashboardTourReducer.enter(TourStep.LeftPanel).phase)
        assertEquals(TourPhase.Button, DashboardTourReducer.enter(TourStep.RightCategories).phase)
        assertEquals(TourPhase.Panel, DashboardTourReducer.enter(TourStep.RightSupport).phase)
    }

    @Test
    fun nextWalksStepsInOrderThenFinishes() {
        val actions = DashboardTourReducer.first()
        val left = DashboardTourReducer.next(actions)!!
        assertEquals(TourStep.LeftPanel, left.step)
        assertEquals(TourPhase.Button, left.phase)

        val categories = DashboardTourReducer.next(left)!!
        assertEquals(TourStep.RightCategories, categories.step)
        assertEquals(TourPhase.Button, categories.phase)

        val support = DashboardTourReducer.next(categories)!!
        assertEquals(TourStep.RightSupport, support.step)

        assertNull("next past the last step finishes the tour", DashboardTourReducer.next(support))
    }

    @Test
    fun nextFromAnyPhaseOfPanelStepAdvancesToNextStep() {
        val leftPanelOpen = TourUiState(TourStep.LeftPanel, TourPhase.Panel)
        assertEquals(TourStep.RightCategories, DashboardTourReducer.next(leftPanelOpen)!!.step)

        val categoriesOpen = TourUiState(TourStep.RightCategories, TourPhase.Panel)
        assertEquals(TourStep.RightSupport, DashboardTourReducer.next(categoriesOpen)!!.step)
    }

    @Test
    fun panelOpenElapsedFlipsButtonToPanelOnce() {
        val button = TourUiState(TourStep.LeftPanel, TourPhase.Button)
        val panel = DashboardTourReducer.panelOpenElapsed(button)
        assertEquals(TourPhase.Panel, panel.phase)

        val stale = DashboardTourReducer.panelOpenElapsed(panel)
        assertSame("a repeat timer signal changes nothing", panel, stale)
    }

    @Test
    fun panelOpenElapsedIgnoredWhenAlreadyPanel() {
        val actions = DashboardTourReducer.first()
        assertSame(actions, DashboardTourReducer.panelOpenElapsed(actions))
    }

    @Test
    fun resumedIsNoOpWhenNotPaused() {
        val actions = DashboardTourReducer.first()
        assertSame(actions, DashboardTourReducer.resumed(actions))
    }

    @Test
    fun resumedAdvancesWhenPausedAndFinishesAfterLastStep() {
        val pausedActions = DashboardTourReducer.paused(DashboardTourReducer.first())
        val resumed = DashboardTourReducer.resumed(pausedActions)!!
        assertEquals(TourStep.LeftPanel, resumed.step)
        assertEquals(false, resumed.paused)

        val pausedSupport = DashboardTourReducer.paused(DashboardTourReducer.enter(TourStep.RightSupport))
        assertNull("resuming after Support finishes the tour", DashboardTourReducer.resumed(pausedSupport))
    }

    @Test
    fun drawersForMatchesTransitionTable() {
        assertEquals(TourDrawerState(left = false, right = false), drawersFor(TourStep.Actions, TourPhase.Panel))
        assertEquals(TourDrawerState(left = false, right = false), drawersFor(TourStep.LeftPanel, TourPhase.Button))
        assertEquals(TourDrawerState(left = true, right = false), drawersFor(TourStep.LeftPanel, TourPhase.Panel))
        assertEquals(TourDrawerState(left = false, right = false), drawersFor(TourStep.RightCategories, TourPhase.Button))
        assertEquals(TourDrawerState(left = false, right = true), drawersFor(TourStep.RightCategories, TourPhase.Panel))
        assertEquals(TourDrawerState(left = false, right = true), drawersFor(TourStep.RightSupport, TourPhase.Button))
        assertEquals(TourDrawerState(left = false, right = true), drawersFor(TourStep.RightSupport, TourPhase.Panel))
    }

    @Test
    fun tourHasFourSteps() {
        assertEquals(4, TourStep.entries.size)
    }
}
