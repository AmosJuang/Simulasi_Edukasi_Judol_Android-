package com.example.simulasijudolpapb

import android.app.Application
import com.google.firebase.FirebaseApp

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase early for the whole process
        FirebaseApp.initializeApp(this)
    }
}

