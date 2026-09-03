package com.a3322505a.guitarlearning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.core.IntervalTheory
import com.a3322505a.guitarlearning.training.AnswerResult
import com.a3322505a.guitarlearning.training.IntervalLevel
import com.a3322505a.guitarlearning.training.IntervalPayload
import com.a3322505a.guitarlearning.training.Question
import com.a3322505a.guitarlearning.training.QuestionState
import com.a3322505a.guitarlearning.training.TrainingSession
import com.a3322505a.guitarlearning.training.TrainingStateMachine
import com.a3322505a.guitarlearning.ui.choices.AnswerChoices
import com.a3322505a.guitarlearning.ui.components.PixelButton
import com.a3322505a.guitarlearning.ui.components.PixelButtonStyle
import com.a3322505a.guitarlearning.ui.components.PixelHeader
import com.a3322505a.guitarlearning.ui.components.PixelPanel
import com.a3322505a.guitarlearning.ui.components.PixelStats
import com.a3322505a.guitarlearning.ui.theme.PixelInkMuted

@Composable
fun BasicTheoryScreen(
    onBack: () -> Unit,
    onOpenIntervals: () -> Unit,
) {
    SelectionPage(title = "基础乐理", onBack = onBack) {
        PixelButton(
            text = "全音 / 半音与音程",
            onClick = onOpenIntervals,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun IntervalLevelScreen(
    selectedLevel: IntervalLevel,
    onBack: () -> Unit,
    onSelect: (IntervalLevel) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PixelHeader(title = "全音 / 半音与音程", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IntervalLevel.entries.forEach { level ->
                    PixelButton(
                        text = level.label + "\n" + level.subtitle,
                        onClick = { onSelect(level) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 58.dp),
                        style = if (level == selectedLevel) {
                            PixelButtonStyle.Primary
                        } else {
                            PixelButtonStyle.Secondary
                        },
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
fun IntervalTrainingScreen(
    trainingSession: TrainingSession,
    stateMachine: TrainingStateMachine,
    level: IntervalLevel,
    onBack: () -> Unit,
) {
    var state by remember(stateMachine, level) {
        mutableStateOf<QuestionState>(stateMachine.state)
    }
    var questionSequence by remember(stateMachine, level) { mutableIntStateOf(0) }
    val question = state.question
    val result = when (val current = state) {
        is QuestionState.AwaitingAnswer -> null
        is QuestionState.Correct -> current.result
        is QuestionState.Incorrect -> current.result
        is QuestionState.AwaitingSequenceAnswer,
        is QuestionState.SequenceProgress,
        is QuestionState.SequenceCompleted,
        is QuestionState.CorrectionRequired,
        is QuestionState.CorrectionConfirmed,
        is QuestionState.AwaitingSetAnswer,
        is QuestionState.SetProgress,
        is QuestionState.SetCompleted,
        is QuestionState.SetCorrectionRequired,
        is QuestionState.SetCorrectionProgress,
        is QuestionState.SetCorrectionConfirmed -> null
    }
    val session = trainingSession.currentSession

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PixelHeader(
                title = "全音 / 半音与音程",
                subtitle = level.label,
                onBack = onBack,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LessonCard(level)

                PixelPanel(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PixelStats(
                            correctCount = session.correctCount,
                            errorCount = session.questionCount - session.correctCount,
                        )
                        Text(
                            text = question.prompt,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        AnswerChoices(
                            questionId = questionSequence.toString() + ":" +
                                question.knowledgeItemId,
                            choices = question.choices,
                            submittedAnswer = result?.submittedAnswer,
                            correctAnswer = result?.correctAnswer,
                            onAnswer = { answer -> state = stateMachine.submitAnswer(answer) },
                            columns = minOf(question.choices.size, 3),
                        )
                    }
                }

                if (result != null) {
                    IntervalExplanationCard(question, result)
                    PixelButton(
                        text = "下一题",
                        onClick = {
                            state = stateMachine.nextQuestion()
                            questionSequence += 1
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonCard(level: IntervalLevel) {
    PixelPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = level.lessonTitle, style = MaterialTheme.typography.titleMedium)
            level.lessonLines.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun IntervalExplanationCard(question: Question, result: AnswerResult) {
    val payload = question.payload as IntervalPayload
    PixelPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "正确答案：" + result.correctAnswer,
                color = if (result.isCorrect) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (!result.isCorrect) {
                Text(text = "你选了：" + result.submittedAnswer)
            }

            payload.degreeSpan?.let { span ->
                Text(text = "字母跨度", color = PixelInkMuted)
                Text(
                    text = IntervalTheory.diatonicPath(
                        payload.startNote,
                        payload.endNote,
                        payload.octave,
                    ).joinToString("   "),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(text = (1..span).joinToString("   "))
                Text(text = "→ " + degreeName(span))
            }

            Text(text = "半音数量", color = PixelInkMuted)
            Text(
                text = IntervalTheory.chromaticPath(
                    payload.startNote,
                    payload.endNote,
                    payload.octave,
                ).joinToString(" — "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(text = "相差 ${payload.semitoneDistance} 个半音")
            Text(text = "所以是：" + result.correctAnswer, fontWeight = FontWeight.Bold)
        }
    }
}

private fun degreeName(span: Int): String = when (span) {
    2 -> "二度"
    3 -> "三度"
    4 -> "四度"
    5 -> "五度"
    6 -> "六度"
    7 -> "七度"
    8 -> "八度"
    else -> "$span 度"
}

@Composable
private fun SelectionPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            PixelHeader(title = title, onBack = onBack)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                PixelPanel(modifier = Modifier.fillMaxWidth(0.82f)) {
                    content()
                }
            }
        }
    }
}
