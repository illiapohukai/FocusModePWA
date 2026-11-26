package io.github.illiapohukai.focusmode.feature.missions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.LocalMotion
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import io.github.illiapohukai.focusmode.core.domain.mission.MissionDraftPhase
import io.github.illiapohukai.focusmode.core.domain.mission.MissionGenerationOwner
import io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GenerationPhase { WORKING, SUCCESS, ENDED }

data class GenerateUiState(val phase: GenerationPhase = GenerationPhase.WORKING, val slow: Boolean = false)

/**
 * Observes the one generation owner; it never starts a request. The phase comes from the durable
 * draft: GENERATING = working, BRIEFING = success (a fully validated plan exists), anything else
 * = ended (failure or cancel; the Creation screen shows the safe error and keeps every input).
 */
@HiltViewModel
class MissionGenerateViewModel @Inject constructor(
    missions: MissionRepository,
    private val owner: MissionGenerationOwner,
) : ViewModel() {
    private val slow = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            owner.reconcile()
            delay(SLOW_NOTICE_MS)
            slow.value = true
        }
    }

    val uiState: StateFlow<GenerateUiState> = combine(missions.observeDraft(), slow) { draft, isSlow ->
        GenerateUiState(
            phase = when {
                draft?.phase == MissionDraftPhase.BRIEFING && draft.plan != null -> GenerationPhase.SUCCESS
                draft?.phase == MissionDraftPhase.GENERATING -> GenerationPhase.WORKING
                else -> GenerationPhase.ENDED
            },
            slow = isSlow,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GenerateUiState())

    /** "Abbrechen": the request is cancelled; a late answer is ignored; inputs stay. */
    fun cancel(onDone: () -> Unit) {
        viewModelScope.launch {
            owner.cancel()
            onDone()
        }
    }

    private companion object { const val SLOW_NOTICE_MS = 9_000L }
}

@Composable
fun MissionGenerateRoute(actions: MissionFlowActions, viewModel: MissionGenerateViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var left by rememberSaveable { mutableStateOf(false) }
    // Failure (or a cancel from elsewhere): back to the form, where the safe message is shown.
    LaunchedEffect(state.phase) {
        if (state.phase == GenerationPhase.ENDED && !left) { left = true; actions.onGenerationEnded() }
    }
    val cancel = { if (!left) { left = true; viewModel.cancel(actions.onGenerationEnded) } }
    BackHandler(enabled = state.phase == GenerationPhase.WORKING) { cancel() }
    MissionGenerateScreen(
        state = state,
        onCancel = cancel,
        onSuccessShown = { if (!left) { left = true; actions.onBriefing() } },
    )
}

@Composable
fun MissionGenerateScreen(state: GenerateUiState, onCancel: () -> Unit, onSuccessShown: () -> Unit) {
    val reduced = LocalMotion.current.reduced
    val success = state.phase == GenerationPhase.SUCCESS
    // The status is written shortly after the screen appears, so it is announced once.
    var statusReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(120); statusReady = true }
    Box(
        Modifier.fillMaxSize().background(FocusPalette.Cream).background(
            Brush.radialGradient(listOf(FocusPalette.Lavender.copy(alpha = 0.20f), Color.Transparent), center = Offset.Unspecified, radius = 900f),
        ),
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).testTag("mission_generate")) {
            FlowTopBar("Focus Mission", onBack = null)
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 520.dp).padding(horizontal = Spacing.screenHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val artWidth = (maxWidth * 0.34f).coerceIn(100.dp, 136.dp)
                    Box(Modifier.width(artWidth).aspectRatio(266f / 358f), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.requiredSize(artWidth * 1.5f).clearAndSetSemantics { }) {
                            drawCircle(Brush.radialGradient(listOf(Color(0x1F7C3AED), Color(0x0D7C3AED), Color.Transparent)), radius = size.minDimension / 2f, center = Offset(size.width / 2f, size.height * 0.54f))
                        }
                        MissionOperatorArt(succeeded = success, reducedMotion = reduced, onSuccessShown = onSuccessShown, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    if (success) "Deine Mission ist bereit" else "Deine Mission entsteht",
                    style = MaterialTheme.typography.headlineMedium, color = FocusPalette.Graphite, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp).semantics { heading() },
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    "Wir strukturieren dein Ziel und bereiten die nächsten Schritte vor.",
                    style = MaterialTheme.typography.bodyMedium, color = FocusPalette.GraphiteSoft, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 292.dp),
                )
                Spacer(Modifier.height(20.dp))
                RouteDots(success, reduced)
                Spacer(Modifier.height(12.dp))
                val status = when {
                    success -> "Mission bereit"
                    !statusReady -> ""
                    state.slow -> "Das dauert einen Moment länger. Deine Eingaben bleiben erhalten."
                    else -> "Mission wird erstellt …"
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = if (success) Color(0xFF15803D) else FocusPalette.Purple,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 300.dp).heightIn(min = 18.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("generate_status"),
                )
                Spacer(Modifier.height(26.dp))
                // Success: the plan exists, so the screen only reports it; cancel steps aside.
                Box(
                    Modifier.alpha(if (success) 0f else 1f).heightIn(min = 48.dp).widthIn(min = 132.dp).clip(Radii.pill)
                        .background(Color.White.copy(alpha = 0.66f)).border(BorderStroke(1.dp, FocusPalette.Outline), Radii.pill)
                        .clickable(enabled = !success, role = Role.Button, onClick = onCancel)
                        .padding(horizontal = 22.dp).testTag("generate_cancel"),
                    contentAlignment = Alignment.Center,
                ) { Text("Abbrechen", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = Color(0xFF5C544D)) }
            }
        }
    }
}

/** PWA `.mgen-route`: four calm dots; green and still once the Mission is ready. Decorative. */
@Composable
private fun RouteDots(success: Boolean, reduced: Boolean) {
    val transition = rememberInfiniteTransition(label = "route")
    Box(Modifier.width(74.dp).height(8.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(2.dp).clip(Radii.pill).background(if (success) Color(0x3815803D) else Color(0x296D28D9)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(4) { i ->
                val alpha = if (success) 1f else if (reduced) {
                    transition.animateFloat(0.3f, 0.7f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "calm").value
                } else {
                    transition.animateFloat(
                        0.22f, 0.22f,
                        infiniteRepeatable(keyframes { durationMillis = 1600; 0.22f at 0; 1f at 384; 0.22f at 992; 0.22f at 1600 }, initialStartOffset = StartOffset(i * 200)),
                        label = "dot$i",
                    ).value
                }
                Box(Modifier.size(8.dp).clip(CircleShape).alpha(alpha).background(if (success) Color(0xFF15803D) else FocusPalette.PurpleBright))
            }
        }
    }
}
