package com.shrew.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

data class Store(val id: Long, val name: String, val lat: Double?, val lng: Double?, val trips: Int, val lastTripAt: Long?)

data class Item(val id: Long, val barcode: String?, val name: String)

/** One logged price, joined with its store name. */
data class PriceRow(
  val id: Long,
  val itemId: Long,
  val itemName: String,
  val storeId: Long,
  val storeName: String,
  val cents: Long,
  val isSale: Boolean,
  val at: Long,
  val sessionId: Long,
)

data class ItemStats(
  val item: Item,
  val count: Int,
  val last: PriceRow?,
  val lowest: PriceRow?,
  val highest: PriceRow?,
  /** Median of non-sale prices, or null when every price was a sale. */
  val usualCents: Long?,
)

enum class Kind { NEW, CHEAPER, SAME, MORE, WAY_MORE }

/** Default band around the last price; a rise beyond it is "way more expensive". */
const val DEFAULT_MARGIN_CENTS = 100L

/** How one logged price compares with what came before it. */
data class Verdict(
  val price: PriceRow,
  val kind: Kind,
  /** The price logged just before this one, at any store. */
  val last: PriceRow?,
  /** Lowest price logged before this one. */
  val lowest: PriceRow?,
  /** ±band: $1.00, or wider when the item's earlier prices already spread further. */
  val marginCents: Long,
) {
  /** Positive when today is dearer than last time. */
  val diffCents: Long get() = if (last == null) 0 else price.cents - last.cents
}

data class Trip(
  val sessionId: Long,
  val storeId: Long,
  val storeName: String,
  val startedAt: Long,
  val endedAt: Long?,
  val entries: List<Verdict>,
) {
  val count: Int get() = entries.size
  val totalCents: Long get() = entries.sumOf { it.price.cents }
  /** Sum of drops against last time. */
  val savedCents: Long get() = entries.sumOf { v -> v.last?.let { maxOf(0L, it.cents - v.price.cents) } ?: 0L }
  /** Sum paid above the best price seen before. */
  val overpaidCents: Long get() = entries.sumOf { v -> v.lowest?.let { maxOf(0L, v.price.cents - it.cents) } ?: 0L }
}

data class TripHead(val sessionId: Long, val storeName: String, val startedAt: Long, val count: Int, val totalCents: Long)

private class Helper(context: Context) : SQLiteOpenHelper(context, "shrew.db", null, 1) {
  override fun onConfigure(db: SQLiteDatabase) {
    db.setForeignKeyConstraintsEnabled(false)
  }

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE stores (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, lat REAL, lng REAL, created_at INTEGER NOT NULL)")
    db.execSQL("CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT, barcode TEXT UNIQUE, name TEXT NOT NULL, brand TEXT, size TEXT, created_at INTEGER NOT NULL)")
    db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, store_id INTEGER NOT NULL, started_at INTEGER NOT NULL, ended_at INTEGER)")
    db.execSQL(
      "CREATE TABLE prices (id INTEGER PRIMARY KEY AUTOINCREMENT, item_id INTEGER NOT NULL, store_id INTEGER NOT NULL, " +
        "cents INTEGER NOT NULL, is_sale INTEGER NOT NULL DEFAULT 0, at INTEGER NOT NULL, session_id INTEGER NOT NULL, source TEXT NOT NULL)",
    )
    db.execSQL("CREATE INDEX prices_item ON prices(item_id, at)")
    db.execSQL("CREATE INDEX prices_session ON prices(session_id)")
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}

/**
 * All storage. Local SQLite only; nothing leaves the phone.
 * The data set is small (hundreds of rows), so queries run synchronously.
 * [version] bumps on every write so Compose readers can re-query.
 */
object Repo {
  private lateinit var db: SQLiteDatabase
  var version by mutableIntStateOf(0)
    private set

  val isOpen: Boolean get() = ::db.isInitialized

  fun open(context: Context) {
    if (!isOpen) db = Helper(context.applicationContext).writableDatabase
  }

  private fun changed() {
    version++
  }

  private inline fun <T> query(sql: String, vararg args: Any?, map: (Cursor) -> T): List<T> {
    val out = ArrayList<T>()
    db.rawQuery(sql, args.map { it?.toString() }.toTypedArray()).use { c ->
      while (c.moveToNext()) out.add(map(c))
    }
    return out
  }

  private fun Cursor.longOrNull(i: Int): Long? = if (isNull(i)) null else getLong(i)
  private fun Cursor.doubleOrNull(i: Int): Double? = if (isNull(i)) null else getDouble(i)

  // ---- Stores ----

  fun stores(): List<Store> = query(
    "SELECT s.id, s.name, s.lat, s.lng, " +
      "(SELECT COUNT(*) FROM sessions x WHERE x.store_id = s.id AND x.ended_at IS NOT NULL), " +
      "(SELECT MAX(started_at) FROM sessions x WHERE x.store_id = s.id AND x.ended_at IS NOT NULL) " +
      "FROM stores s ORDER BY s.name COLLATE NOCASE",
  ) { Store(it.getLong(0), it.getString(1), it.doubleOrNull(2), it.doubleOrNull(3), it.getInt(4), it.longOrNull(5)) }

  fun store(id: Long): Store? = stores().firstOrNull { it.id == id }

  fun addStore(name: String, lat: Double?, lng: Double?): Long {
    val v = ContentValues()
    v.put("name", name.trim())
    if (lat != null && lng != null) {
      v.put("lat", lat)
      v.put("lng", lng)
    }
    v.put("created_at", System.currentTimeMillis())
    val id = db.insert("stores", null, v)
    changed()
    return id
  }

  fun renameStore(id: Long, name: String) {
    val v = ContentValues()
    v.put("name", name.trim())
    db.update("stores", v, "id = ?", arrayOf(id.toString()))
    changed()
  }

  /** Fills in coordinates for a store that was added without a location fix. */
  fun setStoreLocationIfMissing(id: Long, lat: Double, lng: Double) {
    val v = ContentValues()
    v.put("lat", lat)
    v.put("lng", lng)
    db.update("stores", v, "id = ? AND lat IS NULL", arrayOf(id.toString()))
    changed()
  }

  /** Moves every trip and price from [fromId] to [intoId], then removes [fromId]. */
  fun mergeStore(fromId: Long, intoId: Long) {
    db.beginTransaction()
    try {
      db.execSQL("UPDATE prices SET store_id = ? WHERE store_id = ?", arrayOf<Any?>(intoId, fromId))
      db.execSQL("UPDATE sessions SET store_id = ? WHERE store_id = ?", arrayOf<Any?>(intoId, fromId))
      db.execSQL("DELETE FROM stores WHERE id = ?", arrayOf<Any?>(fromId))
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    changed()
  }

  /** Removes the store with all its trips and prices. */
  fun deleteStore(id: Long) {
    db.beginTransaction()
    try {
      db.execSQL("DELETE FROM prices WHERE store_id = ?", arrayOf<Any?>(id))
      db.execSQL("DELETE FROM sessions WHERE store_id = ?", arrayOf<Any?>(id))
      db.execSQL("DELETE FROM stores WHERE id = ?", arrayOf<Any?>(id))
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    changed()
  }

  // ---- Items ----

  fun items(): List<Item> = query("SELECT id, barcode, name FROM items ORDER BY name COLLATE NOCASE") {
    Item(it.getLong(0), if (it.isNull(1)) null else it.getString(1), it.getString(2))
  }

  fun item(id: Long): Item? = query("SELECT id, barcode, name FROM items WHERE id = ?", id) {
    Item(it.getLong(0), if (it.isNull(1)) null else it.getString(1), it.getString(2))
  }.firstOrNull()

  fun itemByBarcode(code: String): Item? = query("SELECT id, barcode, name FROM items WHERE barcode = ?", code) {
    Item(it.getLong(0), it.getString(1), it.getString(2))
  }.firstOrNull()

  fun addItem(name: String, barcode: String?): Long {
    if (barcode != null) clearBarcode(barcode)
    val v = ContentValues()
    v.put("name", name.trim())
    if (barcode != null) v.put("barcode", barcode)
    v.put("created_at", System.currentTimeMillis())
    val id = db.insert("items", null, v)
    changed()
    return id
  }

  fun renameItem(id: Long, name: String) {
    val v = ContentValues()
    v.put("name", name.trim())
    db.update("items", v, "id = ?", arrayOf(id.toString()))
    changed()
  }

  /** Attaches [code] to the item. A barcode belongs to one item, so any other holder loses it. */
  fun linkBarcode(itemId: Long, code: String) {
    clearBarcode(code)
    val v = ContentValues()
    v.put("barcode", code)
    db.update("items", v, "id = ?", arrayOf(itemId.toString()))
    changed()
  }

  private fun clearBarcode(code: String) {
    db.execSQL("UPDATE items SET barcode = NULL WHERE barcode = ?", arrayOf<Any?>(code))
  }

  // ---- Prices ----

  private const val PRICE_SELECT =
    "SELECT p.id, p.item_id, i.name, p.store_id, s.name, p.cents, p.is_sale, p.at, p.session_id " +
      "FROM prices p JOIN items i ON i.id = p.item_id JOIN stores s ON s.id = p.store_id "

  private fun priceRow(c: Cursor) = PriceRow(
    c.getLong(0), c.getLong(1), c.getString(2), c.getLong(3), c.getString(4), c.getLong(5), c.getInt(6) != 0, c.getLong(7), c.getLong(8),
  )

  fun addPrice(itemId: Long, storeId: Long, sessionId: Long, cents: Long, isSale: Boolean, source: String): Long {
    val v = ContentValues()
    v.put("item_id", itemId)
    v.put("store_id", storeId)
    v.put("session_id", sessionId)
    v.put("cents", cents)
    v.put("is_sale", if (isSale) 1 else 0)
    v.put("at", System.currentTimeMillis())
    v.put("source", source)
    val id = db.insert("prices", null, v)
    changed()
    return id
  }

  fun deletePrice(id: Long) {
    db.delete("prices", "id = ?", arrayOf(id.toString()))
    changed()
  }

  fun price(id: Long): PriceRow? = query("$PRICE_SELECT WHERE p.id = ?", id) { priceRow(it) }.firstOrNull()

  /** Newest first. */
  fun history(itemId: Long): List<PriceRow> = query("$PRICE_SELECT WHERE p.item_id = ? ORDER BY p.at DESC, p.id DESC", itemId) { priceRow(it) }

  fun stats(itemId: Long): ItemStats? {
    val item = item(itemId) ?: return null
    val h = history(itemId)
    val regular = h.filter { !it.isSale }.map { it.cents }.sorted()
    return ItemStats(
      item = item,
      count = h.size,
      last = h.firstOrNull(),
      // Ties go to the most recent.
      lowest = h.minByOrNull { it.cents },
      highest = h.maxByOrNull { it.cents },
      usualCents = if (regular.isEmpty()) null else regular[regular.size / 2],
    )
  }

  /** Items with how often they were bought and their last price, for lists and name matching. */
  fun itemsWithLast(): List<ItemStats> = items().mapNotNull { stats(it.id) }

  fun verdict(priceId: Long): Verdict? {
    val p = price(priceId) ?: return null
    return verdictFor(p, history(p.itemId))
  }

  private fun verdictFor(p: PriceRow, historyNewestFirst: List<PriceRow>): Verdict {
    val before = historyNewestFirst.filter { it.at < p.at || (it.at == p.at && it.id < p.id) }
    val last = before.firstOrNull()
    val lowest = before.minByOrNull { it.cents }
    val highest = before.maxByOrNull { it.cents }
    val spread = if (lowest != null && highest != null) highest.cents - lowest.cents else 0L
    val margin = maxOf(DEFAULT_MARGIN_CENTS, spread)
    val kind = when {
      last == null -> Kind.NEW
      p.cents < last.cents -> Kind.CHEAPER
      p.cents - last.cents > margin -> Kind.WAY_MORE
      p.cents > last.cents -> Kind.MORE
      else -> Kind.SAME
    }
    return Verdict(p, kind, last, lowest, margin)
  }

  // ---- Trips ----

  fun openSessionId(): Long? = query("SELECT id FROM sessions WHERE ended_at IS NULL ORDER BY id DESC LIMIT 1") { it.getLong(0) }.firstOrNull()

  fun startSession(storeId: Long): Long {
    val v = ContentValues()
    v.put("store_id", storeId)
    v.put("started_at", System.currentTimeMillis())
    val id = db.insert("sessions", null, v)
    changed()
    return id
  }

  /** Closes the trip. A trip with nothing scanned is removed instead. */
  fun endSession(id: Long) {
    val n = query("SELECT COUNT(*) FROM prices WHERE session_id = ?", id) { it.getInt(0) }.first()
    if (n == 0) {
      db.delete("sessions", "id = ?", arrayOf(id.toString()))
    } else {
      val v = ContentValues()
      v.put("ended_at", System.currentTimeMillis())
      db.update("sessions", v, "id = ?", arrayOf(id.toString()))
    }
    changed()
  }

  fun trip(sessionId: Long): Trip? {
    val head = query(
      "SELECT x.id, x.store_id, s.name, x.started_at, x.ended_at FROM sessions x JOIN stores s ON s.id = x.store_id WHERE x.id = ?",
      sessionId,
    ) { arrayOf<Any?>(it.getLong(0), it.getLong(1), it.getString(2), it.getLong(3), it.longOrNull(4)) }.firstOrNull() ?: return null
    val rows = query("$PRICE_SELECT WHERE p.session_id = ? ORDER BY p.at DESC, p.id DESC", sessionId) { priceRow(it) }
    val histories = HashMap<Long, List<PriceRow>>()
    val entries = rows.map { r -> verdictFor(r, histories.getOrPut(r.itemId) { history(r.itemId) }) }
    return Trip(head[0] as Long, head[1] as Long, head[2] as String, head[3] as Long, head[4] as Long?, entries)
  }

  fun recentTrips(limit: Int = 20): List<TripHead> = query(
    "SELECT x.id, s.name, x.started_at, COUNT(p.id), COALESCE(SUM(p.cents), 0) FROM sessions x " +
      "JOIN stores s ON s.id = x.store_id LEFT JOIN prices p ON p.session_id = x.id " +
      "WHERE x.ended_at IS NOT NULL GROUP BY x.id ORDER BY x.started_at DESC LIMIT $limit",
  ) { TripHead(it.getLong(0), it.getString(1), it.getLong(2), it.getInt(3), it.getLong(4)) }
}
