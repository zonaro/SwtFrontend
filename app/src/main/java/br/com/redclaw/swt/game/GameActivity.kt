/*
 * GameActivity.kt
 *
 * Copyright (C) 2026 RedClaw Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package br.com.redclaw.swt.game

import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import br.com.redclaw.swt.R
import br.com.redclaw.swt.cores.CoreUpdaterImpl
import br.com.redclaw.swt.views.ScaledAppCompatActivity
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.SaveState
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Game activity — renders a libretro core via [GLRetroView].
 *
 * Responsibilities:
 * - Resolve core .so path via [CoreUpdaterImpl] + [DirectoriesManager]
 * - Render with [GLRetroView] (lifecycle-aware)
 * - Handle save states (slot 0 + auto-save) via [StatesManager]
 * - Handle battery saves (SRAM) via [SavesManager]
 * - In-game menu overlay (resume, save/load, reset, quit)
 * - Physical gamepad input via [onKeyDown]/[onGenericMotionEvent]
 * - Touch overlay via [GameTouchOverlay] (shown when no gamepad)
 * - DS dual-screen: core variables for melonds/desmume screen layout
 * - External display support via [SecondaryDisplayPresentation]
 */
class GameActivity : ScaledAppCompatActivity(), GameTouchOverlay.TouchInputListener {

    companion object {
        private const val TAG = "SwtGameActivity"
        const val EXTRA_GAME = "game"
        const val EXTRA_SYSTEM_CORE_CONFIG = "system_core_config"
        const val EXTRA_LOAD_SAVE = "load_save"

        fun launch(
            context: Context,
            game: Game,
            systemCoreConfig: SystemCoreConfig,
            loadSave: Boolean = true,
        ) {
            val intent = Intent(context, GameActivity::class.java).apply {
                putExtra(EXTRA_GAME, game)
                putExtra(EXTRA_SYSTEM_CORE_CONFIG, systemCoreConfig)
                putExtra(EXTRA_LOAD_SAVE, loadSave)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var retroViewContainer: FrameLayout
    private lateinit var touchOverlay: GameTouchOverlay
    private lateinit var menuComposeView: ComposeView

    private var retroView: GLRetroView? = null
    private lateinit var gameLoaderHelper: GameLoaderHelper
    private lateinit var directoriesManager: DirectoriesManager
    private lateinit var statesManager: StatesManager
    private lateinit var savesManager: SavesManager

    private var currentGame: Game? = null
    private var currentSystemCoreConfig: SystemCoreConfig? = null
    private var isGameLoaded = false
    private var isMenuVisible by mutableStateOf(false)
    private var hasGamepad = false
    private var showSecondaryButton by mutableStateOf(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var secondaryPresentation: Presentation? = null
    private var secondaryDisplayId: Int = Display.DEFAULT_DISPLAY

    // region Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()

        retroViewContainer = findViewById(R.id.retro_view_container)
        touchOverlay = findViewById(R.id.touch_overlay)
        findViewById<View>(R.id.menu_overlay).visibility = View.GONE

        val container = findViewById<FrameLayout>(R.id.game_container)
        menuComposeView = ComposeView(this).apply {
            setContent {
                GameMenuOverlay(
                    visible = isMenuVisible,
                    showSecondaryButton = showSecondaryButton,
                    onResume = { toggleMenu() },
                    onSaveState = {
                        scope.launch { saveState() }
                        toggleMenu()
                    },
                    onLoadState = {
                        scope.launch { loadState() }
                        toggleMenu()
                    },
                    onReset = {
                        retroView?.reset()
                        toggleMenu()
                    },
                    onRestart = { scope.launch { restartGame() } },
                    onSecondaryDisplay = {
                        toggleSecondaryDisplay()
                        toggleMenu()
                    },
                    onQuit = { scope.launch { saveSnapshotAndFinish() } },
                )
            }
        }
        container.addView(menuComposeView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        touchOverlay.listener = this
        initializeComponents()

        val game = intent.getSerializableExtra(EXTRA_GAME) as? Game
        val coreConfig = intent.getSerializableExtra(EXTRA_SYSTEM_CORE_CONFIG) as? SystemCoreConfig
        val loadSave = intent.getBooleanExtra(EXTRA_LOAD_SAVE, true)

        if (game == null || coreConfig == null) {
            Log.e(TAG, "Missing game or system core config in intent")
            Toast.makeText(this, "Failed to load game: missing data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentGame = game
        currentSystemCoreConfig = coreConfig
        hasGamepad = checkHasGamepad()
        touchOverlay.visibility = if (hasGamepad) View.GONE else View.VISIBLE

        scope.launch { loadGame(game, coreConfig, loadSave) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enterImmersiveMode()
    }

    override fun onPause() {
        super.onPause()
        scope.launch { autoSaveOnPause() }
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissSecondaryDisplay()
        retroView?.let { lifecycle.removeObserver(it) }
        scope.cancel()
    }

    // endregion

    // region Initialization

    private fun initializeComponents() {
        directoriesManager = DirectoriesManager(applicationContext)
        val coreUpdater = CoreUpdaterImpl(applicationContext, directoriesManager)
        gameLoaderHelper = GameLoaderHelper(applicationContext, directoriesManager, coreUpdater)
        statesManager = StatesManager(directoriesManager)
        savesManager = SavesManager(directoriesManager)
    }

    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    private fun checkHasGamepad(): Boolean {
        val deviceIds = InputDevice.getDeviceIds()
        for (id in deviceIds) {
            val device = InputDevice.getDevice(id) ?: continue
            val sources = device.sources
            if (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            ) {
                return true
            }
        }
        return false
    }

    // endregion

    // region Game Loading

    private suspend fun loadGame(
        game: Game,
        systemCoreConfig: SystemCoreConfig,
        loadSave: Boolean,
    ) {
        try {
            val coreFile = gameLoaderHelper.resolveCorePath(systemCoreConfig.coreID)
            if (coreFile == null) {
                Log.e(TAG, "Core not downloaded: ${systemCoreConfig.coreID.coreName}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@GameActivity,
                        "Core not available: ${systemCoreConfig.coreID.coreDisplayName}",
                        Toast.LENGTH_LONG,
                    ).show()
                    finish()
                }
                return
            }

            val gameFile = gameLoaderHelper.resolveGameFile(game)
            if (gameFile == null) {
                Log.e(TAG, "Failed to resolve game file: ${game.fileUri}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GameActivity, "Failed to load game file", Toast.LENGTH_SHORT).show()
                    finish()
                }
                return
            }

            val coreVariables = mutableListOf<Variable>()
            systemCoreConfig.defaultSettings.forEach { cv ->
                coreVariables.add(Variable(cv.key, cv.value))
            }

            if (gameLoaderHelper.isDualScreenSystem(game)) {
                val dsLayout = GameLoaderHelper.DsScreenLayout.TOP_BOTTOM
                coreVariables.add(gameLoaderHelper.getDsScreenLayoutVariable(systemCoreConfig.coreID, dsLayout))
            }

            var saveRAMData: ByteArray? = null
            if (loadSave) {
                saveRAMData = try {
                    savesManager.getSaveRAM(game, systemCoreConfig)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load save RAM", e)
                    null
                }
            }

            val viewData = GLRetroViewData(applicationContext).apply {
                coreFilePath = coreFile.absolutePath
                gameFilePath = gameFile.absolutePath
                systemDirectory = gameLoaderHelper.getSystemDirectory().absolutePath
                savesDirectory = gameLoaderHelper.getSavesDirectory().absolutePath
                variables = coreVariables.toTypedArray()
                saveRAMState = saveRAMData
                shader = ShaderConfig.Default
                rumbleEventsEnabled = systemCoreConfig.rumbleSupported
                skipDuplicateFrames = systemCoreConfig.skipDuplicateFrames
                enableMicrophone = systemCoreConfig.supportsMicrophone
            }

            withContext(Dispatchers.Main) {
                createRetroView(viewData, game, systemCoreConfig, loadSave)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load game", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@GameActivity, "Failed to load game: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun createRetroView(
        viewData: GLRetroViewData,
        game: Game,
        systemCoreConfig: SystemCoreConfig,
        loadSave: Boolean,
    ) {
        val view = GLRetroView(this, viewData).apply {
            isFocusable = false
            isFocusableInTouchMode = false
        }

        retroView = view
        retroViewContainer.addView(view)
        lifecycle.addObserver(view)

        isGameLoaded = true
        Log.i(TAG, "Game loaded: ${game.title} (${systemCoreConfig.coreID.coreName})")

        if (loadSave && systemCoreConfig.statesSupported) {
            scope.launch { restoreAutoSave(game, systemCoreConfig) }
        }

        if (gameLoaderHelper.isDualScreenSystem(game)) {
            showSecondaryButton = true
        }
    }

    private suspend fun restoreAutoSave(game: Game, systemCoreConfig: SystemCoreConfig) {
        try {
            val autoSave = statesManager.getAutoSave(game, systemCoreConfig.coreID)
            if (autoSave != null) {
                withContext(Dispatchers.Main) {
                    retroView?.unserializeState(autoSave.state)
                    Log.d(TAG, "Auto-save restored")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore auto-save", e)
        }
    }

    // endregion

    // region Gamepad Input

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            toggleMenu()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_START || keyCode == KeyEvent.KEYCODE_MENU) {
            toggleMenu()
            return true
        }
        retroView?.sendKeyEvent(KeyEvent.ACTION_DOWN, keyCode, 0)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        retroView?.sendKeyEvent(KeyEvent.ACTION_UP, keyCode, 0)
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        event?.let {
            val xAxis = it.getAxisValue(MotionEvent.AXIS_X)
            val yAxis = it.getAxisValue(MotionEvent.AXIS_Y)
            val zAxis = it.getAxisValue(MotionEvent.AXIS_Z)
            val rzAxis = it.getAxisValue(MotionEvent.AXIS_RZ)

            if (xAxis != 0f || yAxis != 0f) {
                retroView?.sendMotionEvent(0, xAxis, yAxis, 0)
            }
            if (zAxis != 0f || rzAxis != 0f) {
                retroView?.sendMotionEvent(1, zAxis, rzAxis, 0)
            }
        }
        return super.onGenericMotionEvent(event)
    }

    // endregion

    // region Touch Input Listener

    override fun onKeyDown(keyCode: Int) {
        retroView?.sendKeyEvent(KeyEvent.ACTION_DOWN, keyCode, 0)
    }

    override fun onKeyUp(keyCode: Int) {
        retroView?.sendKeyEvent(KeyEvent.ACTION_UP, keyCode, 0)
    }

    // endregion

    // region Menu

    private fun toggleMenu() {
        isMenuVisible = !isMenuVisible
        retroView?.frameSpeed = if (isMenuVisible) 0 else 1
    }

    // endregion

    // region Save States

    private suspend fun saveState() {
        val game = currentGame ?: return
        val config = currentSystemCoreConfig ?: return
        if (!config.statesSupported) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@GameActivity, "Save states not supported", Toast.LENGTH_SHORT).show()
            }
            return
        }
        try {
            val state = retroView?.serializeState() ?: return
            statesManager.setSlotSave(game, SaveState(state, SaveState.Metadata()), config.coreID, 0)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@GameActivity, "State saved", Toast.LENGTH_SHORT).show()
            }
            Log.i(TAG, "State saved to slot 0")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save state", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@GameActivity, "Failed to save state", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun loadState() {
        val game = currentGame ?: return
        val config = currentSystemCoreConfig ?: return
        if (!config.statesSupported) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@GameActivity, "Save states not supported", Toast.LENGTH_SHORT).show()
            }
            return
        }
        try {
            val saveState = statesManager.getSlotSave(game, config.coreID, 0)
            if (saveState != null) {
                withContext(Dispatchers.Main) {
                    val success = retroView?.unserializeState(saveState.state) ?: false
                    Toast.makeText(
                        this@GameActivity,
                        if (success) "State loaded" else "Failed to load state",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GameActivity, "No save state found", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load state", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@GameActivity, "Failed to load state", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun saveSnapshotAndFinish() {
        val game = currentGame ?: return
        val config = currentSystemCoreConfig ?: return
        try {
            val sram = retroView?.serializeSRAM()
            if (sram != null && sram.isNotEmpty()) {
                savesManager.setSaveRAM(game, sram)
            }
            if (config.statesSupported) {
                val state = retroView?.serializeState()
                if (state != null) {
                    statesManager.setAutoSave(game, config.coreID, SaveState(state, SaveState.Metadata()))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save snapshot", e)
        }
        dismissSecondaryDisplay()
        finish()
    }

    private suspend fun autoSaveOnPause() {
        val game = currentGame ?: return
        val config = currentSystemCoreConfig ?: return
        try {
            if (config.statesSupported) {
                val state = retroView?.serializeState()
                if (state != null) {
                    statesManager.setAutoSave(game, config.coreID, SaveState(state, SaveState.Metadata()))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Auto-save on pause failed", e)
        }
    }

    private suspend fun restartGame() {
        val game = currentGame ?: return
        val config = currentSystemCoreConfig ?: return
        saveSnapshotAndFinish()
        launch(this, game, config, true)
    }

    // endregion

    // region DS Secondary Display

    private fun toggleSecondaryDisplay() {
        if (secondaryPresentation != null) {
            dismissSecondaryDisplay()
            return
        }
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val externalDisplay = displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

        if (externalDisplay == null) {
            Toast.makeText(this, "No external display found", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            secondaryDisplayId = externalDisplay.displayId
            secondaryPresentation = SecondaryGamePresentation(this, externalDisplay).apply { show() }
            Log.i(TAG, "Secondary display started on display $secondaryDisplayId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start secondary display", e)
            Toast.makeText(this, "Failed to start secondary display", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dismissSecondaryDisplay() {
        try {
            secondaryPresentation?.dismiss()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dismiss secondary display", e)
        }
        secondaryPresentation = null
        secondaryDisplayId = Display.DEFAULT_DISPLAY
    }

    /**
     * Presentation for DS bottom screen on external display.
     * Pattern from Kwiq's SecondaryDisplayPresentation.
     */
    inner class SecondaryGamePresentation(
        hostActivity: Context,
        display: Display,
    ) : Presentation(hostActivity, display) {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val ctx = context ?: return

            val metrics = android.util.DisplayMetrics()
            display.getMetrics(metrics)

            val composeView = ComposeView(ctx).apply {
                setContent {
                    SecondaryScreen(
                        coreDisplayName = currentSystemCoreConfig?.coreID?.coreDisplayName,
                        screenWidthPx = metrics.widthPixels,
                        screenHeightPx = metrics.heightPixels,
                    )
                }
            }

            @Suppress("DEPRECATION")
            window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )

            setContentView(composeView)
        }
    }

    // endregion
}
