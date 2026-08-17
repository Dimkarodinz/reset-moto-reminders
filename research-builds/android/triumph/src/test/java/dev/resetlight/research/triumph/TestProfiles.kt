package dev.resetlight.research.triumph

import dev.resetlight.profiles.AdapterProfile
import dev.resetlight.profiles.AdapterProfileLoader
import dev.resetlight.profiles.EcuProfile
import dev.resetlight.profiles.EcuProfileLoader

internal object TestProfiles {
    fun adapter(): AdapterProfile = resource("vlinker-mc-android.adaptermap.yaml")
        .use(AdapterProfileLoader()::load)

    fun ecu(): EcuProfile = resource("tiger-900-gt-pro-2021.ecumap.yaml")
        .use(EcuProfileLoader()::load)

    private fun resource(name: String) = checkNotNull(
        TestProfiles::class.java.getResourceAsStream("/profiles/$name"),
    ) { "Missing generated test profile $name" }
}
