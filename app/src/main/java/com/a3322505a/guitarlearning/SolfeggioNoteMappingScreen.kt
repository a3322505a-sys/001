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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.training.AnswerResult
import com.a3322505a.guitarlearning.training.CorrectErrorStats
import com.a3322505a.guitarlearning.training.QuestionType
import com.a3322505a.guitarlearning.training.TrainingSession
import com.a3322505a.guitarlearning.ui.choices.AnswerChoices
import com.a3322505a.guitarlearning.ui.feedback.answerFeedback

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
    var question by remember { mutableStateOf(trainingSession.nextQuestion()) }
    var result by remember { mutableStateOf<AnswerResult?>(null) }
    var questionSequence by remember { mutableIntStateOf(0) }

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

                    val session = trainingSession.currentSession
                    CorrectErrorStats(session = session)

                    AnswerChoices(
                        questionId = questionSequence.toString() + ":" + question.knowledgeItemId,
                        choices = question.choices,
                        onAnswer = { answer ->
                            val submission = trainingSession.submitAnswer(answer)
                            if (submission.accepted) result = submission
                        },
                    )

                    result?.let { submission ->
                        val feedback = answerFeedback(question, submission)
                        Text(
                            text = feedback.symbol + " " + feedback.answerPair,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        feedback.correctAnswerText?.let { Text(text = it) }
                        Text(text = "本题反应时间：" + submission.responseMs + " ms")
                        Button(onClick = {
                            question = trainingSession.nextQuestion()
                            result = null
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
