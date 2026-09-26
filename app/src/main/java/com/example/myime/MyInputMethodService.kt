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
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

class MyInputMethodService : InputMethodService() {

    private enum class KeyboardMode { LETTERS, NUMBERS, SYMBOLS, HANDWRITING }
    private enum class InputMode { EN, ZH }
    private enum class KeyType { CHAR, SHIFT, BACKSPACE, ENTER, SPACE, MODE_SWITCH, LANG_SWITCH }

    private data class Key(
        val label: String,
        val alt: String? = null,
        val weight: Float = 1f,
        val type: KeyType = KeyType.CHAR,
        val target: KeyboardMode? = null
    )

    private var keyboardMode = KeyboardMode.LETTERS
    private var inputMode = InputMode.EN
    private var isShifted = false

    private var rootView: LinearLayout? = null
    private var candidateBar: LinearLayout? = null
    private var handwritingView: HandwritingView? = null

    private val recognizer by lazy { HandwritingRecognizer(this) }

    override fun onCreateInputView(): View {
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#D1D5DB"))
            setPadding(dp(3), dp(8), dp(3), dp(8))
        }
        rootView = view
        rebuildKeyboard()
        return view
    }

    private fun rebuildKeyboard() {
        val root = rootView ?: return
        root.removeAllViews()
        handwritingView = null

        if (keyboardMode == KeyboardMode.HANDWRITING) {
            addCandidateBar(root)
            addHandwritingArea(root)
        } else {
            val rows = when (keyboardMode) {
                KeyboardMode.LETTERS -> letterRows()
                KeyboardMode.NUMBERS -> numberRows()
                KeyboardMode.SYMBOLS -> symbolRows()
                KeyboardMode.HANDWRITING -> return
            }
            for (row in rows) root.addView(buildRow(row))
        }
    }

    private fun addCandidateBar(root: LinearLayout) {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#EFEFEF"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
            )
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        candidateBar = bar

        val scroll = HorizontalScrollView(this).apply {
            horizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(bar)
        }

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
            )
            setBackgroundColor(Color.parseColor("#EFEFEF"))
            addView(scroll)
        }
        root.addView(wrapper)
    }

    private fun addHandwritingArea(root: LinearLayout) {
        val hw = HandwritingView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setRecognizer(recognizer)
            setOnResultListener { candidates -> updateCandidates(candidates) }
        }
        handwritingView = hw
        root.addView(hw)

        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
            )
        }
        bottomRow.addView(createFunctionKey(
            Key("清空", weight = 1.2f, type = KeyType.CHAR)
        ) { handwritingView?.clear(); clearCandidates() })
        bottomRow.addView(createFunctionKey(
            Key("空格", weight = 1.2f, type = KeyType.SPACE)
        ) { currentInputConnection?.commitText(" ", 1) })
        bottomRow.addView(createFunctionKey(
            Key("⌫", weight = 1.2f, type = KeyType.BACKSPACE)
        ) { deleteBackward() })
        bottomRow.addView(createFunctionKey(
            Key(",", weight = 0.7f, type = KeyType.CHAR)
        ) { commit(",") })
        bottomRow.addView(createFunctionKey(
            Key(".", weight = 0.7f, type = KeyType.CHAR)
        ) { commit(".") })
        bottomRow.addView(createFunctionKey(
            Key("EN", weight = 1.2f, type = KeyType.LANG_SWITCH)
        ) {
            inputMode = InputMode.EN
            keyboardMode = KeyboardMode.LETTERS
            rebuildKeyboard()
        })
        bottomRow.addView(createFunctionKey(
            Key("换行", weight = 1.4f, type = KeyType.ENTER)
        ) { sendEnterKey() })
        root.addView(bottomRow)
    }

    private fun updateCandidates(candidates: List<String>) {
        val bar = candidateBar ?: return
        bar.removeAllViews()
        for (cand in candidates) {
            val tv = TextView(this).apply {
                text = cand
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTextColor(Color.BLACK)
                setPadding(dp(20), dp(6), dp(20), dp(6))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    commit(cand)
                    handwritingView?.clear()
                    clearCandidates()
                }
            }
            bar.addView(tv)
        }
    }

    private fun clearCandidates() {
        candidateBar?.removeAllViews()
    }

    // ================= 布局定义 =================

    private fun letterRows(): List<List<Key>> = listOf(
        listOf(
            Key("q", "1"), Key("w", "2"), Key("e", "3"), Key("r", "4"), Key("t", "5"),
            Key("y", "6"), Key("u", "7"), Key("i", "8"), Key("o", "9"), Key("p", "0")
        ),
        listOf(
            Key("a", "~"), Key("s", "!"), Key("d", "@"), Key("f", "#"),
            Key("g", "%"), Key("h", "'"), Key("j", "&"), Key("k", "*"), Key("l", "?")
        ),
        listOf(
            Key("⇧", weight = 1.4f, type = KeyType.SHIFT),
            Key("z"), Key("x"), Key("c"), Key("v"), Key("b"), Key("n"), Key("m"),
            Key("⌫", weight = 1.4f, type = KeyType.BACKSPACE)
        ),
        listOf(
            Key("!?#", type = KeyType.MODE_SWITCH, target = KeyboardMode.SYMBOLS),
            Key("123", type = KeyType.MODE_SWITCH, target = KeyboardMode.NUMBERS),
            Key(",", weight = 0.7f),
            Key("🎤", weight = 4.5f, type = KeyType.SPACE),
            Key(".", weight = 0.7f),
            Key(langLabel(), weight = 1.2f, type = KeyType.LANG_SWITCH),
            Key("换行", weight = 1.4f, type = KeyType.ENTER)
        )
    )

    private fun numberRows(): List<List<Key>> = listOf(
        listOf(
            Key("1"), Key("2"), Key("3"), Key("4"), Key("5"),
            Key("6"), Key("7"), Key("8"), Key("9"), Key("0")
        ),
        listOf(
            Key("-"), Key("/"), Key(":"), Key(";"), Key("("),
            Key(")"), Key("$"), Key("&"), Key("@"), Key("\"")
        ),
        listOf(
            Key("#+=", weight = 1.4f, type = KeyType.MODE_SWITCH, target = KeyboardMode.SYMBOLS),
            Key("."), Key(","), Key("?"), Key("!"), Key("'"),
            Key("⌫", weight = 1.4f, type = KeyType.BACKSPACE)
        ),
        listOf(
            Key("ABC", type = KeyType.MODE_SWITCH, target = KeyboardMode.LETTERS),
            Key(",", weight = 0.7f),
            Key("🎤", weight = 4.5f, type = KeyType.SPACE),
            Key(".", weight = 0.7f),
            Key(langLabel(), weight = 1.2f, type = KeyType.LANG_SWITCH),
            Key("换行", weight = 1.4f, type = KeyType.ENTER)
        )
    )

    private fun symbolRows(): List<List<Key>> = listOf(
        listOf(
            Key("["), Key("]"), Key("{"), Key("}"), Key("#"),
            Key("%"), Key("^"), Key("*"), Key("+"), Key("=")
        ),
        listOf(
            Key("_"), Key("\\"), Key("|"), Key("~"), Key("<"),
            Key(">"), Key("€"), Key("£"), Key("¥"), Key("•")
        ),
        listOf(
            Key("123", weight = 1.4f, type = KeyType.MODE_SWITCH, target = KeyboardMode.NUMBERS),
            Key("."), Key(","), Key("?"), Key("!"), Key("'"),
            Key("⌫", weight = 1.4f, type = KeyType.BACKSPACE)
        ),
        listOf(
            Key("ABC", type = KeyType.MODE_SWITCH, target = KeyboardMode.LETTERS),
            Key(",", weight = 0.7f),
            Key("🎤", weight = 4.5f, type = KeyType.SPACE),
            Key(".", weight = 0.7f),
            Key(langLabel(), weight = 1.2f, type = KeyType.LANG_SWITCH),
            Key("换行", weight = 1.4f, type = KeyType.ENTER)
        )
    )

    private fun langLabel(): String = if (inputMode == InputMode.EN) "EN" else "中"

    // ================= 渲染 =================

    private fun buildRow(keys: List<Key>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
            )
        }.also { row ->
            for (key in keys) row.addView(createKeyView(key))
        }
    }

    private fun createKeyView(key: Key): View = when (key.type) {
        KeyType.CHAR -> createCharKey(key)
        KeyType.SPACE -> createFunctionKey(key) { currentInputConnection?.commitText(" ", 1) }
        KeyType.BACKSPACE -> createFunctionKey(key) { deleteBackward() }
        KeyType.ENTER -> createFunctionKey(key) { sendEnterKey() }
        KeyType.SHIFT -> createFunctionKey(key, active = isShifted) {
            isShifted = !isShifted
            rebuildKeyboard()
        }
        KeyType.MODE_SWITCH -> createFunctionKey(key) {
            key.target?.let {
                keyboardMode = it
                isShifted = false
                rebuildKeyboard()
            }
        }
        KeyType.LANG_SWITCH -> createFunctionKey(key) {
            if (inputMode == InputMode.EN) {
                inputMode = InputMode.ZH
                keyboardMode = KeyboardMode.HANDWRITING
            } else {
                inputMode = InputMode.EN
                keyboardMode = KeyboardMode.LETTERS
            }
            rebuildKeyboard()
        }
    }

    private fun createCharKey(key: Key): View {
        val upper = isShifted && keyboardMode == KeyboardMode.LETTERS
        val displayLabel = if (upper) key.label.uppercase() else key.label
        val showAlt = key.alt != null && keyboardMode == KeyboardMode.LETTERS

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, key.weight
            ).apply {
                marginStart = dp(3); marginEnd = dp(3)
            }
            background = createKeyBackground(isFunction = false, isActive = false)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val text = if (isShifted && keyboardMode == KeyboardMode.LETTERS) {
                    key.label.uppercase()
                } else key.label
                commit(text)
                if (isShifted) {
                    isShifted = false
                    rebuildKeyboard()
                }
            }
        }

        if (showAlt) {
            container.addView(TextView(this).apply {
                text = key.alt
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(Color.parseColor("#333333"))
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(4)
            })
        }

        container.addView(TextView(this).apply {
            text = displayLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0,
            if (showAlt) 2f else 3f
        ))

        return container
    }

    private fun createFunctionKey(key: Key, active: Boolean = false, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = key.label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, key.weight
            ).apply {
                marginStart = dp(3); marginEnd = dp(3)
            }
            background = createKeyBackground(isFunction = true, isActive = active)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
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

    // ================= 输入行为 =================

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun deleteBackward() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) ic.commitText("", 1)
        else ic.deleteSurroundingText(1, 0)
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

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics
    ).toInt()
}