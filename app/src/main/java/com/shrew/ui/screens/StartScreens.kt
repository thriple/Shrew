package com.shrew.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.shrew.AppState
import com.shrew.Fix
import com.shrew.Purpose
import com.shrew.R
import com.shrew.Scr
import com.shrew.data.Money
import com.shrew.data.NameMatch
import com.shrew.data.Repo
import com.shrew.data.Store
import com.shrew.ui.C
import com.shrew.ui.CardRow
import com.shrew.ui.CardRows
import com.shrew.ui.GlassNote
import com.shrew.ui.GlassProgress
import com.shrew.ui.GlassTile
import com.shrew.ui.Hint
import com.shrew.ui.Ico
import com.shrew.ui.IconBadge
import com.shrew.ui.LocalReduceMotion
import com.shrew.ui.NOTCH_TILE_H
import com.shrew.ui.NOTCH_TILE_W
import com.shrew.ui.NotchPanel
import com.shrew.ui.NotchTile
import com.shrew.ui.PanelButton
import com.shrew.ui.PrimaryButton
import com.shrew.ui.SectionTitle
import com.shrew.ui.SheetShape
import com.shrew.ui.SoftButton
import com.shrew.ui.SoftCard
import com.shrew.ui.Sans
import com.shrew.ui.TextButton
import com.shrew.ui.VSpace
import com.shrew.ui.condensed
import com.shrew.ui.kitSpring
import com.shrew.ui.raisedShadow
import com.shrew.ui.sans
import com.shrew.ui.soft
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------
// 1. Loading
// ---------------------------------------------------------------------------------------------

@Composable
fun LoadingScreen(app: AppState) {
  val progress = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    progress.animateTo(0.62f, tween(260))
    Repo.open(app.appContext)
    progress.animateTo(1f, tween(240))
    delay(120)
    val next = if (app.cameraIntroDone || app.cameraGranted) Scr.Home else Scr.CameraIntro
    app.reset(next)
  }
  Column(Modifier.fillMaxSize().background(C.Surface).statusBarsPadding()) {
    NotchPanel(
      height = 470.dp,
      fill = C.Mint,
      tile = { GlassTile { Text("${(progress.value * 100).toInt()}%", style = sans(20, C.Black)) } },
    ) {
      IconBadge(R.drawable.ic_panel_tag, 72.dp, 40.dp, 22.dp, Modifier.panelTop(30.dp))
      Column(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Shrew", style = condensed(68, line = 1f))
        Text("Know if it was cheaper last time.", style = sans(17, C.Black, 1.3f), modifier = Modifier.width(186.dp))
      }
    }
    Column(Modifier.padding(start = 34.dp, end = 34.dp, top = 80.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
      Text("Opening your price history", style = soft(16, C.Muted))
      GlassProgress(progress.value)
    }
  }
}

// ---------------------------------------------------------------------------------------------
// 2. Camera access (first launch)
// ---------------------------------------------------------------------------------------------

@Composable
fun CameraIntroScreen(app: AppState) {
  ScreenLayout(
    panel = {
      NotchPanel(290.dp, C.Mint, tile = { NotchTile("Asked", "Once") }) {
        PanelColumn(top = 24.dp, gap = 14.dp) {
          IconBadge(R.drawable.ic_panel_camera, 52.dp, 28.dp, 16.dp)
          PanelText("One permission before your first trip", condensed(28, line = 1.12f), maxLines = 3)
        }
        PanelFoot("The camera is how items get into your list.", bottom = 20.dp)
      }
    },
    footer = {
      PrimaryButton("Continue", { app.requestCamera() })
      TextButton("Not now, I'll type items in", {
        app.cameraIntroDone = true
        app.reset(Scr.Home)
      })
    },
    gap = 18.dp,
  ) {
    SoftCard {
      CardRow("Barcodes", "The quickest way to identify it.", icon = R.drawable.ic_drawn_barcode)
      com.shrew.ui.Hairline()
      CardRow("Product names", "Read off the pack as a fallback.", icon = R.drawable.ic_kit_47_box)
      com.shrew.ui.Hairline()
      CardRow("Shelf-tag prices", "Pre-filled for you to check.", icon = R.drawable.ic_drawn_price_tag)
    }
    GlassNote(
      R.drawable.ic_kit_25_shutter,
      buildAnnotatedString {
        append("No photos are taken or stored. Android asks next: choose ")
        withStyle(SpanStyle(fontFamily = Sans)) { append("While using the app") }
        append(" and you won't be asked again.")
      },
    )
  }
}

// ---------------------------------------------------------------------------------------------
// 3. Home
// ---------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(app: AppState, interactive: Boolean = true) {
  @Suppress("UNUSED_VARIABLE") val v = Repo.version
  val trips = remember(v) { Repo.recentTrips() }
  val stores = remember(v) { Repo.stores() }
  val itemCount = remember(v) { Repo.items().size }
  val openTrip = remember(v) { Repo.openSessionId()?.let { Repo.trip(it) } }
  ScreenLayout(
    panel = {
      NotchPanel(
        320.dp,
        C.Mint,
        tile = { NotchTile("${stores.size} saved", "Stores", onClick = if (interactive) ({ app.go(Scr.Stores) }) else null) },
      ) {
        PanelColumn(top = 26.dp, gap = 10.dp) {
          Text("Shrew", style = sans(17, C.Black))
          if (openTrip == null) {
            PanelText("Heading into a store?", condensed(38, line = 1.1f))
            PanelText("Scan as you fill the cart and see how each price compares.", sans(15, C.Black, 1.35f), maxLines = 3)
          } else {
            PanelText("You're mid-trip", condensed(38, line = 1.1f))
            PanelText(
              "At ${openTrip.storeName}. ${itemsLabel(openTrip.count)} so far, ${Money.format(openTrip.totalCents)}.",
              sans(15, C.Black, 1.35f),
              maxLines = 3,
            )
          }
        }
        PanelButton(
          if (openTrip == null) "Start shopping" else "Back to the trip",
          { if (interactive) app.startShopping() },
          Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 18.dp),
        )
      }
    },
  ) {
    SectionTitle("Recent trips")
    if (trips.isEmpty()) {
      Text("No trips yet. Your first one shows up here.", style = soft(15, C.Muted, 1.4f))
    } else {
      SoftCard {
        CardRows(trips) { t ->
          CardRow(
            t.storeName,
            "${day(t.startedAt)} · ${itemsLabel(t.count)}",
            trailing = Money.format(t.totalCents),
            onClick = if (interactive) ({ app.go(Scr.Summary(t.sessionId)) }) else null,
          )
        }
      }
    }
    VSpace(4.dp)
    SectionTitle("Your items")
    SoftButton({ if (interactive) app.go(Scr.Items) }, Modifier.fillMaxWidth().height(62.dp), corner = 20.dp, s = 6.dp) {
      Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Price history by item", style = sans(17), modifier = Modifier.weight(1f))
        Text("$itemCount", style = sans(14, C.Muted))
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// 4. Location box (trip start)
// ---------------------------------------------------------------------------------------------

@Composable
fun LocationAskScreen(app: AppState) {
  val reduce = LocalReduceMotion.current
  val rise = remember { Animatable(if (reduce) 0f else 1f) }
  LaunchedEffect(Unit) { rise.animateTo(0f, kitSpring(400f, 30f, reduce)) }
  Box(Modifier.fillMaxSize()) {
    HomeScreen(app, interactive = false)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))
    Box(
      Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .graphicsLayer { translationY = rise.value * size.height },
    ) {
      Box(
        Modifier.matchParentSize().drawBehind {
          val path = (SheetShape.createOutline(size, layoutDirection, this) as Outline.Generic).path
          raisedShadow(path.asAndroidPath(), 10.dp.toPx())
          drawPath(path, C.Surface)
        },
      )
      Box(
        Modifier.align(Alignment.TopEnd).padding(end = 10.dp).size(NOTCH_TILE_W.dp, NOTCH_TILE_H.dp)
          .clip(RoundedCornerShape(22.dp)).background(C.Mint),
        contentAlignment = Alignment.Center,
      ) { Ico(R.drawable.ic_panel_pin, C.Black, 30.dp) }
      Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 20.dp)) {
        Text("Which store are you in?", style = condensed(26, C.Ink, 1.12f), modifier = Modifier.width(196.dp))
        VSpace(18.dp)
        Text(
          "The app checks where you are and picks the store for this trip. Only at trip start, never in the background.",
          style = soft(16, C.Ink, 1.4f),
        )
        VSpace(14.dp)
        SectionTitle("Android asks next")
        VSpace(14.dp)
        SoftCard {
          CardRow("While using the app", "Asked once. Later trips skip this.", icon = R.drawable.ic_kit_77_location_pulse, titleSize = 16)
          com.shrew.ui.Hairline()
          CardRow("Only this time", "This box returns each trip.", icon = R.drawable.ic_kit_66_hourglass, titleSize = 16)
        }
        VSpace(36.dp)
        PrimaryButton("Continue", { app.requestLocation() })
        VSpace(6.dp)
        TextButton("I'll pick the store myself", { app.pickStoreMyself() })
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// 5. Store confirm (and manual store pick)
// ---------------------------------------------------------------------------------------------

private const val MATCH_RADIUS_M = 150.0

@Composable
fun StoreConfirmScreen(app: AppState) {
  @Suppress("UNUSED_VARIABLE") val v = Repo.version
  val stores = remember(v) { Repo.stores() }
  val fix = app.fix
  val here = fix as? Fix.At
  val byDistance: List<Pair<Store, Double?>> = remember(stores, here) {
    stores
      .map { s -> s to if (here != null && s.lat != null && s.lng != null) NameMatch.metres(here.lat, here.lng, s.lat, s.lng) else null }
      .sortedWith(compareBy<Pair<Store, Double?>> { it.second ?: Double.MAX_VALUE }.thenBy { it.first.name.lowercase() })
  }
  val nearest = byDistance.firstOrNull()?.takeIf { (it.second ?: Double.MAX_VALUE) <= MATCH_RADIUS_M }
  val others = byDistance.filter { it !== nearest }
  val newStore = { app.go(Scr.NameEntry(Purpose.NEW_STORE)) }

  ScreenLayout(
    panel = {
      NotchPanel(
        310.dp,
        C.Mint,
        tile = {
          GlassTile {
            Ico(R.drawable.ic_panel_pin, C.Black, 22.dp)
            val label = when {
              fix is Fix.Locating -> "…"
              nearest?.second != null -> metres(nearest.second!!)
              here != null -> "New"
              else -> "Pick"
            }
            Text(label, style = sans(17, C.Black))
          }
        },
      ) {
        PanelColumn(top = 34.dp, gap = 8.dp) {
          when {
            fix is Fix.Locating -> {
              Text("One location fix, then it stops", style = sans(16, C.Black))
              PanelText("Finding the store…", condensed(44, line = 1.02f))
            }
            nearest != null -> {
              Text("Looks like you're at", style = sans(16, C.Black))
              PanelText(nearest.first.name, condensed(44, line = 1.02f))
            }
            here != null -> {
              Text("No saved store within 150 m", style = sans(16, C.Black))
              PanelText("A new store?", condensed(44, line = 1.02f))
            }
            else -> {
              Text(if (stores.isEmpty()) "No stores saved yet" else "Pick from your stores", style = sans(16, C.Black))
              PanelText("Which store?", condensed(44, line = 1.02f))
            }
          }
        }
        if (nearest != null) {
          val last = nearest.first.lastTripAt
          Text(
            if (last != null) "last trip here: ${day(last)}" else "first trip here",
            style = sans(14, C.Black),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 176.dp),
          )
        }
        val action: Pair<String, () -> Unit>? = when {
          fix is Fix.Locating -> null
          nearest != null -> "Yes, I'm here" to { app.startTrip(nearest.first.id) }
          else -> "Add this store" to newStore
        }
        if (action != null) {
          PanelButton(action.first, action.second, Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 18.dp))
        }
      }
    },
    gap = 14.dp,
  ) {
    when {
      fix is Fix.Locating -> {
        Hint(R.drawable.ic_kit_76_target, "Location is less exact indoors, so you'll get to check the name.")
        TextButton("I'll pick the store myself", { app.pickStoreMyself() })
      }
      else -> {
        if (here != null) {
          Hint(R.drawable.ic_kit_76_target, "Location is less exact indoors, so check the name before you start.")
        } else if (!app.locationGranted) {
          Hint(R.drawable.ic_kit_76_target, "Location is off for Shrew, so pick the store yourself.")
          if (app.locationBlocked) TextButton("Turn location on in settings", { app.openSettings() }, color = C.Mint)
        }
        VSpace(6.dp)
        if (others.isNotEmpty()) SectionTitle(if (nearest != null) "Somewhere else?" else "Your stores")
        others.forEach { (s, d) ->
          SoftButton({ app.startTrip(s.id) }, Modifier.fillMaxWidth().height(62.dp), corner = 20.dp, s = 6.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
              Text(s.name, style = sans(17), modifier = Modifier.weight(1f), maxLines = 1)
              if (d != null) Text(metres(d), style = sans(14, C.Muted))
            }
          }
        }
        SoftButton(newStore, Modifier.fillMaxWidth().height(62.dp), corner = 20.dp, s = 6.dp) {
          Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Ico(R.drawable.ic_kit_62_plus, C.Mint)
            Text(if (here != null) "Add this place as a new store" else "Add a new store", style = sans(17))
          }
        }
      }
    }
  }
}
