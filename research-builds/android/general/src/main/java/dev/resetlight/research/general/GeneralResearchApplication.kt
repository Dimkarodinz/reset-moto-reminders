package dev.resetlight.research.general

import android.app.Application

class GeneralResearchApplication : Application() {
    val container: GeneralResearchAppContainer by lazy { GeneralResearchAppContainer(this) }
}
