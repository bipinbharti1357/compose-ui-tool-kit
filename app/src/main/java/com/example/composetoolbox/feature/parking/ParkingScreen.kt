@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalSharedTransitionApi::class,
)

package com.example.composetoolbox.feature.parking

import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.composetoolbox.ui.LocalAppSettings
import com.example.composetoolbox.ui.components.DrawerMenuButton
import com.example.composetoolbox.ui.components.EmptyState
import com.example.composetoolbox.ui.components.EqualWidthRow
import com.example.composetoolbox.ui.components.SlideToConfirm
import com.example.composetoolbox.ui.components.StatItem
import com.example.composetoolbox.ui.formatMoney
import com.example.composetoolbox.ui.theme.ComposeToolboxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ParkingTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Slots("Slots", Icons.Filled.GridView),
    Active("Active", Icons.Filled.Timer),
    History("History", Icons.Filled.History),
    LotMap("Map", Icons.Filled.Map),
}

private val timeFormat = DateTimeFormatter.ofPattern("hh:mm a")
private fun Long.asTime(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(timeFormat)

@Composable
fun ParkingScreen(vm: ParkingViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scaffoldState = rememberBottomSheetScaffoldState(snackbarHostState = snackbarHostState)

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedSlotId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCheckout by remember { mutableStateOf<Ticket?>(null) }

    // produceState: a clock that ticks every second for live timers/fees.
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    // Passing a lambda defers the read, so only the timer Texts recompose every second.
    val nowProvider: () -> Long = { now }

    // DisposableEffect: observe the Activity lifecycle and clean up when leaving composition.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selectedSlot = state.slots.firstOrNull { it.id == selectedSlotId }
    val selectedTicket = selectedSlot?.ticketId?.let { state.active[it] }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun closeSheet() {
        scope.launch {
            scaffoldState.bottomSheetState.partialExpand()
            selectedSlotId = null
        }
    }

    fun checkout(ticket: Ticket) {
        val closed = vm.checkout(ticket.id, settings.hourlyRate) ?: return
        if (selectedSlotId == ticket.slotId) closeSheet()
        showMessage("${closed.plate} checked out · collect ${formatMoney(closed.fee ?: 0.0, settings.currency)}")
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        sheetContent = {
            SlotSheetContent(
                slot = selectedSlot,
                ticket = selectedTicket,
                nowProvider = nowProvider,
                hourlyRate = settings.hourlyRate,
                currency = settings.currency,
                onPark = { plate, type, ev ->
                    val slot = selectedSlot ?: return@SlotSheetContent
                    if (vm.park(slot.id, plate, type, ev)) {
                        closeSheet()
                        showMessage("$plate parked at ${slot.label}")
                    } else {
                        showMessage("Could not park: check the number or whether it's already inside")
                    }
                },
                onCheckout = { checkout(it) },
                onClose = { closeSheet() },
            )
        },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Car Parking") },
                    navigationIcon = { DrawerMenuButton() },
                    actions = {
                        IconButton(onClick = {
                            vm.simulateArrival()
                            showMessage("A vehicle arrived at the gate. Drag it onto a free slot.")
                        }) {
                            BadgedBox(badge = {
                                if (state.queue.isNotEmpty()) Badge { Text("${state.queue.size}") }
                            }) {
                                Icon(Icons.Filled.Notifications, contentDescription = "Simulate arrival")
                            }
                        }
                    },
                )
                TabRow(selectedTabIndex = tab) {
                    ParkingTab.entries.forEachIndexed { index, t ->
                        LeadingIconTab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(t.label, maxLines = 1) },
                            icon = { Icon(t.icon, contentDescription = null) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (ParkingTab.entries[tab]) {
                ParkingTab.Slots -> SlotsTab(
                    state = state,
                    onSlotClick = { id ->
                        selectedSlotId = id
                        scope.launch { scaffoldState.bottomSheetState.expand() }
                    },
                    onDropVehicle = { plate, slotId ->
                        if (vm.parkFromQueue(plate, slotId)) showMessage("$plate parked by drag & drop")
                    },
                )
                ParkingTab.Active -> ActiveTab(
                    tickets = state.active.values.sortedBy { it.entryTime },
                    nowProvider = nowProvider,
                    hourlyRate = settings.hourlyRate,
                    currency = settings.currency,
                    onCheckout = { pendingCheckout = it },
                )
                ParkingTab.History -> HistoryTab(state.history, settings.currency)
                ParkingTab.LotMap -> LotMapTab(state.slots)
            }
        }
    }

    pendingCheckout?.let { ticket ->
        AlertDialog(
            onDismissRequest = { pendingCheckout = null },
            icon = { Icon(Icons.Filled.Payments, contentDescription = null) },
            title = { Text("Check out ${ticket.plate}?") },
            text = {
                Text(
                    "Parked for ${formatDuration(now - ticket.entryTime)}.\n" +
                        "Amount due: ${formatMoney(ticket.feeAt(now, settings.hourlyRate), settings.currency)}",
                )
            },
            confirmButton = {
                Button(onClick = { checkout(ticket); pendingCheckout = null }) { Text("Collect & check out") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCheckout = null }) { Text("Cancel") }
            },
        )
    }
}

/* ------------------------------ Slots tab ------------------------------ */

@Composable
private fun SlotsTab(
    state: ParkingState,
    onSlotClick: (String) -> Unit,
    onDropVehicle: (plate: String, slotId: String) -> Unit,
) {
    val slotsByLevel = remember(state.slots) { state.slots.groupBy { it.level } }
    val levels = slotsByLevel.keys.sorted()
    val pagerState = rememberPagerState(pageCount = { levels.size })
    val occupied = state.slots.count { !it.isFree }

    Column(Modifier.fillMaxSize()) {
        // Custom Layout: four equal-width stats
        EqualWidthRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            StatItem("Total", "${state.slots.size}")
            StatItem("Occupied", "$occupied")
            StatItem("Free", "${state.slots.size - occupied}")
            StatItem("EV free", "${state.slots.count { it.evCharger && it.isFree }}")
        }

        AnimatedVisibility(visible = state.queue.isNotEmpty()) {
            Column {
                Text(
                    "At the gate: long-press a vehicle and drag it onto a free slot",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.queue, key = { it.plate }) { vehicle ->
                        QueueChip(vehicle, Modifier.animateItem())
                    }
                }
            }
        }

        Row(Modifier.weight(1f)) {
            // VerticalPager: swipe up/down between parking levels
            VerticalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                key = { levels[it] },
            ) { page ->
                val level = levels[page]
                val levelSlots = slotsByLevel.getValue(level)
                Column(Modifier.fillMaxSize()) {
                    Text(
                        "Level $level · ${levelSlots.count { it.isFree }} free · swipe ↕ for other levels",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 92.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        gridItems(levelSlots, key = { it.id }) { slot ->
                            SlotCell(
                                slot = slot,
                                ticket = slot.ticketId?.let { state.active[it] },
                                onClick = { onSlotClick(slot.id) },
                                onDrop = { plate -> onDropVehicle(plate, slot.id) },
                            )
                        }
                    }
                }
            }
            // Page indicator dots, size animated with animateDpAsState
            Column(
                Modifier
                    .fillMaxHeight()
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                levels.indices.forEach { index ->
                    val selected = pagerState.currentPage == index
                    val dotSize by animateDpAsState(if (selected) 12.dp else 8.dp, label = "dot")
                    Box(
                        Modifier
                            .padding(4.dp)
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
    }
}

/** Drag source: long-press starts a system drag carrying the plate number as plain text. */
@Composable
private fun QueueChip(vehicle: QueuedVehicle, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier.dragAndDropSource {
            detectTapGestures(onLongPress = {
                startTransfer(DragAndDropTransferData(ClipData.newPlainText("plate", vehicle.plate)))
            })
        },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(vehicle.type.icon, contentDescription = vehicle.type.label, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(vehicle.plate, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Free slots are drop targets; colour animates on hover / occupancy. */
@Composable
private fun SlotCell(slot: ParkingSlot, ticket: Ticket?, onClick: () -> Unit, onDrop: (String) -> Unit) {
    val hovering = remember { mutableStateOf(false) }
    val currentOnDrop by rememberUpdatedState(onDrop)
    val background by animateColorAsState(
        when {
            hovering.value -> MaterialTheme.colorScheme.tertiaryContainer
            ticket != null -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        label = "slotColor",
    )
    val dropTarget = remember(slot.id) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { hovering.value = true }
            override fun onExited(event: DragAndDropEvent) { hovering.value = false }
            override fun onEnded(event: DragAndDropEvent) { hovering.value = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovering.value = false
                val plate = event.toAndroidDragEvent().clipData?.getItemAt(0)?.text?.toString() ?: return false
                currentOnDrop(plate)
                return true
            }
        }
    }
    val dropModifier = if (slot.isFree) {
        Modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) },
            target = dropTarget,
        )
    } else Modifier

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = background),
        modifier = Modifier
            .aspectRatio(1f)
            .then(dropModifier),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(slot.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (slot.evCharger) {
                    Icon(Icons.Filled.EvStation, contentDescription = "EV charger", modifier = Modifier.size(16.dp))
                }
            }
            if (ticket != null) {
                Icon(ticket.type.icon, contentDescription = ticket.type.label)
                Text(ticket.plate, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            } else {
                Text("Free", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/* ---------------------------- Bottom sheet ----------------------------- */

@Composable
private fun SlotSheetContent(
    slot: ParkingSlot?,
    ticket: Ticket?,
    nowProvider: () -> Long,
    hourlyRate: Double,
    currency: String,
    onPark: (String, VehicleType, Boolean) -> Unit,
    onCheckout: (Ticket) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (slot == null) {
            Spacer(Modifier.height(8.dp))
        } else {
            SlotSheetBody(slot, ticket, nowProvider, hourlyRate, currency, onPark, onCheckout, onClose)
        }
    }
}

@Composable
private fun SlotSheetBody(
    slot: ParkingSlot,
    ticket: Ticket?,
    nowProvider: () -> Long,
    hourlyRate: Double,
    currency: String,
    onPark: (String, VehicleType, Boolean) -> Unit,
    onCheckout: (Ticket) -> Unit,
    onClose: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Slot ${slot.label}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (slot.evCharger) {
                AssistChip(
                    onClick = {},
                    label = { Text("EV charger") },
                    leadingIcon = { Icon(Icons.Filled.EvStation, null, Modifier.size(18.dp)) },
                )
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
        }
        if (ticket == null) {
            key(slot.id) { ParkForm(slot, currency, onPark) }
        } else {
            TicketCard(ticket, nowProvider, hourlyRate, currency)
            SlideToConfirm(text = "Slide to check out", onConfirmed = { onCheckout(ticket) })
        }
    }
}

@Composable
private fun ParkForm(slot: ParkingSlot, currency: String, onPark: (String, VehicleType, Boolean) -> Unit) {
    var plate by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(VehicleType.CAR) }
    var ev by rememberSaveable { mutableStateOf(false) }
    val plateValid = plate.length in 6..12

    OutlinedTextField(
        value = plate,
        onValueChange = { input -> plate = input.uppercase().filter { it.isLetterOrDigit() }.take(12) },
        label = { Text("Vehicle number") },
        placeholder = { Text("e.g. MH12AB1234") },
        singleLine = true,
        isError = plate.isNotEmpty() && !plateValid,
        supportingText = { if (plate.isNotEmpty() && !plateValid) Text("6–12 letters or digits") },
        leadingIcon = { Icon(type.icon, contentDescription = null) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        VehicleType.entries.forEach { option ->
            Row(
                Modifier.selectable(selected = type == option, onClick = { type = option }, role = Role.RadioButton),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = type == option, onClick = null)
                Spacer(Modifier.width(4.dp))
                Text(option.label)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("EV charging")
            Text(
                if (slot.evCharger) "+${formatMoney(EV_CHARGING_FEE, currency)} flat" else "No charger at this slot",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = ev && slot.evCharger, onCheckedChange = { ev = it }, enabled = slot.evCharger)
    }
    Button(
        onClick = { onPark(plate, type, ev && slot.evCharger) },
        enabled = plateValid,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.LocalParking, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Park vehicle")
    }
}

/** ConstraintLayout ticket: elements positioned relative to each other, flat hierarchy. */
@Composable
fun TicketCard(
    ticket: Ticket,
    nowProvider: () -> Long,
    hourlyRate: Double,
    currency: String,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier.fillMaxWidth()) {
        ConstraintLayout(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            val (icon, plate, slot, divider, duration, fee, entry) = createRefs()
            Icon(
                ticket.type.icon,
                contentDescription = ticket.type.label,
                modifier = Modifier
                    .size(40.dp)
                    .constrainAs(icon) {
                        start.linkTo(parent.start)
                        top.linkTo(parent.top)
                    },
            )
            Text(
                ticket.plate,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.constrainAs(plate) {
                    start.linkTo(icon.end, margin = 12.dp)
                    top.linkTo(icon.top)
                },
            )
            Text(
                "Slot ${ticket.slotLabel} · ${ticket.type.label}" + if (ticket.evCharging) " · EV" else "",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.constrainAs(slot) {
                    start.linkTo(plate.start)
                    top.linkTo(plate.bottom)
                },
            )
            HorizontalDivider(
                Modifier.constrainAs(divider) {
                    top.linkTo(icon.bottom, margin = 12.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.fillToConstraints
                },
            )
            Text(
                formatDuration(nowProvider() - ticket.entryTime),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.constrainAs(duration) {
                    start.linkTo(parent.start)
                    top.linkTo(divider.bottom, margin = 12.dp)
                },
            )
            Text(
                formatMoney(ticket.feeAt(nowProvider(), hourlyRate), currency),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.constrainAs(fee) {
                    end.linkTo(parent.end)
                    baseline.linkTo(duration.baseline)
                },
            )
            Text(
                "Entered at ${ticket.entryTime.asTime()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.constrainAs(entry) {
                    start.linkTo(parent.start)
                    top.linkTo(duration.bottom, margin = 4.dp)
                },
            )
        }
    }
}

/* ------------------------------ Active tab ----------------------------- */

/** List → detail with a shared element transition (icon and plate fly between screens). */
@Composable
private fun ActiveTab(
    tickets: List<Ticket>,
    nowProvider: () -> Long,
    hourlyRate: Double,
    currency: String,
    onCheckout: (Ticket) -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = tickets.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selected != null) { selectedId = null }

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        AnimatedContent(targetState = selected, contentKey = { it?.id }, label = "ticketDetail") { target ->
            if (target == null) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (tickets.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No vehicles parked",
                                message = "Park one from the Slots tab.",
                                icon = Icons.Filled.LocalParking,
                            )
                        }
                    }
                    items(tickets, key = { it.id }) { ticket ->
                        Card(onClick = { selectedId = ticket.id }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    ticket.type.icon,
                                    contentDescription = ticket.type.label,
                                    modifier = Modifier
                                        .sharedElement(rememberSharedContentState("icon-${ticket.id}"), this@AnimatedContent)
                                        .size(36.dp),
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        ticket.plate,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.sharedElement(rememberSharedContentState("plate-${ticket.id}"), this@AnimatedContent),
                                    )
                                    Text("Slot ${ticket.slotLabel}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    formatDuration(nowProvider() - ticket.entryTime),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                    }
                }
            } else {
                TicketDetail(
                    ticket = target,
                    nowProvider = nowProvider,
                    hourlyRate = hourlyRate,
                    currency = currency,
                    sharedScope = this@SharedTransitionLayout,
                    animatedScope = this@AnimatedContent,
                    onBack = { selectedId = null },
                    onCheckout = { onCheckout(target); selectedId = null },
                )
            }
        }
    }
}

@Composable
private fun TicketDetail(
    ticket: Ticket,
    nowProvider: () -> Long,
    hourlyRate: Double,
    currency: String,
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onCheckout: () -> Unit,
) {
    val context = LocalContext.current
    with(sharedScope) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(120.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            ticket.type.icon,
                            contentDescription = ticket.type.label,
                            modifier = Modifier
                                .sharedElement(rememberSharedContentState("icon-${ticket.id}"), animatedScope)
                                .size(72.dp),
                        )
                    }
                }
                Text(
                    ticket.plate,
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.sharedElement(rememberSharedContentState("plate-${ticket.id}"), animatedScope),
                )
                TicketCard(ticket, nowProvider, hourlyRate, currency)
            }
            // BottomAppBar with actions and a docked FAB
            BottomAppBar(
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to list")
                    }
                    IconButton(onClick = {
                        val text = "Parking ticket ${ticket.plate} · slot ${ticket.slotLabel} · since ${ticket.entryTime.asTime()}"
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                                "Share ticket",
                            ),
                        )
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share ticket")
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = onCheckout,
                        containerColor = BottomAppBarDefaults.bottomAppBarFabColor,
                        elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                    ) {
                        Icon(Icons.Filled.Payments, contentDescription = "Check out")
                    }
                },
                windowInsets = WindowInsets(0),
            )
        }
    }
}

/* ------------------------------ History tab ---------------------------- */

@Composable
private fun HistoryTab(history: List<Ticket>, currency: String) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                EqualWidthRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                ) {
                    StatItem("Vehicles served", "${history.size}")
                    StatItem("Revenue", formatMoney(history.sumOf { it.fee ?: 0.0 }, currency))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (history.isEmpty()) {
            item { EmptyState(title = "No history yet", message = "Checked-out vehicles appear here.", icon = Icons.Filled.History) }
        }
        items(history, key = { "history-${it.id}" }) { ticket ->
            Column(Modifier.animateItem()) {
                ListItem(
                    headlineContent = { Text(ticket.plate) },
                    supportingContent = {
                        Text(
                            "${ticket.slotLabel} · ${formatDuration((ticket.exitTime ?: ticket.entryTime) - ticket.entryTime)} · " +
                                "out ${ticket.exitTime?.asTime() ?: "-"}",
                        )
                    },
                    leadingContent = { Icon(ticket.type.icon, contentDescription = ticket.type.label) },
                    trailingContent = {
                        Text(formatMoney(ticket.fee ?: 0.0, currency), style = MaterialTheme.typography.titleSmall)
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

/* ------------------------------ Map tab -------------------------------- */

/** Canvas lot map with pinch-zoom/pan (transformable) and double-tap reset (detectTapGestures). */
@Composable
private fun LotMapTab(slots: List<ParkingSlot>) {
    val levels = remember(slots) { slots.map { it.level }.distinct().sorted() }
    var level by rememberSaveable { mutableIntStateOf(1) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.6f, 4f)
        offset += panChange
    }
    val textMeasurer = rememberTextMeasurer()
    val levelSlots = slots.filter { it.level == level }

    val freeColor = MaterialTheme.colorScheme.primaryContainer
    val takenColor = MaterialTheme.colorScheme.errorContainer
    val laneColor = MaterialTheme.colorScheme.surfaceVariant
    val lineColor = MaterialTheme.colorScheme.outline
    val evColor = Color(0xFF22C55E)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            levels.forEach { l ->
                FilterChip(selected = level == l, onClick = { level = l }, label = { Text("Level $l") })
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clipToBounds()
                .transformable(transformState)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                    })
                },
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            ) {
                val columns = 6
                val pad = 16.dp.toPx()
                val laneHeight = size.height * 0.22f
                val slotWidth = (size.width - pad * 2) / columns
                val slotHeight = (size.height - pad * 2 - laneHeight) / 2
                val inset = 4.dp.toPx()

                drawRect(laneColor, topLeft = Offset(pad, pad + slotHeight), size = Size(size.width - pad * 2, laneHeight))
                val laneY = pad + slotHeight + laneHeight / 2
                drawLine(
                    lineColor,
                    start = Offset(pad, laneY),
                    end = Offset(size.width - pad, laneY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 16f)),
                )

                levelSlots.forEachIndexed { index, slot ->
                    val row = index / columns
                    val col = index % columns
                    val top = if (row == 0) pad else pad + slotHeight + laneHeight
                    val left = pad + col * slotWidth
                    drawRoundRect(
                        color = if (slot.isFree) freeColor else takenColor,
                        topLeft = Offset(left + inset, top + inset),
                        size = Size(slotWidth - inset * 2, slotHeight - inset * 2),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                    )
                    if (slot.evCharger) {
                        drawCircle(evColor, radius = 5.dp.toPx(), center = Offset(left + slotWidth - 12.dp.toPx(), top + 12.dp.toPx()))
                    }
                    val layout = textMeasurer.measure(slot.label, labelStyle)
                    drawText(
                        layout,
                        topLeft = Offset(left + (slotWidth - layout.size.width) / 2, top + (slotHeight - layout.size.height) / 2),
                    )
                }
            }
        }
        Text(
            "Pinch to zoom · drag to pan · double-tap to reset · green dot = EV charger",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun TicketCardPreview() {
    ComposeToolboxTheme(dynamicColor = false) {
        TicketCard(
            ticket = Ticket(1, "MH12AB1234", VehicleType.CAR, "L1-1", "L1-01", System.currentTimeMillis() - 5_400_000, evCharging = true),
            nowProvider = { System.currentTimeMillis() },
            hourlyRate = 40.0,
            currency = "₹",
            modifier = Modifier.padding(16.dp),
        )
    }
}
