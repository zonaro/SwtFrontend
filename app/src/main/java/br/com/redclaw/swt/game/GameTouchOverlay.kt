/*
 * GameTouchOverlay.kt
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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

/**
 * View-based touch control overlay for on-screen gamepad.
 *
 * Renders a transparent overlay with:
 * - D-pad cross (left side)
 * - A/B face buttons (right side)
 * - L/R shoulder buttons (top)
 * - Start/Select buttons (center bottom)
 *
 * Dispatches key events to a [TouchInputListener].
 *
 * Pattern adapted from HylianBox's View-based gamepad overlays
 * (GamePad.kt, TouchControlLayout.kt) for pure Views.
 */
class GameTouchOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /**
     * Listener for touch input events dispatched by the overlay.
     */
    interface TouchInputListener {
        fun onKeyDown(keyCode: Int)
        fun onKeyUp(keyCode: Int)
    }

    var listener: TouchInputListener? = null

    // Paint objects for rendering
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        textSize = 36f
        textAlign = Paint.Align.CENTER
    }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        style = Paint.Style.FILL
    }

    // Control regions (rectangles for hit testing)
    private val dpadUp = RectF()
    private val dpadDown = RectF()
    private val dpadLeft = RectF()
    private val dpadRight = RectF()
    private val btnA = RectF()
    private val btnB = RectF()
    private val btnL = RectF()
    private val btnR = RectF()
    private val btnStart = RectF()
    private val btnSelect = RectF()

    // Currently pressed states
    private val pressedKeys = mutableSetOf<Int>()

    // Button size relative to view dimensions
    private var buttonRadius = 0f
    private var dpadCenterX = 0f
    private var dpadCenterY = 0f
    private var btnACenterX = 0f
    private var btnACenterY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buttonRadius = w * 0.06f
        val margin = w * 0.08f

        // D-pad position (left side, vertically centered)
        dpadCenterX = margin + buttonRadius * 3
        dpadCenterY = h * 0.65f

        // D-pad direction rects
        val dLen = buttonRadius * 2.2f
        val dShort = buttonRadius * 1.4f
        dpadUp.set(
            dpadCenterX - dShort / 2, dpadCenterY - dLen,
            dpadCenterX + dShort / 2, dpadCenterY - dShort / 2,
        )
        dpadDown.set(
            dpadCenterX - dShort / 2, dpadCenterY + dShort / 2,
            dpadCenterX + dShort / 2, dpadCenterY + dLen,
        )
        dpadLeft.set(
            dpadCenterX - dLen, dpadCenterY - dShort / 2,
            dpadCenterX - dShort / 2, dpadCenterY + dShort / 2,
        )
        dpadRight.set(
            dpadCenterX + dShort / 2, dpadCenterY - dShort / 2,
            dpadCenterX + dLen, dpadCenterY + dShort / 2,
        )

        // A/B buttons (right side)
        btnACenterX = w - margin - buttonRadius * 3
        btnACenterY = h * 0.65f
        val abSpacing = buttonRadius * 3.5f
        btnA.set(
            btnACenterX + abSpacing / 2 - buttonRadius,
            btnACenterY - buttonRadius,
            btnACenterX + abSpacing / 2 + buttonRadius,
            btnACenterY + buttonRadius,
        )
        btnB.set(
            btnACenterX - abSpacing / 2 - buttonRadius,
            btnACenterY - buttonRadius,
            btnACenterX - abSpacing / 2 + buttonRadius,
            btnACenterY + buttonRadius,
        )

        // L/R shoulders (top)
        btnL.set(margin, h * 0.02f, w * 0.3f, h * 0.08f)
        btnR.set(w * 0.7f, h * 0.02f, w - margin, h * 0.08f)

        // Start/Select (center bottom)
        btnStart.set(w * 0.55f, h * 0.85f, w * 0.7f, h * 0.92f)
        btnSelect.set(w * 0.3f, h * 0.85f, w * 0.45f, h * 0.92f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // D-pad
        drawDpad(canvas)

        // A/B buttons
        drawCircleButton(canvas, btnA, "A", KeyEvent.KEYCODE_BUTTON_A)
        drawCircleButton(canvas, btnB, "B", KeyEvent.KEYCODE_BUTTON_B)

        // L/R shoulders
        drawRectButton(canvas, btnL, "L", KeyEvent.KEYCODE_BUTTON_L1)
        drawRectButton(canvas, btnR, "R", KeyEvent.KEYCODE_BUTTON_R1)

        // Start/Select
        drawRectButton(canvas, btnStart, "START", KeyEvent.KEYCODE_BUTTON_START)
        drawRectButton(canvas, btnSelect, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT)
    }

    private fun drawDpad(canvas: Canvas) {
        val paint = if (pressedKeys.intersect(
                setOf(
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                ),
            ).isNotEmpty()
        ) pressedPaint else bgPaint

        // Center
        canvas.drawCircle(dpadCenterX, dpadCenterY, buttonRadius * 1.2f, paint)
        canvas.drawCircle(dpadCenterX, dpadCenterY, buttonRadius * 1.2f, borderPaint)

        // Arms
        val armLen = buttonRadius * 2f
        val armW = buttonRadius * 1f

        // Up
        canvas.drawRoundRect(
            dpadCenterX - armW / 2, dpadCenterY - armLen,
            dpadCenterX + armW / 2, dpadCenterY - armW / 2,
            4f, 4f,
            if (KeyEvent.KEYCODE_DPAD_UP in pressedKeys) pressedPaint else bgPaint,
        )
        canvas.drawRoundRect(
            dpadCenterX - armW / 2, dpadCenterY - armLen,
            dpadCenterX + armW / 2, dpadCenterY - armW / 2,
            4f, 4f,
            borderPaint,
        )

        // Down
        canvas.drawRoundRect(
            dpadCenterX - armW / 2, dpadCenterY + armW / 2,
            dpadCenterX + armW / 2, dpadCenterY + armLen,
            4f, 4f,
            if (KeyEvent.KEYCODE_DPAD_DOWN in pressedKeys) pressedPaint else bgPaint,
        )
        canvas.drawRoundRect(
            dpadCenterX - armW / 2, dpadCenterY + armW / 2,
            dpadCenterX + armW / 2, dpadCenterY + armLen,
            4f, 4f,
            borderPaint,
        )

        // Left
        canvas.drawRoundRect(
            dpadCenterX - armLen, dpadCenterY - armW / 2,
            dpadCenterX - armW / 2, dpadCenterY + armW / 2,
            4f, 4f,
            if (KeyEvent.KEYCODE_DPAD_LEFT in pressedKeys) pressedPaint else bgPaint,
        )
        canvas.drawRoundRect(
            dpadCenterX - armLen, dpadCenterY - armW / 2,
            dpadCenterX - armW / 2, dpadCenterY + armW / 2,
            4f, 4f,
            borderPaint,
        )

        // Right
        canvas.drawRoundRect(
            dpadCenterX + armW / 2, dpadCenterY - armW / 2,
            dpadCenterX + armLen, dpadCenterY + armW / 2,
            4f, 4f,
            if (KeyEvent.KEYCODE_DPAD_RIGHT in pressedKeys) pressedPaint else bgPaint,
        )
        canvas.drawRoundRect(
            dpadCenterX + armW / 2, dpadCenterY - armW / 2,
            dpadCenterX + armLen, dpadCenterY + armW / 2,
            4f, 4f,
            borderPaint,
        )
    }

    private fun drawCircleButton(canvas: Canvas, rect: RectF, label: String, keyCode: Int) {
        val isPressed = keyCode in pressedKeys
        val paint = if (isPressed) pressedPaint else bgPaint
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = buttonRadius * 1.2f

        canvas.drawCircle(cx, cy, r, paint)
        canvas.drawCircle(cx, cy, r, borderPaint)
        textPaint.textSize = r * 0.8f
        canvas.drawText(label, cx, cy + r * 0.3f, textPaint)
    }

    private fun drawRectButton(canvas: Canvas, rect: RectF, label: String, keyCode: Int) {
        val isPressed = keyCode in pressedKeys
        val paint = if (isPressed) pressedPaint else bgPaint
        canvas.drawRoundRect(rect, 12f, 12f, paint)
        canvas.drawRoundRect(rect, 12f, 12f, borderPaint)
        textPaint.textSize = rect.height() * 0.4f
        canvas.drawText(
            label,
            rect.centerX(),
            rect.centerY() + textPaint.textSize * 0.35f,
            textPaint,
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val x = event.x
        val y = event.y

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val newlyPressed = mutableSetOf<Int>()

                // D-pad hit testing
                if (dpadUp.contains(x, y)) newlyPressed.add(KeyEvent.KEYCODE_DPAD_UP)
                if (dpadDown.contains(x, y)) newlyPressed.add(KeyEvent.KEYCODE_DPAD_DOWN)
                if (dpadLeft.contains(x, y)) newlyPressed.add(KeyEvent.KEYCODE_DPAD_LEFT)
                if (dpadRight.contains(x, y)) newlyPressed.add(KeyEvent.KEYCODE_DPAD_RIGHT)

                // Face buttons
                if (btnA.contains(x, y)) newlyPressed.add(KeyEvent.KEYCODE_BUTTON_A)
                if (btnB.contains(x, y)) newlyPressed.add(KeyEvent.KEYCODE_BUTTON_B)

                // Shoulders
                if (btnL.contains(x, y)) newlyPressed.add(KeyEvent.KEYCODE_BUTTON_L1)
                if (btnR.contains(x, y)) newlyPressed.add(KeyEvent.KEYCODE_BUTTON_R1)

                // Start/Select
                if (btnStart.contains(x, y)) newlyPressed.add(KeyEvent.KEYCODE_BUTTON_START)
                if (btnSelect.contains(x, y)) newlyPressed.add(KeyEvent.KEYCODE_BUTTON_SELECT)

                // Dispatch key up for released keys
                val released = pressedKeys - newlyPressed
                released.forEach { keyCode ->
                    listener?.onKeyUp(keyCode)
                }

                // Dispatch key down for new presses
                val newPresses = newlyPressed - pressedKeys
                newPresses.forEach { keyCode ->
                    listener?.onKeyDown(keyCode)
                }

                pressedKeys.clear()
                pressedKeys.addAll(newlyPressed)
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Release all keys
                pressedKeys.forEach { keyCode ->
                    listener?.onKeyUp(keyCode)
                }
                pressedKeys.clear()
                invalidate()
            }
        }
        return true
    }
}
