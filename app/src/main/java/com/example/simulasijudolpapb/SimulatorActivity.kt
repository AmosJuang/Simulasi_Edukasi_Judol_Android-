package com.example.simulasijudolpapb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class SimulatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Firebase and sign in anonymously for this session
        FirebaseApp.initializeApp(this)
        Firebase.auth.signInAnonymously().addOnCompleteListener { task ->
            // Ignoring result here; ViewModel writes will include current user id if available
            if (!task.isSuccessful) {
                // optionally log or show a toast in debug
            }
        }

        setContent {
            MaterialTheme {
                Surface {
                    AppRoot()
                }
            }
        }
    }
}
