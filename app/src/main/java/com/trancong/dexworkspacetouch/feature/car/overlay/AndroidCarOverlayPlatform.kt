package com.trancong.dexworkspacetouch.feature.car.overlay

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.GridLayout
import android.widget.TextView
import androidx.compose.ui.graphics.asAndroidBitmap
import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcutSlot
import com.trancong.dexworkspacetouch.platform.launch.android.legacyExternalDisplayWorkArea
import com.trancong.dexworkspacetouch.ui.design.DwtViewColors
import com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure.PackageManagerAppIconLoader
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconLoader
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconState
import java.util.concurrent.atomic.AtomicBoolean

class AndroidCarOverlayPermissionChecker(context: Context) : CarOverlayPermissionChecker {
    private val applicationContext = context.applicationContext
    override fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(applicationContext)
}

internal class AndroidCarOverlayHost(
    val androidDisplay: Display,
) : CarOverlayHost {
    override val display = CarOverlayDisplay(androidDisplay.displayId)
}

fun createActivityCarOverlayHost(activity: Activity): CarOverlayHost? {
    val display = activity.window.decorView.display ?: return null
    return display
        .takeIf { it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON }
        ?.let(::AndroidCarOverlayHost)
}

class AndroidCarOverlayDisplayEvents(context: Context) : CarOverlayDisplayEvents {
    private val displayManager = context.applicationContext.getSystemService(DisplayManager::class.java)

    override fun register(onDisplayRemoved: (Int) -> Unit): CarOverlayDisplayRegistration {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = onDisplayRemoved.invoke(displayId)
        }
        displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        return CarOverlayDisplayRegistration { displayManager.unregisterDisplayListener(listener) }
    }
}

class AndroidCarFloatingDockWindowFactory(context: Context) : CarFloatingDockWindowFactory {
    private val applicationContext = context.applicationContext
    private val appIconLoader: AppIconLoader = PackageManagerAppIconLoader.create(applicationContext)

    override fun create(
        host: CarOverlayHost,
        onShortcut: (CarWorkspaceShortcutSlot) -> Unit,
        onDockStateChanged: () -> Unit,
    ): CarFloatingDockWindow? {
        val androidDisplay = (host as? AndroidCarOverlayHost)?.androidDisplay
            ?.takeIf { it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON }
            ?: return null
        val displayContext = applicationContext.createDisplayContext(androidDisplay)
        val windowContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else {
            displayContext
        }
        val windowManager = windowContext.getSystemService(WindowManager::class.java)
        val density = windowContext.resources.displayMetrics.density
        val collapsedSize = (72 * density).toInt()
        val workArea = windowManager.carDockWorkArea(windowContext, androidDisplay)
        val initialPosition = CarFloatingDockPositioner.defaultPosition(
            workArea,
            collapsedSize,
            collapsedSize,
        )
        val view = LinearLayout(windowContext).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(DwtViewColors.Background)
            isFocusable = false
        }
        val params = WindowManager.LayoutParams(
            collapsedSize,
            collapsedSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialPosition.x
            y = initialPosition.y
            title = "Car floating dock"
        }
        return try {
            windowManager.addView(view, params)
            AndroidCarFloatingDockWindow(
                display = host.display,
                windowManager = windowManager,
                root = view,
                params = params,
                density = density,
                workArea = workArea,
                initialPosition = initialPosition,
                onShortcut = onShortcut,
                onDockStateChanged = onDockStateChanged,
                appIconLoader = appIconLoader,
            ).also(CarFloatingDockWindow::collapse)
        } catch (_: SecurityException) {
            null
        } catch (_: WindowManager.BadTokenException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }
}

private fun WindowManager.carDockWorkArea(
    context: Context,
    display: Display,
): CarFloatingDockWorkArea {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val metrics = currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
        )
        return CarFloatingDockWorkArea(
            metrics.bounds.left + insets.left,
            metrics.bounds.top + insets.top,
            metrics.bounds.right - insets.right,
            metrics.bounds.bottom - insets.bottom,
        )
    }
    val legacy = legacyExternalDisplayWorkArea(display, context)
        ?: return CarFloatingDockWorkArea(0, 0, 1, 1)
    return CarFloatingDockWorkArea(
        0,
        0,
        legacy.widthPx,
        legacy.heightPx - legacy.bottomInsetPx,
    )
}

private class AndroidCarFloatingDockWindow(
    override val display: CarOverlayDisplay,
    private val windowManager: WindowManager,
    private val root: LinearLayout,
    private val params: WindowManager.LayoutParams,
    private val density: Float,
    private val workArea: CarFloatingDockWorkArea,
    initialPosition: CarFloatingDockPosition,
    private val onShortcut: (CarWorkspaceShortcutSlot) -> Unit,
    private val onDockStateChanged: () -> Unit,
    private val appIconLoader: AppIconLoader,
) : CarFloatingDockWindow {
    private val removed = AtomicBoolean(false)
    private val shortcutViews = mutableMapOf<CarWorkspaceShortcutSlot, View>()
    private var handleView: View? = null
    private var collapseView: View? = null
    private var position = initialPosition
    private var shortcuts: List<CarFloatingWorkspaceShortcut> = emptyList()
    private var actionsEnabled = true
    override var dockState: CarFloatingDockState = CarFloatingDockState.Collapsed
        private set

    override fun expand() {
        if (removed.get() || dockState == CarFloatingDockState.Expanded) return
        dockState = CarFloatingDockState.Expanded
        root.removeAllViews()
        handleView = null
        shortcutViews.clear()
        val grid = GridLayout(root.context).apply {
            columnCount = CarFloatingDockGrid.Columns
            rowCount = CarFloatingDockGrid.rows(shortcuts.size)
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        shortcuts.forEach { shortcut ->
            grid.addView(workspaceCard(shortcut) { onShortcut(shortcut.slot) }.also { view ->
                view.isEnabled = actionsEnabled && shortcut.enabled
                view.alpha = if (view.isEnabled) 1f else 0.5f
                view.layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(CarFloatingDockGrid.row(shortcut.slot)),
                    GridLayout.spec(CarFloatingDockGrid.column(shortcut.slot), 1f),
                ).apply {
                    width = 0
                    height = (68 * density).toInt()
                }
                shortcutViews[shortcut.slot] = view
            })
        }
        root.addView(
            grid,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (CarFloatingDockGrid.rows(shortcuts.size) * CarFloatingDockGrid.TileHeightDp * density)
                    .toInt(),
            ),
        )
        root.addView(
            button("Collapse", "Collapse Floating Dock") { collapse() }
                .also { collapseView = it },
        )
        val expandedWidth = (360 * density).toInt()
            .coerceAtMost(workArea.right - workArea.left)
        val expandedHeight = (CarFloatingDockGrid.expandedHeightDp(shortcuts.size) * density)
            .toInt()
            .coerceAtMost(workArea.bottom - workArea.top)
        updateBounds(expandedWidth, expandedHeight, wrapHeight = true)
        onDockStateChanged()
    }

    private fun workspaceCard(
        shortcut: CarFloatingWorkspaceShortcut,
        onClick: () -> Unit,
    ): View = CarFloatingWorkspaceCardView(root.context, shortcut, appIconLoader).apply {
        contentDescription = shortcut.accessibilityLabel
        isClickable = true
        isFocusable = false
        minimumHeight = (56 * density).toInt()
        setOnClickListener { onClick() }
    }

    override fun collapse() {
        if (removed.get()) return
        dockState = CarFloatingDockState.Collapsed
        root.removeAllViews()
        shortcutViews.clear()
        collapseView = null
        root.addView(dragHandle().also { handleView = it })
        val size = (72 * density).toInt()
        updateBounds(size, size, wrapHeight = false)
        onDockStateChanged()
    }

    override fun setActionsEnabled(enabled: Boolean) {
        actionsEnabled = enabled
        shortcutViews.forEach { (slot, view) ->
            val shortcutEnabled = shortcuts.firstOrNull { it.slot == slot }?.enabled == true
            view.isEnabled = enabled && shortcutEnabled
            view.alpha = if (view.isEnabled) 1f else 0.5f
        }
    }

    override fun updateShortcuts(shortcuts: List<CarFloatingWorkspaceShortcut>) {
        this.shortcuts = shortcuts.toList()
        if (dockState == CarFloatingDockState.Expanded) {
            dockState = CarFloatingDockState.Collapsed
            expand()
        }
    }

    private fun button(
        label: String,
        accessibilityLabel: String = label,
        onClick: () -> Unit,
    ): TextView = configureButton(
        view = TextView(root.context),
        label = label,
        accessibilityLabel = accessibilityLabel,
        onClick = onClick,
    )

    private fun <T : TextView> configureButton(
        view: T,
        label: String,
        accessibilityLabel: String,
        onClick: () -> Unit,
    ): T = view.apply {
        text = label
        contentDescription = accessibilityLabel
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        setTextColor(DwtViewColors.OnSurface)
        isClickable = true
        isFocusable = false
        minHeight = (56 * density).toInt()
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            if (label == "CAR") LinearLayout.LayoutParams.MATCH_PARENT else (56 * density).toInt(),
        )
    }

    private fun dragHandle(): TextView {
        val gesture = CarDockDragGesture(ViewConfiguration.get(root.context).scaledTouchSlop.toFloat())
        return configureButton(
            view = CarDockDragHandleView(root.context),
            label = "CAR",
            accessibilityLabel = "Open Car workspace shortcuts",
            onClick = ::expand,
        ).apply {
            setOnTouchListener { _, event ->
                val action = when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> CarDockTouchAction.Down
                    MotionEvent.ACTION_MOVE -> CarDockTouchAction.Move
                    MotionEvent.ACTION_UP -> CarDockTouchAction.Up
                    MotionEvent.ACTION_CANCEL -> CarDockTouchAction.Cancel
                    else -> return@setOnTouchListener false
                }
                when (val result = gesture.onTouch(action, event.rawX, event.rawY, position.x, position.y)) {
                    CarDockGestureResult.None -> Unit
                    CarDockGestureResult.Tap -> performClick()
                    is CarDockGestureResult.Drag -> moveTo(result.x, result.y)
                    is CarDockGestureResult.EndDrag -> snapTo(result.x, result.y)
                }
                true
            }
        }
    }

    private fun moveTo(x: Int, y: Int) {
        val size = (72 * density).toInt()
        position = CarFloatingDockPositioner.clamp(
            x, y, position.edge, workArea, size, size,
        )
        updatePosition()
    }

    private fun snapTo(x: Int, y: Int) {
        val size = (72 * density).toInt()
        position = CarFloatingDockPositioner.snap(x, y, workArea, size, size)
        updatePosition()
    }

    private fun updateBounds(width: Int, height: Int, wrapHeight: Boolean) {
        position = CarFloatingDockPositioner.forSize(position, workArea, width, height)
        params.width = width
        params.height = if (wrapHeight) WindowManager.LayoutParams.WRAP_CONTENT else height
        updatePosition()
    }

    private fun updatePosition() {
        params.x = position.x
        params.y = position.y
        windowManager.updateViewLayout(root, params)
    }

    override fun remove() {
        if (!removed.compareAndSet(false, true)) return
        try {
            windowManager.removeViewImmediate(root)
        } catch (_: IllegalArgumentException) {
            // Display removal may detach the window before the listener runs.
        }
    }

    override fun performHandleClickForTest(): Boolean {
        if (removed.get() || handleView == null) return false
        return requireNotNull(handleView).performClick()
    }
    override fun performShortcutClickForTest(slot: CarWorkspaceShortcutSlot): Boolean =
        !removed.get() && shortcutViews[slot]?.performClick() == true
    override fun performCollapseClickForTest(): Boolean =
        !removed.get() && collapseView?.performClick() == true
    override fun positionForTest(): CarFloatingDockPosition = position
}

private class CarDockDragHandleView(context: Context) : TextView(context) {
    override fun performClick(): Boolean = super.performClick()
}

private class CarFloatingWorkspaceCardView(
    context: Context,
    private val shortcut: CarFloatingWorkspaceShortcut,
    iconLoader: AppIconLoader,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DwtViewColors.Surface }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DwtViewColors.SurfaceAlt
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DwtViewColors.Outline
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DwtViewColors.OnSurface
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val icons = shortcut.preview?.cells.orEmpty().associate { cell ->
        cell to cell.appIdentity?.let(iconLoader::loadIcon)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val outerPadding = 6f * density
        val card = RectF(outerPadding, outerPadding, width - outerPadding, height - outerPadding)
        val radius = 10f * density
        canvas.drawRoundRect(card, radius, radius, cardPaint)
        when (shortcut.state) {
            is CarFloatingWorkspaceShortcutState.Configured -> drawPreview(canvas, card)
            CarFloatingWorkspaceShortcutState.Unconfigured -> drawSymbol(canvas, card, "+")
            is CarFloatingWorkspaceShortcutState.Unavailable -> {
                canvas.drawRoundRect(card, radius, radius, borderPaint)
                drawSymbol(canvas, card, "!")
            }
        }
    }

    private fun drawPreview(canvas: Canvas, card: RectF) {
        val previewPadding = 5f * density
        val gap = 1.5f * density
        val preview = RectF(
            card.left + previewPadding,
            card.top + previewPadding,
            card.right - previewPadding,
            card.bottom - previewPadding,
        )
        shortcut.preview?.cells.orEmpty().forEach { cell ->
            val bounds = cell.bounds
            val destination = RectF(
                preview.left + preview.width() * bounds.left + gap,
                preview.top + preview.height() * bounds.top + gap,
                preview.left + preview.width() * bounds.right - gap,
                preview.top + preview.height() * bounds.bottom - gap,
            )
            if (destination.width() <= 0f || destination.height() <= 0f) return@forEach
            canvas.drawRoundRect(destination, 4f * density, 4f * density, cellPaint)
            val iconSize = minOf(destination.width(), destination.height(), 30f * density)
                .coerceAtLeast(0f)
            if (iconSize < 8f * density) return@forEach
            val left = destination.centerX() - iconSize / 2f
            val top = destination.centerY() - iconSize / 2f
            val icon = icons[cell] as? AppIconState.Ready
            if (icon != null) {
                canvas.drawBitmap(
                    icon.image.asAndroidBitmap(),
                    null,
                    RectF(left, top, left + iconSize, top + iconSize),
                    null,
                )
            } else if (cell.appIdentity != null) {
                symbolPaint.textSize = minOf(18f * density, iconSize * .65f)
                val fallback = cell.appIdentity.packageName.first().uppercase()
                val baseline = destination.centerY() -
                    (symbolPaint.ascent() + symbolPaint.descent()) / 2f
                canvas.drawText(fallback, destination.centerX(), baseline, symbolPaint)
            }
        }
    }

    private fun drawSymbol(canvas: Canvas, card: RectF, symbol: String) {
        symbolPaint.textSize = 26f * density
        val baseline = card.centerY() - (symbolPaint.ascent() + symbolPaint.descent()) / 2f
        canvas.drawText(symbol, card.centerX(), baseline, symbolPaint)
    }

    override fun performClick(): Boolean = super.performClick()
}
