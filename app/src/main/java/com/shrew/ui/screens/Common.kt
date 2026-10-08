package com.shrew.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shrew.data.Kind
import com.shrew.data.Money
import com.shrew.data.Verdict
import com.shrew.ui.C
import com.shrew.ui.PanelFootWidth
import com.shrew.ui.sans
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every screen: the notched panel on top, a scrolling body, and actions pinned to the bottom.
 * Side padding 24 (the mock's 24px gutters); the body scrolls on shorter phones.
 */
@Composable
fun ScreenLayout(
  panel: @Composable () -> Unit,
  footer: (@Composable ColumnScope.() -> Unit)? = null,
  gap: Dp = 16.dp,
  body: @Composable ColumnScope.() -> Unit,
) {
  Column(Modifier.fillMaxSize().background(C.Surface).systemBarsPadding().imePadding()) {
    panel()
    Column(
      Modifier
        .weight(1f)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp)
        .padding(top = 20.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(gap),
      content = body,
    )
    if (footer != null) {
      Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = footer,
      )
    }
  }
}

/** Panel text at the top left (x 34, inside the 10dp panel inset). */
fun Modifier.panelTop(top: Dp = 26.dp): Modifier = this.padding(start = 24.dp, end = 24.dp, top = top)

/** Panel text at the bottom left, beside the cut. */
@Composable
fun BoxScope.PanelFoot(text: String, color: Color = C.Black, bottom: Dp = 22.dp) {
  Text(
    text,
    style = sans(15, color, 1.3f),
    modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = bottom).width(PanelFootWidth),
    maxLines = 3,
    overflow = TextOverflow.Ellipsis,
  )
}

@Composable
fun PanelText(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = 2) {
  Text(text, style = style, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Column for panel content anchored top-left. */
@Composable
fun BoxScope.PanelColumn(top: Dp = 26.dp, gap: Dp = 8.dp, content: @Composable ColumnScope.() -> Unit) {
  Column(Modifier.align(Alignment.TopStart).fillMaxWidth().panelTop(top), verticalArrangement = Arrangement.spacedBy(gap), content = content)
}

private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

fun day(ms: Long): String = dayFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
fun time(ms: Long): String = timeFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun times(n: Int): String = when (n) {
  0 -> "not bought yet"
  1 -> "bought once"
  2 -> "bought twice"
  else -> "bought $n times"
}

fun itemsLabel(n: Int): String = if (n == 1) "1 item" else "$n items"

fun metres(m: Double): String = if (m < 1000) "${m.toInt()} m" else String.format(Locale.getDefault(), "%.1f km", m / 1000)

/** Short verdict for strips and lists ("$0.79 cheaper", "+$0.50", "Same", "New"). */
fun shortVerdict(v: Verdict): String = when (v.kind) {
  Kind.NEW -> "New"
  Kind.SAME -> "Same"
  Kind.CHEAPER -> "${Money.format(-v.diffCents)} cheaper"
  Kind.MORE, Kind.WAY_MORE -> "${Money.signed(v.diffCents)} dearer"
}
