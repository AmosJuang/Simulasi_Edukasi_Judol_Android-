package com.example.simulasijudolpapb

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel

class ResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val simulatorVM: SimulatorViewModel = viewModel()
                    ResultScreen(
                        vm = simulatorVM,
                        onBack = { finish() }
                    ) { text ->
                        val send = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, text)
                            type = "text/plain"
                        }
                        startActivity(Intent.createChooser(send, "Share anti-judi"))
                    }
                }
            }
        }
    }
}
