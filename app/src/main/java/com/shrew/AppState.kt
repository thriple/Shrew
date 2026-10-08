package com.shrew

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.shrew.data.NameChip
import com.shrew.data.PriceCandidate
import com.shrew.data.Repo
import com.shrew.scan.Locator
import com.shrew.scan.Scanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class ScanMode { ITEM, TAG, LINK }

enum class Purpose { TYPE_ITEM, NEW_ITEM, NEW_STORE, RENAME_STORE, RENAME_ITEM }

sealed interface Scr {
  data object Loading : Scr
  data object CameraIntro : Scr
  data object Home : Scr
  data object LocationAsk : Scr
  data object StoreConfirm : Scr
  data class Scan(val mode: ScanMode) : Scr
  data object NameRead : Scr
  data object ItemMatch : Scr
  data object Price : Scr
  data object Verdict : Scr
  data object TripList : Scr
  data class Summary(val sessionId: Long) : Scr
  data object Items : Scr
  data class ItemHistory(val itemId: Long) : Scr
  data object Stores : Scr
  data class StoreDetail(val storeId: Long) : Scr
  data class NameEntry(val purpose: Purpose, val targetId: Long = 0) : Scr
}

/** Where the location step stands on the store-confirm screen. */
sealed interface Fix {
  data object Locating : Fix
  data class At(val lat: Double, val lng: Double) : Fix
  /** No fix: permission refused, location off, or the user chose to pick. */
  data object None : Fix
}

/**
 * Screen stack plus the state of the scan in progress. Lives as long as the activity.
 * The trip itself (store, session, prices) is in the database, so it survives a restart.
 */
class AppState(private val activity: Activity, val scope: CoroutineScope) {
  val scanner = Scanner()
  val appContext: Context = activity.applicationContext
  private val prefs = activity.getSharedPreferences("shrew", Context.MODE_PRIVATE)

  val stack = mutableStateListOf<Scr>(Scr.Loading)
  val top: Scr get() = stack.last()

  fun go(s: Scr) { stack.add(s) }
  fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
  fun replace(s: Scr) { stack[stack.lastIndex] = s }
  fun reset(vararg s: Scr) {
    stack.clear()
    stack.addAll(s.toList())
  }

  /** Back to the camera for the current trip. */
  fun toScan() = reset(Scr.Home, Scr.Scan(ScanMode.ITEM))

  // ---- Permissions ----

  var cameraGranted by mutableStateOf(has(Manifest.permission.CAMERA))
    private set
  var locationGranted by mutableStateOf(hasLocation())
    private set

  /** Set by the activity: launch Android's prompts. */
  var askCamera: () -> Unit = {}
  var askLocation: () -> Unit = {}

  private fun has(p: String) = ContextCompat.checkSelfPermission(activity, p) == PackageManager.PERMISSION_GRANTED
  private fun hasLocation() = has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)

  fun refreshPermissions() {
    cameraGranted = has(Manifest.permission.CAMERA)
    locationGranted = hasLocation()
  }

  var cameraIntroDone: Boolean
    get() = prefs.getBoolean("camera_intro_done", false)
    set(v) { prefs.edit().putBoolean("camera_intro_done", v).apply() }

  private var cameraAsked: Boolean
    get() = prefs.getBoolean("camera_asked", false)
    set(v) { prefs.edit().putBoolean("camera_asked", v).apply() }

  private var locationAsked: Boolean
    get() = prefs.getBoolean("location_asked", false)
    set(v) { prefs.edit().putBoolean("location_asked", v).apply() }

  /** Android stops showing its prompt after two refusals; then only settings can grant it. */
  val cameraBlocked: Boolean
    get() = !cameraGranted && cameraAsked && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)

  val locationBlocked: Boolean
    get() = !locationGranted && locationAsked &&
      !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)

  fun requestCamera() {
    if (cameraBlocked) openSettings() else {
      cameraAsked = true
      askCamera()
    }
  }

  fun onCameraResult(granted: Boolean) {
    cameraGranted = granted
    if (top == Scr.CameraIntro) {
      cameraIntroDone = true
      reset(Scr.Home)
    }
  }

  fun openSettings() {
    activity.startActivity(
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
  }

  // ---- Trip start ----

  var fix by mutableStateOf<Fix>(Fix.None)
  private var fixJob: Job? = null

  /** Home's "Start shopping". */
  fun startShopping() {
    val open = Repo.openSessionId()
    if (open != null) {
      toScan()
      return
    }
    if (locationGranted) locate() else go(Scr.LocationAsk)
  }

  /** Location box: Continue. */
  fun requestLocation() {
    if (locationBlocked) {
      pickStoreMyself()
      return
    }
    locationAsked = true
    askLocation()
  }

  fun onLocationResult(granted: Boolean) {
    locationGranted = granted
    if (top == Scr.LocationAsk) {
      if (granted) {
        replace(Scr.StoreConfirm)
        startFix()
      } else {
        fix = Fix.None
        replace(Scr.StoreConfirm)
      }
    }
  }

  fun pickStoreMyself() {
    fixJob?.cancel()
    fix = Fix.None
    if (top == Scr.StoreConfirm) return
    if (top == Scr.LocationAsk) replace(Scr.StoreConfirm) else go(Scr.StoreConfirm)
  }

  private fun locate() {
    go(Scr.StoreConfirm)
    startFix()
  }

  private fun startFix() {
    fix = Fix.Locating
    fixJob?.cancel()
    fixJob = scope.launch {
      val loc = try { Locator.fix(activity) } catch (e: Exception) { null }
      fix = if (loc != null) Fix.At(loc.latitude, loc.longitude) else Fix.None
    }
  }

  /** Starts the trip at [storeId] and opens the camera. */
  fun startTrip(storeId: Long) {
    val f = fix
    if (f is Fix.At) Repo.setStoreLocationIfMissing(storeId, f.lat, f.lng)
    Repo.startSession(storeId)
    clearScan()
    toScan()
  }

  val sessionId: Long? get() = Repo.openSessionId()

  fun endTrip() {
    val id = sessionId ?: return
    Repo.endSession(id)
    clearScan()
    if (Repo.trip(id) != null) reset(Scr.Home, Scr.Summary(id)) else reset(Scr.Home)
  }

  // ---- The item being scanned ----

  /** The frame behind the "name read" screen. Memory only. */
  var frame by mutableStateOf<Bitmap?>(null)
  val chips = mutableStateListOf<NameChip>()
  /** Name read off the pack, looked up, or typed. */
  var nameRead by mutableStateOf("")
  /** A barcode not yet in the list, waiting to be attached to the item the user picks or saves. */
  var pendingBarcode by mutableStateOf<String?>(null)
  var priceCandidates by mutableStateOf<List<PriceCandidate>>(emptyList())
  var itemId by mutableStateOf<Long?>(null)
  /** Item that the LINK scan attaches a barcode to. */
  var linkItemId by mutableStateOf<Long?>(null)
  var lastPriceId by mutableStateOf<Long?>(null)

  fun clearScan() {
    frame = null
    chips.clear()
    nameRead = ""
    pendingBarcode = null
    priceCandidates = emptyList()
    itemId = null
    linkItemId = null
  }

  fun openItem(id: Long) {
    itemId = id
    go(Scr.Price)
  }
}
