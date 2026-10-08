package com.shrew.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shrew.R
import com.shrew.data.Kind

/** Colour tokens from the handoff. Text on mint, amber and the verdict colours is always black. */
object C {
  val Surface = Color(0xFF24272B)
  val Ink = Color(0xFFECEFEC)
  val Muted = Color(0xFFA3AAB0)
  val Mint = Color(0xFF01FF9D)
  val Black = Color(0xFF050706)
  val Amber = Color(0xFFFFB61E)
  val Camera = Color(0xFF0A0C0B)
  val Hairline = Color(0x14FFFFFF)
  val Track = Color(0xFF1C1F23)
  val Pill = Color(0xFF0A0809)
  val CameraMuted = Color(0xFFB4BBB7)
  val OnBlackMuted = Color(0xFFD9DEDB)
  /** Verdict: cheaper than last time. */
  val Cheaper = Color(0xFF0E915F)
  /** Verdict: dearer by more than the margin. */
  val WayMore = Color(0xFFFF4B2F)
}

/** Panel colour for a verdict. Same and new use the plain raised surface. */
fun Kind.panelColor(): Color? = when (this) {
  Kind.CHEAPER -> C.Cheaper
  Kind.MORE -> C.Amber
  Kind.WAY_MORE -> C.WayMore
  Kind.SAME, Kind.NEW -> null
}

/** Accent for small text on the surface (strips, rows). */
fun Kind.accent(): Color = when (this) {
  Kind.CHEAPER -> C.Mint
  Kind.MORE -> C.Amber
  Kind.WAY_MORE -> C.WayMore
  Kind.SAME, Kind.NEW -> C.Muted
}

val Soft = FontFamily(Font(R.font.yoshida_soft_medium, FontWeight.Medium))
val Sans = FontFamily(Font(R.font.yoshida_sans_bold, FontWeight.Bold))
/** Headlines and big prices only, 26sp and larger (user's rule: no bold condensed at small sizes). */
val Condensed = FontFamily(Font(R.font.yoshida_sans_black_condensed, FontWeight.Black))

fun soft(size: Int, color: Color = C.Ink, line: Float = 1.3f) =
  TextStyle(fontFamily = Soft, fontWeight = FontWeight.Medium, fontSize = size.sp, lineHeight = (size * line).sp, color = color)

fun sans(size: Int, color: Color = C.Ink, line: Float = 1.25f) =
  TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = size.sp, lineHeight = (size * line).sp, color = color)

fun condensed(size: Int, color: Color = C.Black, line: Float = 1.1f): TextStyle {
  require(size >= 26) { "Condensed is for 26sp and up" }
  return TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Black, fontSize = size.sp, lineHeight = (size * line).sp, color = color)
}

/** Small uppercase section label ("RECENT TRIPS"). */
val SectionLabel = TextStyle(fontFamily = Soft, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.08.em, color = C.Muted)

/** Small uppercase label inside the notch tile ("2 SAVED"). */
val TileLabel = TextStyle(fontFamily = Soft, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.08.em, color = C.Muted)

/** True when the system asks for reduced motion (animator scale 0); springs become instant. */
val LocalReduceMotion = staticCompositionLocalOf { false }
