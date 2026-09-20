package com.kshavrin.mymoney.feature.dashboard.tour

// Delay before a panel step (LeftPanel / RightCategories) auto-advances from its Button phase
// (cutout on the top-bar button, panel closed) to its Panel phase (panel slid open, cutout on the
// panel). The host schedules this; tapping the button itself reaches the same Panel phase sooner.
const val TOUR_PANEL_OPEN_DELAY_MS = 900L

// Spotlight registry keys for the six tour targets on the dashboard.
const val TOUR_TARGET_ACTIONS = "tour_target_actions"
const val TOUR_TARGET_MENU = "tour_target_menu"
const val TOUR_TARGET_LEFT_PANEL = "tour_target_left_panel"
const val TOUR_TARGET_MORE = "tour_target_more"
const val TOUR_TARGET_CATEGORIES = "tour_target_categories"
const val TOUR_TARGET_SUPPORT = "tour_target_support"

enum class TourStep { Actions, LeftPanel, RightCategories, RightSupport }

// Only LeftPanel and RightCategories are genuinely two-phase (D16): Button = panel closed, cutout on
// the top-bar button; Panel = panel open, cutout on the panel/item. Actions and RightSupport carry
// Panel as an inert placeholder (no button phase) so the field is never null.
enum class TourPhase { Button, Panel }

data class TourUiState(
    val step: TourStep,
    val phase: TourPhase,
    val paused: Boolean = false,
)

// Which side drawers a given (step, phase) requires — the single source of truth the ViewModel
// applies to leftDrawerOpen/rightDrawerOpen whenever the tour enters or resumes a step (D9).
data class TourDrawerState(
    val left: Boolean,
    val right: Boolean,
)

fun drawersFor(
    step: TourStep,
    phase: TourPhase,
): TourDrawerState =
    when (step) {
        TourStep.Actions -> TourDrawerState(left = false, right = false)
        TourStep.LeftPanel ->
            if (phase == TourPhase.Panel) TourDrawerState(left = true, right = false) else TourDrawerState(left = false, right = false)
        TourStep.RightCategories ->
            if (phase == TourPhase.Panel) TourDrawerState(left = false, right = true) else TourDrawerState(left = false, right = false)
        TourStep.RightSupport -> TourDrawerState(left = false, right = true)
    }

fun drawersFor(state: TourUiState): TourDrawerState = drawersFor(state.step, state.phase)

object DashboardTourReducer {
    fun first(): TourUiState = enter(TourStep.Actions)

    // Fresh entry into a step: panel steps start in Button phase; the others hold Panel as a
    // placeholder. Always un-paused.
    fun enter(step: TourStep): TourUiState = TourUiState(step = step, phase = initialPhase(step), paused = false)

    private fun initialPhase(step: TourStep): TourPhase =
        when (step) {
            TourStep.LeftPanel, TourStep.RightCategories -> TourPhase.Button
            TourStep.Actions, TourStep.RightSupport -> TourPhase.Panel
        }

    // null = the tour is finished (advanced past the last step).
    fun next(state: TourUiState): TourUiState? {
        val nextStep = TourStep.entries.getOrNull(state.step.ordinal + 1) ?: return null
        return enter(nextStep)
    }

    // Button → Panel; a no-op (identity) in every other state so a stale timer after "Next" changes
    // nothing.
    fun panelOpenElapsed(state: TourUiState): TourUiState =
        if (state.phase == TourPhase.Button) state.copy(phase = TourPhase.Panel) else state

    fun paused(state: TourUiState): TourUiState = state.copy(paused = true)

    // ON_RESUME fires on first show and after returning from a real action. It only advances when the
    // tour is paused; otherwise it is a no-op. null = the tour is finished.
    fun resumed(state: TourUiState): TourUiState? = if (!state.paused) state else next(state)
}
