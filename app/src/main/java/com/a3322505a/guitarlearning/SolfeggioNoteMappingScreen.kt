package com.a3322505a.guitarlearning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.training.CorrectErrorStats
import com.a3322505a.guitarlearning.training.CORRECT_FEEDBACK_DURATION_MS
import com.a3322505a.guitarlearning.training.QuestionState
import com.a3322505a.guitarlearning.training.QuestionType
import com.a3322505a.guitarlearning.training.TrainingSession
import com.a3322505a.guitarlearning.training.TrainingStateMachine
import com.a3322505a.guitarlearning.ui.choices.AnswerChoices
import com.a3322505a.guitarlearning.ui.feedback.AnswerReview
import kotlinx.coroutines.delay

private val mappingQuestionTypes = setOf(
    QuestionType.NoteToSolfege,
    QuestionType.SolfegeToNote,
    QuestionType.NoteToDegree,
    QuestionType.DegreeToNote,
    QuestionType.SolfegeToDegree,
    QuestionType.DegreeToSolfege,
)

/** A fretboard-free trainer for the fixed note/solfege/degree mapping. */
@Composable
fun SolfeggioNoteMappingScreen(
    trainingSession: TrainingSession,
    onBack: (() -> Unit)? = null,
) {
    val stateMachine = remember(trainingSession) { TrainingStateMachine(trainingSession) }
    var state by remember(trainingSession) { mutableStateOf<QuestionState>(stateMachine.state) }
    var questionSequence by remember(trainingSession) { mutableIntStateOf(0) }
    val question = state.question
    val submittedAnswer = when (val current = state) {
        is QuestionState.AwaitingAnswer -> null
        is QuestionState.Correct -> current.result
        is QuestionState.Incorrect -> current.result
    }

    LaunchedEffect(state) {
        val correctState = state as? QuestionState.Correct ?: return@LaunchedEffect
        delay(CORRECT_FEEDBACK_DURATION_MS)
        if (state === correctState) {
            state = stateMachine.nextQuestion()
            questionSequence += 1
        }
    }

    require(question.type in mappingQuestionTypes) {
        "Mapping screen requires one of the six note/solfege/degree directions"
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.sizeIn(minWidth = 64.dp, minHeight = 48.dp),
                    ) {
                        Text(text = "返回")
                    }
                } else {
                    Spacer(modifier = Modifier.width(64.dp))
                }
                Text(
                    text = "音名 / 唱名 / 级数",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.width(64.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = question.prompt,
                        style = MaterialTheme.typography.titleLarge,
                    )

                    CorrectErrorStats(session = trainingSession.currentSession)

                    AnswerChoices(
                        questionId = questionSequence.toString() + ":" + question.knowledgeItemId,
                        choices = question.choices,
                        submittedAnswer = submittedAnswer?.submittedAnswer,
                        correctAnswer = submittedAnswer?.correctAnswer,
                        onAnswer = { answer -> state = stateMachine.submitAnswer(answer) },
                    )

                    when (val current = state) {
                        is QuestionState.AwaitingAnswer -> Unit
                        is QuestionState.Correct -> {
                            AnswerReview(question = question, result = current.result)
                        }
                        is QuestionState.Incorrect -> {
                            AnswerReview(question = question, result = current.result)
                            Button(onClick = {
                                state = stateMachine.nextQuestion()
                                questionSequence += 1
                            }) {
                                Text(text = "下一题")
                            }
                        }
                    }
                }
            }
        }
    }
}
