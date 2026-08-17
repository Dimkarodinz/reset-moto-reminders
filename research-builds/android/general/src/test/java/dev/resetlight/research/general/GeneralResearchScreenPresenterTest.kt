package dev.resetlight.research.general

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneralResearchScreenPresenterTest {
    private val valid = GeneralVehicleValidation.Valid(GeneralVehicle("Honda", "Africa Twin", 2022))

    @Test
    fun `start requires valid motorcycle and selected adapter`() {
        val presenter = GeneralResearchScreenPresenter()
        assertFalse(presenter.present(valid, false, GeneralSessionState.Idle).startEnabled)
        assertTrue(presenter.present(valid, true, GeneralSessionState.Idle).startEnabled)
    }

    @Test
    fun `partial failed report remains shareable`() {
        val report = File("partial.jsonl")
        val presentation = GeneralResearchScreenPresenter().present(
            valid,
            true,
            GeneralSessionState.Failed(valid.vehicle, "Stopped", report),
        )
        assertTrue(presentation.canShare)
        assertFalse(presentation.running)
    }
}
