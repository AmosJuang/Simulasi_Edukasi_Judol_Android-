package com.example.simulasijudolpapb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel

class LossAnalyticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val simulatorVM: SimulatorViewModel = viewModel()
                    AnalyticsScreen(simulatorVM) { finish() }
                }
            }
        }
    }
}
