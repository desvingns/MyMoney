# Dashboard period swipe navigation + peeking label; disable left-drawer swipe-to-open (S01)
Epic: monefy-behavioral-fidelity
Order: 07 of 09
Status: draft
Depends-on: 06 (PeriodStrip removed; PeriodLabel exists)
Date: 2026-06-01

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: On the dashboard, swiping left/right changes the period within the selected type (previous/next day, week, month, or year) and the top label shows the current period with the adjacent periods peeking at the edges (05.jpg "2025 | 2026"; 11.jpg "…, 1 июня | Вторник, 2 июня"). Also disable the left drawer's swipe-to-open so a horizontal swipe no longer opens the menu (the hamburger button still opens it).
LAYERS: domain presentation
CHANGED_HINT: core/domain/.../model/Period.kt (or the existing PeriodArithmetic util) (+ pure next()/previous(): Day ±1 day, Week ±1 week, Month ±1 month, Year ±1 year, All = no-op, CustomRange shift by range length); feature/dashboard/.../DashboardViewModel.kt (+ handle PreviousPeriod / NextPeriod -> period = period.previous()/next() -> recomputeBalance()); feature/dashboard/.../DashboardState.kt + DashboardEvent (+ the two events); feature/dashboard/.../DashboardScreen.kt (add a horizontal-drag detector on the dashboard content -> emit Prev/Next; upgrade the static PeriodLabel to a 3-up peeking label; set the LEFT ModalNavigationDrawer gesturesEnabled = false at L103); screenshots 05.jpg / 11.jpg
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - Add pure Period.next()/previous(), unit-tested for EVERY variant (All unchanged; CustomRange shifts by (end - start + 1) days preserving the range length).
  - Dashboard horizontal swipe: a LEFT swipe -> NextPeriod, a RIGHT swipe -> PreviousPeriod (confirm direction against 05.jpg). Use a drag threshold so taps / vertical scroll aren't captured; keep SoundKey.SWIPE / HapticKind.SOFT on change.
  - Set the LEFT ModalNavigationDrawer `gesturesEnabled = false` (DashboardScreen.kt:103) so a swipe changes the period rather than opening the drawer; the hamburger IconButton must STILL open it. (The right drawer is already gesturesEnabled=false.)
  - The period label shows the current period centered + previous/next faded at the edges (05.jpg / 11.jpg). No new nav; no hardcoded strings (EN+RU); English ids; no comments unless WHY.
=== END SPEC ===

## Gap / context
User note #4. Period has no next()/previous() helper; the dashboard has no swipe handler; and the
left ModalNavigationDrawer's default swipe-to-open (gesturesEnabled defaults true, DashboardScreen.kt:103)
intercepts right-swipes — so today a right-swipe opens the menu. The reference uses horizontal swipes
to move through periods and shows a peeking previous/current/next label at the top.

## Implementation links
(pending — fill commit + changed files after `/cmp --feature --next`)
