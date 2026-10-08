package com.shrew.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shrew.AppState
import com.shrew.Purpose
import com.shrew.R
import com.shrew.ScanMode
import com.shrew.Scr
import com.shrew.data.Kind
import com.shrew.data.Lookup
import com.shrew.data.Money
import com.shrew.data.NameChip
import com.shrew.data.NameMatch
import com.shrew.data.NameReader
import com.shrew.data.PriceParser
import com.shrew.data.Repo
import com.shrew.scan.CameraPreview
import com.shrew.ui.C
import com.shrew.ui.CardRow
import com.shrew.ui.Chip
import com.shrew.ui.GlassNote
import com.shrew.ui.GlassSwitch
import com.shrew.ui.Hint
import com.shrew.ui.Ico
import com.shrew.ui.NOTCH_TILE_H
import com.shrew.ui.NOTCH_TILE_W
import com.shrew.ui.NotchPanel
import com.shrew.ui.NotchShape
import com.shrew.ui.NotchTile
import com.shrew.ui.PrimaryButton
import com.shrew.ui.SecondaryButton
import com.shrew.ui.SectionTitle
import com.shrew.ui.SoftButton
import com.shrew.ui.SoftCard
import com.shrew.ui.StatusPill
import com.shrew.ui.Strip
import com.shrew.ui.TextButton
import com.shrew.ui.TileLabel
import com.shrew.ui.accent
import com.shrew.ui.condensed
import com.shrew.ui.glass
import com.shrew.ui.panelColor
import com.shrew.ui.pressed
import com.shrew.ui.sans
import com.shrew.ui.soft
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// The dark camera panel shared by Scan and "name read".
// ---------------------------------------------------------------------------------------------

@Composable
private fun CameraPanel(
  app: AppState,
  status: String,
  statusDot: Color,
  caption: String,
  modifier: Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  @Suppress("UNUSED_VARIABLE") val v = Repo.version
  val trip = remember(v) { app.sessionId?.let { Repo.trip(it) } }
  Box(modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp)) {
    Box(Modifier.matchParentSize().clip(NotchShape).background(C.Camera)) {
      content()
      // Scrim under the store name so it reads over the live picture.
      Box(
        Modifier.align(Alignment.BottomStart).fillMaxWidth().height(110.dp)
          .background(Brush.verticalGradient(listOf(Color.Transparent, C.Camera.copy(alpha = 0.85f)))),
      )
    }
    Box(Modifier.align(Alignment.TopCenter).padding(top = 18.dp)) { StatusPill(status, statusDot) }
    Column(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 20.dp).width(186.dp)) {
      Text(trip?.storeName ?: "", style = sans(17, Color.White), maxLines = 1)
      Text(caption, style = TileLabel.copy(color = C.CameraMuted), maxLines = 1)
    }
    Box(Modifier.align(Alignment.BottomEnd).size(NOTCH_TILE_W.dp, NOTCH_TILE_H.dp)) {
      NotchTile(itemsLabel(trip?.count ?: 0), Money.format(trip?.totalCents ?: 0), onClick = { app.go(Scr.TripList) })
    }
  }
}

/** Four mint corner brackets around the framing area (4dp, radius 12, 34dp arms). */
@Composable
private fun Brackets(modifier: Modifier) {
  Canvas(modifier) {
    val arm = 34.dp.toPx()
    val sw = 4.dp.toPx()
    val r = 12.dp.toPx()
    val w = size.width
    val h = size.height
    val corners = listOf(Offset(0f, 0f), Offset(w - arm, 0f), Offset(0f, h - arm), Offset(w - arm, h - arm))
    for (c in corners) {
      clipRect(c.x, c.y, c.x + arm, c.y + arm) {
        drawRoundRect(C.Mint, Offset(sw / 2, sw / 2), Size(w - sw, h - sw), CornerRadius(r, r), style = Stroke(sw))
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// 6. Scan (automatic). Also reads a shelf tag (TAG) or links a barcode to an item (LINK).
// ---------------------------------------------------------------------------------------------

@Composable
fun ScanScreen(app: AppState, mode: ScanMode) {
  val scope = rememberCoroutineScope()
  val idle = when (mode) {
    ScanMode.ITEM -> "Looking for a barcode or a name"
    ScanMode.TAG -> "Looking for prices on the tag"
    ScanMode.LINK -> "Looking for the barcode only"
  }
  var status by remember { mutableStateOf(idle) }
  var warn by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  @Suppress("UNUSED_VARIABLE") val v = Repo.version
  val trip = remember(v) { app.sessionId?.let { Repo.trip(it) } }
  val last = trip?.entries?.firstOrNull()
  val linkItem = remember(app.linkItemId) { app.linkItemId?.let { Repo.item(it) } }

  fun say(text: String, isWarn: Boolean = false) {
    status = text
    warn = isWarn
  }

  fun onScan() {
    if (busy) return
    busy = true
    say("Reading…")
    scope.launch {
      try {
        val frame = app.scanner.scan(readText = mode != ScanMode.LINK)
        if (frame == null) {
          say("Couldn't take a picture. Try again", true)
          return@launch
        }
        when (mode) {
          ScanMode.TAG -> {
            val prices = PriceParser.parse(frame.lines)
            if (prices.isEmpty()) {
              say("No price found on the tag. Try again", true)
            } else {
              app.priceCandidates = prices
              app.back()
            }
          }
          ScanMode.LINK -> {
            val code = frame.barcode
            val id = app.linkItemId
            if (code == null || id == null) {
              say("No barcode found. Try again", true)
            } else {
              Repo.linkBarcode(id, code)
              app.linkItemId = null
              app.itemId = id
              app.replace(Scr.Price)
            }
          }
          ScanMode.ITEM -> {
            app.clearScan()
            app.priceCandidates = PriceParser.parse(frame.lines)
            val chips = NameReader.chips(frame.lines)
            val code = frame.barcode
            if (code != null) {
              val known = Repo.itemByBarcode(code)
              if (known != null) {
                app.itemId = known.id
                app.go(Scr.Price)
              } else {
                say("Barcode found. Looking up the name…")
                val name = Lookup.productName(code) ?: NameReader.name(chips)
                app.pendingBarcode = code
                app.nameRead = name
                val matches = if (name.isBlank()) emptyList() else NameMatch.best(name, Repo.items(), { it.name })
                if (matches.isNotEmpty()) app.go(Scr.ItemMatch) else app.go(Scr.NameEntry(Purpose.NEW_ITEM))
              }
            } else if (chips.isNotEmpty()) {
              app.frame = frame.image
              app.chips.addAll(chips)
              app.nameRead = NameReader.name(chips)
              app.go(Scr.NameRead)
            } else {
              say("Nothing readable. Move closer and tap Scan", true)
            }
          }
        }
      } finally {
        busy = false
      }
    }
  }

  Column(Modifier.fillMaxSize().background(C.Surface).statusBarsPadding().navigationBarsPadding()) {
    CameraPanel(
      app,
      status = status,
      statusDot = if (warn) C.Amber else C.Mint,
      caption = when (mode) {
        ScanMode.ITEM -> "scanning items"
        ScanMode.TAG -> "reading the shelf tag"
        ScanMode.LINK -> "linking a barcode"
      },
      modifier = Modifier.weight(1f),
    ) {
      if (app.cameraGranted) {
        CameraPreview(app.scanner, Modifier.fillMaxSize())
        Brackets(Modifier.align(Alignment.Center).padding(bottom = 40.dp).size(222.dp, 280.dp))
      } else {
        Column(
          Modifier.align(Alignment.Center).padding(horizontal = 36.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text("The camera is off", style = sans(18, Color.White))
          Text("Allow it to scan, or type items in below.", style = soft(14, C.CameraMuted, 1.4f))
          SoftButton({ app.requestCamera() }, Modifier.height(44.dp), corner = 14.dp, s = 0.dp, fill = C.Mint, raised = false) {
            Text(if (app.cameraBlocked) "Open settings" else "Allow camera", style = sans(15, C.Black), modifier = Modifier.padding(horizontal = 16.dp))
          }
        }
      }
    }
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Hint(
        R.drawable.ic_kit_22_focus_ring,
        when (mode) {
          ScanMode.ITEM -> "Hold the product or its shelf tag in the frame. One tap looks for a barcode, then the name."
          ScanMode.TAG -> "Hold the shelf tag in the frame. The numbers on it are offered for you to pick."
          ScanMode.LINK -> "Hold the barcode of ${linkItem?.name ?: "the item"} in the frame."
        },
      )
      PrimaryButton(if (busy) "Reading…" else "Scan", { onScan() }, height = 64.dp, big = true, enabled = app.cameraGranted && !busy)
      when (mode) {
        ScanMode.ITEM -> {
          Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SecondaryButton("Type it instead", { app.go(Scr.NameEntry(Purpose.TYPE_ITEM)) }, Modifier.weight(1f))
            SecondaryButton("End trip", { app.endTrip() }, Modifier.weight(1f))
          }
          if (last != null) {
            Strip("Last: ${last.price.itemName}", shortVerdict(last), last.kind.accent(), onClick = { app.go(Scr.TripList) })
          } else {
            Strip("Nothing scanned yet", null)
          }
        }
        ScanMode.TAG -> SecondaryButton("Type the price instead", { app.back() }, Modifier.fillMaxWidth())
        ScanMode.LINK -> SecondaryButton("Skip linking", {
          val id = app.linkItemId
          app.linkItemId = null
          if (id != null) {
            app.itemId = id
            app.replace(Scr.Price)
          } else {
            app.back()
          }
        }, Modifier.fillMaxWidth())
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// 7. No barcode: name read off the pack
// ---------------------------------------------------------------------------------------------

/** The captured frame, cropped to fill, with mint boxes on the selected text and grey on the rest. */
@Composable
private fun FrameWithBoxes(image: Bitmap, chips: List<NameChip>, modifier: Modifier) {
  val bmp = remember(image) { image.asImageBitmap() }
  Canvas(modifier) {
    val scale = maxOf(size.width / bmp.width, size.height / bmp.height)
    val dw = bmp.width * scale
    val dh = bmp.height * scale
    val ox = (size.width - dw) / 2
    val oy = (size.height - dh) / 2
    drawImage(bmp, dstOffset = IntOffset(ox.toInt(), oy.toInt()), dstSize = IntSize(dw.toInt(), dh.toInt()))
    drawRect(Color.Black.copy(alpha = 0.25f))
    val pad = 4.dp.toPx()
    for (c in chips) {
      val l = c.line ?: continue
      val sw = if (c.selected) 3.dp.toPx() else 2.dp.toPx()
      drawRoundRect(
        if (c.selected) C.Mint else Color(0xFF7B8480),
        topLeft = Offset(ox + l.left * scale - pad, oy + l.top * scale - pad),
        size = Size(l.width * scale + 2 * pad, l.height * scale + 2 * pad),
        cornerRadius = CornerRadius(6.dp.toPx()),
        style = Stroke(sw),
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NameReadScreen(app: AppState) {
  val image = app.frame
  val name = NameReader.name(app.chips)
  Column(Modifier.fillMaxSize().background(C.Surface).statusBarsPadding().navigationBarsPadding()) {
    CameraPanel(
      app,
      status = "No barcode found. Name read instead",
      statusDot = C.Mint,
      caption = "frame kept in memory only",
      modifier = Modifier.height(382.dp),
    ) {
      if (image != null) FrameWithBoxes(image, app.chips.toList(), Modifier.fillMaxSize())
    }
    Column(
      Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      SectionTitle("Name read from the pack")
      Box(Modifier.fillMaxWidth().glass(18.dp).padding(horizontal = 16.dp, vertical = 15.dp)) {
        Text(name.ifBlank { "Tap words below" }, style = sans(18, if (name.isBlank()) C.Black.copy(alpha = 0.4f) else C.Black))
      }
      Text("Tap words to add or drop them.", style = soft(14, C.Muted))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        app.chips.forEachIndexed { i, c ->
          Chip(c.text, c.selected, { app.chips[i] = c.copy(selected = !c.selected) }, corner = 22.dp)
        }
      }
    }
    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      PrimaryButton("Find this item", {
        app.nameRead = name
        val matches = NameMatch.best(name, Repo.items(), { it.name })
        if (matches.isNotEmpty()) app.go(Scr.ItemMatch) else app.go(Scr.NameEntry(Purpose.NEW_ITEM))
      }, enabled = name.isNotBlank())
      Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        SecondaryButton("Scan again", { app.back() }, Modifier.weight(1f))
        SecondaryButton("Type it instead", { app.go(Scr.NameEntry(Purpose.TYPE_ITEM)) }, Modifier.weight(1f))
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// 8. Confirm the item (name matches always need the user's confirmation)
// ---------------------------------------------------------------------------------------------

@Composable
fun ItemMatchScreen(app: AppState) {
  val name = app.nameRead
  val barcode = app.pendingBarcode
  @Suppress("UNUSED_VARIABLE") val v = Repo.version
  val matches = remember(name, v) {
    val stats = Repo.itemsWithLast()
    NameMatch.best(name, stats, { it.item.name })
  }
  var picked by remember(matches) { mutableStateOf(matches.firstOrNull()?.value?.item?.id) }
  val pickedItem = matches.firstOrNull { it.value.item.id == picked }?.value?.item
  ScreenLayout(
    panel = {
      NotchPanel(
        240.dp,
        C.Mint,
        tile = {
          SoftButton({ app.back() }, Modifier.fillMaxSize(), corner = 22.dp, s = 5.dp) { Text("Scan again", style = sans(16)) }
        },
      ) {
        PanelColumn(top = 24.dp, gap = 6.dp) {
          PanelText("Is this the one?", condensed(38, line = 1.1f), maxLines = 1)
          Text(if (barcode != null) "New barcode. Name found" else "Name read from the pack", style = sans(15, C.Black))
          PanelText(name, sans(14, C.Black))
        }
        PanelFoot(if (matches.size == 1) "1 close match in your own list" else "${matches.size} close matches in your own list")
      }
    },
    footer = {
      PrimaryButton("Yes, this one", {
        val id = picked ?: return@PrimaryButton
        if (barcode != null) Repo.linkBarcode(id, barcode)
        app.pendingBarcode = null
        app.openItem(id)
      }, enabled = picked != null)
      if (barcode == null && pickedItem != null && pickedItem.barcode == null) {
        TextButton("Link a barcode to it now", {
          app.linkItemId = pickedItem.id
          app.go(Scr.Scan(ScanMode.LINK))
        })
      }
    },
  ) {
    matches.forEach { m ->
      val st = m.value
      val selected = st.item.id == picked
      val last = st.last
      val select = Modifier.clickable(remember { MutableInteractionSource() }, null) { picked = st.item.id }
      val look = if (selected) {
        Modifier.pressed(22.dp, 4.dp).border(2.dp, C.Mint, RoundedCornerShape(22.dp))
      } else {
        Modifier.soft(1f, 22.dp, 6.dp)
      }
      Row(
        Modifier.fillMaxWidth().then(look).then(select).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(st.item.name, style = sans(17))
          Text("${if (m.score >= 0.65) "Close match" else "Similar name"} · ${times(st.count)}", style = soft(14, C.Muted))
          if (last != null) Text("last ${Money.format(last.cents)} · ${last.storeName} · ${day(last.at)}", style = soft(13, C.Muted))
        }
        if (selected) {
          Box(Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(C.Mint), contentAlignment = Alignment.Center) {
            Ico(R.drawable.ic_check, C.Black, 16.dp)
          }
        }
      }
    }
    SoftButton({ app.go(Scr.NameEntry(Purpose.NEW_ITEM)) }, Modifier.fillMaxWidth().height(58.dp), corner = 20.dp, s = 6.dp) {
      Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Ico(R.drawable.ic_kit_62_plus, C.Mint)
        Text("Neither. Save as a new item", style = sans(16))
      }
    }
    Hint(
      R.drawable.ic_kit_55_linked_loops,
      if (barcode != null) {
        "The barcode is linked to the item you pick, so it's recognised straight away next time."
      } else {
        "Name matches need this check. Link a barcode once and the item is recognised straight away next time."
      },
      size = 14,
    )
  }
}

// ---------------------------------------------------------------------------------------------
// 9. Confirm the price
// ---------------------------------------------------------------------------------------------

@Composable
fun PriceScreen(app: AppState) {
  val itemId = app.itemId
  @Suppress("UNUSED_VARIABLE") val v = Repo.version
  val item = remember(itemId, v) { itemId?.let { Repo.item(it) } }
  val trip = remember(v) { app.sessionId?.let { Repo.trip(it) } }
  if (item == null || trip == null) return
  val candidates = app.priceCandidates
  val first = candidates.firstOrNull()
  var text by remember(candidates) { mutableStateOf(first?.let { Money.plain(it.cents) } ?: "") }
  // An unlabelled price next to a "reg" one is most likely the sale price.
  var sale by remember(candidates) { mutableStateOf(first != null && first.label.isEmpty() && candidates.any { it.label == "reg" }) }
  val cents = Money.parse(text)

  fun key(k: Char) {
    text = when {
      k == '<' -> text.dropLast(1)
      k == '.' -> if (text.contains('.')) text else if (text.isEmpty()) "0." else "$text."
      text.contains('.') -> if (text.substringAfter('.').length >= 2) text else text + k
      text == "0" -> k.toString()
      text.length >= 5 -> text
      else -> text + k
    }
  }

  ScreenLayout(
    panel = {
      NotchPanel(180.dp, C.Mint, tile = { NotchTile("Store", trip.storeName, valueStyle = sans(15)) }) {
        PanelText(item.name, sans(22, C.Black, 1.15f), Modifier.panelTop(24.dp))
        PanelFoot("What does it cost here today?", bottom = 18.dp)
      }
    },
    footer = {
      PrimaryButton("Save price", {
        val c = cents ?: return@PrimaryButton
        val fromTag = candidates.any { it.cents == c }
        val id = Repo.addPrice(item.id, trip.storeId, trip.sessionId, c, sale, if (fromTag) "ocr" else "manual")
        app.lastPriceId = id
        app.clearScan()
        app.reset(Scr.Home, Scr.Scan(ScanMode.ITEM), Scr.Verdict)
      }, enabled = cents != null && cents > 0)
    },
    gap = 14.dp,
  ) {
    SectionTitle(if (candidates.isEmpty()) "Shelf tag" else "Read from the shelf tag")
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      candidates.forEach { c ->
        val label = if (c.label.isEmpty()) Money.format(c.cents) else "${Money.format(c.cents)} ${c.label}"
        Chip(label, cents == c.cents, {
          text = Money.plain(c.cents)
          if (c.label == "reg") sale = false
        })
      }
      Chip(if (candidates.isEmpty()) "Read the tag" else "Read again", false, { app.go(Scr.Scan(ScanMode.TAG)) }, icon = R.drawable.ic_panel_camera)
    }
    Row(
      Modifier.fillMaxWidth().height(84.dp).glass(24.dp).padding(horizontal = 22.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(Money.symbol, style = condensed(30))
      Text(text.ifEmpty { "0.00" }, style = condensed(46, if (text.isEmpty()) C.Black.copy(alpha = 0.3f) else C.Black, 1f), maxLines = 1)
    }
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      Text("Sale price. Keep it out of the usual price.", style = soft(15, C.Ink, 1.3f), modifier = Modifier.weight(1f))
      GlassSwitch(sale) { sale = it }
    }
    Keypad { key(it) }
  }
}

@Composable
private fun Keypad(onKey: (Char) -> Unit) {
  val rows = listOf("123", "456", "789", ".0<")
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    rows.forEach { r ->
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        r.forEach { k ->
          SoftButton({ onKey(k) }, Modifier.weight(1f).height(54.dp), corner = 16.dp, s = 4.dp) {
            if (k == '<') Ico(R.drawable.ic_backspace, C.Ink, 26.dp) else Text(k.toString(), style = sans(22))
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// 10. Verdict
// ---------------------------------------------------------------------------------------------

@Composable
fun VerdictScreen(app: AppState) {
  val priceId = app.lastPriceId
  @Suppress("UNUSED_VARIABLE") val v = Repo.version
  val verdict = remember(priceId, v) { priceId?.let { Repo.verdict(it) } } ?: return
  val trip = remember(v) { app.sessionId?.let { Repo.trip(it) } }
  val p = verdict.price
  val fill = verdict.kind.panelColor()
  val on = if (fill == null) C.Ink else C.Black
  val (icon, headline) = when (verdict.kind) {
    Kind.NEW -> R.drawable.ic_panel_new to "New to your list"
    Kind.SAME -> R.drawable.ic_panel_same to "Same as last time"
    Kind.CHEAPER -> R.drawable.ic_panel_down to "Cheaper"
    Kind.MORE -> R.drawable.ic_panel_up to "More expensive"
    Kind.WAY_MORE -> R.drawable.ic_panel_up to "Way more expensive"
  }
  val last = verdict.last
  val lowest = verdict.lowest

  ScreenLayout(
    panel = {
      NotchPanel(
        320.dp,
        fill,
        tile = {
          Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(C.Black).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.Center,
          ) {
            when (verdict.kind) {
              Kind.NEW -> {
                Text("First", style = sans(20, C.Mint))
                Text("time logged", style = sans(12, C.OnBlackMuted))
              }
              Kind.SAME -> {
                Text(Money.format(0), style = sans(20, C.Muted))
                Text("vs last time", style = sans(12, C.OnBlackMuted))
              }
              else -> {
                val color = if (verdict.kind == Kind.CHEAPER) C.Mint else if (verdict.kind == Kind.WAY_MORE) C.WayMore else C.Amber
                Text(Money.signed(verdict.diffCents), style = sans(20, color), maxLines = 1)
                Text("vs last time", style = sans(12, C.OnBlackMuted))
              }
            }
          }
        },
      ) {
        PanelColumn(top = 30.dp, gap = 4.dp) {
          PanelText(p.itemName, sans(16, on), maxLines = 1)
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            Ico(icon, on, 30.dp)
            PanelText(headline, condensed(36, on, 1.1f), maxLines = 1)
          }
          Text(Money.format(p.cents), style = condensed(64, on, 1f), maxLines = 1)
        }
        PanelFoot(if (p.isSale) "Sale price, logged at ${p.storeName}" else "Logged at ${p.storeName}", color = on)
      }
    },
    footer = {
      Strip(
        "This trip: ${itemsLabel(trip?.count ?: 0)}",
        Money.format(trip?.totalCents ?: 0),
        onClick = { app.go(Scr.TripList) },
        leftStyle = sans(15),
      )
      PrimaryButton("Scan next item", { app.toScan() }, height = 60.dp)
      TextButton("Undo this scan", {
        app.lastPriceId = null
        Repo.deletePrice(p.id)
        app.toScan()
      })
    },
    gap = 18.dp,
  ) {
    if (last != null) {
      SoftCard {
        CardRow("Last time", "${last.storeName} · ${day(last.at)}", Money.format(last.cents), icon = R.drawable.ic_kit_66_hourglass)
        if (lowest != null) {
          com.shrew.ui.Hairline()
          CardRow("Lowest you've paid", "${lowest.storeName} · ${day(lowest.at)}", Money.format(lowest.cents), C.Mint, icon = R.drawable.ic_kit_51_chevron_down)
        }
      }
    }
    when {
      verdict.kind == Kind.NEW ->
        GlassNote(R.drawable.ic_kit_66_hourglass, AnnotatedString("First time logged. Next time Shrew compares against this price."))
      (verdict.kind == Kind.MORE || verdict.kind == Kind.WAY_MORE) && lowest != null && lowest.cents < p.cents ->
        GlassNote(
          R.drawable.ic_drawn_pin,
          AnnotatedString("You paid ${Money.format(p.cents - lowest.cents)} less at ${lowest.storeName} on ${day(lowest.at)}."),
          bold = true,
        )
      verdict.kind == Kind.CHEAPER && (lowest == null || p.cents < lowest.cents) ->
        GlassNote(R.drawable.ic_kit_51_chevron_down, AnnotatedString("Your best price yet for this item."), bold = true)
      else -> {}
    }
    if (verdict.kind == Kind.WAY_MORE) {
      Hint(R.drawable.ic_kit_76_target, "More than ${Money.format(verdict.marginCents)} above last time.", size = 14)
    }
  }
}
