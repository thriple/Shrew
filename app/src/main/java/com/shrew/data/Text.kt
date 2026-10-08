package com.shrew.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** One line of text read off a frame, in upright image pixels. */
data class OcrLine(val text: String, val left: Int, val top: Int, val height: Int, val width: Int = 0)

/** A price read from a shelf tag. [label] is shown after the price on its chip ("reg", "member"), or empty. */
data class PriceCandidate(val cents: Long, val label: String)

/** A word group read off a pack, offered as a chip on the "name read" screen. */
data class NameChip(val text: String, val selected: Boolean, val line: OcrLine? = null)

object Money {
  /** Set at startup from the device's currency. */
  var symbol: String = "$"

  fun format(cents: Long): String {
    val a = abs(cents)
    val s = "$symbol${a / 100}.${(a % 100).toString().padStart(2, '0')}"
    return if (cents < 0) "−$s" else s
  }

  /** "+$0.50" / "−$0.50". */
  fun signed(cents: Long): String = if (cents > 0) "+${format(cents)}" else format(cents)

  /** Keypad text ("5.9") to cents, or null if it is not a price. */
  fun parse(text: String): Long? {
    if (text.isEmpty() || text == ".") return null
    val parts = text.split('.')
    val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
    val frac = if (parts.size > 1) parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: return null else 0L
    return whole * 100 + frac
  }

  /** Cents to keypad text, without the symbol ("5.99"). */
  fun plain(cents: Long): String = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

object PriceParser {
  private val unitMarker = Regex(
    """(?i)(\bunit\b|\bper\s+(oz|lb|kg|g|ml|l|100|each)\b|/\s*(fl|oz|lb|kg|g|ml|l|100|ct|sheet|roll|ea)\b|¢\s*/|c\s*/\s*(oz|fl|ml|g)\b)""",
  )
  private val regMarker = Regex("""(?i)\b(reg|regular|was|orig|usual)\b""")
  private val memberMarker = Regex("""(?i)\b(member|members|club|card|rewards)\b""")
  private const val CUR = """(?:\${'$'}|€|£|(?<![A-Za-z])K)"""
  private val decimal = Regex("""(?<![\d.,])(\d{1,4})[.,](\d{2})(?![\d])""")
  private val spaced = Regex("""$CUR\s*(\d{1,4})\s+(\d{2})(?!\d)""")
  private val squashed = Regex("""$CUR\s*(\d{3,5})(?![\d.,])""")
  private val centsOnly = Regex("""(?<![\d.,])(\d{1,2})\s*¢""")

  /**
   * Prices read off a shelf tag, best guess first.
   * Unit prices are dropped (build note: ignore numbers labelled "unit" or "/oz").
   * Larger print ranks first; prices labelled "reg" or "member" rank after unlabelled ones.
   */
  fun parse(lines: List<OcrLine>): List<PriceCandidate> {
    data class Hit(val cents: Long, val label: String, val height: Int, val order: Int)
    val hits = ArrayList<Hit>()
    var order = 0
    for (line in lines) {
      val t = line.text
      if (unitMarker.containsMatchIn(t)) continue
      val label = when {
        regMarker.containsMatchIn(t) -> "reg"
        memberMarker.containsMatchIn(t) -> "member"
        else -> ""
      }
      val found = ArrayList<Long>()
      decimal.findAll(t).forEach { m -> found.add(m.groupValues[1].toLong() * 100 + m.groupValues[2].toLong()) }
      if (found.isEmpty()) spaced.findAll(t).forEach { m -> found.add(m.groupValues[1].toLong() * 100 + m.groupValues[2].toLong()) }
      if (found.isEmpty()) squashed.findAll(t).forEach { m -> found.add(m.groupValues[1].toLong()) }
      if (found.isEmpty()) centsOnly.findAll(t).forEach { m -> found.add(m.groupValues[1].toLong()) }
      for (c in found) if (c in 1..999_999) hits.add(Hit(c, label, line.height, order++))
    }
    val sorted = hits.sortedWith(
      compareBy<Hit> { if (it.label.isEmpty()) 0 else 1 }.thenByDescending { it.height }.thenBy { it.order },
    )
    val seen = HashSet<Long>()
    return sorted.filter { seen.add(it.cents) }.take(4).map { PriceCandidate(it.cents, it.label) }
  }
}

object NameReader {
  private val sizePattern = Regex("""(?i)^\d+([.,]\d+)?\s?(gal|oz|fl\.? ?oz|lb|lbs|kg|g|ml|l|ltr|ct|pk|pack|pcs)\.?$""")
  private val priceLike = Regex("""\d[.,]\d{2}\b|[${'$'}€£¢]""")

  /** Chips from the text on a pack, in reading order. Larger print and pack sizes start selected. */
  fun chips(lines: List<OcrLine>): List<NameChip> {
    data class Part(val text: String, val line: OcrLine)
    val parts = ArrayList<Part>()
    for (line in lines) {
      for (raw in line.text.split('·', '•', '|')) {
        val t = raw.replace(Regex("""\s+"""), " ").trim().trim(',', '.', ':', ';', '-', '*')
        if (t.length < 2 || t.length > 40) continue
        if (priceLike.containsMatchIn(t)) continue
        val digits = t.count { it.isDigit() }
        if (digits * 2 > t.length) continue
        if (t.none { it.isLetter() }) continue
        parts.add(Part(tidyCase(t), line))
      }
    }
    val seen = HashSet<String>()
    val unique = parts.filter { seen.add(it.text.lowercase()) }
    if (unique.isEmpty()) return emptyList()
    val ordered = unique.sortedWith(compareBy<Part> { it.line.top }.thenBy { it.line.left }).take(12)
    val maxH = ordered.maxOf { it.line.height }
    val big = ordered.filter { it.line.height >= maxH * 0.4 }.sortedByDescending { it.line.height }.take(4).toSet()
    return ordered.map { p -> NameChip(p.text, p in big || sizePattern.matches(p.text), p.line) }
  }

  fun name(chips: List<NameChip>): String = chips.filter { it.selected }.joinToString(" ") { it.text }

  /** "HILLTOP DAIRY" reads better as "Hilltop Dairy". Mixed-case text is left alone. */
  private fun tidyCase(t: String): String {
    if (t.any { it.isLowerCase() } || t.count { it.isLetter() } < 4) return t
    return t.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
  }
}

/** Fuzzy matching of a read or typed name against the user's own item list. */
object NameMatch {
  data class Match<T>(val value: T, val score: Double)

  fun <T> best(query: String, candidates: List<T>, name: (T) -> String, limit: Int = 3, min: Double = 0.35): List<Match<T>> {
    val q = normalize(query)
    if (q.isEmpty()) return emptyList()
    return candidates
      .map { Match(it, score(q, normalize(name(it)))) }
      .filter { it.score >= min }
      .sortedByDescending { it.score }
      .take(limit)
  }

  fun score(a: String, b: String): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    if (a == b) return 1.0
    return 0.55 * tokenScore(a.split(' '), b.split(' ')) + 0.45 * dice(trigrams(a), trigrams(b))
  }

  fun normalize(s: String): String =
    s.lowercase().replace(Regex("""[^\p{L}\p{N}%]+"""), " ").trim().replace(Regex("""\s+"""), " ")

  private fun tokenScore(a: List<String>, b: List<String>): Double {
    var matched = 0.0
    for (x in a) {
      var best = 0.0
      for (y in b) {
        val s = when {
          x == y -> 1.0
          min(x.length, y.length) >= 3 && (x.startsWith(y) || y.startsWith(x)) -> 0.8
          min(x.length, y.length) >= 4 && levenshtein(x, y) <= 1 -> 0.75
          else -> 0.0
        }
        if (s > best) best = s
      }
      matched += best
    }
    return 2 * matched / (a.size + b.size)
  }

  private fun trigrams(s: String): Set<String> {
    val p = "  $s "
    return (0..p.length - 3).map { p.substring(it, it + 3) }.toSet()
  }

  private fun dice(a: Set<String>, b: Set<String>): Double =
    if (a.isEmpty() || b.isEmpty()) 0.0 else 2.0 * a.intersect(b).size / (a.size + b.size)

  private fun levenshtein(a: String, b: String): Int {
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
      val cur = IntArray(b.length + 1)
      cur[0] = i
      for (j in 1..b.length) {
        cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
      }
      prev = cur
    }
    return prev[b.length]
  }

  /** Distance in metres between two coordinates (haversine). */
  fun metres(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2)
    return 2 * r * Math.asin(min(1.0, Math.sqrt(max(0.0, h))))
  }
}

/** Normalises retail barcodes so UPC-A (12 digits) and its EAN-13 form (leading 0) are the same key. */
fun normalizeBarcode(raw: String): String {
  val d = raw.filter { it.isDigit() }
  return if (d.length == 12) "0$d" else d.ifEmpty { raw }
}
