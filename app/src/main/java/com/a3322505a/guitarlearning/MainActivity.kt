package com.a3322505a.guitarlearning

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.storage.PersistentTrainingStore
import com.a3322505a.guitarlearning.training.AnswerResult
import com.a3322505a.guitarlearning.training.TrainingEngine
import com.a3322505a.guitarlearning.training.TrainingSession
import com.a3322505a.guitarlearning.ui.choices.AnswerChoices
import com.a3322505a.guitarlearning.ui.feedback.answerFeedback
import com.a3322505a.guitarlearning.ui.theme.GuitarLearningTheme
import com.a3322505a.guitarlearning.ui.fretboard.Fretboard

class MainActivity : ComponentActivity() {
    private lateinit var trainingSession: TrainingSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PersistentTrainingStore(applicationContext)
        trainingSession = TrainingSession(
            engine = TrainingEngine(
                settings = store.loadSettings(),
                progressProvider = { store.loadProgress() },
            ),
            store = store,
        )
        setContent {
            GuitarLearningTheme {
                GuitarLearningApp(trainingSession)
            }
        }
    }

    override fun onStop() {
        if (::trainingSession.isInitialized) trainingSession.finish()
        super.onStop()
    }
}

@Composable
fun GuitarLearningApp(trainingSession: TrainingSession) {
    var question by remember { mutableStateOf(trainingSession.currentQuestion()) }
    var result by remember { mutableStateOf<AnswerResult?>(null) }
    var questionSequence by remember { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "电吉他训练", style = MaterialTheme.typography.headlineMedium)
            Text(text = "V0.1 · 固定唱名训练", style = MaterialTheme.typography.bodyLarge)
            Text(text = question.prompt, style = MaterialTheme.typography.titleMedium)
            Fretboard(selectedPosition = question.fretPosition)
            AnswerChoices(
                questionId = "${questionSequence}:${question.knowledgeItemId}",
                choices = question.choices,
                onAnswer = { answer ->
                    val submission = trainingSession.submitAnswer(answer)
                    if (submission.accepted) {
                        result = submission
                    }
                },
            )
            result?.let { submission ->
                val feedback = answerFeedback(question, submission)
                if (feedback.isCorrect) {
                    Text(
                        text = "${feedback.symbol} ${feedback.answerPair}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                } else {
                    Text(text = feedback.symbol, style = MaterialTheme.typography.titleMedium)
                }
                feedback.correctAnswerText?.let { Text(text = it) }
                feedback.correctPositionText?.let { Text(text = it) }
                Text(text = "本题反应时间：${submission.responseMs} ms")
                Button(onClick = {
                    question = trainingSession.nextQuestion()
                    result = null
                    questionSequence += 1
                }) {
                    Text(text = "下一题")
                }
            }
            val session = trainingSession.currentSession
            Text(
                text = "本次训练：正确 ${session.correctCount} · 错误 ${session.questionCount - session.correctCount}",
            )
            Text(text = "平均反应时间：${session.avgResponseMs.toLong()} ms")
        }
    }
}
