package dev.resetlight.research.triumph

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResearchScreenPresenterTest {
    private val presenter = ResearchScreenPresenter()
    private val valid = VehicleInputValidation.Valid(ResearchVehicle("Tiger 900", 2021))

    @Test
    fun `start requires valid vehicle adapter and idle terminal state`() {
        assertFalse(presenter.present(valid, false, ResearchSessionState.Idle).startEnabled)
        assertFalse(
            presenter.present(
                VehicleInputValidation.Invalid("bad"),
                true,
                ResearchSessionState.Idle,
            ).startEnabled,
        )
        assertTrue(presenter.present(valid, true, ResearchSessionState.Idle).startEnabled)
        assertFalse(presenter.present(valid, true, ResearchSessionState.Idle, writeOptionsReady = false).startEnabled)
    }

    @Test
    fun `running state disables input and terminal report is shareable`() {
        val running = presenter.present(
            valid,
            true,
            ResearchSessionState.Running(ResearchVehicle("Tiger 900", 2021), ResearchSessionStage.SCANNING_DTCS),
        )
        assertTrue(running.running)
        assertFalse(running.startEnabled)
        assertFalse(running.canShare)

        val failed = presenter.present(
            valid,
            true,
            ResearchSessionState.Failed(ResearchVehicle("Tiger 900", 2021), "failed", File("partial.jsonl")),
        )
        assertTrue(failed.canShare)
        assertTrue(failed.startEnabled)
    }
}
