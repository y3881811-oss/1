package com.example.myime

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView

class MyInputMethodService : InputMethodService() {

    private var isShifted = false
    private val letterViews = mutableListOf<Pair<TextView, String>>()
    private var shiftView: TextView? = null

    override fun onCreateInputView(): View {
        return buildKeyboardView()
    }

    private fun buildKeyboardView(): View {
        letterViews.clear()
        shiftView = null

        val keyboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#D1D5DB"))
            setPadding(dp(3), dp(8), dp(3), dp(8))
        }

        // 第一行：q w e r t y u i o p
        val row1 = createRow()
        listOf(
            "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
            "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0"
        ).forEach { (letter, alt) ->
            row1.addView(createLetterKey(letter, alt, 1f))
        }
        keyboard.addView(row1)

        // 第二行：a s d f g h j k l
        val row2 = createRow()
        listOf(
            "a" to "~", "s" to "!", "d" to "@", "f" to "#",
            "g" to "%", "h" to "'", "j" to "&", "k" to "*", "l" to "?"
        ).forEach { (letter, alt) ->
            row2.addView(createLetterKey(letter, alt, 1f))
        }
        keyboard.addView(row2)

        // 第三行：Shift + z x c v b n m + Backspace
        val row3 = createRow()
        val shift = createFunctionKey("⇧", 1.4f, active = isShifted) {
            isShifted = !isShifted
            updateShiftState()
        }
        shiftView = shift
        row3.addView(shift)
        for (c in "zxcvbnm") {
            row3.addView(createLetterKey(c.toString(), null, 1f))
        }
        row3.addView(createFunctionKey("⌫", 1.4f, active = false) {
            deleteBackward()
        })
        keyboard.addView(row3)

        // 第四行
        val row4 = createRow()
        row4.addView(createFunctionKey("!?#", 1f, active = false) { /* TODO 符号布局 */ })
        row4.addView(createFunctionKey("123", 1f, active = false) { /* TODO 数字布局 */ })
        row4.addView(createFunctionKey(",", 0.7f, active = false) { commit(",") })
        row4.addView(createSpaceKey(4.5f))
        row4.addView(createFunctionKey(".", 0.7f, active = false) { commit(".") })
        row4.addView(createFunctionKey("中/英", 1.2f, active = false) { /* TODO 中英切换 */ })
        row4.addView(createFunctionKey("换行", 1.4f, active = false) { sendEnterKey() })
        keyboard.addView(row4)

        updateShiftState()
        return keyboard
    }

    private fun createRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
            )
        }
    }

    /**
     * 字母键：主字母 + 上方小数字/符号（可选）。
     */
    private fun createLetterKey(letter: String, alt: String?, weight: Float): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                weight
            ).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
            background = createKeyBackground(isFunction = false, isActive = false)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val text = if (isShifted) letter.uppercase() else letter
                commit(text)
                if (isShifted) {
                    isShifted = false
                    updateShiftState()
                }
            }
        }

        if (alt != null) {
            val altView = TextView(this).apply {
                text = alt
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(Color.parseColor("#333333"))
                gravity = Gravity.CENTER
            }
            container.addView(altView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply {
                topMargin = dp(4)
            })
        }

        val mainView = TextView(this).apply {
            text = if (isShifted) letter.uppercase() else letter
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }
        container.addView(mainView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0,
            if (alt != null) 2f else 3f
        ))

        letterViews.add(mainView to letter)
        return container
    }

    private fun createFunctionKey(
        label: String,
        weight: Float,
        active: Boolean,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                weight
            ).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
            background = createKeyBackground(isFunction = true, isActive = active)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun createSpaceKey(weight: Float): TextView {
        return TextView(this).apply {
            text = "🎤"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                weight
            ).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
            background = createKeyBackground(isFunction = false, isActive = false)
            isClickable = true
            isFocusable = true
            setOnClickListener { commit(" ") }
        }
    }

    private fun createKeyBackground(isFunction: Boolean, isActive: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            when {
                isActive -> setColor(Color.WHITE)
                isFunction -> setColor(Color.parseColor("#ADB3BC"))
                else -> setColor(Color.WHITE)
            }
        }
    }

    private fun updateShiftState() {
        for ((view, letter) in letterViews) {
            view.text = if (isShifted) letter.uppercase() else letter
        }
        shiftView?.background = createKeyBackground(isFunction = true, isActive = isShifted)
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun deleteBackward() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun sendEnterKey() {
        val ic = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}