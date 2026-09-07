package dev.resetlight.features.service

import dev.resetlight.diagnostics.CanResponseExtractor
import dev.resetlight.diagnostics.DiagnosticParseException
import dev.resetlight.diagnostics.DiagnosticWriteChannel
import dev.resetlight.diagnostics.WriteIntent
import dev.resetlight.diagnostics.diagnosticHexBytes
import dev.resetlight.diagnostics.elmConfigurationAccepted
import dev.resetlight.diagnostics.hexOnly
import dev.resetlight.diagnostics.u
import dev.resetlight.domain.DistanceUnit
import dev.resetlight.domain.UiMessage
import dev.resetlight.domain.UiText
import dev.resetlight.profiles.CombinedServiceWriteProfile
import dev.resetlight.profiles.InstrumentFamilyProfile
import dev.resetlight.profiles.ServiceReminderStrategy
import dev.resetlight.profiles.securityKeyFor
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException

data class CombinedServiceReminderPayload(
    val odometerKm: Int,
    val nextServiceOdometerKm: Int,
    val frames: List<String>,
)

class InstrumentSecurityKeyDerivation {
    fun derive(seedHex: String, aesKeyHex: String): String {
        val seed = seedHex.diagnosticHexBytes()
        val key = aesKeyHex.diagnosticHexBytes()
        require(seed.size == AES_BLOCK_BYTES) { "Instrument seed must contain 16 bytes" }
        require(key.size == AES_BLOCK_BYTES) { "Instrument key must contain 16 bytes" }
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(seed).copyOfRange(0, DERIVED_KEY_BYTES).toHex()
    }

    private companion object {
        const val AES_BLOCK_BYTES = 16
        const val DERIVED_KEY_BYTES = 4
    }
}

class CombinedServiceReminderPayloadBuilder(
    private val profile: CombinedServiceWriteProfile,
) {
    fun build(
        strategy: ServiceReminderStrategy,
        intervalKm: Int,
        nextServiceDate: LocalDate,
        a500Payload: String,
        a000Payload: String,
    ): CombinedServiceReminderPayload {
        require(intervalKm in profile.minimumDistanceKm..profile.maximumDistanceKm)
        require(intervalKm % profile.distanceStepKm == 0)
        val year = nextServiceDate.year - profile.yearBase
        require(year in 0..0xFF)

        val odometerBytes = didData(a500Payload, A500, expectedBytes = 3)
        val odometerKm = odometerBytes.unsignedInt()
        val nextServiceOdometerKm = Math.addExact(odometerKm, intervalKm)

        val payload = when (strategy) {
            ServiceReminderStrategy.UPDATED_COMBINED -> {
                require(nextServiceOdometerKm <= 0xFFFFFF)
                val preserved = didData(a000Payload, A000, expectedBytes = 12).copyOfRange(0, 3)
                profile.requestPrefix.diagnosticHexBytes() +
                    odometerBytes +
                    nextServiceOdometerKm.toBytes(3) +
                    preserved +
                    byteArrayOf(year.toByte(), nextServiceDate.monthValue.toByte(), nextServiceDate.dayOfMonth.toByte())
            }
            ServiceReminderStrategy.HYBRID_COMBINED -> {
                didData(a000Payload, A000, expectedBytes = 5)
                val encodedDistance = nextServiceOdometerKm / profile.distanceStepKm
                require(encodedDistance <= 0xFFFF)
                profile.requestPrefix.diagnosticHexBytes() +
                    encodedDistance.toBytes(2) +
                    byteArrayOf(year.toByte(), nextServiceDate.monthValue.toByte(), nextServiceDate.dayOfMonth.toByte())
            }
            ServiceReminderStrategy.ORIGINAL_SPLIT -> error("Original split reset does not use a combined payload")
        }
        return CombinedServiceReminderPayload(
            odometerKm = odometerKm,
            nextServiceOdometerKm = nextServiceOdometerKm,
            frames = payload.toIsoTpFrames(),
        )
    }

    private fun didData(payload: String, did: String, expectedBytes: Int): ByteArray {
        val normalized = payload.hexOnly()
        val prefix = "62$did"
        require(normalized.startsWith(prefix)) { "Unexpected response for DID $did" }
        return normalized.substring(prefix.length).diagnosticHexBytes().also {
            require(it.size == expectedBytes) {
                "Response for DID $did has ${it.size} bytes; expected $expectedBytes"
            }
        }
    }

    private companion object {
        const val A500 = "A500"
        const val A000 = "A000"
    }
}

class CombinedServiceReminderResetService(
    private val family: InstrumentFamilyProfile,
    private val extractor: CanResponseExtractor? = null,
    private val keyDerivation: InstrumentSecurityKeyDerivation = InstrumentSecurityKeyDerivation(),
) {
    private val profile = requireNotNull(family.combinedWrite)
    private val payloadBuilder = CombinedServiceReminderPayloadBuilder(profile)

    suspend fun reset(
        channel: DiagnosticWriteChannel,
        distance: Int,
        distanceUnit: DistanceUnit,
        nextServiceDate: LocalDate,
    ): ServiceReminderResetResult {
        val intervalKm = DistanceUnit.convert(distance, distanceUnit, DistanceUnit.KILOMETERS)
        if (!validInput(intervalKm, nextServiceDate)) return invalidInput()

        family.module.transport.observedElmAdapterConfiguration.forEach { command ->
            val response = execute(channel, command, WriteIntent.READ)
            if (!elmConfigurationAccepted(command, response)) {
                return ServiceReminderResetResult.Blocked(
                    UiText(UiMessage.INSTRUMENT_REASON_TRANSPORT_REJECTED, command),
                )
            }
        }

        val sessionResponse = payload(execute(channel, profile.sessionRequest, WriteIntent.READ))
        val securityKey = try {
            profile.securityKeyFor(sessionResponse)
        } catch (_: IllegalArgumentException) {
            return unrecognized()
        }
        val seedResponse = payload(execute(channel, profile.seedRequest, WriteIntent.READ)).hexOnly()
        if (!seedResponse.startsWith(profile.seedPositivePrefix) ||
            seedResponse.length != profile.seedPositivePrefix.length + SEED_HEX_LENGTH
        ) return unrecognized()
        val derived = try {
            keyDerivation.derive(seedResponse.substring(profile.seedPositivePrefix.length), securityKey.aesKey)
        } catch (_: IllegalArgumentException) {
            return unrecognized()
        }
        val keyRequest = profile.keyRequestPrefix + derived
        val keyResponse = payload(execute(channel, keyRequest, WriteIntent.READ)).hexOnly()
        if (keyResponse != profile.keyPositivePrefix) return unrecognized()

        val before = mutableMapOf<String, String>()
        profile.readRequests.forEach { request ->
            before[request] = payload(execute(channel, request, WriteIntent.READ))
        }
        if (!before.getValue(A010_REQUEST).hexOnly().startsWith("62A010")) return unrecognized()
        val built = try {
            payloadBuilder.build(
                family.strategy,
                intervalKm,
                nextServiceDate,
                before.getValue(A500_REQUEST),
                before.getValue(A000_REQUEST),
            )
        } catch (_: IllegalArgumentException) {
            return unrecognized()
        }

        val flowControl = execute(channel, built.frames.first(), WriteIntent.WRITE)
        if (!isContinueToSend(flowControl)) {
            return ServiceReminderResetResult.Blocked(
                UiText(UiMessage.SERVICE_RESET_REASON_DISTANCE_REJECTED),
            )
        }
        built.frames.drop(1).forEachIndexed { index, frame ->
            val response = execute(channel, frame, WriteIntent.WRITE)
            if (index == built.frames.lastIndex - 1) {
                val positive = try {
                    payload(response).hexOnly()
                } catch (_: DiagnosticParseException) {
                    ""
                }
                if (positive != profile.writePositiveResponse) {
                    return ServiceReminderResetResult.Blocked(
                        UiText(UiMessage.SERVICE_RESET_REASON_DATE_UNCONFIRMED),
                    )
                }
            }
        }

        val verifyA000 = payload(
            execute(channel, A000_REQUEST, WriteIntent.READ, failureIsAmbiguous = true),
        )
        val verifyA010 = payload(
            execute(channel, A010_REQUEST, WriteIntent.READ, failureIsAmbiguous = true),
        )
        if (!verifyWrittenValues(verifyA000, built, nextServiceDate) ||
            !verifyA010.hexOnly().startsWith("62A010")
        ) {
            return ServiceReminderResetResult.Blocked(
                UiText(UiMessage.SERVICE_RESET_REASON_DATE_UNCONFIRMED),
            )
        }
        return ServiceReminderResetResult.Committed(
            odometerKm = built.odometerKm,
            distance = distance,
            distanceUnit = distanceUnit,
            nextServiceDate = nextServiceDate,
        )
    }

    private fun verifyWrittenValues(
        response: String,
        built: CombinedServiceReminderPayload,
        date: LocalDate,
    ): Boolean = try {
        val normalized = response.hexOnly()
        if (!normalized.startsWith("62A000")) return false
        val data = normalized.substring(6).diagnosticHexBytes()
        when (family.strategy) {
            ServiceReminderStrategy.UPDATED_COMBINED -> data.size == 12 &&
                data.copyOfRange(3, 6).unsignedInt() == built.nextServiceOdometerKm &&
                data[9].u() == date.year - profile.yearBase &&
                data[10].u() == date.monthValue && data[11].u() == date.dayOfMonth
            ServiceReminderStrategy.HYBRID_COMBINED -> data.size == 5 &&
                data.copyOfRange(0, 2).unsignedInt() == built.nextServiceOdometerKm / profile.distanceStepKm &&
                data[2].u() == date.year - profile.yearBase &&
                data[3].u() == date.monthValue && data[4].u() == date.dayOfMonth
            ServiceReminderStrategy.ORIGINAL_SPLIT -> false
        }
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun isContinueToSend(response: String): Boolean {
        val responseId = family.module.transport.responseCanId.removePrefix("0x").uppercase()
        val frames = response.lines().map(String::hexOnly).filter(String::isNotEmpty)
        val data = frames.firstOrNull { it.startsWith(responseId) }
            ?.substring(responseId.length)
            ?: frames.singleOrNull()
            ?: return false
        return data.length >= 2 && data.substring(0, 2).equals("30", ignoreCase = true)
    }

    private fun validInput(intervalKm: Int, date: LocalDate): Boolean =
        intervalKm in profile.minimumDistanceKm..profile.maximumDistanceKm &&
            intervalKm % profile.distanceStepKm == 0 &&
            date.year - profile.yearBase in 0..0xFF

    private fun invalidInput() = ServiceReminderResetResult.Blocked(
        UiText(UiMessage.SERVICE_RESET_REASON_INVALID_INPUT),
    )

    private fun unrecognized() = ServiceReminderResetResult.Blocked(
        UiText(UiMessage.SERVICE_RESET_REASON_UNRECOGNIZED_STATUS),
    )

    private fun payload(response: String): String = extractor?.extract(response) ?: response

    private suspend fun execute(
        channel: DiagnosticWriteChannel,
        request: String,
        intent: WriteIntent,
        failureIsAmbiguous: Boolean = intent == WriteIntent.WRITE,
    ): String = try {
        channel.execute(request, intent)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        throw ServiceReminderResetFailure(request, failure, writeStarted = failureIsAmbiguous)
    }

    private companion object {
        const val SEED_HEX_LENGTH = 32
        const val A500_REQUEST = "0322A500"
        const val A000_REQUEST = "0322A000"
        const val A010_REQUEST = "0322A010"
    }
}

private fun ByteArray.toIsoTpFrames(): List<String> {
    require(size in 8..0xFFF)
    val frames = mutableListOf<String>()
    frames += (byteArrayOf((0x10 or (size ushr 8)).toByte(), size.toByte()) + take(6)).paddedFrame().toHex()
    var offset = 6
    var sequence = 1
    while (offset < size) {
        val count = minOf(7, size - offset)
        frames += (byteArrayOf((0x20 or (sequence and 0x0F)).toByte()) + copyOfRange(offset, offset + count))
            .paddedFrame().toHex()
        offset += count
        sequence++
    }
    return frames
}

private fun ByteArray.paddedFrame(): ByteArray = copyOf(8)

private fun Int.toBytes(count: Int): ByteArray = ByteArray(count) { index ->
    (this ushr ((count - index - 1) * 8)).toByte()
}

private fun ByteArray.unsignedInt(): Int = fold(0) { result, byte -> (result shl 8) or byte.u() }

private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
