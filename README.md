# Compose Toolbox

One Android app, three mini-apps — **Todo List**, **Expense Tracker (CSV/PDF export)** and
**Car Parking Management** — built so that every component from the
*Android Jetpack Compose – Complete Reference Guide* is used for a real purpose.

## Open & run
1. Android Studio Ladybug (2024.2) or newer → **File ▸ Open** → select this folder.
2. Let Gradle sync (Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01).
   If Studio says the wrapper JAR is missing, accept its offer to use the Gradle wrapper / download Gradle.
3. Run the `app` configuration on a device or emulator (minSdk 26, Android 8.0+).
4. UI test: right-click `app/src/androidTest/.../TodoScreenTest` → Run.

## Project layout
```
app/src/main/java/com/example/composetoolbox/
├── MainActivity.kt              theme + CompositionLocalProvider + AppRoot
├── LegacyActivity.kt            XML layout hosting Compose via ComposeView
├── navigation/AppShell.kt       adaptive shell, NavHost, drawers, nav bar / rail
├── ui/theme/Theme.kt            Material 3, dynamic colour, dark mode
├── ui/AppLocals.kt              CompositionLocals, settings model, money formatting
├── ui/components/               EqualWidthRow (Layout), UniformWidthRow (SubcomposeLayout), SlideToConfirm…
└── feature/
    ├── home/        dashboard
    ├── todo/        Todo List
    ├── expense/     Expense Tracker + ReportExporter (CSV & PDF)
    ├── parking/     Car Parking
    └── settings/    theme, currency, parking rate
```

## Where each component is used

| Guide section | Component | Where |
|---|---|---|
| 4.1 Layout | Column, Row, Box, Spacer, Surface | everywhere |
| | HorizontalDivider / VerticalDivider | Todo menu, Expense summary stats row |
| 4.2 Display | Text, Icon, Card | everywhere |
| | Image | Home hero logo, drawer header, Settings › About |
| 4.3 Input | Button, OutlinedButton, TextButton | Add-task sheet, dialogs |
| | ElevatedButton | Expense › Quick add grid |
| | FilledTonalButton | Expense › “Reset filters” |
| | IconButton, FloatingActionButton | Todo FAB, app bars |
| | ExtendedFloatingActionButton | Expense “Add expense” (collapses on scroll) |
| | SmallFloatingActionButton | Todo “scroll to top” |
| | TextField / OutlinedTextField / BasicTextField | Expense note / amount & plate number / Todo search bar |
| | Checkbox, RadioButton, Switch | Todo items / priority, vehicle type, theme, currency / reminder, EV, dynamic colour |
| | Slider, RangeSlider | Monthly budget / amount filter |
| 4.4 Feedback | CircularProgressIndicator | Home tip loading |
| | LinearProgressIndicator | Todo completion, budget usage, export in progress |
| | Snackbar | Undo delete, export result, check-out receipt |
| | Badge (BadgedBox) | Nav bar, Home cards, parking arrivals |
| 4.5 | Modifier chains | everywhere |
| 5.1 Lists | LazyColumn, LazyRow | Todo/expense/tickets, arrivals queue |
| | LazyVerticalGrid / LazyHorizontalGrid | Parking slots / Expense quick-add |
| | LazyVerticalStaggeredGrid / LazyHorizontalStaggeredGrid | Home dashboard / Home quick actions |
| 5.2 Structure | Scaffold, NavHost + composable { } | every screen / AppShell |
| | TopAppBar, CenterAlignedTopAppBar, LargeTopAppBar, BottomAppBar | Todo & Parking / Expense / Home (collapsing) / Parking ticket detail |
| | NavigationBar, NavigationRail | phones / medium widths |
| | ModalNavigationDrawer, PermanentNavigationDrawer | phones & medium / ≥ 840dp |
| 5.3 Overlays | AlertDialog, Dialog | Clear completed, check-out confirm / Add expense |
| | ModalBottomSheet, BottomSheetScaffold | Add task / Parking slot sheet |
| | DropdownMenu, ExposedDropdownMenuBox | Sort, export, long-press delete / expense category |
| | Popup, TooltipBox | Home info / Export icon |
| 5.4 Tabs | TabRow + Tab, ScrollableTabRow, LeadingIconTab | Todo filters / expense categories / parking tabs |
| | HorizontalPager, VerticalPager | Todo filter pages / parking levels |
| 5.5 Gestures | SwipeToDismissBox | Swipe a task to delete |
| | PullToRefreshBox | Todo “sync” |
| | draggable | SlideToConfirm (check-out) |
| | transformable | Parking lot map pinch-zoom/pan |
| | scrollable | Settings parking-rate dial |
| | detectTapGestures | Double-tap task, double-tap map reset, long-press drag start |
| | dragAndDropSource / dragAndDropTarget | Drag an arriving vehicle onto a free slot |
| 5.6 Layout | ConstraintLayout | Parking TicketCard |
| | FlowRow, FlowColumn | Todo tags / Home “Built with” |
| | BoxWithConstraints | Adaptive shell |
| | Layout, SubcomposeLayout | EqualWidthRow / UniformWidthRow |
| | Canvas | Home progress ring, expense donut, parking lot map |
| 5.7 Animation | AnimatedVisibility, AnimatedContent, Crossfade | scroll-to-top & export bar / search title & expense total & list↔detail / Home tip |
| | animateFloatAsState, animateColorAsState, animateDpAsState | progress bars & donut / card & slot colours / elevation & pager dots |
| | updateTransition, rememberInfiniteTransition | Todo card done-state (colour + elevation + alpha) / Home logo pulse |
| | Animatable | Home ring, SlideToConfirm thumb |
| | AnimatedVectorDrawable | Todo FAB “+ → ×” (`res/drawable/avd_plus_to_close.xml`) |
| | Shared element transitions | Parking Active list → ticket detail |
| 5.8 State | remember, mutableStateOf, rememberSaveable, state hoisting | everywhere (`TodoItemCard`, `StatItem` are stateless) |
| | LaunchedEffect, DisposableEffect, SideEffect | focus/animations / lifecycle observer in Parking / ScreenTracker in AppShell |
| | rememberCoroutineScope, produceState, derivedStateOf, snapshotFlow | snackbars / Home tip & parking clock / FAB expand & scroll-to-top / debounced search |
| | CompositionLocal | `LocalAppSettings` (currency, rate), `LocalOpenDrawer` |
| | ViewModel + Flow + collectAsStateWithLifecycle | all features |
| 5.9 Interop | AndroidView | `CalendarView` in the Add-expense dialog |
| | ComposeView | `LegacyActivity` (drawer › “Legacy XML screen”) |
| 3 | Testing & semantics | `TodoScreenTest` (testTag + semantics tree) |
| 3 | @Preview | TodoItemCard, TicketCard, CategoryDonut, SlideToConfirm, EqualWidthRow |

Not included: **Compose Multiplatform** — it needs a separate Kotlin Multiplatform project
structure, so it can't live inside a plain Android app module.

## Export report
Expense screen → download icon → **Export CSV (Excel)** or **Export PDF** opens the system
“Save as” picker (Storage Access Framework — no storage permission needed). The export
respects the current category tab and amount filter. **Share summary** sends a text summary.

## Next steps
Data is in-memory (sample data is seeded on launch). To persist it, add Room and expose
`Flow<List<…>>` from DAOs — the ViewModels already use StateFlow, so the screens won’t change.
