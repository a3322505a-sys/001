package com.a3322505a.guitarlearning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.training.AnswerResult
import com.a3322505a.guitarlearning.training.QuestionType
import com.a3322505a.guitarlearning.training.TrainingSession
import com.a3322505a.guitarlearning.ui.choices.AnswerChoices
import com.a3322505a.guitarlearning.ui.feedback.answerFeedback

/** A portrait-friendly, fretboard-free trainer for the fixed solfege mapping. */
@Composable
fun SolfeggioNoteMappingScreen(
    trainingSession: TrainingSession,
    onBack: (() -> Unit)? = null,
) {
    var question by remember { mutableStateOf(trainingSession.nextQuestion()) }
    var result by remember { mutableStateOf<AnswerResult?>(null) }
    var questionSequence by remember { mutableIntStateOf(0) }

    require(question.type == QuestionType.NoteToSolfege || question.type == QuestionType.SolfegeToNote) {
        "Mapping screen requires a note/solfege question"
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    TextButton(onClick = onBack) { Text(text = "返回") }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "唱名与音名",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (onBack != null) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            Text(
                text = question.prompt,
                style = MaterialTheme.typography.headlineSmall,
            )
            AnswerChoices(
                questionId = "${questionSequence}:${question.knowledgeItemId}",
                choices = question.choices,
                onAnswer = { answer ->
                    val submission = trainingSession.submitAnswer(answer)
                    if (submission.accepted) result = submission
                },
            )

            result?.let { submission ->
                val feedback = answerFeedback(question, submission)
                Text(
                    text = "${feedback.symbol} ${feedback.answerPair}",
                    style = MaterialTheme.typography.titleMedium,
                )
                feedback.correctAnswerText?.let { Text(text = it) }
                Text(text = "本题反应时间：${submission.responseMs} ms")
                Button(onClick = {
                    question = trainingSession.nextQuestion()
                    result = null
                    questionSequence += 1
                }) {
                    Text(text = "下一题")
                }
            }

            Spacer(modifier = Modifier.padding(2.dp))
            val session = trainingSession.currentSession
            Text(text = "本次训练：正确 ${session.correctCount} · 错误 ${session.questionCount - session.correctCount}")
            Text(text = "平均反应时间：${session.avgResponseMs.toLong()} ms")
        }
    }
}
