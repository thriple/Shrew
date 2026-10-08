package com.shrew.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Free product-name lookup in Open Food Facts. Coverage varies by region; null on any failure. */
object Lookup {
  suspend fun productName(barcode: String): String? = withContext(Dispatchers.IO) {
    try {
      val url = URL("https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=product_name,brands,quantity")
      val c = url.openConnection() as HttpURLConnection
      c.connectTimeout = 4000
      c.readTimeout = 4000
      c.setRequestProperty("User-Agent", "Shrew/0.1 (Android grocery price tracker)")
      try {
        if (c.responseCode != 200) return@withContext null
        val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        if (json.optInt("status") != 1) return@withContext null
        val p = json.optJSONObject("product") ?: return@withContext null
        val name = p.optString("product_name").trim()
        if (name.isEmpty()) return@withContext null
        val brand = p.optString("brands").split(',').firstOrNull()?.trim().orEmpty()
        val qty = p.optString("quantity").trim()
        buildString {
          if (brand.isNotEmpty() && !name.contains(brand, ignoreCase = true)) append(brand).append(' ')
          append(name)
          if (qty.isNotEmpty() && !name.contains(qty, ignoreCase = true)) append(", ").append(qty)
        }
      } finally {
        c.disconnect()
      }
    } catch (e: Exception) {
      null
    }
  }
}
