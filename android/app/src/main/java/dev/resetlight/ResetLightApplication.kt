package dev.resetlight

import android.app.Application
import dev.resetlight.app.AppContainer

class ResetLightApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
