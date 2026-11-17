package com.example.composetoolbox.feature.parking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingViewModelTest {

    private fun ParkingViewModel.slot(id: String) = state.value.slots.first { it.id == id }
    private fun ParkingViewModel.activeByPlate(plate: String) = state.value.active.values.firstOrNull { it.plate == plate }

    @Test
    fun initialState() {
        val s = ParkingViewModel().state.value
        assertEquals(36, s.slots.size)
        assertEquals(4, s.active.size)
        assertEquals(4, s.slots.count { !it.isFree })
        assertEquals(3, s.history.size)
        assertEquals(listOf("RJ14CD7777", "TS08JK2468"), s.queue.map { it.plate })
        // every occupied slot points at an active ticket on that slot
        s.slots.filterNot { it.isFree }.forEach { slot ->
            assertEquals(slot.id, s.active.getValue(slot.ticketId!!).slotId)
        }
    }

    @Test
    fun park_normalizesPlateAndOccupiesSlot() {
        val vm = ParkingViewModel()
        assertTrue(vm.park("L1-3", "mh 01-ab 9999", VehicleType.BIKE, evCharging = false))

        val ticket = vm.activeByPlate("MH01AB9999")
        assertNotNull(ticket)
        assertEquals("L1-3", ticket!!.slotId)
        assertEquals("L1-03", ticket.slotLabel)
        assertEquals(VehicleType.BIKE, ticket.type)
        assertEquals(ticket.id, vm.slot("L1-3").ticketId)
    }

    @Test
    fun park_evChargingOnlyWhenSlotHasCharger() {
        val vm = ParkingViewModel()
        assertTrue(vm.park("L1-2", "AAAA1111", VehicleType.CAR, evCharging = true)) // EV slot
        assertTrue(vm.park("L1-5", "BBBB2222", VehicleType.CAR, evCharging = true)) // no charger
        assertTrue(vm.activeByPlate("AAAA1111")!!.evCharging)
        assertFalse(vm.activeByPlate("BBBB2222")!!.evCharging)
    }

    @Test
    fun park_rejectsInvalidRequests() {
        val vm = ParkingViewModel()
        val before = vm.state.value
        assertFalse("occupied slot", vm.park("L1-1", "ZZZZ0000", VehicleType.CAR, false))
        assertFalse("unknown slot", vm.park("L9-99", "ZZZZ0000", VehicleType.CAR, false))
        assertFalse("plate too short", vm.park("L1-3", "a-1", VehicleType.CAR, false))
        assertFalse("already parked", vm.park("L1-3", "mh12ab1234", VehicleType.CAR, false))
        assertEquals(before, vm.state.value)
    }

    @Test
    fun park_removesVehicleFromQueue() {
        val vm = ParkingViewModel()
        assertTrue(vm.park("L1-3", "RJ14CD7777", VehicleType.CAR, false))
        assertEquals(listOf("TS08JK2468"), vm.state.value.queue.map { it.plate })
    }

    @Test
    fun parkFromQueue_usesQueuedVehicleType() {
        val vm = ParkingViewModel()
        assertTrue(vm.parkFromQueue("TS08JK2468", "L2-5"))
        assertEquals(VehicleType.BIKE, vm.activeByPlate("TS08JK2468")!!.type)
        assertFalse(vm.state.value.queue.any { it.plate == "TS08JK2468" })
    }

    @Test
    fun parkFromQueue_onOccupiedSlot_fails() {
        val vm = ParkingViewModel()
        assertFalse(vm.parkFromQueue("RJ14CD7777", "L1-1"))
        assertEquals(2, vm.state.value.queue.size)
    }

    @Test
    fun checkout_freesSlot_andMovesTicketToHistory() {
        val vm = ParkingViewModel()
        vm.park("L1-6", "KA01ZZ0001", VehicleType.VAN, false)
        val ticket = vm.activeByPlate("KA01ZZ0001")!!

        val closed = vm.checkout(ticket.id, hourlyRate = 40.0)

        assertNotNull(closed)
        assertNotNull(closed!!.exitTime)
        assertEquals(60.0, closed.fee!!, 0.0) // 1 started hour × 40 × 1.5
        assertNull(vm.activeByPlate("KA01ZZ0001"))
        assertTrue(vm.slot("L1-6").isFree)
        assertEquals(closed, vm.state.value.history.first())
    }

    @Test
    fun checkout_unknownTicket_returnsNull() {
        val vm = ParkingViewModel()
        val before = vm.state.value
        assertNull(vm.checkout(9_999, 40.0))
        assertEquals(before, vm.state.value)
    }

    @Test
    fun simulateArrival_addsValidPlateToQueue() {
        val vm = ParkingViewModel()
        vm.simulateArrival()
        val queue = vm.state.value.queue
        assertEquals(3, queue.size)
        assertTrue(Regex("[A-Z]{2}\\d{2}[A-Z]{2}\\d{4}").matches(queue.last().plate))
    }

    @Test
    fun onResume_addsArrivalOnlyWhenQueueIsEmpty() {
        val vm = ParkingViewModel()
        vm.onResume()
        assertEquals(2, vm.state.value.queue.size)

        vm.parkFromQueue("RJ14CD7777", "L1-3")
        vm.parkFromQueue("TS08JK2468", "L1-5")
        assertTrue(vm.state.value.queue.isEmpty())

        vm.onResume()
        assertEquals(1, vm.state.value.queue.size)
    }
}
