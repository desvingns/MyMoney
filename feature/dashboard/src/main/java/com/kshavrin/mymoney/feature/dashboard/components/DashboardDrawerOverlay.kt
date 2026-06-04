package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.kshavrin.mymoney.core.ui.theme.LocalMotion
import com.kshavrin.mymoney.core.ui.theme.dashboardDrawerPanelContainer
import com.kshavrin.mymoney.core.ui.theme.dashboardDrawerPanelContent
import com.kshavrin.mymoney.core.ui.theme.dashboardDrawerScrim

enum class DrawerSide {
    Left,
    Right,
}

@Composable
fun DashboardDrawerOverlay(
    open: Boolean,
    side: DrawerSide,
    widthFraction: Float = 0.62f,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val motion = LocalMotion.current
    val animationSpec = tween<Float>(
        durationMillis = motion.drawerOverlayEnterExitDuration,
        easing = motion.easeStandard,
    )
    val slideAnimationSpec = tween<IntOffset>(
        durationMillis = motion.drawerOverlayEnterExitDuration,
        easing = motion.easeStandard,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(animationSpec = animationSpec),
            exit = fadeOut(animationSpec = animationSpec),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.dashboardDrawerScrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = open,
            modifier = Modifier
                .align(if (side == DrawerSide.Left) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight(),
            enter = slideInHorizontally(
                animationSpec = slideAnimationSpec,
            ) { width -> if (side == DrawerSide.Left) -width else width } + fadeIn(animationSpec = animationSpec),
            exit = slideOutHorizontally(
                animationSpec = slideAnimationSpec,
            ) { width -> if (side == DrawerSide.Left) -width else width } + fadeOut(animationSpec = animationSpec),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.dashboardDrawerPanelContainer,
                contentColor = MaterialTheme.colorScheme.dashboardDrawerPanelContent,
            ) {
                content()
            }
        }
    }
}
