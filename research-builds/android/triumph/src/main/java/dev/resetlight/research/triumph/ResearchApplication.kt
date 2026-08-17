package dev.resetlight.research.triumph

import android.app.Application

class ResearchApplication : Application() {
    val container: ResearchAppContainer by lazy { ResearchAppContainer(this) }
}
