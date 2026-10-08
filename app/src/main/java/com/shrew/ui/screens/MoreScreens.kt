package com.shrew.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.shrew.AppState
import com.shrew.Fix
import com.shrew.Purpose
import com.shrew.R
import com.shrew.Scr
import com.shrew.data.Kind
import com.shrew.data.Money
import com.shrew.data.NameMatch
import com.shrew.data.Repo
import com.shrew.data.Trip
import com.shrew.ui.C
import com.shrew.ui.CardRow
import com.shrew.ui.CardRows
import com.shrew.ui.Hint
import com.shrew.ui.Ico
import com.shrew.ui.NotchPanel
import com.shrew.ui.NotchTile
import com.shrew.ui.PrimaryButton
import com.shrew.ui.SectionTitle
import com.shrew.ui.SoftButton
import com.shrew.ui.SoftCard
import com.shrew.ui.TextButton
import com.shrew.ui.accent
import com.shrew.ui.condensed
import com.shrew.ui.glass
import com.shrew.ui.sans
import com.shrew.ui.soft

/** The entries of a trip as card rows (newest first). */
@Composable
private fun TripEntries(app: AppState, trip: Trip) {
  if (trip.entries.isEmpty()) {
    Text("Nothing scanned yet.", style = soft(15, C.Muted))
    return
  }
  SoftCard {
    CardRows(trip.entries) { e ->
      val p = e.price
      val sub = buildString {
        append(time(p.at))
        append(" · ")
        append(shortVerdict(e))
        if (p.isSale) append(" · sale")
      }
      CardRow(
        p.itemName,
        sub,
        Money.format(p.cents),
        trailingColor = if (e.kind == Kind.SAME || e.kind == Kind.NEW) C.Ink else e.kind.accent(),
        titleSize = 16,
        onClick = { app.go(Scr.ItemHistory(p.itemId)) },
      )
    }
  }
}

// ---------------------------------------------------------------------------------------------
// 11. Trip list (items so far, running total)
// ---------------------------------------------------------------------------------------------

@Composable
fun TripListScreen(app: AppState) {
  @Suppress("UNUSED_VARIABLE") val v = Repo.version
  val trip = remember(v) { app.sessionId?.let { Repo.trip(it) } } ?: return
  ScreenLayout(
    panel = {
      NotchPanel(240.dp, C.Mint, tile = { NotchTile(itemsLabel(trip.count), Money.format(trip.totalCents)) }) {
        PanelColumn(top = 26.dp, gap = 8.dp) {
          Text("This trip", style = sans(16, C.Black))
          PanelText(trip.storeName, condensed(38, line = 1.1f))
        }
        PanelFoot("Started ${time(trip.startedAt)}. Tap an item for its history.")
      }
    },
    footer = {
      PrimaryButton("Back to scanning", { app.toScan() })
      TextButton("End trip", { app.endTrip() })
    },
  ) {
    TripEntries(app, trip)
  }
}

// ---------------------------------------------------------------------------------------------
// 12. End-of-trip summary (also opened from Home's recent trips)
// ---------------------------------------------------------------------------------------------

@Composable
fun SummaryScreen(app: AppState, sessionId: Long) {
  @Suppress("UNUSED_VARIABLE") val v = Repo.version
  val trip = remember(sessionId, v) { Repo.trip(sessionId) } ?: return
  val justEnded = trip.endedAt != null && System.currentTimeMillis() - trip.endedAt < 60_000
  ScreenLayout(
    panel = {
      NotchPanel(280.dp, C.Mint, tile = { NotchTile("Items", "${trip.count}") }) {
        PanelColumn(top = 26.dp, gap = 6.dp) {
          Text(if (justEnded) "Trip done" else "Trip on ${day(trip.startedAt)}", style = sans(16, C.Black))
          PanelText(Money.format(trip.totalCents), condensed(56, line = 1.05f), maxLines = 1)
        }
        PanelFoot("${trip.storeName}\n${day(trip.startedAt)}, ${time(trip.startedAt)}")
      }
    },
    footer = { PrimaryButton(if (justEnded) "Done" else "Back", { if (justEnded) app.reset(Scr.Home) else app.back() }) },
  ) {
    SoftCard {
      CardRow(
        "Saved against last time",
        "Drops since you last bought each item",
        Money.format(trip.savedCents),
        C.Mint,
        icon = R.drawable.ic_kit_51_chevron_down,
        titleSize = 16,
      )
      com.shrew.ui.Hairline()
      CardRow(
        "Above your best prices",
        "Paid over the lowest you'd seen before",
        Money.format(trip.overpaidCents),
        if (trip.overpaidCents > 0) C.Amber else C.Ink,
        icon = R.drawable.ic_drawn_pin,
        titleSize = 16,
      )
    }
    SectionTitle("Items")
    TripEntries(app, trip)
  }
}

// ---------------------------------------------------------------------------------------------
// Items list (entry to 13)
// ---------------------------------------------------------------------------------------------

@Composable
fun ItemsScreen(app: AppState) {
  @Suppress("UNUSED_VARIABLE") val v = Repo.version
  val items = remember(v) { Repo.itemsWithLast() }
  ScreenLayout(
    panel = {
      NotchPanel(200.dp, C.Mint, tile = { NotchTile("Saved", itemsLabel(items.size)) }) {
        PanelText("Your items", condensed(38, line = 1.1f), Modifier.panelTop(26.dp))
        PanelFoot("Only your own prices. Nothing leaves the phone.")
      }
    },
  ) {
    if (items.isEmpty()) {
      Text("No items yet. Everything you scan lands here.", style = soft(15, C.Muted, 1.4f))
    } else {
      SoftCard {
        CardRows(items) { st ->
          val last = st.last
          CardRow(
            st.item.name,
            if (last != null) "last ${Money.format(last.cents)} · ${last.storeName}" else "no price yet",
            titleSize = 16,
            onClick = { app.go(Scr.ItemHistory(st.item.id)) },
          )
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// 13. Item history (price over time by store)
// ---------------------------------------------------------------------------------------------

@Composable
fun ItemHistoryScreen(app: AppState, itemId: Long) {
  @Suppress("UNUSED_VARIABLE") val v = Repo.version
  val stats = remember(itemId, v) { Repo.stats(itemId) } ?: return
  val history = remember(itemId, v) { Repo.history(itemId) }
  ScreenLayout(
    panel = {
      NotchPanel(220.dp, C.Mint, tile = { NotchTile("Bought", if (stats.count == 1) "Once" else "${stats.count} times") }) {
        PanelText(stats.item.name, sans(22, C.Black, 1.15f), Modifier.panelTop(24.dp), maxLines = 3)
        PanelFoot(stats.usualCents?.let { "Usual price ${Money.format(it)}" } ?: "No regular price yet")
      }
    },
    footer = { TextButton("Rename", { app.go(Scr.NameEntry(Purpose.RENAME_ITEM, itemId)) }) },
  ) {
    val last = stats.last
    val lowest = stats.lowest
    val highest = stats.highest
    if (last != null && lowest != null && highest != null) {
      SoftCard {
        CardRow("Last time", "${last.storeName} · ${day(last.at)}", Money.format(last.cents), icon = R.drawable.ic_kit_66_hourglass)
        com.shrew.ui.Hairline()
        CardRow("Lowest", "${lowest.storeName} · ${day(lowest.at)}", Money.format(lowest.cents), C.Mint, icon = R.drawable.ic_kit_51_chevron_down)
        com.shrew.ui.Hairline()
        CardRow("Highest", "${highest.storeName} · ${day(highest.at)}", Money.format(highest.cents), C.Amber, icon = R.drawable.ic_panel_up)
      }
    }
    Hint(
      R.drawable.ic_kit_55_linked_loops,
      if (stats.item.barcode != null) "Barcode ${stats.item.barcode} is linked." else "No barcode linked. Name matches ask you to confirm.",
      size = 14,
    )
    if (history.isNotEmpty()) {
      SectionTitle("Price history")
      SoftCard {
        CardRows(history) { p ->
          CardRow(
            p.storeName,
            if (p.isSale) "${day(p.at)} · sale" else day(p.at),
            Money.format(p.cents),
            titleSize = 16,
          )
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// 14. Stores (rename, merge, delete)
// ---------------------------------------------------------------------------------------------

@Composable
fun StoresScreen(app: AppState) {
  @Suppress("UNUSED_VARIABLE") val v = Repo.version
  val stores = remember(v) { Repo.stores() }
  ScreenLayout(
    panel = {
      NotchPanel(200.dp, C.Mint, tile = { NotchTile("Saved", "${stores.size}") }) {
        PanelText("Stores", condensed(38, line = 1.1f), Modifier.panelTop(26.dp))
        PanelFoot("Tap one to rename, merge or delete it.")
      }
    },
  ) {
    if (stores.isEmpty()) {
      Text("No stores yet. They're added when you start a trip.", style = soft(15, C.Muted, 1.4f))
    } else {
      SoftCard {
        CardRows(stores) { s ->
          val trips = if (s.trips == 1) "1 trip" else "${s.trips} trips"
          CardRow(
            s.name,
            if (s.lastTripAt != null) "$trips · last ${day(s.lastTripAt)}" else trips,
            titleSize = 16,
            onClick = { app.go(Scr.StoreDetail(s.id)) },
          )
        }
      }
    }
  }
}

@Composable
fun StoreDetailScreen(app: AppState, storeId: Long) {
  @Suppress("UNUSED_VARIABLE") val v = Repo.version
  val stores = remember(v) { Repo.stores() }
  val store = stores.firstOrNull { it.id == storeId } ?: return
  val inUse = remember(v) { app.sessionId?.let { Repo.trip(it)?.storeId } == storeId }
  // Destructive actions need a second tap: "merge:<id>" or "delete".
  var armed by remember { mutableStateOf<String?>(null) }
  val others = stores.filter { it.id != storeId }
  val trips = if (store.trips == 1) "1 trip" else "${store.trips} trips"
  ScreenLayout(
    panel = {
      NotchPanel(220.dp, C.Mint, tile = { NotchTile("Location", if (store.lat != null) "Saved" else "None") }) {
        PanelColumn(top = 26.dp, gap = 6.dp) {
          Text("Store", style = sans(16, C.Black))
          PanelText(store.name, condensed(38, line = 1.1f))
        }
        PanelFoot(if (store.lastTripAt != null) "$trips · last ${day(store.lastTripAt)}" else trips)
      }
    },
    footer = {
      if (!inUse) {
        TextButton(
          if (armed == "delete") "Tap again to delete it and its $trips" else "Delete store",
          {
            if (armed == "delete") {
              Repo.deleteStore(storeId)
              app.back()
            } else {
              armed = "delete"
            }
          },
          color = if (armed == "delete") C.WayMore else C.Ink,
        )
      }
    },
    gap = 14.dp,
  ) {
    SoftButton({ app.go(Scr.NameEntry(Purpose.RENAME_STORE, storeId)) }, Modifier.fillMaxWidth().height(62.dp), corner = 20.dp, s = 6.dp) {
      Text("Rename", style = sans(17), modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp))
    }
    if (inUse) {
      Hint(R.drawable.ic_kit_76_target, "This store has the trip in progress. End it to merge or delete the store.", size = 14)
    } else if (others.isNotEmpty()) {
      SectionTitle("Merge into")
      Hint(R.drawable.ic_kit_55_linked_loops, "For duplicates. Trips and prices move to the store you pick.", size = 14)
      others.forEach { o ->
        val key = "merge:${o.id}"
        val isArmed = armed == key
        SoftButton(
          {
            if (isArmed) {
              Repo.mergeStore(storeId, o.id)
              app.back()
            } else {
              armed = key
            }
          },
          Modifier.fillMaxWidth().height(62.dp),
          corner = 20.dp,
          s = 6.dp,
        ) {
          Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (isArmed) "Tap again to merge into ${o.name}" else o.name, style = sans(16, if (isArmed) C.Amber else C.Ink), modifier = Modifier.weight(1f), maxLines = 2)
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// 15–17. Type it instead, new item naming, new store naming (and renames)
// ---------------------------------------------------------------------------------------------

@Composable
fun NameEntryScreen(app: AppState, purpose: Purpose, targetId: Long) {
  val initial = remember(purpose, targetId) {
    when (purpose) {
      Purpose.NEW_ITEM -> app.nameRead
      Purpose.RENAME_STORE -> Repo.store(targetId)?.name ?: ""
      Purpose.RENAME_ITEM -> Repo.item(targetId)?.name ?: ""
      Purpose.TYPE_ITEM, Purpose.NEW_STORE -> ""
    }
  }
  var text by remember(purpose, targetId) { mutableStateOf(initial) }
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) {
    try {
      focus.requestFocus()
    } catch (e: IllegalStateException) {
      // Not attached yet; the user can tap the field.
    }
  }
  val suggestions = remember(text, purpose) {
    if (purpose == Purpose.TYPE_ITEM && text.isNotBlank()) NameMatch.best(text, Repo.itemsWithLast(), { it.item.name }, limit = 5, min = 0.3) else emptyList()
  }
  val headline = when (purpose) {
    Purpose.TYPE_ITEM -> "What did you pick up?"
    Purpose.NEW_ITEM -> "Name this item"
    Purpose.NEW_STORE -> "Name this store"
    Purpose.RENAME_STORE -> "Rename the store"
    Purpose.RENAME_ITEM -> "Rename the item"
  }
  val foot = when (purpose) {
    Purpose.TYPE_ITEM -> "Pick it if it's already in your list."
    Purpose.NEW_ITEM -> if (app.pendingBarcode != null) "Its barcode is linked, so next time it's instant." else "Saved to your own list."
    Purpose.NEW_STORE -> if (app.fix is Fix.At) "Saved with this location." else "Saved without a location."
    Purpose.RENAME_STORE, Purpose.RENAME_ITEM -> "History stays as it is."
  }
  val tileLabel = if (purpose == Purpose.NEW_STORE || purpose == Purpose.RENAME_STORE) "Store" else "Item"
  val tileValue = if (purpose == Purpose.RENAME_STORE || purpose == Purpose.RENAME_ITEM) "Rename" else "New"

  fun save() {
    val name = text.trim()
    if (name.isEmpty()) return
    when (purpose) {
      Purpose.TYPE_ITEM -> {
        val id = Repo.addItem(name, null)
        app.replace(Scr.Price)
        app.itemId = id
      }
      Purpose.NEW_ITEM -> {
        val id = Repo.addItem(name, app.pendingBarcode)
        app.pendingBarcode = null
        app.itemId = id
        app.replace(Scr.Price)
      }
      Purpose.NEW_STORE -> {
        val f = app.fix
        val id = if (f is Fix.At) Repo.addStore(name, f.lat, f.lng) else Repo.addStore(name, null, null)
        app.startTrip(id)
      }
      Purpose.RENAME_STORE -> {
        Repo.renameStore(targetId, name)
        app.back()
      }
      Purpose.RENAME_ITEM -> {
        Repo.renameItem(targetId, name)
        app.back()
      }
    }
  }

  ScreenLayout(
    panel = {
      NotchPanel(200.dp, C.Mint, tile = { NotchTile(tileLabel, tileValue) }) {
        PanelText(headline, condensed(32, line = 1.1f), Modifier.panelTop(26.dp))
        PanelFoot(foot)
      }
    },
    footer = {
      PrimaryButton(
        when (purpose) {
          Purpose.TYPE_ITEM -> "Save as a new item"
          Purpose.NEW_ITEM -> "Save item"
          Purpose.NEW_STORE -> "Save store and start"
          Purpose.RENAME_STORE, Purpose.RENAME_ITEM -> "Save"
        },
        { save() },
        enabled = text.isNotBlank(),
      )
    },
    gap = 14.dp,
  ) {
    Box(Modifier.fillMaxWidth().height(64.dp).glass(18.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
      if (text.isEmpty()) {
        Text(if (purpose == Purpose.NEW_STORE || purpose == Purpose.RENAME_STORE) "Store name" else "Item name", style = sans(20, C.Black.copy(alpha = 0.35f)))
      }
      BasicTextField(
        value = text,
        onValueChange = { text = it.take(80) },
        singleLine = true,
        textStyle = sans(20, C.Black),
        cursorBrush = SolidColor(C.Black),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { save() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
      )
    }
    if (suggestions.isNotEmpty()) {
      SectionTitle("Already in your list")
      suggestions.forEach { m ->
        val st = m.value
        val last = st.last
        SoftButton({ app.itemId = st.item.id; app.replace(Scr.Price) }, Modifier.fillMaxWidth(), corner = 20.dp, s = 6.dp) {
          Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Text(st.item.name, style = sans(16))
              Text(if (last != null) "last ${Money.format(last.cents)} · ${last.storeName} · ${day(last.at)}" else times(st.count), style = soft(13, C.Muted))
            }
            Ico(R.drawable.ic_check, C.Mint, 18.dp)
          }
        }
      }
    }
    if (purpose == Purpose.NEW_ITEM && app.nameRead.isNotBlank()) {
      Hint(R.drawable.ic_kit_47_box, "Filled in from the pack or the product lookup. Edit it to how you'd say it.", size = 14)
    }
  }
}
