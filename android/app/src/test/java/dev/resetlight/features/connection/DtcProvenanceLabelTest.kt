package dev.resetlight.features.connection

import dev.resetlight.R
import dev.resetlight.profiles.DtcMessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DtcProvenanceLabelTest {
    @Test
    fun `maps every DTC description status to a clear provenance label`() {
        assertEquals(R.string.dtc_source_oem_confirmed, dtcProvenanceLabelResource(DtcMessageStatus.OEM_CONFIRMED))
        assertEquals(R.string.dtc_source_vehicle_observed, dtcProvenanceLabelResource(DtcMessageStatus.VEHICLE_OBSERVED))
        assertEquals(R.string.dtc_source_open_data_generic, dtcProvenanceLabelResource(DtcMessageStatus.OPEN_DATA_GENERIC))
        assertEquals(R.string.dtc_source_inferred, dtcProvenanceLabelResource(DtcMessageStatus.INFERRED))
        assertEquals(R.string.dtc_source_unverified, dtcProvenanceLabelResource(DtcMessageStatus.GENERIC_CLASSIFICATION))
        assertEquals(R.string.dtc_source_unverified, dtcProvenanceLabelResource(DtcMessageStatus.UNKNOWN))
    }
}
