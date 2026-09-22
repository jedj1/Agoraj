package com.newoether.agora.ui.chat

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.R
import com.newoether.agora.ui.common.AgoraHaptics
import com.newoether.agora.ui.components.TypewriterMode
import com.newoether.agora.ui.components.TypewriterText
import com.newoether.agora.ui.motion.AgoraMotionPolicy
import com.newoether.agora.util.MainThreadProbe
import com.newoether.agora.ui.motion.MotionAwareCircularProgressIndicator as CircularProgressIndicator
import kotlinx.coroutines.delay

internal fun AnimatedContentTransitionScope<Pair<Boolean, Boolean>>.chatMainContentTransition(
    motionPolicy: AgoraMotionPolicy,
    pivotY: Float,
): ContentTransform {
    val targetNewChat = targetState.first
    val targetShowLaunch = targetState.second
    val initialNewChat = initialState.first
    val initialShowLaunch = initialState.second

    return if (targetNewChat && (targetShowLaunch != initialShowLaunch || targetNewChat != initialNewChat)) {
        val fadeInSpec = tween<Float>(500)
        val enter = if (motionPolicy.allowSpatialTransitions) {
            val enterSpec = tween<Float>(
                700,
                easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1.0f),
            )
            fadeIn(animationSpec = fadeInSpec) +
                scaleIn(
                    initialScale = 0.6f,
                    transformOrigin = TransformOrigin(0.5f, pivotY),
                    animationSpec = enterSpec,
                )
        } else {
            fadeIn(animationSpec = fadeInSpec)
        }
        enter
            .togetherWith(fadeOut(animationSpec = tween(300)))
    } else if (!targetNewChat && !initialNewChat) {
        // Switching between existing conversations: no animation
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        // Returning from new-chat to an existing conversation
        fadeIn(animationSpec = tween(300))
            .togetherWith(fadeOut(animationSpec = tween(300)))
    }
}

@Composable
internal fun ChatWelcomeContent(
    bottomBarHeight: Dp,
    windowHeightDp: Float,
    topBarH: Dp,
    newChatEntryId: Long,
    newChatMotion: NewChatMotionPolicy,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomBarHeight),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            val welcomeText = stringResource(R.string.welcome_to_agora)
            val availableWelcomeHeight =
                windowHeightDp +
                    topBarH.value / 2f -
                    bottomBarHeight.value
            val welcomeTopPadding =
                (availableWelcomeHeight / 2f).coerceAtLeast(0f).dp
            val welcomeModifier =
                Modifier.padding(top = welcomeTopPadding)
            TypewriterText(
                text = welcomeText,
                animationKey = newChatEntryId,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                typeSpeedMs = 100,
                animate = newChatMotion.animateWelcomeText,
                mode = TypewriterMode.TEXT_GRADIENT,
                modifier = welcomeModifier,
            )
        }
    }
}

@Composable
internal fun BoxScope.ChatSelectionOverlay(
    shareSelectionActive: Boolean,
    motionPolicy: AgoraMotionPolicy,
    bottomBarHeight: Dp,
    selectableShareMessageIds: Set<String>,
    selectedShareMessageIds: Set<String>,
    conversationInteraction: ConversationInteractionProjection,
    haptics: AgoraHaptics,
    onShareMessages: (Set<String>) -> Unit,
) {
    AnimatedVisibility(
        visible = shareSelectionActive,
        enter = if (motionPolicy.allowSpatialTransitions) {
            fadeIn(tween(220)) + scaleIn(
                initialScale = 0.86f,
                animationSpec = tween(220),
            )
        } else {
            fadeIn(tween(220))
        },
        exit = if (motionPolicy.allowSpatialTransitions) {
            fadeOut(tween(180)) + scaleOut(
                targetScale = 0.86f,
                animationSpec = tween(180),
            )
        } else {
            fadeOut(tween(180))
        },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = bottomBarHeight + 10.dp),
    ) {
        ShareSelectionFab(
            allSelected = selectableShareMessageIds.isNotEmpty() &&
                selectedShareMessageIds.containsAll(selectableShareMessageIds),
            hasSelection = selectedShareMessageIds.isNotEmpty(),
            onDismiss = {
                conversationInteraction.dismissShareSelection()
            },
            onToggleAll = {
                haptics.selection()
                conversationInteraction.toggleAllShareMessages()
            },
            onConfirm = {
                val selection = conversationInteraction.takeShareSelection()
                if (selection.isNotEmpty()) {
                    onShareMessages(selection)
                }
            },
        )
    }
}

@Composable
internal fun ChatSwitchingOverlay(isSwitching: Boolean, isTransitioningToNewChat: Boolean) {
    AnimatedVisibility(
        visible = isSwitching && !isTransitioningToNewChat,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                SwitchDiagnosticsLabel()
            }
        }
    }
}

/**
 * Plain-text evidence under the cover spinner: pipeline phase, main thread state, the
 * currently running Looper callback and the main-thread stack sampled at 10Hz by
 * [MainThreadProbe]. While the main thread is stuck nothing recomposes, so the label
 * refreshes with whatever the sampler recorded as soon as a frame gets released.
 */
@Composable
private fun SwitchDiagnosticsLabel() {
    var snapshot by remember { mutableStateOf<MainThreadProbe.Snapshot?>(null) }
    LaunchedEffect(Unit) {
        MainThreadProbe.ensureInstalled()
        MainThreadProbe.startSampling()
        try {
            while (true) {
                snapshot = MainThreadProbe.snapshot()
                delay(120L)
            }
        } finally {
            MainThreadProbe.stopSampling()
        }
    }
    val data = snapshot ?: return
    Text(
        text = buildString {
            appendLine("cover ${formatProbeMs(data.overlayMs)} ${data.sessionLabel}")
            appendLine("phase ${data.phase.ifBlank { "-" }} ${data.phaseMs}ms")
            if (data.busyMessage.isNotBlank()) {
                appendLine("main BUSY ${data.busyMs}ms")
                appendLine("msg ${data.busyMessage}")
            } else {
                appendLine("main idle ${data.idleMs}ms")
            }
            appendLine("main stack (-${data.stackAgeMs}ms)")
            data.mainStack.forEach { appendLine(it) }
            if (data.blockHistory.isNotEmpty()) {
                appendLine("recent blocks (>${MainThreadProbe.OVERRUN_WARN_MS}ms):")
                data.blockHistory.forEach { appendLine(" $it") }
            }
        }.trimEnd(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 13.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = 20.dp, start = 16.dp, end = 16.dp)
            .widthIn(max = 640.dp)
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
    )
}

private fun formatProbeMs(ms: Long): String {
    val seconds = ms / 1000
    val centis = (ms % 1000) / 10
    return "$seconds.${centis.toString().padStart(2, '0')}s"
}
