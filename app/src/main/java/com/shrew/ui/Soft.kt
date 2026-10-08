package com.shrew.ui

import android.graphics.BlurMaskFilter
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.sqrt

// ---------------------------------------------------------------------------------------------
// Shadows. Raised: dark rgba(0,0,0,.55) down-right by s, light rgba(255,255,255,.065) up-left by s,
// both blurred 2s. Pressed: the same pair, inset.
// ---------------------------------------------------------------------------------------------

private const val DARK = 0x8C000000.toInt()
private const val LIGHT = 0x11FFFFFF

/** BlurMaskFilter radius that matches a CSS blur of 2s (sigma s). */
private fun blurFor(s: Float) = max(0.5f, s * 1.6f)

/** Draws the raised shadow pair for [path] (a closed outline in this draw scope). */
fun DrawScope.raisedShadow(path: android.graphics.Path, s: Float) {
  if (s < 0.5f) return
  drawIntoCanvas { canvas ->
    val nc = canvas.nativeCanvas
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    p.maskFilter = BlurMaskFilter(blurFor(s), BlurMaskFilter.Blur.NORMAL)
    p.color = DARK
    nc.save(); nc.translate(s, s); nc.drawPath(path, p); nc.restore()
    p.color = LIGHT
    nc.save(); nc.translate(-s, -s); nc.drawPath(path, p); nc.restore()
  }
}

/** Draws the inset shadow pair inside [path]. */
fun DrawScope.insetShadow(path: android.graphics.Path, s: Float) {
  if (s < 0.5f) return
  drawIntoCanvas { canvas ->
    val nc = canvas.nativeCanvas
    nc.save()
    nc.clipPath(path)
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    p.style = android.graphics.Paint.Style.STROKE
    p.strokeWidth = s * 2f
    p.maskFilter = BlurMaskFilter(blurFor(s), BlurMaskFilter.Blur.NORMAL)
    p.color = DARK
    nc.save(); nc.translate(s, s); nc.drawPath(path, p); nc.restore()
    p.color = LIGHT
    nc.save(); nc.translate(-s, -s); nc.drawPath(path, p); nc.restore()
    nc.restore()
  }
}

private fun roundRectPath(size: Size, r: Float): android.graphics.Path {
  val p = android.graphics.Path()
  p.addRoundRect(0f, 0f, size.width, size.height, r, r, android.graphics.Path.Direction.CW)
  return p
}

/**
 * A soft surface. depth 1 = raised by [s], 0 = flat, negative = pressed in by |depth| * s.
 * [fill] is the material; it defaults to the one surface colour.
 */
fun Modifier.soft(depth: Float, corner: Dp, s: Dp, fill: Color = C.Surface): Modifier = drawBehind {
  val r = corner.toPx()
  val path = roundRectPath(size, r)
  raisedShadow(path, max(depth, 0f) * s.toPx())
  drawRoundRect(fill, cornerRadius = CornerRadius(r, r))
  insetShadow(path, max(-depth, 0f) * s.toPx())
}

/** Pressed (recessed) surface. */
fun Modifier.pressed(corner: Dp, s: Dp = 3.dp, fill: Color = C.Surface): Modifier = soft(-1f, corner, s, fill)

// ---------------------------------------------------------------------------------------------
// Glass cutouts: mint base, three soft blobs, a white veil and an inset shadow. Static gradient.
// ---------------------------------------------------------------------------------------------

private class Blob(val x: Float, val y: Float, val color: Color, val a0: Float, val a1: Float, val s1: Float, val s2: Float)

private val GlassBlobs = listOf(
  Blob(0.18f, 0.22f, Color(190, 255, 230), 0.95f, 0.45f, 0.26f, 0.52f),
  Blob(0.82f, 0.30f, Color(0, 214, 190), 0.90f, 0.40f, 0.24f, 0.48f),
  Blob(0.55f, 0.95f, Color(140, 255, 120), 0.85f, 0.40f, 0.22f, 0.46f),
)

/** Paints the aurora (CSS radial-gradient, farthest-corner circles) into the current bounds. */
fun DrawScope.glassFill() {
  drawRect(C.Mint)
  val w = size.width
  val h = size.height
  for (b in GlassBlobs) {
    val cx = b.x * w
    val cy = b.y * h
    val dx = max(cx, w - cx)
    val dy = max(cy, h - cy)
    val radius = max(1f, sqrt(dx * dx + dy * dy))
    drawRect(
      Brush.radialGradient(
        0f to b.color.copy(alpha = b.a0),
        b.s1 to b.color.copy(alpha = b.a1),
        b.s2 to b.color.copy(alpha = 0f),
        center = Offset(cx, cy),
        radius = radius,
      ),
    )
  }
}

/** A glass window. Text inside is black. [veil] is 0.14 for fields and notes, 0.2 for notch tiles. */
fun Modifier.glass(corner: Dp, veil: Float = 0.14f): Modifier = this
  .clip(RoundedCornerShape(corner))
  .drawBehind {
    glassFill()
    drawRect(Color.White.copy(alpha = veil))
    insetShadow(roundRectPath(size, corner.toPx()), 3.dp.toPx())
  }

// ---------------------------------------------------------------------------------------------
// Notched panel. Sits 10dp inside the screen edges, radius 30; its bottom-right corner is cut away
// 76dp tall with an inner radius of 22, leaving room for a 132x64 tile 12dp from the cut.
// The cut is anchored to the right so the tile keeps its size on any screen width.
// ---------------------------------------------------------------------------------------------

const val NOTCH_TILE_W = 132
const val NOTCH_TILE_H = 64
private const val CUT_FROM_RIGHT = 144f
private const val NOTCH_DEPTH = 76f

/** Top panel with the bottom-right cut. */
object NotchShape : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    with(density) {
      val w = size.width
      val h = size.height
      val r = 30.dp.toPx()
      val ri = 22.dp.toPx()
      val n = NOTCH_DEPTH.dp.toPx()
      val cut = w - CUT_FROM_RIGHT.dp.toPx()
      val p = Path()
      p.moveTo(r, 0f)
      p.lineTo(w - r, 0f)
      p.arcTo(Rect(w - 2 * r, 0f, w, 2 * r), -90f, 90f, false)
      p.lineTo(w, h - n - r)
      p.arcTo(Rect(w - 2 * r, h - n - 2 * r, w, h - n), 0f, 90f, false)
      p.lineTo(cut + ri, h - n)
      p.arcTo(Rect(cut, h - n, cut + 2 * ri, h - n + 2 * ri), -90f, -90f, false)
      p.lineTo(cut, h - r)
      p.arcTo(Rect(cut - 2 * r, h - 2 * r, cut, h), 0f, 90f, false)
      p.lineTo(r, h)
      p.arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
      p.lineTo(0f, r)
      p.arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
      p.close()
      return Outline.Generic(p)
    }
  }
}

/** Bottom sheet with the notch at its top right (the location box). Spans the full width. */
object SheetShape : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    with(density) {
      val w = size.width
      val h = size.height
      val r = 30.dp.toPx()
      val ri = 22.dp.toPx()
      val n = NOTCH_DEPTH.dp.toPx()
      val cut = w - 154.dp.toPx()
      val p = Path()
      p.moveTo(0f, h)
      p.lineTo(0f, r)
      p.arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
      p.lineTo(cut - r, 0f)
      p.arcTo(Rect(cut - 2 * r, 0f, cut, 2 * r), -90f, 90f, false)
      p.lineTo(cut, n - ri)
      p.arcTo(Rect(cut, n - 2 * ri, cut + 2 * ri, n), 180f, -90f, false)
      p.lineTo(w - r, n)
      p.arcTo(Rect(w - 2 * r, n, w, n + 2 * r), -90f, 90f, false)
      p.lineTo(w, h)
      p.close()
      return Outline.Generic(p)
    }
  }
}

private fun DrawScope.outlinePath(shape: Shape): Path =
  (shape.createOutline(size, layoutDirection, this) as Outline.Generic).path

/**
 * The notched top panel. [height] is the panel's own height (the mock's path bottom minus 10).
 * [fill] null draws the plain surface, raised. [tile] sits in the cut; [content] fills the panel.
 */
@Composable
fun NotchPanel(
  height: Dp,
  fill: Color?,
  modifier: Modifier = Modifier,
  tile: (@Composable BoxScope.() -> Unit)? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp).height(height)) {
    Box(
      Modifier.matchParentSize().drawBehind {
        val path = outlinePath(NotchShape)
        if (fill == null) raisedShadow(path.asAndroidPath(), 6.dp.toPx())
        drawPath(path, fill ?: C.Surface)
      },
    )
    Box(Modifier.matchParentSize(), content = content)
    if (tile != null) {
      Box(Modifier.align(Alignment.BottomEnd).size(NOTCH_TILE_W.dp, NOTCH_TILE_H.dp), content = tile)
    }
  }
}

/** Text area in the panel's lower left, beside the cut (the mock's 186dp column at x 34). */
val PanelFootWidth = 186.dp

// ---------------------------------------------------------------------------------------------
// Motion. Springs from motion.ts, mapped as dampingRatio = damping / (2 * sqrt(stiffness)).
// ---------------------------------------------------------------------------------------------

fun <T> kitSpring(stiffness: Float, damping: Float, reduce: Boolean): AnimationSpec<T> =
  if (reduce) snap() else spring(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness)

/**
 * SoftButton port. On press the shadows flip from raised to pressed (700/40); on release it springs
 * back past rest and settles (380/13). The label scales to 0.96 a beat behind.
 */
@Composable
fun SoftButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  corner: Dp = 18.dp,
  s: Dp = 6.dp,
  fill: Color = C.Surface,
  enabled: Boolean = true,
  raised: Boolean = true,
  contentAlignment: Alignment = Alignment.Center,
  content: @Composable BoxScope.() -> Unit,
) {
  val reduce = LocalReduceMotion.current
  val interaction = remember { MutableInteractionSource() }
  val down by interaction.collectIsPressedAsState()
  val rest = if (raised) 1f else 0f
  val depth by animateFloatAsState(
    targetValue = if (down) -0.6f else rest,
    animationSpec = if (down) kitSpring(700f, 40f, reduce) else kitSpring(380f, 13f, reduce),
    label = "depth",
  )
  val scale by animateFloatAsState(
    targetValue = if (down) 0.96f else 1f,
    animationSpec = kitSpring(300f, 22f, reduce),
    label = "scale",
  )
  Box(
    modifier
      .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
      .soft(if (raised || down) depth else 0f, corner, s, fill)
      .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
    contentAlignment = contentAlignment,
  ) {
    Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }, contentAlignment = contentAlignment, content = content)
  }
}

/** Mint primary button (Continue, Scan, Save price). */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 56.dp, enabled: Boolean = true, big: Boolean = false) {
  SoftButton(onClick, modifier.fillMaxWidth().height(height), corner = if (big) 22.dp else 18.dp, s = 6.dp, fill = C.Mint, enabled = enabled) {
    Text(label, style = sans(if (big) 21 else 18, C.Black))
  }
}

/** Raised surface button (Type it instead, End trip). */
@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  SoftButton(onClick, modifier.height(48.dp), corner = 16.dp, s = 5.dp, enabled = enabled) {
    Text(label, style = sans(16), textAlign = TextAlign.Center, maxLines = 1)
  }
}

/** Flat text button under the primary action ("Not now, I'll type items in"). */
@Composable
fun TextButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = C.Ink) {
  SoftButton(onClick, modifier.fillMaxWidth().height(48.dp), corner = 16.dp, s = 3.dp, raised = false) {
    Text(label, style = sans(16, color), textAlign = TextAlign.Center)
  }
}

/** Black button in the panel's foot (198x52, radius 18) with a mint label. */
@Composable
fun PanelButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  SoftButton(onClick, modifier.size(198.dp, 52.dp), corner = 18.dp, s = 0.dp, fill = C.Black, raised = false) {
    Text(label, style = sans(17, C.Mint), maxLines = 1)
  }
}

/** Raised surface tile for the notch: small uppercase label over a value. */
@Composable
fun NotchTile(label: String, value: String, onClick: (() -> Unit)? = null, valueStyle: TextStyle = sans(18)) {
  val inner: @Composable BoxScope.() -> Unit = {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
      Text(label.uppercase(), style = TileLabel, maxLines = 1)
      Text(value, style = valueStyle, maxLines = 1)
    }
  }
  if (onClick != null) {
    SoftButton(onClick, Modifier.fillMaxWidth().fillMaxHeight(), corner = 22.dp, s = 5.dp, contentAlignment = Alignment.CenterStart, content = inner)
  } else {
    Box(Modifier.fillMaxWidth().fillMaxHeight().soft(1f, 22.dp, 5.dp), contentAlignment = Alignment.CenterStart, content = inner)
  }
}

/** Glass tile for the notch (percent, distance). */
@Composable
fun GlassTile(content: @Composable RowScope.() -> Unit) {
  Row(
    Modifier.fillMaxWidth().fillMaxHeight().glass(22.dp, 0.2f),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
  )
}

/** Black tile in a panel corner holding a mint icon (splash, camera access). */
@Composable
fun IconBadge(@DrawableRes icon: Int, size: Dp, iconSize: Dp, corner: Dp, modifier: Modifier = Modifier) {
  Box(modifier.size(size).clip(RoundedCornerShape(corner)).drawBehind { drawRect(C.Black) }, contentAlignment = Alignment.Center) {
    Ico(icon, C.Mint, iconSize)
  }
}

@Composable
fun Ico(@DrawableRes res: Int, color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
  Image(painterResource(res), contentDescription = null, colorFilter = ColorFilter.tint(color), modifier = modifier.size(size))
}

// ---------------------------------------------------------------------------------------------
// Cards, rows, chips, notes.
// ---------------------------------------------------------------------------------------------

/** Raised card with hairline-divided rows (radius 22, padding 4/18). */
@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Column(modifier.fillMaxWidth().soft(1f, 22.dp, 6.dp).padding(horizontal = 18.dp, vertical = 4.dp), content = content)
}

@Composable
fun Hairline() {
  Box(Modifier.fillMaxWidth().height(1.dp).drawBehind { drawRect(C.Hairline) })
}

/** One card row: optional recessed icon tile, title and subtitle, trailing text. */
@Composable
fun CardRow(
  title: String,
  sub: String?,
  trailing: String? = null,
  trailingColor: Color = C.Ink,
  @DrawableRes icon: Int? = null,
  titleSize: Int = 17,
  onClick: (() -> Unit)? = null,
) {
  var m = Modifier.fillMaxWidth()
  if (onClick != null) m = m.clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
  Row(m.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    if (icon != null) {
      Box(Modifier.size(38.dp).pressed(12.dp), contentAlignment = Alignment.Center) { Ico(icon, C.Mint) }
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = sans(titleSize))
      if (sub != null) Text(sub, style = soft(14, C.Muted))
    }
    if (trailing != null) Text(trailing, style = sans(18, trailingColor))
  }
}

/** Rows in a card with hairlines between them. */
@Composable
fun <T> CardRows(items: List<T>, row: @Composable (T) -> Unit) {
  items.forEachIndexed { i, it ->
    if (i > 0) Hairline()
    row(it)
  }
}

/** Selectable chip. Selected is mint with black text; otherwise raised surface. */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, corner: Dp = 14.dp, @DrawableRes icon: Int? = null) {
  SoftButton(
    onClick,
    Modifier.height(44.dp),
    corner = corner,
    s = 4.dp,
    fill = if (selected) C.Mint else C.Surface,
    raised = !selected,
  ) {
    Row(Modifier.padding(horizontal = if (corner > 16.dp) 16.dp else 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (icon != null) Ico(icon, if (selected) C.Black else C.Mint, 20.dp)
      Text(label, style = sans(15, if (selected) C.Black else C.Ink), maxLines = 1)
    }
  }
}

/** Mint icon beside muted text (hints and notes on the surface). */
@Composable
fun Hint(@DrawableRes icon: Int, text: String, size: Int = 15) {
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Ico(icon, C.Mint)
    Text(text, style = soft(size, C.Muted, 1.4f))
  }
}

/** Glass note: black icon and text on the aurora. */
@Composable
fun GlassNote(@DrawableRes icon: Int, text: AnnotatedString, bold: Boolean = false) {
  Row(
    Modifier.fillMaxWidth().glass(18.dp).padding(horizontal = 16.dp, vertical = 13.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Ico(icon, C.Black)
    Text(text, style = if (bold) sans(16, C.Black, 1.35f) else soft(15, C.Black, 1.4f))
  }
}

/** Recessed strip ("Last: …", "This trip: …"). */
@Composable
fun Strip(left: String, right: String?, rightColor: Color = C.Ink, onClick: (() -> Unit)? = null, leftStyle: TextStyle = soft(15, C.Muted)) {
  var m = Modifier.fillMaxWidth().height(46.dp).pressed(16.dp)
  if (onClick != null) m = m.clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
  Row(m.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(left, style = leftStyle, maxLines = 1, modifier = Modifier.weight(1f))
    if (right != null) Text(right, style = sans(15, rightColor), maxLines = 1)
  }
}

/** Glass switch (64x34). On: aurora track, knob right. Off: recessed track, knob left. */
@Composable
fun GlassSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
  val reduce = LocalReduceMotion.current
  val x by animateDpAsState(if (on) 34.dp else 4.dp, kitSpring(520f, 34f, reduce), label = "knob")
  Box(
    Modifier
      .size(64.dp, 34.dp)
      .then(if (on) Modifier.glass(17.dp, 0f) else Modifier.pressed(17.dp))
      .clickable(remember { MutableInteractionSource() }, null, role = Role.Switch) { onChange(!on) },
  ) {
    Box(
      Modifier.offset(x = x, y = 4.dp).size(26.dp).drawBehind {
        val r = size.width / 2
        val path = roundRectPath(size, r)
        drawIntoCanvas { canvas ->
          val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
          p.maskFilter = BlurMaskFilter(blurFor(3.dp.toPx()), BlurMaskFilter.Blur.NORMAL)
          p.color = DARK
          canvas.nativeCanvas.save(); canvas.nativeCanvas.translate(3.dp.toPx(), 3.dp.toPx())
          canvas.nativeCanvas.drawPath(path, p); canvas.nativeCanvas.restore()
        }
        drawCircle(C.Surface)
        drawCircle(if (on) C.Mint else C.Muted, radius = 3.dp.toPx())
      },
    )
  }
}

/** Track with a glass fill (loading). */
@Composable
fun GlassProgress(fraction: Float, modifier: Modifier = Modifier) {
  Box(modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)).drawBehind {
    drawRect(C.Track)
    val w = size.width * fraction.coerceIn(0f, 1f)
    if (w > 0f) {
      drawContext.canvas.save()
      drawContext.canvas.clipRect(0f, 0f, w, size.height)
      glassFill()
      drawContext.canvas.restore()
    }
    insetShadow(roundRectPath(size, 8.dp.toPx()), 3.dp.toPx())
  })
}

/** Camera status pill: dark, hairline border, mint dot, white text. */
@Composable
fun StatusPill(text: String, dot: Color = C.Mint) {
  Row(
    Modifier
      .height(40.dp)
      .clip(RoundedCornerShape(20.dp))
      .drawBehind { drawRect(C.Pill) }
      .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
      .padding(start = 14.dp, end = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Box(Modifier.size(8.dp).drawBehind { drawCircle(dot) })
    Text(text, style = sans(14, Color.White), maxLines = 1)
  }
}

@Composable
fun SectionTitle(text: String) {
  Text(text.uppercase(), style = SectionLabel)
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))
