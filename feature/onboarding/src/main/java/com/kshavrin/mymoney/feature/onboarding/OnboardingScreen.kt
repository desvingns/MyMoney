package com.kshavrin.mymoney.feature.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.ui.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGE_COUNT })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state.completed) {
        if (state.completed) onComplete()
    }

    OnboardingContent(
        pagerState = pagerState,
        currentPage = pagerState.currentPage,
        coroutineScope = coroutineScope,
        onGetStarted = { viewModel.completeOnboarding() },
    )
}

@Composable
fun OnboardingContent(
    pagerState: PagerState,
    currentPage: Int,
    coroutineScope: CoroutineScope,
    onGetStarted: () -> Unit,
) {
    val isLastPage = currentPage == ONBOARDING_PAGE_COUNT - 1
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            OnboardingSlide(page = page)
        }

        PagerDotsIndicator(
            pageCount = ONBOARDING_PAGE_COUNT,
            currentPage = currentPage,
            modifier = Modifier.padding(vertical = Spacing.m),
        )

        Button(
            onClick = {
                if (isLastPage) {
                    onGetStarted()
                } else {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(currentPage + 1)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(
                    id = if (isLastPage) {
                        R.string.onboarding_get_started
                    } else {
                        R.string.onboarding_next
                    },
                ),
            )
        }
    }
}

@Composable
private fun OnboardingSlide(page: Int) {
    val slide = ONBOARDING_PAGES[page]
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = slide.heroRes),
            contentDescription = null,
            modifier = Modifier.size(200.dp),
        )
        Spacer(modifier = Modifier.height(Spacing.xl))
        Text(
            text = stringResource(id = slide.headlineRes),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.m))
        Text(
            text = stringResource(id = slide.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.l),
        )
    }
}

@Composable
private fun PagerDotsIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            Surface(
                modifier = Modifier
                    .size(if (isActive) 12.dp else 8.dp)
                    .clip(CircleShape),
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                content = {},
            )
        }
    }
}

private const val ONBOARDING_PAGE_COUNT = 4

private data class OnboardingSlideRes(
    @DrawableRes val heroRes: Int,
    @StringRes val headlineRes: Int,
    @StringRes val bodyRes: Int,
)

private val ONBOARDING_PAGES = listOf(
    OnboardingSlideRes(
        heroRes = R.drawable.onboarding_hero_1,
        headlineRes = R.string.onboarding_slide_1_headline,
        bodyRes = R.string.onboarding_slide_1_body,
    ),
    OnboardingSlideRes(
        heroRes = R.drawable.onboarding_hero_2,
        headlineRes = R.string.onboarding_slide_2_headline,
        bodyRes = R.string.onboarding_slide_2_body,
    ),
    OnboardingSlideRes(
        heroRes = R.drawable.onboarding_hero_3,
        headlineRes = R.string.onboarding_slide_3_headline,
        bodyRes = R.string.onboarding_slide_3_body,
    ),
    OnboardingSlideRes(
        heroRes = R.drawable.onboarding_hero_4,
        headlineRes = R.string.onboarding_slide_4_headline,
        bodyRes = R.string.onboarding_slide_4_body,
    ),
)
