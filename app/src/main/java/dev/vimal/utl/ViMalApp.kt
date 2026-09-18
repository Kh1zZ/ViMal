package dev.vimal.utl

import android.app.Application

class ViMalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Future: DI initialization (Hilt/Koin) goes here when added
    }
}
