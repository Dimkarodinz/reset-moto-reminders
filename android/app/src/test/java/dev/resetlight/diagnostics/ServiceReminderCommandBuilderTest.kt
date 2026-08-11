package dev.resetlight.diagnostics

import dev.resetlight.profiles.EcuProfileLoader
import dev.resetlight.domain.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ServiceReminderCommandBuilderTest {
    private val serviceProfile = EcuProfileLoader()
        .load(generatedProfile("tiger-900-gt-pro-2021.ecumap.yaml"))
        .serviceReminder
    private val builder = ServiceReminderCommandBuilder(serviceProfile)

    @Test
    fun `reproduces both captured distance and date commands`() {
        assertEquals(
            ServiceReminderCommands("3364", "5C1B0807016E0000"),
            builder.build(10_000, LocalDate.of(2027, 8, 7)),
        )
        assertEquals(
            ServiceReminderCommands("3350", "5C1B0808016E0000"),
            builder.build(8_000, LocalDate.of(2027, 8, 8)),
        )
    }

    @Test
    fun `uses the captured miles service while preserving the selected-unit value`() {
        assertEquals(
            ServiceReminderCommands("343C", "5C1B0816016E0000"),
            builder.build(6_000, DistanceUnit.MILES, LocalDate.of(2027, 8, 22)),
        )
        assertEquals(
            ServiceReminderCommands("333C", "5C1B0816016E0000"),
            builder.build(6_000, DistanceUnit.KILOMETERS, LocalDate.of(2027, 8, 22)),
        )
    }

    @Test
    fun `rejects unsupported distance and date encodings`() {
        listOf(-100, 0, 50, 25_600).forEach { distance ->
            assertThrows(IllegalArgumentException::class.java) {
                builder.build(distance, LocalDate.of(2027, 8, 8))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            builder.build(8_000, LocalDate.of(1999, 12, 31))
        }
        assertThrows(IllegalArgumentException::class.java) {
            builder.build(8_000, LocalDate.of(2256, 1, 1))
        }
    }

    private fun generatedProfile(name: String): ByteArray {
        val file = File("build/generated/profileAssets/profiles/$name")
        check(file.isFile) { "Generated profile is missing: ${file.absolutePath}" }
        return file.readBytes()
    }
}
