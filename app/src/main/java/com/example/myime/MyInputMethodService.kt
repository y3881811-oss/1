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

    // 搜狗风格配色
    private val colorKeyboardBg = Color.parseColor("#EDEEF0")
    private val colorKeyBg = Color.WHITE
    private val colorFunctionKeyBg = Color.parseColor("#C8CCD2")
    private val colorEnterKeyBg = Color.parseColor("#4A90E2")
    private val colorCandidateBg = Color.WHITE
    private val colorTextPrimary = Color.parseColor("#111111")
    private val colorTextAlt = Color.parseColor("#666666")

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
            setBackgroundColor(colorKeyboardBg)
            setPadding(dp(4), dp(8), dp(4), dp(8))
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
            addHandwritingBottomRow(root)
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

    // ============ 候选栏 ============
    private fun addCandidateBar(root: LinearLayout) {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(colorCandidateBg)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)
            )
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        candidateBar = bar

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
            )
            addView(bar)
        }

        // 白底 + 底部一条细分割线
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)
            )
        }
        wrapper.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
        ))
        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#DDDDDD"))
        }
        wrapper.addView(divider, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        ))

        root.addView(wrapper)
    }

    // ============ 手写区域 ============
    private fun addHandwritingArea(root: LinearLayout) {
        val area = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setPadding(dp(2), dp(6), dp(2), dp(6))
        }

        // 左：手写画布，白底圆角
        val hw = HandwritingView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
            ).apply {
                marginEnd = dp(6)
            }
            background = createRoundedBackground(Color.WHITE, dp(10))
            setRecognizer(recognizer)
            setOnResultListener { candidates -> updateCandidates(candidates) }
        }
        handwritingView = hw
        area.addView(hw)

        // 右：竖排标点列
        val sideColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                dp(56), ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val sideKeys = listOf<Pair<String, () -> Unit>>(
            "⌫" to { deleteBackward() },
            "，" to { commit("，") },
            "。" to { commit("。") },
            "？" to { commit("？") },
            "！" to { commit("！") }
        )
        for ((label, action) in sideKeys) {
            val isFunctionKey = label == "⌫"
            val tv = TextView(this).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(colorTextPrimary)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
                ).apply {
                    bottomMargin = dp(4)
                }
                background = createRoundedBackground(
                    if (isFunctionKey) colorFunctionKeyBg else colorKeyBg,
                    dp(8)
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }
            sideColumn.addView(tv)
        }
        area.addView(sideColumn)
        root.addView(area)
    }

    // ============ 手写模式底部功能行 ============
    private fun addHandwritingBottomRow(root: LinearLayout) {
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
            )
        }
        bottomRow.addView(createFunctionKey(
            Key("清空", weight = 1.2f, type = KeyType.CHAR)
        ) { handwritingView?.clear(); clearCandidates() })
        bottomRow.addView(createFunctionKey(
            Key("空格", weight = 2.5f, type = KeyType.SPACE)
        ) { currentInputConnection?.commitText(" ", 1) })
        bottomRow.addView(createFunctionKey(
            Key("中/英", weight = 1.4f, type = KeyType.LANG_SWITCH)
        ) {
            inputMode = InputMode.EN
            keyboardMode = KeyboardMode.LETTERS
            rebuildKeyboard()
        })
        bottomRow.addView(createEnterKey(
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
                setTextColor(colorTextPrimary)
                setPadding(dp(18), dp(4), dp(18), dp(4))
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
            Key("符", type = KeyType.MODE_SWITCH, target = KeyboardMode.SYMBOLS),
            Key("123", type = KeyType.MODE_SWITCH, target = KeyboardMode.NUMBERS),
            Key("，", weight = 0.8f),
            Key("空格", weight = 4.5f, type = KeyType.SPACE),
            Key("。", weight = 0.8f),
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
            Key("，", weight = 0.8f),
            Key("空格", weight = 4.5f, type = KeyType.SPACE),
            Key("。", weight = 0.8f),
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
            Key("，", weight = 0.8f),
            Key("空格", weight = 4.5f, type = KeyType.SPACE),
            Key("。", weight = 0.8f),
            Key(langLabel(), weight = 1.2f, type = KeyType.LANG_SWITCH),
            Key("换行", weight = 1.4f, type = KeyType.ENTER)
        )
    )

    private fun langLabel(): String = if (inputMode == InputMode.EN) "中/英" else "中/英"

    // ================= 渲染 =================

    private fun buildRow(keys: List<Key>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            )
        }.also { row ->
            for (key in keys) row.addView(createKeyView(key))
        }
    }

    private fun createKeyView(key: Key): View = when (key.type) {
        KeyType.CHAR -> createCharKey(key)
        KeyType.SPACE -> createFunctionKey(key) { currentInputConnection?.commitText(" ", 1) }
        KeyType.BACKSPACE -> createFunctionKey(key) { deleteBackward() }
        KeyType.ENTER -> createEnterKey(key) { sendEnterKey() }
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
                topMargin = dp(3); bottomMargin = dp(3)
            }
            background = createRoundedBackground(colorKeyBg, dp(10))
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(colorTextAlt)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(4)
            })
        }

        container.addView(TextView(this).apply {
            text = displayLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(colorTextPrimary)
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colorTextPrimary)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, key.weight
            ).apply {
                marginStart = dp(3); marginEnd = dp(3)
                topMargin = dp(3); bottomMargin = dp(3)
            }
            background = createRoundedBackground(
                if (active) Color.WHITE else colorFunctionKeyBg,
                dp(10)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun createEnterKey(key: Key, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = key.label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, key.weight
            ).apply {
                marginStart = dp(3); marginEnd = dp(3)
                topMargin = dp(3); bottomMargin = dp(3)
            }
            background = createRoundedBackground(colorEnterKeyBg, dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun createRoundedBackground(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(color)
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