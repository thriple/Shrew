package com.shrew

import android.Manifest
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.shrew.data.Money
import com.shrew.ui.C
import com.shrew.ui.LocalReduceMotion
import com.shrew.ui.screens.CameraIntroScreen
import com.shrew.ui.screens.HomeScreen
import com.shrew.ui.screens.ItemHistoryScreen
import com.shrew.ui.screens.ItemMatchScreen
import com.shrew.ui.screens.ItemsScreen
import com.shrew.ui.screens.LoadingScreen
import com.shrew.ui.screens.LocationAskScreen
import com.shrew.ui.screens.NameEntryScreen
import com.shrew.ui.screens.NameReadScreen
import com.shrew.ui.screens.PriceScreen
import com.shrew.ui.screens.ScanScreen
import com.shrew.ui.screens.StoreConfirmScreen
import com.shrew.ui.screens.StoreDetailScreen
import com.shrew.ui.screens.StoresScreen
import com.shrew.ui.screens.SummaryScreen
import com.shrew.ui.screens.TripListScreen
import com.shrew.ui.screens.VerdictScreen
import java.util.Currency
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
  private lateinit var app: AppState

  private val cameraPrompt = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    app.onCameraResult(granted)
  }

  private val locationPrompt = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
    app.onLocationResult(result.values.any { it })
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
    Money.symbol = try {
      Currency.getInstance(Locale.getDefault()).symbol.takeIf { it.length <= 2 } ?: "$"
    } catch (e: Exception) {
      "$"
    }
    app = AppState(this, lifecycleScope)
    app.askCamera = { cameraPrompt.launch(Manifest.permission.CAMERA) }
    app.askLocation = {
      locationPrompt.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val reduce = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    setContent {
      CompositionLocalProvider(LocalReduceMotion provides reduce) { App(app) }
    }
  }

  override fun onResume() {
    super.onResume()
    // The user may have changed a permission in system settings.
    if (::app.isInitialized) app.refreshPermissions()
  }
}

@Composable
private fun App(app: AppState) {
  val reduce = LocalReduceMotion.current
  BackHandler(enabled = app.stack.size > 1) { app.back() }
  Box(Modifier.fillMaxSize().background(C.Surface)) {
    AnimatedContent(
      targetState = app.top,
      transitionSpec = {
        if (reduce) {
          EnterTransition.None togetherWith ExitTransition.None
        } else {
          // Content enters from 0.96 scale to sharp (general spring 420/32).
          val ratio = 32f / (2f * sqrt(420f))
          (fadeIn(spring(dampingRatio = ratio, stiffness = 420f)) + scaleIn(spring(dampingRatio = ratio, stiffness = 420f), initialScale = 0.96f)) togetherWith
            fadeOut(tween(90))
        }
      },
      label = "screen",
    ) { s ->
      when (s) {
        Scr.Loading -> LoadingScreen(app)
        Scr.CameraIntro -> CameraIntroScreen(app)
        Scr.Home -> HomeScreen(app)
        Scr.LocationAsk -> LocationAskScreen(app)
        Scr.StoreConfirm -> StoreConfirmScreen(app)
        is Scr.Scan -> ScanScreen(app, s.mode)
        Scr.NameRead -> NameReadScreen(app)
        Scr.ItemMatch -> ItemMatchScreen(app)
        Scr.Price -> PriceScreen(app)
        Scr.Verdict -> VerdictScreen(app)
        Scr.TripList -> TripListScreen(app)
        is Scr.Summary -> SummaryScreen(app, s.sessionId)
        Scr.Items -> ItemsScreen(app)
        is Scr.ItemHistory -> ItemHistoryScreen(app, s.itemId)
        Scr.Stores -> StoresScreen(app)
        is Scr.StoreDetail -> StoreDetailScreen(app, s.storeId)
        is Scr.NameEntry -> NameEntryScreen(app, s.purpose, s.targetId)
      }
    }
  }
}
