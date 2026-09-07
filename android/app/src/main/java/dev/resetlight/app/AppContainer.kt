package dev.resetlight.app

import android.content.Context
import dev.resetlight.BuildConfig
import dev.resetlight.features.service.ClusterFingerprintGate
import dev.resetlight.logging.EventJournal
import dev.resetlight.logging.FileJournalSink
import dev.resetlight.profiles.AdapterProfileLoader
import dev.resetlight.profiles.DtcDescriptionLookup
import dev.resetlight.profiles.DtcMapLoader
import dev.resetlight.profiles.DtcTranslationLoader
import dev.resetlight.profiles.EcuProfileLoader
import dev.resetlight.profiles.EngineFamilyProfileLoader
import dev.resetlight.profiles.InstrumentFamilyProfileLoader
import dev.resetlight.profiles.MotorcycleProfileCatalogLoader
import dev.resetlight.profiles.LocalizedDtcDescriptions
import java.util.Locale
import dev.resetlight.transport.bluetooth.AndroidBluetoothFacade
import dev.resetlight.transport.bluetooth.AndroidBleAdapterFacade
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val profile = applicationContext.assets
        .open("profiles/vlinker-mc-android.adaptermap.yaml")
        .use(AdapterProfileLoader()::load)
    private val obdlinkCxProfile = applicationContext.assets
        .open("profiles/obdlink-cx.adaptermap.yaml")
        .use(AdapterProfileLoader()::load)
    private val obdlinkMxProfile = applicationContext.assets
        .open("profiles/obdlink-mx-android.adaptermap.yaml")
        .use(AdapterProfileLoader()::load)
    private val obdlinkLxProfile = applicationContext.assets
        .open("profiles/obdlink-lx-android.adaptermap.yaml")
        .use(AdapterProfileLoader()::load)
    private val obdlinkMxPlusProfile = applicationContext.assets
        .open("profiles/obdlink-mx-plus-android.adaptermap.yaml")
        .use(AdapterProfileLoader()::load)
    private val bluetooth = AndroidBluetoothFacade(applicationContext)
    val ecuProfile = applicationContext.assets
        .open("profiles/tiger-900-gt-pro-2021.ecumap.yaml")
        .use(EcuProfileLoader()::load)
    private val modernEngineFamily = applicationContext.assets
        .open("profiles/triumph-modern-can.enginefamily.yaml")
        .use(EngineFamilyProfileLoader()::load)
    private val instrumentFamilies = listOf(
        "triumph-original-tft.instrumentfamily.yaml",
        "triumph-updated-tft.instrumentfamily.yaml",
        "triumph-hybrid-display.instrumentfamily.yaml",
    ).associate { asset ->
        applicationContext.assets.open("profiles/$asset")
            .use(InstrumentFamilyProfileLoader()::load)
            .let { it.id to it }
    }
    private val motorcycleCatalog = applicationContext.assets
        .open("profiles/triumph.motorcycleprofiles.yaml")
        .use { source ->
            MotorcycleProfileCatalogLoader().load(
                source,
                engineFamilies = mapOf(modernEngineFamily.id to modernEngineFamily),
                instrumentFamilies = instrumentFamilies,
            )
        }
    val dtcDictionary = applicationContext.assets
        .open("profiles/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml")
        .use(DtcMapLoader()::load)

    /**
     * English is authoritative and the default. For a supported non-English
     * phone locale, overlay the matching translation; the English dictionary
     * still resolves every code and supplies any message the overlay omits. A
     * missing or unreadable overlay silently falls back to English.
     */
    val dtcDescriptions: DtcDescriptionLookup = run {
        val language = Locale.getDefault().language
        if (language !in DtcTranslationLoader.SUPPORTED_LOCALES) return@run dtcDictionary
        val asset = "profiles/triumph-tiger-900-gt-pro-2021.$language.dtctranslation.yaml"
        val translation = runCatching {
            applicationContext.assets.open(asset).use(DtcTranslationLoader()::load)
        }.getOrNull() ?: return@run dtcDictionary
        LocalizedDtcDescriptions(dtcDictionary, translation)
    }
    private val journalFile = File(
        applicationContext.filesDir,
        "diagnostic-logs/session-${Instant.now().toEpochMilli()}.jsonl",
    )
    private val journal = EventJournal(scope, FileJournalSink(journalFile))

    init {
        check(dtcDictionary.isApplicableTo(ecuProfile, "engine_ecu")) {
            "The packaged DTC dictionary does not match the packaged engine ECU profile"
        }
    }

    val adapterSession = AdapterSessionOwner(
        profile = profile,
        additionalProfiles = listOf(
            obdlinkCxProfile,
            obdlinkMxProfile,
            obdlinkLxProfile,
            obdlinkMxPlusProfile,
        ),
        bluetooth = bluetooth,
        bleAdapter = AndroidBleAdapterFacade(applicationContext),
        journal = journal,
        scope = scope,
        distanceUnits = ecuProfile.distanceUnits,
        engineReadOnlyCaptureProfile = ecuProfile.engineReadOnlyCapture,
        instrumentReadOnlyCaptureProfile = ecuProfile.instrumentReadOnlyCapture,
        dtcReadProfile = ecuProfile.diagnosticTroubleCodes.read,
        dtcDescriptions = dtcDescriptions,
        dtcClearProfile = ecuProfile.diagnosticTroubleCodes.clear,
        engineSecurityAccessProfile = ecuProfile.engineSecurityAccess,
        serviceReminderProfile = ecuProfile.serviceReminder,
        clusterFingerprintGate = ClusterFingerprintGate(ecuProfile),
        motorcycleId = ecuProfile.motorcycleId,
        motorcycleCatalog = motorcycleCatalog,
        writesEnabled = BuildConfig.WRITE_OPERATIONS_ENABLED,
        engineResponseCanId = ecuProfile.engineEcu.transport.responseCanId,
        instrumentResponseCanId = ecuProfile.instrumentCluster.transport.responseCanId,
    )
}
