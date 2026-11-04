package com.example.composetoolbox.feature.parking

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.ceil

enum class VehicleType(val label: String, val icon: ImageVector, val rateMultiplier: Double) {
    CAR("Car", Icons.Filled.DirectionsCar, 1.0),
    BIKE("Bike", Icons.Filled.TwoWheeler, 0.5),
    VAN("Van/SUV", Icons.Filled.LocalShipping, 1.5),
}

data class ParkingSlot(
    val id: String,
    val level: Int,
    val number: Int,
    val evCharger: Boolean,
    val ticketId: Long? = null,
) {
    val label: String get() = "L$level-${number.toString().padStart(2, '0')}"
    val isFree: Boolean get() = ticketId == null
}

data class Ticket(
    val id: Long,
    val plate: String,
    val type: VehicleType,
    val slotId: String,
    val slotLabel: String,
    val entryTime: Long,
    val evCharging: Boolean,
    val exitTime: Long? = null,
    val fee: Double? = null,
)

data class QueuedVehicle(val plate: String, val type: VehicleType)

data class ParkingState(
    val slots: List<ParkingSlot> = emptyList(),
    val active: Map<Long, Ticket> = emptyMap(),
    val history: List<Ticket> = emptyList(),
    val queue: List<QueuedVehicle> = emptyList(),
)

const val EV_CHARGING_FEE = 50.0

/** Billed per started hour (minimum 1 hour) × vehicle multiplier, plus a flat EV fee. */
fun Ticket.feeAt(now: Long, hourlyRate: Double): Double {
    val hours = ceil((now - entryTime) / 3_600_000.0).coerceAtLeast(1.0)
    return hours * hourlyRate * type.rateMultiplier + if (evCharging) EV_CHARGING_FEE else 0.0
}

fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
}

class ParkingViewModel : ViewModel() {
    private var nextTicketId = 1L
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ParkingState> = _state.asStateFlow()

    fun park(slotId: String, plate: String, type: VehicleType, evCharging: Boolean): Boolean {
        val s = _state.value
        val slot = s.slots.firstOrNull { it.id == slotId } ?: return false
        val normalized = plate.uppercase().filter { it.isLetterOrDigit() }
        if (!slot.isFree || normalized.length < 4) return false
        if (s.active.values.any { it.plate == normalized }) return false

        val ticket = Ticket(
            id = nextTicketId++,
            plate = normalized,
            type = type,
            slotId = slot.id,
            slotLabel = slot.label,
            entryTime = System.currentTimeMillis(),
            evCharging = evCharging && slot.evCharger,
        )
        _state.value = s.copy(
            slots = s.slots.map { if (it.id == slotId) it.copy(ticketId = ticket.id) else it },
            active = s.active + (ticket.id to ticket),
            queue = s.queue.filterNot { it.plate == normalized },
        )
        return true
    }

    /** Used by drag & drop: a queued vehicle was dropped on a free slot. */
    fun parkFromQueue(plate: String, slotId: String): Boolean {
        val vehicle = _state.value.queue.firstOrNull { it.plate == plate }
        return park(slotId, plate, vehicle?.type ?: VehicleType.CAR, evCharging = false)
    }

    fun checkout(ticketId: Long, hourlyRate: Double): Ticket? {
        val s = _state.value
        val ticket = s.active[ticketId] ?: return null
        val now = System.currentTimeMillis()
        val closed = ticket.copy(exitTime = now, fee = ticket.feeAt(now, hourlyRate))
        _state.value = s.copy(
            slots = s.slots.map { if (it.ticketId == ticketId) it.copy(ticketId = null) else it },
            active = s.active - ticketId,
            history = listOf(closed) + s.history,
        )
        return closed
    }

    fun simulateArrival() {
        val s = _state.value
        val vehicle = QueuedVehicle(randomPlate(), VehicleType.entries.random())
        _state.value = s.copy(queue = s.queue + vehicle)
    }

    /** Called from a lifecycle observer when the app returns to the foreground. */
    fun onResume() {
        if (_state.value.queue.isEmpty()) simulateArrival()
    }

    private fun randomPlate(): String {
        val states = listOf("MH", "KA", "DL", "TN", "GJ", "RJ", "UP", "WB", "KL", "TS")
        val district = (1..40).random().toString().padStart(2, '0')
        val series = "${('A'..'Z').random()}${('A'..'Z').random()}"
        return "${states.random()}$district$series${(1000..9999).random()}"
    }

    private fun initialState(): ParkingState {
        val slots = (1..3).flatMap { level ->
            (1..12).map { n -> ParkingSlot(id = "L$level-$n", level = level, number = n, evCharger = n <= 2) }
        }.toMutableList()

        val now = System.currentTimeMillis()
        val minute = 60_000L
        val active = mutableMapOf<Long, Ticket>()
        listOf(
            Triple("MH12AB1234", VehicleType.CAR, 0),
            Triple("KA05MN4321", VehicleType.BIKE, 3),
            Triple("DL3CAF9090", VehicleType.VAN, 13),
            Triple("TN09XY5555", VehicleType.CAR, 26),
        ).forEachIndexed { i, (plate, type, slotIndex) ->
            val slot = slots[slotIndex]
            val ticket = Ticket(
                id = nextTicketId++,
                plate = plate,
                type = type,
                slotId = slot.id,
                slotLabel = slot.label,
                entryTime = now - (i + 1) * 47 * minute,
                evCharging = slot.evCharger,
            )
            active[ticket.id] = ticket
            slots[slotIndex] = slot.copy(ticketId = ticket.id)
        }

        val history = listOf(
            Ticket(nextTicketId++, "KL07BB2020", VehicleType.CAR, "L1-5", "L1-05", now - 300 * minute, false, now - 190 * minute, 80.0),
            Ticket(nextTicketId++, "UP32EF1111", VehicleType.BIKE, "L2-8", "L2-08", now - 420 * minute, false, now - 400 * minute, 20.0),
            Ticket(nextTicketId++, "WB20GH3030", VehicleType.VAN, "L3-1", "L3-01", now - 600 * minute, true, now - 480 * minute, 170.0),
        )

        val queue = listOf(
            QueuedVehicle("RJ14CD7777", VehicleType.CAR),
            QueuedVehicle("TS08JK2468", VehicleType.BIKE),
        )
        return ParkingState(slots = slots, active = active, history = history, queue = queue)
    }
}
