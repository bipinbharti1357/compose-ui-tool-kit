package com.example.composetoolbox.feature.parking

import org.junit.Assert.assertEquals
import org.junit.Test

class ParkingLogicTest {

    private val hour = 3_600_000L

    private fun ticket(type: VehicleType = VehicleType.CAR, ev: Boolean = false) =
        Ticket(id = 1, plate = "MH12AB1234", type = type, slotId = "L1-1", slotLabel = "L1-01", entryTime = 0L, evCharging = ev)

    @Test
    fun fee_isAtLeastOneHour() {
        assertEquals(40.0, ticket().feeAt(now = 0L, hourlyRate = 40.0), 0.0)
        assertEquals(40.0, ticket().feeAt(now = 5 * 60_000L, hourlyRate = 40.0), 0.0)
    }

    @Test
    fun fee_isBilledPerStartedHour() {
        assertEquals(40.0, ticket().feeAt(now = hour, hourlyRate = 40.0), 0.0)
        assertEquals(80.0, ticket().feeAt(now = hour + 1, hourlyRate = 40.0), 0.0)
        assertEquals(120.0, ticket().feeAt(now = 150 * 60_000L, hourlyRate = 40.0), 0.0)
    }

    @Test
    fun fee_appliesVehicleMultiplier() {
        assertEquals(20.0, ticket(VehicleType.BIKE).feeAt(hour, 40.0), 0.0)
        assertEquals(60.0, ticket(VehicleType.VAN).feeAt(hour, 40.0), 0.0)
    }

    @Test
    fun fee_addsFlatEvCharge() {
        assertEquals(40.0 + EV_CHARGING_FEE, ticket(ev = true).feeAt(hour, 40.0), 0.0)
    }

    @Test
    fun formatDuration_formatsAsHoursMinutesSeconds() {
        assertEquals("00:00:00", formatDuration(0))
        assertEquals("01:02:03", formatDuration(3_723_000))
        assertEquals("25:00:00", formatDuration(25 * hour))
        assertEquals("00:00:00", formatDuration(-5_000))
    }

    @Test
    fun slot_labelAndFreeState() {
        val slot = ParkingSlot(id = "L2-7", level = 2, number = 7, evCharger = false)
        assertEquals("L2-07", slot.label)
        assertEquals(true, slot.isFree)
        assertEquals(false, slot.copy(ticketId = 5).isFree)
    }
}
