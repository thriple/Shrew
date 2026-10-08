package com.shrew.scan

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.location.Location
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.shrew.data.OcrLine
import com.shrew.data.normalizeBarcode
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** What one tap on Scan found in a single frame. */
class Frame(val image: Bitmap, val barcode: String?, val lines: List<OcrLine>)

/**
 * Tap-to-capture scanner. A tap takes one frame (kept in memory only, never saved), looks for a
 * retail barcode, then reads the text on the same frame.
 */
class Scanner {
  @Volatile var capture: ImageCapture? = null
  private val worker = Executors.newSingleThreadExecutor()

  private val barcodes: BarcodeScanner by lazy {
    BarcodeScanning.getClient(
      BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .build(),
    )
  }
  private val text: TextRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

  /** Captures and analyses one frame. Null when the camera is not ready or the capture failed. */
  suspend fun scan(readText: Boolean = true): Frame? {
    val bmp = shoot() ?: return null
    val input = InputImage.fromBitmap(bmp, 0)
    val codes = barcodes.process(input).awaitOrNull().orEmpty()
    val cx = bmp.width / 2
    val cy = bmp.height / 2
    // Several codes in view: take the one nearest the middle of the frame.
    val code = codes
      .filter { !it.rawValue.isNullOrEmpty() }
      .minByOrNull { b -> b.boundingBox?.let { abs(it.centerX() - cx) + abs(it.centerY() - cy) } ?: Int.MAX_VALUE }
      ?.rawValue
      ?.let { normalizeBarcode(it) }
    val lines = ArrayList<OcrLine>()
    if (readText) {
      val result = text.process(input).awaitOrNull()
      result?.textBlocks?.forEach { block ->
        block.lines.forEach { line ->
          val box = line.boundingBox
          if (box != null) lines.add(OcrLine(line.text, box.left, box.top, box.height(), box.width()))
        }
      }
    }
    return Frame(bmp, code, lines)
  }

  private suspend fun shoot(): Bitmap? {
    val cap = capture ?: return null
    return suspendCancellableCoroutine<Bitmap?> { cont ->
      cap.takePicture(
        worker,
        object : ImageCapture.OnImageCapturedCallback() {
          override fun onCaptureSuccess(image: ImageProxy) {
            val out = try {
              upright(image.toBitmap(), image.imageInfo.rotationDegrees)
            } catch (e: Exception) {
              null
            } finally {
              image.close()
            }
            if (cont.isActive) cont.resume(out)
          }

          override fun onError(exception: ImageCaptureException) {
            if (cont.isActive) cont.resume(null)
          }
        },
      )
    }
  }

  /** Rotates to upright and caps the long side, so text boxes line up with what is shown. */
  private fun upright(src: Bitmap, degrees: Int): Bitmap {
    val long = max(src.width, src.height)
    val scale = if (long > 1600) 1600f / long else 1f
    if (degrees == 0 && scale == 1f) return src
    val m = Matrix()
    m.postScale(scale, scale)
    m.postRotate(degrees.toFloat())
    val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    if (out !== src) src.recycle()
    return out
  }
}

/** Live camera preview bound to the screen's lifecycle; feeds [scanner] its capture use case. */
@Composable
fun CameraPreview(scanner: Scanner, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val owner = LocalLifecycleOwner.current
  val view = remember {
    PreviewView(context).apply {
      // TextureView-based, so the preview clips to the notched panel.
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
      scaleType = PreviewView.ScaleType.FILL_CENTER
    }
  }
  DisposableEffect(owner) {
    val future = ProcessCameraProvider.getInstance(context)
    var provider: ProcessCameraProvider? = null
    val preview = Preview.Builder().build()
    val cap = ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
      .setResolutionSelector(
        ResolutionSelector.Builder()
          .setResolutionStrategy(ResolutionStrategy(Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
          .build(),
      )
      .build()
    var disposed = false
    future.addListener(
      {
        if (disposed) return@addListener
        try {
          val p = future.get()
          provider = p
          preview.setSurfaceProvider(view.surfaceProvider)
          // A screen change briefly composes two previews; the newer one takes the camera.
          p.unbindAll()
          p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, cap)
          scanner.capture = cap
        } catch (e: Exception) {
          if (scanner.capture === cap) scanner.capture = null
        }
      },
      ContextCompat.getMainExecutor(context),
    )
    onDispose {
      disposed = true
      if (scanner.capture === cap) scanner.capture = null
      // Only this preview's own use cases, so an incoming screen keeps the camera.
      provider?.unbind(preview, cap)
    }
  }
  AndroidView(factory = { view }, modifier = modifier)
}

/** A single foreground location fix at trip start. Never runs in the background. */
object Locator {
  @SuppressLint("MissingPermission")
  suspend fun fix(context: Context): Location? {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val token = CancellationTokenSource()
    val current = withTimeoutOrNull(12_000) {
      client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).awaitOrNull()
    }
    if (current == null) token.cancel()
    return current ?: client.lastLocation.awaitOrNull()
  }
}

suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine<T?> { cont ->
  addOnSuccessListener { if (cont.isActive) cont.resume(it) }
  addOnFailureListener { if (cont.isActive) cont.resume(null) }
  addOnCanceledListener { if (cont.isActive) cont.resume(null) }
}
